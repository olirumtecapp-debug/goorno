import type { VercelRequest, VercelResponse } from "@vercel/node";

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.50",
    versionCode: 71,
    releaseDate: "2026-10-24",
    apkUrl: "https://goorno-app.vercel.app/GoOrNo-v3.3.50.apk",
    directDownload: "/GoOrNo-v3.3.50.apk",
    fileSizeBytes: 55336412,
    fileSizeFormatted: "55.3 MB",
    changelog: [
      "⭐ Correção da Nota do Passageiro na 99: Suporte completo a formato de avaliação com ponto e corridas (ex: 4,95 · 736 corridas, 5,00 · 105 corridas), eliminando o 'Nota: --'.",
      "⚡ Correção do Fatiamento de Lista da 99: Fim do resíduo de chamadas anteriores, do falso split [1/2] e de taxas distorcidas (R$ 0,22/km).",
      "🌐 Landing Page Oficial Atualizada: Novos cards comparativos reais e vitrine dos Recursos Inteligentes (Supermercado, Paradas, Bairros Favoritos, Risco).",
      "🛡️ Estabilidade Total Preservada: Motor consolidado mantendo toda a robustez e segurança."
    ],
    mandatory: false,
  });
}
