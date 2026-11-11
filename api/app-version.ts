type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.96",
    versionCode: 117,
    releaseDate: "2026-11-11",
    apkUrl: "https://raw.githubusercontent.com/olirumtecapp-debug/goorno/main/GoOrNo.apk",
    directDownload: "/GoOrNo.apk",
    fileSizeBytes: 55410000,
    fileSizeFormatted: "52.8 MB",
    changelog: [
      "🔥 Correção Crítica de Fonte: Substituído arquivo corrompido pela fonte binária autêntica Plus Jakarta Sans Bold.",
      "✅ Histórico Preciso: Corridas só contam como Aceitas se você marcar explicitamente.",
      "⚡ Ação Rápida 'Aceitou?': Confirme corridas aceitas com 1 toque no atalho flutuante ou na notificação.",
      "🚦 Semáforo & Metas: Visual limpo com Plus Jakarta Sans Bold e sombras dinâmicas de alto contraste."
    ],
    mandatory: true,
  });
}
