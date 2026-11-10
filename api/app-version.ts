type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.86",
    versionCode: 107,
    releaseDate: "2026-11-09",
    apkUrl: "https://raw.githubusercontent.com/olirumtecapp-debug/goorno/main/GoOrNo.apk",
    directDownload: "/GoOrNo.apk",
    fileSizeBytes: 55410000,
    fileSizeFormatted: "52.8 MB",
    changelog: [
      "⚡ Navegação 100% Fluida: Correção estrutural das 4 abas (Início, Histórico, Metas e Ajustes) sem congelamento.",
      "🛡️ Estabilidade Total: Eliminação de travamentos de inicialização e compatibilidade plena com Android.",
      "🚦 Semáforo & Metas: Acesso direto às configurações de R$/km e R$/hora com resposta instantânea.",
      "🗺️ Leitura Inteligente 99: Ignora a lista geral de chamados e lê imediatamente ao tocar no ícone do mapa.",
      "🚗 Uber 100% Ativa: Monitoramento prioritário de todas as chamadas e radar preservado."
    ],
    mandatory: true,
  });
}
