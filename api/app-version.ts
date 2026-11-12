type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.105",
    versionCode: 126,
    releaseDate: "2026-11-12",
    apkUrl: "https://raw.githubusercontent.com/olirumtecapp-debug/goorno/main/GoOrNo.apk",
    directDownload: "/GoOrNo.apk",
    fileSizeBytes: 56400000,
    fileSizeFormatted: "53.8 MB",
    changelog: [
      "🎨 Tipografia refinada: Montserrat Bold, Roboto Bold e Fonte do Sistema.",
      "🔍 Novo tamanho de fonte GGG (34sp) com expansão automática e texto 100% nítido sem sombras embaçadas.",
      "🚥 Semáforo visual com pílulas coloridas verticais antes de cada valor (KM, Hora e Nota).",
      "🌓 Modos Claro, Escuro e Colorido 100% estabilizados em todas as telas.",
      "⚡ Abertura instantânea (0ms), atalho de economia de bateria e persistência total com Uber, 99 e Waze."
    ],
    mandatory: true,
  });
}
