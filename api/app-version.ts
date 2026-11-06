type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.77",
    versionCode: 98,
    releaseDate: "2026-11-06",
    apkUrl: "https://raw.githubusercontent.com/olirumtecapp-debug/goorno/main/GoOrNo-v3.3.77.apk",
    directDownload: "/GoOrNo-v3.3.77.apk",
    fileSizeBytes: 55400000,
    fileSizeFormatted: "55.4 MB",
    changelog: [
      "🛡️ Blindagem Total do Modo 'Buscando': Mapa ocioso com 99 Abastece ou Solicitações (3) 100% silenciado.",
      "⏱️ Trava Anti-Glitch de Tempo: Rejeição absoluta de durações anômalas (ex: 1009 min).",
      "👤 Detecção de Passageiro Novo preservada sem poluição de '--'."
    ],
    mandatory: false,
  });
}
