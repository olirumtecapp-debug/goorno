type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.67",
    versionCode: 88,
    releaseDate: "2026-11-02",
    apkUrl: "https://goorno-app.vercel.app/GoOrNo-v3.3.67.apk",
    directDownload: "/GoOrNo.apk",
    fileSizeBytes: 55360988,
    fileSizeFormatted: "55.4 MB",
    changelog: [
      "🚀 Leitura Uber 100% Restaurada: Correção de captura para chamadas nativas e fallback visual rápido.",
      "🎯 Nota do Passageiro Garantida: Captura perfeita mesmo sob sobreposição de janela e no primeiro card de Solicitações.",
      "🏷️ Pílulas Flutuantes Estritamente Abaixo do Card: Design limpo e sem poluição visual na tela.",
      "⚡ Fim dos Travamentos e ANR: Execução leve e responsiva em background."
    ],
    mandatory: false,
  });
}
