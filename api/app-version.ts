type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.106",
    versionCode: 127,
    releaseDate: "2026-11-12",
    apkUrl: "https://raw.githubusercontent.com/olirumtecapp-debug/goorno/main/GoOrNo.apk",
    directDownload: "/GoOrNo.apk",
    fileSizeBytes: 57350000,
    fileSizeFormatted: "54.7 MB",
    changelog: [
      "🎨 Nova fonte Poppins ExtraBold: moderna, geométrica e com contraste perfeito.",
      "👁️ Ergonomia visual: números em tom suave antirreflexo sem ofuscar a visão noturna.",
      "🚀 Leitura instantânea contínua: eliminação de travamentos em transições de tela e gavetas na 99 e Uber.",
      "📏 Escala de fontes refinada (M, G, GG) sem estouro de layout.",
      "⚡ Abertura instantânea (0ms), atalho de economia de bateria e persistência total com Uber, 99 e Waze."
    ],
    mandatory: true,
  });
}
