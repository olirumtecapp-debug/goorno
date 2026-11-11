type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.97",
    versionCode: 118,
    releaseDate: "2026-11-11",
    apkUrl: "https://raw.githubusercontent.com/olirumtecapp-debug/goorno/main/GoOrNo.apk",
    directDownload: "/GoOrNo.apk",
    fileSizeBytes: 56300000,
    fileSizeFormatted: "53.7 MB",
    changelog: [
      "📹 Câmera Secreta GoOrNo: Dashcam inteligente em segundo plano sem travar Uber e 99.",
      "🔒 100% Gratuito e Local: Gravação circular em loop sem consumo de internet ou nuvem paga.",
      "⚙️ Configuração Completa: Qualidade 360p/480p/720p, Lente Frontal/Traseira e corte de 1 a 15 min.",
      "🎥 Galeria de Gravações: Miniaturas com duração, player nativo com marca d'água oficial e botão de compartilhamento.",
      "🎛️ Atalho Flutuante: Controle ao vivo de gravação e troca instantânea de lente no botão flutuante."
    ],
    mandatory: true,
  });
}
