type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.71",
    versionCode: 92,
    releaseDate: "2026-11-03",
    apkUrl: "https://goorno-app.vercel.app/GoOrNo-v3.3.71.apk",
    directDownload: "/GoOrNo.apk",
    fileSizeBytes: 55377368,
    fileSizeFormatted: "55.4 MB",
    changelog: [
      "⚡ Resposta Instantânea na 99: Fim do delay na lista de Solicitações (leitura imediata sem precisar atualizar a tela).",
      "📲 Atualização em Tempo Real: Verificação automática de nova versão sempre que abrir ou retornar ao app.",
      "🎯 Nota do Passageiro Garantida: Captura perfeita mesmo com emojis no primeiro card de Solicitações.",
      "💰 Correção da Tarifa Expresso: Descarte de subtarifas e captura do valor cheio da corrida."
    ],
    mandatory: false,
  });
}
