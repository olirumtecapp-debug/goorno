type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.73",
    versionCode: 94,
    releaseDate: "2026-11-04",
    apkUrl: "https://goorno-app.vercel.app/GoOrNo-v3.3.73.apk",
    directDownload: "/GoOrNo.apk",
    fileSizeBytes: 55377368,
    fileSizeFormatted: "55.4 MB",
    changelog: [
      "🛡️ Blindagem de Acessibilidade: Botão flutuante 100% transparente para a leitura da tela, eliminando pontos cegos na 99 e Uber.",
      "👁️ Ocultar e Restaurar Botão: Opção de ocultar o atalho flutuante e trazê-lo de volta com 1 clique direto na notificação superior.",
      "⏹️ Desativar Copiloto: Acesso rápido para pausar o monitoramento direto no menu flutuante.",
      "⭐ Leitura de Nota Impecável: Fim da perda da nota do passageiro em qualquer posição da tela."
    ],
    mandatory: false,
  });
}
