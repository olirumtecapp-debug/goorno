type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.65",
    versionCode: 86,
    releaseDate: "2026-11-02",
    apkUrl: "https://goorno-app.vercel.app/GoOrNo-v3.3.65.apk",
    directDownload: "/GoOrNo.apk",
    fileSizeBytes: 55360988,
    fileSizeFormatted: "55.4 MB",
    changelog: [
      "🏷️ Novo Padrão de Pílulas Flutuantes: Badges de alto contraste para Mercado, Corrida Curta, Embarque Distante, Risco e Rodízio.",
      "🚗 Rodízio Municipal de São Paulo: Alerta automático no destino/origem dentro do Centro Expandido com base na placa cadastrada.",
      "📍 Cadastro de Risco e Favorito Flexível: Escolha rápida entre Rua ou Bairro e campo de texto livremente editável.",
      "🎯 Refinamento de Leitura da Nota: Captura precisa da avaliação do passageiro sem exibir tracinhos.",
      "⚡ R$/Km e R$/Hora Impecáveis: Separação rigorosa entre deslocamento de busca e corrida com passageiro."
    ],
    mandatory: false,
  });
}
