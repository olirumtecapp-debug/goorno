type VercelRequest = any;
type VercelResponse = any;

export default function handler(_req: VercelRequest, res: VercelResponse) {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.52",
    versionCode: 73,
    releaseDate: "2026-10-24",
    apkUrl: "https://goorno-app.vercel.app/GoOrNo-v3.3.52.apk",
    directDownload: "/GoOrNo-v3.3.52.apk",
    fileSizeBytes: 55356892,
    fileSizeFormatted: "55.4 MB",
    changelog: [
      "✉️ Fale Conosco Integrado no App: Canal direto de Caixa Postal adicionado nas telas de Ajustes e no topo do app para envio de dúvidas, sugestões e suporte diretamente ao desenvolvedor.",
      "🎨 Redesign Moderno da Caixa Postal no Site e no App: Modal totalmente reestilizado com a paleta escura obsidian, neon volt e bordas esmeralda do GoOrNo.",
      "🛡️ Adequação Jurídica & LGPD v1.3: Termos, Privacidade, CDC Art. 49 (Direito de arrependimento em 7 dias com estorno PIX) e segurança de PIN com salt dinâmico e bloqueio anti-brute-force.",
      "⚡ Estabilidade e Robustez: Preservação total de todo o leitor inteligente de ofertas Uber e 99."
    ],
    mandatory: false,
  });
}
