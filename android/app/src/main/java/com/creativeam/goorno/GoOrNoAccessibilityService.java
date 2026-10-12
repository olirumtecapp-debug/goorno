package com.creativeam.goorno;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoOrNoAccessibilityService extends AccessibilityService {

    private static final Pattern PRICE_PATTERN = Pattern.compile("R\\$\\s*([0-9]{1,3}[.,][0-9]{2})");
    private static final Pattern KM_PATTERN = Pattern.compile("([0-9]{1,3}(?:[.,][0-9]{1,2})?)\\s*km", Pattern.CASE_INSENSITIVE);
    private static final Pattern MIN_PATTERN = Pattern.compile("([0-9]{1,3})\\s*min", Pattern.CASE_INSENSITIVE);

    private String lastSignature = "";
    private long lastCaptureTime = 0L;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString().toLowerCase(Locale.ROOT);

        // Monitora Uber, 99 e inDrive
        boolean isDriverApp = pkg.contains("ubercab") || pkg.contains("taxis99") || pkg.contains("didi") || pkg.contains("indriver");
        if (!isDriverApp) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        List<String> texts = new ArrayList<>();
        collectTexts(root, texts);
        root.recycle();

        if (texts.isEmpty()) return;
        String joined = String.join(" | ", texts);

        Matcher priceMatcher = PRICE_PATTERN.matcher(joined);
        if (!priceMatcher.find()) return;

        double offeredValue = parseDoubleSafe(priceMatcher.group(1));
        if (offeredValue < 4.5 || offeredValue > 600.0) return;

        List<Double> kms = new ArrayList<>();
        Matcher kmMatcher = KM_PATTERN.matcher(joined);
        while (kmMatcher.find()) {
            double v = parseDoubleSafe(kmMatcher.group(1));
            if (v >= 0.1 && v <= 250.0) kms.add(v);
        }
        if (kms.isEmpty()) return;

        List<Integer> mins = new ArrayList<>();
        Matcher minMatcher = MIN_PATTERN.matcher(joined);
        while (minMatcher.find()) {
            int m = (int) parseDoubleSafe(minMatcher.group(1));
            if (m >= 1 && m <= 300) mins.add(m);
        }

        double pickupKm = kms.size() >= 2 ? kms.get(0) : 1.2;
        double tripKm = kms.size() >= 2 ? kms.get(1) : kms.get(0);
        double totalKm = Math.max(0.2, pickupKm + tripKm);

        int pickupMin = mins.size() >= 2 ? mins.get(0) : 4;
        int tripMin = mins.size() >= 2 ? mins.get(1) : (mins.isEmpty() ? 15 : mins.get(0));
        int totalMin = Math.max(1, pickupMin + tripMin);

        String sig = String.format(Locale.US, "%.2f_%.1f_%d", offeredValue, totalKm, totalMin);
        long now = System.currentTimeMillis();
        if (sig.equals(lastSignature) && (now - lastCaptureTime) < 8000) {
            return;
        }
        lastSignature = sig;
        lastCaptureTime = now;

        double perKm = offeredValue / totalKm;
        double perHour = (offeredValue / totalMin) * 60.0;
        double netProfit = offeredValue - (totalKm * 0.75);

        String verdict = (perKm >= 2.0 && perHour >= 40.0) ? "🟢 GO! ACEITAR" : (perKm >= 1.5 ? "🟡 ATENÇÃO" : "🔴 NO! RECUSAR");
        String platform = pkg.contains("99") || pkg.contains("didi") ? "99" : (pkg.contains("indriver") ? "inDrive" : "Uber");

        String summaryToast = String.format(
            Locale.getDefault(),
            "%s | %s R$ %.2f\nR$ %.2f/km • R$ %.0f/h • Lucro: R$ %.2f",
            verdict, platform, offeredValue, perKm, perHour, netProfit
        );

        new Handler(Looper.getMainLooper()).post(() ->
            Toast.makeText(getApplicationContext(), summaryToast, Toast.LENGTH_LONG).show()
        );

        // Envia para o histórico e Street View do GoOrNo na nuvem
        final String rawPayload = joined.replace("\"", "'");
        new Thread(() -> {
            try {
                URL url = new URL("https://goorno-app.vercel.app/api/rides/scan-screenshot");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);
                String json = "{\"rawText\":\"" + rawPayload + "\",\"fileName\":\"" + platform + "\"}";
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {}
        }).start();
    }

    private void collectTexts(AccessibilityNodeInfo node, List<String> out) {
        if (node == null) return;
        if (node.getText() != null && node.getText().length() > 0) {
            out.add(node.getText().toString().trim());
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectTexts(child, out);
                child.recycle();
            }
        }
    }

    private double parseDoubleSafe(String s) {
        try {
            return Double.parseDouble(s.replace(",", ".").trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    @Override
    public void onInterrupt() {}
}
