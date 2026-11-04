type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.74",
    versionCode: 95,
    releaseDate: "2026-11-04",
    apkUrl: "https://goorno-app.vercel.app/GoOrNo-v3.3.74.apk",
    directDownload: "/GoOrNo.apk",
    fileSizeBytes: 55393880,
    fileSizeFormatted: "55.4 MB",
    changelog: [
      "📬 Caixa Postal Oficial: Suporte concentrado 100% no app com histórico de respostas.",
      "🛡️ Blindagem de Atualização: Sem necessidade de forçar parada ou limpar cache manualmente.",
      "🔒 Exclusão Segura: Remoção definitiva de contas com confirmação protegida no painel.",
      "⚡ Estabilidade Reforçada: Conexão contínua com os serviços de acessibilidade."
    ],
    mandatory: false,
  });
}
