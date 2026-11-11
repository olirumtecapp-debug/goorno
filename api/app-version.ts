type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.98",
    versionCode: 119,
    releaseDate: "2026-11-11",
    apkUrl: "https://raw.githubusercontent.com/olirumtecapp-debug/goorno/main/GoOrNo.apk",
    directDownload: "/GoOrNo.apk",
    fileSizeBytes: 56500000,
    fileSizeFormatted: "53.9 MB",
    changelog: [
      "🔑 Continuar com o Google: Login 100% gratuito, sem senhas ou códigos por SMS.",
      "☁️ Sincronização Automática: Licença, configurações e metas salvas na sua conta Google.",
      "📹 Câmera Secreta GoOrNo: Dashcam discreta em segundo plano sem travar Uber ou 99.",
      "🔒 100% Gratuito e Local: Gravação circular em loop sem consumo de internet.",
      "🎥 Galeria de Gravações & Player com marca d'água oficial GoOrNo."
    ],
    mandatory: true,
  });
}
