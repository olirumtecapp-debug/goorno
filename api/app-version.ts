type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.117",
    versionCode: 138,
    releaseDate: "2026-11-21",
    apkUrl: "https://raw.githubusercontent.com/olirumtecapp-debug/goorno/main/GoOrNo.apk",
    directDownload: "/GoOrNo.apk",
    fileSizeBytes: 57400000,
    fileSizeFormatted: "54.8 MB",
    changelog: [
      "🔁 Chamados Repetidos Inteligentes: ofertas reenviadas pelo app não somam mais no total diário.",
      "📊 Histórico com Métricas Reais: badge discreto 'Chamado repetido (não computado)' sem poluição visual.",
      "🛡️ Blindagem Total: todas as regras de cálculo, multi-janela 99 e persistência mantidas 100% intactas."
    ],
    mandatory: true,
  });
}
