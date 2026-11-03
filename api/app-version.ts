type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.70",
    versionCode: 91,
    releaseDate: "2026-11-03",
    apkUrl: "https://goorno-app.vercel.app/GoOrNo-v3.3.70.apk",
    directDownload: "/GoOrNo.apk",
    fileSizeBytes: 55377368,
    fileSizeFormatted: "55.4 MB",
    changelog: [
      "🎯 Nota do Passageiro Garantida: Captura perfeita com suporte a emojis e no primeiro card de Solicitações.",
      "💰 Correção da Tarifa Expresso: Descarte de subtarifas e captura do valor cheio da corrida.",
      "🔢 Fusão de Centavos Fragmentados: Unificação imediata de valores como R$ 44,80 e R$ 7,70.",
      "⚡ Estabilidade e Descarte de Ruído: Elementos fora da tela e dados antigos bloqueados."
    ],
    mandatory: false,
  });
}
