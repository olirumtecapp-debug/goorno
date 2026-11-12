type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.104",
    versionCode: 125,
    releaseDate: "2026-11-12",
    apkUrl: "https://raw.githubusercontent.com/olirumtecapp-debug/goorno/main/GoOrNo.apk",
    directDownload: "/GoOrNo.apk",
    fileSizeBytes: 57918309,
    fileSizeFormatted: "55.2 MB",
    changelog: [
      "🎨 Novas fontes em negrito de alta legibilidade: Montserrat Bold, Nanum Gothic ExtraBold e Oswald Bold.",
      "🔍 Novo tamanho de fonte GGG (34sp) com expansão automática e texto 100% nítido sem sombras embaçadas.",
      "🚥 Semáforo visual com pílulas coloridas verticais antes de cada valor (KM, Hora e Nota).",
      "🌓 Modos Claro, Escuro e Colorido 100% estabilizados em todas as telas.",
      "⚡ Abertura instantânea (0ms), atalho de economia de bateria e persistência total com Uber, 99 e Waze."
    ],
    mandatory: true,
  });
}
