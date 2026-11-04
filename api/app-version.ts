type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.75",
    versionCode: 96,
    releaseDate: "2026-11-04",
    apkUrl: "https://goorno-app.vercel.app/GoOrNo.apk",
    directDownload: "/GoOrNo.apk",
    fileSizeBytes: 55393880,
    fileSizeFormatted: "55.4 MB",
    changelog: [
      "⚡ Restauração Completa da Acessibilidade: Conexão direta e imediata sem bloqueios ou telas travadas.",
      "📬 Suporte no App: Visualize respostas direto na aba Caixa de Entrada do app GoOrNo."
    ],
    mandatory: true,
  });
}
