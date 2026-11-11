type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.99",
    versionCode: 120,
    releaseDate: "2026-11-11",
    apkUrl: "https://raw.githubusercontent.com/olirumtecapp-debug/goorno/main/GoOrNo.apk",
    directDownload: "/GoOrNo.apk",
    fileSizeBytes: 57300000,
    fileSizeFormatted: "54.6 MB",
    changelog: [
      "🚪 Tela de Boas-Vindas & Login Obrigatório: Entrada rápida com 'Continuar com o Google'.",
      "⏳ Contagem Regressiva de 7 Dias Grátis: Barra visual com dias restantes e status na tela inicial.",
      "☁️ Sincronização Automática: Áreas de risco, metas e custos salvos na conta Google.",
      "📹 Câmera Secreta GoOrNo: Dashcam discreta sem travar Uber ou 99.",
      "🔒 100% Gratuito: Sem senhas ou códigos por SMS."
    ],
    mandatory: true,
  });
}
