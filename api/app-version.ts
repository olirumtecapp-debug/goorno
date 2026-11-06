type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.76",
    versionCode: 97,
    releaseDate: "2026-11-06",
    apkUrl: "https://raw.githubusercontent.com/olirumtecapp-debug/goorno/main/GoOrNo.apk",
    directDownload: "/GoOrNo.apk",
    fileSizeBytes: 55400000,
    fileSizeFormatted: "55.4 MB",
    changelog: [
      "🛡️ Blindagem Total Anti-Falsos Positivos: Telas de painel, recompensas e postos de combustível da 99 totalmente ignoradas.",
      "👤 Detecção Inteligente de Passageiro Novo: Fim dos '--' poluindo a tela e histórico.",
      "⚡ Velocidade e semáforo financeiro 100% preservados."
    ],
    mandatory: false,
  });
}
