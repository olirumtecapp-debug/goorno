type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.116",
    versionCode: 137,
    releaseDate: "2026-11-19",
    apkUrl: "https://raw.githubusercontent.com/olirumtecapp-debug/goorno/main/GoOrNo.apk",
    directDownload: "/GoOrNo.apk",
    fileSizeBytes: 57400000,
    fileSizeFormatted: "54.8 MB",
    changelog: [
      "⚡ Multi-Window Unified: varredura unificada simultânea de todas as janelas e popups da 99.",
      "🚀 Leitura Instantânea em 35ms: eliminação do atraso em transições de janelas sem depender de OCR.",
      "🛡️ Blindagem Total: todas as regras de cálculo, Space Grotesk Bold e persistência mantidas 100% intactas."
    ],
    mandatory: true,
  });
}
