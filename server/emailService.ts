export interface ContactEmailPayload {
  id: string;
  ticketId: string;
  name: string;
  email: string;
  category: string;
  categoryLabel: string;
  subject: string;
  message: string;
  source: string;
}

export async function notifyAdminByEmail(data: ContactEmailPayload): Promise<boolean> {
  const apiKey = process.env.RESEND_API_KEY || "";
  const toEmail = process.env.ADMIN_NOTIFY_EMAIL || "contatocreativeam@gmail.com";

  if (!apiKey) {
    console.log(`[EmailService] RESEND_API_KEY não configurada. Notificação admin simulada para ticket ${data.ticketId}`);
    return true;
  }

  try {
    const res = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        from: "GoOrNo Contato <onboarding@resend.dev>",
        to: [toEmail],
        subject: `📩 [Novo Contato - ${data.categoryLabel}] ${data.subject}`,
        html: `
          <h2>Novo Chamado no GoOrNo</h2>
          <p><strong>Nome:</strong> ${data.name}</p>
          <p><strong>E-mail:</strong> ${data.email}</p>
          <p><strong>Categoria:</strong> ${data.categoryLabel}</p>
          <p><strong>Ticket ID:</strong> ${data.ticketId}</p>
          <p><strong>Mensagem:</strong></p>
          <p style="white-space: pre-wrap; background: #f3f4f6; padding: 12px; border-radius: 8px;">${data.message}</p>
        `,
      }),
    });
    return res.ok;
  } catch (err: any) {
    console.warn("[EmailService] Erro ao notificar admin por email:", err?.message);
    return false;
  }
}

export async function sendVisitorConfirmationEmail(data: ContactEmailPayload): Promise<boolean> {
  const apiKey = process.env.RESEND_API_KEY || "";
  if (!apiKey || !data.email) {
    return true;
  }

  try {
    const res = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        from: "GoOrNo Suporte <onboarding@resend.dev>",
        to: [data.email],
        subject: `Recebemos sua mensagem: ${data.subject} [${data.ticketId}]`,
        html: `
          <h2>Olá, ${data.name}!</h2>
          <p>Confirmamos o recebimento de sua mensagem através do nosso portal.</p>
          <p><strong>Número do Chamado:</strong> ${data.ticketId}</p>
          <p><strong>Assunto:</strong> ${data.subject}</p>
          <p>Nossa equipe entrará em contato em breve.</p>
          <br/>
          <p>Equipe GoOrNo</p>
        `,
      }),
    });
    return res.ok;
  } catch (err: any) {
    console.warn("[EmailService] Erro ao enviar confirmacao ao visitante:", err?.message);
    return false;
  }
}
