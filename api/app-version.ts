type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.53",
    versionCode: 74,
    releaseDate: "2026-10-25",
    apkUrl: "https://goorno-app.vercel.app/GoOrNo-v3.3.53.apk",
    directDownload: "/GoOrNo-v3.3.53.apk",
    fileSizeBytes: 55356892,
    fileSizeFormatted: "55.4 MB",
    changelog: [
      "✨ Janelinha Auxiliar de Alertas Acoplada: Alertas operacionais (Supermercado, Paradas, Chamado Repetido, Embarque Longe, Área de Risco) agora são exibidos em uma janelinha auxiliar translúcida e elegante acoplada abaixo do card principal.",
      "🎯 Card 3 Pilares Totalmente Desafogado: O card principal fica 100% limpo, com proporções preservadas e foco total no valor, tempo, distância e nos 3 pilares (R$/km, R$/hora e nota).",
      "🔗 Sincronização Perfeita: A janelinha auxiliar abre, fecha e é arrastada na tela sincronizada automaticamente com o card.",
      "⚡ Leitura Instantânea Uber & 99: Preservação total de todo o leitor inteligente de ofertas e motor financeiro.",
      "🛡️ Estabilidade & Robustez: Desempenho ultra leve e responsivo."
    ],
    mandatory: false,
  });
}
