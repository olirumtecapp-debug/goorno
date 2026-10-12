package br.com.creativeam.goorno;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GoOrNo Native Android Accessibility Service
 * Monitora chamadas de corrida em tempo real nos pacotes:
 * - com.ubercab.driver (Uber Driver)
 * - com.taxis99.driver / com.didiglobal.driver (99 Motorista)
 * - sinet.startup.inDriver (inDrive)
 */
public class GoOrNoAccessibilityService extends AccessibilityService {

    private static final Pattern PRICE_PATTERN = Pattern.compile("R\\$\\s*([0-9]+[.,][0-9]{2})");
    private static final Pattern KM_PATTERN = Pattern.compile("([0-9]+[.,]?[0-9]*)\\s*km", Pattern.CASE_INSENSITIVE);
    private static final Pattern MIN_PATTERN = Pattern.compile("([0-9]+)\\s*min", Pattern.CASE_INSENSITIVE);

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        List<String> texts = new ArrayList<>();
        collectTexts(root, texts);
        root.recycle();

        String fullScreenText = String.join(" | ", texts);
        Matcher priceMatcher = PRICE_PATTERN.matcher(fullScreenText);
        if (!priceMatcher.find()) return; // Nenhuma oferta de corrida na tela

        double offeredValue = parseNumber(priceMatcher.group(1));
        List<Double> kms = new ArrayList<>();
        Matcher kmMatcher = KM_PATTERN.matcher(fullScreenText);
        while (kmMatcher.find()) {
            kms.add(parseNumber(kmMatcher.group(1)));
        }

        List<Integer> mins = new ArrayList<>();
        Matcher minMatcher = MIN_PATTERN.matcher(fullScreenText);
        while (minMatcher.find()) {
            mins.add((int) parseNumber(minMatcher.group(1)));
        }

        if (offeredValue > 3.0 && !kms.isEmpty()) {
            double pickupKm = kms.size() > 1 ? kms.get(0) : 1.0;
            double tripKm = kms.size() > 1 ? kms.get(1) : kms.get(0);
            int pickupMin = mins.size() > 1 ? mins.get(0) : 4;
            int tripMin = mins.size() > 1 ? mins.get(1) : (mins.isEmpty() ? 15 : mins.get(0));
            String platform = pkg.contains("99") || pkg.contains("didi") ? "99" : (pkg.contains("inDriver") ? "inDrive" : "Uber");

            GoOrNoOverlayBridge.dispatchRideOffer(this, platform, offeredValue, pickupKm, tripKm, pickupMin, tripMin, texts);
        }
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

    private double parseNumber(String raw) {
        try {
            return Double.parseDouble(raw.replace(",", ".").trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    @Override
    public void onInterrupt() {}
}
