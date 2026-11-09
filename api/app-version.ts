type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.82",
    versionCode: 103,
    releaseDate: "2026-11-09",
    apkUrl: "https://raw.githubusercontent.com/olirumtecapp-debug/goorno/main/GoOrNo.apk",
    directDownload: "/GoOrNo.apk",
    fileSizeBytes: 55410000,
    fileSizeFormatted: "52.8 MB",
    changelog: [
      "⭐ Novo Design PRO GoOrNo: Interface limpa e moderna com 4 abas diretas (Início, Histórico, Metas e Ajustes).",
      "🚦 Semáforo & Copiloto com Ativação em 1 Toque: Acesso rápido aos filtros por km e hora com feedback visual imediato.",
      "🗺️ Leitura Inteligente na 99: Bloqueio da lista de solicitações e leitura instantânea ao tocar no ícone do mapa.",
      "🚗 Leitura Prioritária Uber 100% Ativa: Monitoramento total de chamadas entrantes e radar com Street View 360°.",
      "⚡ Navegação Estável & Desempenho: Eliminação de travamentos e sincronização ultra-rápida."
    ],
    mandatory: true,
  });
}
