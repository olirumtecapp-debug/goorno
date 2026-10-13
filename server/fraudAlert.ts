export interface FraudAlertData {
  whatsapp: string;
  fromDevice: string;
  toDevice: string;
  transfersCount: number;
  reason: string;
  clientIp?: string;
  timestamp: string;
}

export async function sendFraudAlertEmail(data: FraudAlertData): Promise<boolean> {
  const apiKey = process.env.RESEND_API_KEY || "";
  const toEmail = "contatocreativeam@gmail.com";

  console.warn(
    `[ALERTA DE FRAUDE] Notificando proprietário sobre suspeita de compartilhamento para ${data.whatsapp}: ${data.reason}`
  );

  const html = `
    <!DOCTYPE html>
    <html>
    <head>
      <meta charset="utf-8">
      <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #0b0f19; color: #f3f4f6; margin: 0; padding: 24px; }
        .card { background-color: #111827; border: 1px solid #dc2626; border-radius: 12px; padding: 24px; max-width: 600px; margin: 0 auto; }
        .badge { background-color: #ef4444; color: #ffffff; padding: 6px 12px; border-radius: 6px; font-weight: bold; font-size: 13px; display: inline-block; }
        h2 { color: #f87171; margin-top: 16px; margin-bottom: 8px; font-size: 20px; }
        .info-row { display: flex; justify-content: space-between; border-bottom: 1px solid #1f2937; padding: 10px 0; font-size: 14px; }
        .label { color: #9ca3af; }
        .val { color: #ffffff; font-weight: 600; }
        .alert-box { background-color: #450a0a; border: 1px solid #991b1b; padding: 12px; border-radius: 8px; margin-top: 16px; font-size: 14px; color: #fca5a5; }
        .footer { margin-top: 24px; font-size: 12px; color: #6b7280; text-align: center; }
      </style>
    </head>
    <body>
      <div class="card">
        <span class="badge">SEGURANÇA & ANTI-FRAUDE</span>
        <h2>🚨 Suspeita de Compartilhamento de Licença</h2>
        <p style="color: #9ca3af; font-size: 14px; margin-bottom: 20px;">
          O sistema GoOrNo detectou tentativas excessivas de troca de aparelho ou atividade incompatível com uso individual.
        </p>

        <div class="info-row">
          <span class="label">WhatsApp do Motorista:</span>
          <span class="val">${data.whatsapp}</span>
        </div>
        <div class="info-row">
          <span class="label">Data / Hora:</span>
          <span class="val">${new Date(data.timestamp).toLocaleString("pt-BR")}</span>
        </div>
        <div class="info-row">
          <span class="label">Trocas no Mês:</span>
          <span class="val" style="color: #ef4444;">${data.transfersCount} trocas detectadas</span>
        </div>
        <div class="info-row">
          <span class="label">Aparelho Anterior:</span>
          <span class="val" style="font-family: monospace;">${data.fromDevice || "N/A"}</span>
        </div>
        <div class="info-row">
          <span class="label">Novo Aparelho (Alvo):</span>
          <span class="val" style="font-family: monospace;">${data.toDevice || "N/A"}</span>
        </div>
        <div class="info-row">
          <span class="label">IP de Origem:</span>
          <span class="val">${data.clientIp || "N/D"}</span>
        </div>

        <div class="alert-box">
          <strong>Motivo do Alerta:</strong><br/>
          ${data.reason}
        </div>

        <p style="margin-top: 20px; font-size: 14px; color: #d1d5db;">
          A transferência automática foi bloqueada para proteger seu faturamento. Você pode gerenciar, liberar ou bloquear este motorista diretamente no <strong>Painel Admin GoOrNo</strong>.
        </p>

        <div class="footer">
          GoOrNo Sistema de Gestão & Monetização • Creative AM
        </div>
      </div>
    </body>
    </html>
  `;

  try {
    const res = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        from: "GoOrNo Alertas <onboarding@resend.dev>",
        to: [toEmail],
        subject: `🚨 [GoOrNo Alerta] Suspeita de Fraude: ${data.whatsapp} (${data.transfersCount} trocas)`,
        html,
      }),
    });

    if (res.ok) {
      console.log(`[fraudAlert] Email enviado com sucesso via Resend para ${toEmail}`);
      return true;
    } else {
      const errText = await res.text();
      console.warn(`[fraudAlert] Falha ao enviar email via Resend: ${res.status} - ${errText}`);
      return false;
    }
  } catch (err: any) {
    console.warn(`[fraudAlert] Erro de rede ao disparar email de fraude:`, err?.message);
    return false;
  }
}
