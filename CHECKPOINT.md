# CHECKPOINT GOORNO — Checkpoint #016 (v3.3.51 - Build 72)
**Data:** 24 de Outubro de 2026  
**Status do Projeto:** ✅ Mensalidade Promocional Adequada para R$ 5,09 (Mínimo Asaas) & Webhook de Pagamento Validado

---

## 1. O que foi consolidado no Checkpoint #016:

### A. Adequação da Mensalidade Promocional para R$ 5,09
1. **Regra de Gateway Asaas (Valor Mínimo de R$ 5,00):**
   - O preço promocional do primeiro mês foi reajustado de R$ 3,99 para **R$ 5,09** em todos os pontos do sistema (`server/licenseStore.ts` e `LicenseManager.kt`).
   - Economia calculada ajustada para R$ 2,90 (R$ 7,99 - R$ 5,09).
   - O gateway Asaas agora gera as cobranças PIX sem rejeição por valor abaixo do piso.

### B. Validação e Robustez do Webhook da Asaas
1. **Múltiplos Aliases de Rota:**
   - Rotas cadastradas: `/api/goorno-webhook`, `/api/webhook`, `/api/webhook/asaas` e `/api/webhooks/asaas`.
2. **Reconciliação e Idempotência:**
   - Extração resiliente de telefone via `externalReference` (`goorno_tel_XXXXX` ou números diretos), `pixQrCodeId` e consulta de cliente.
   - Ativação imediata de 30 dias na licença do motorista com registro no histórico financeiro.
   - Idempotência testada: retransmissões do mesmo evento pela Asaas não duplicam dias nem geram cobrança duplicada.

---

## 2. Links Oficiais e Painéis

| Recurso | URL | Status |
|---|---|---|
| **Produção Web (Vercel)** | `https://goorno-app.vercel.app` | ✅ No Ar & Atualizado (R$ 5,09) |
| **Webhook Asaas Oficial** | `https://goorno-app.vercel.app/api/goorno-webhook` | ✅ Operacional (Status 200) |
| **Download APK Direto** | `https://goorno-app.vercel.app/GoOrNo-v3.3.51.apk` | ✅ Operacional (55.3 MB) |
| **Central de Continuidade** | `http://localhost:3010` | ✅ Checkpoint #016 Ativo |
| **Copiloto Web Local** | `http://localhost:3008` | ✅ Ativo na porta 3008 |
| **Repositório GitHub** | `https://github.com/olirumtecapp-debug/goorno.git` | ✅ Branch main sincronizada |

---

## 3. Frase de Restauração Segura (Rollback)
> *"Antigravity, deu ruim no GoOrNo! Volte para o Checkpoint #015!"*
