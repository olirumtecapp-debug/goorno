type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.115",
    versionCode: 136,
    releaseDate: "2026-11-18",
    apkUrl: "https://raw.githubusercontent.com/olirumtecapp-debug/goorno/main/GoOrNo.apk",
    directDownload: "/GoOrNo.apk",
    fileSizeBytes: 57400000,
    fileSizeFormatted: "54.8 MB",
    changelog: [
      "🔥 Space Grotesk Bold Maxima: espessura encorpada de alto contraste para visibilidade impecável sob sol forte.",
      "👁️ Legibilidade Instantanea: números destacados e agrupados para leitura imediata pelo motorista no suporte.",
      "🛡️ Blindagem Total: todas as regras de leitura, OCR de nota da 99 e persistência mantidas 100% intactas."
    ],
    mandatory: true,
  });
}
