type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.63",
    versionCode: 84,
    releaseDate: "2026-10-31",
    apkUrl: "https://goorno-app.vercel.app/GoOrNo-v3.3.63.apk",
    directDownload: "/GoOrNo.apk",
    fileSizeBytes: 55360988,
    fileSizeFormatted: "55.4 MB",
    changelog: [
      "🛡️ Limpeza Profunda de Endereços: Remoção de resíduos de prefixo de tempo/distância colados a nomes comerciais.",
      "🚫 Bloqueio de Corridas Expiradas: Descarte imediato de avisos como 'Esta corrida foi aceita por outro motorista'.",
      "⭐ Leitura Perfeita de Nota 5.00: Captura de notas com ícones especiais e marcadores de Perfil Premium.",
      "🎯 Clustering de Ofertas Consecutivas: Separação cirúrgica entre chamadas sobrepostas na 99 e Uber.",
      "⚡ Desempenho Ultra Leve & Seguro: Menor latência e 100% de precisão nos 3 pilares (R$/km, R$/hora e nota)."
    ],
    mandatory: false,
  });
}
