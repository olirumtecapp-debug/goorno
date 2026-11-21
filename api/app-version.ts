type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.118",
    versionCode: 139,
    releaseDate: "2026-11-21",
    apkUrl: "https://raw.githubusercontent.com/olirumtecapp-debug/goorno/main/GoOrNo.apk",
    directDownload: "/GoOrNo.apk",
    fileSizeBytes: 57400000,
    fileSizeFormatted: "54.8 MB",
    changelog: [
      "🔁 Chamados Repetidos Inteligentes: ofertas repetidas não somam mais no total diário.",
      "📊 Histórico com Métricas Reais: badge discreto 'Chamado repetido (não computado)'.",
      "💬 Suporte 100% Interno: Fale Conosco e Caixa Postal exclusivos para motoristas cadastrados.",
      "✨ Novidades da Versão: a tela de atualização agora exibe exatamente as melhorias reais."
    ],
    mandatory: true,
  });
}
