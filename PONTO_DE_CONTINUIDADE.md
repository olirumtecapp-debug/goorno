# 📌 Ponto de Continuidade — Projeto GoOrNo (v3.3.0)

**Data do Checkpoint:** 13/10/2026  
**Diretório do Projeto:** `D:\GoOrNo`  
**Código-Fonte Nativo Android:** `D:\GoOrNo\android`  
**Pacote Android:** `com.creativeam.goorno`  
**Última Versão Compilada:** `GoOrNo-v3.3.0.apk` (`versionCode 17` | `versionName "3.3.0"`)  
**Caminho do APK Atual:** `D:\GoOrNo\GoOrNo-v3.3.0.apk`  
**Painel Web Administrativo:** `https://goorno-app.vercel.app/#admin` (ou `/admin`)  
**Repositório GitHub (CI/CD Actions):** `https://github.com/olirumtecapp-debug/goorno`

---

## 1. Estado Atual do Projeto (100% Funcional e Integrado)

### 🚀 Novidades da `v3.3.0` (Sistema Completo de Monetização, Anti-Fraude e Painel Master)

1. **Monetização Direta via PIX (Gateway Asaas em Produção):**
   - **Plano Mensal:** R$ 9,90 / mês (recorrência via PIX para motoristas).
   - **Plano Vitalício:** R$ 39,90 (pagamento único com acesso para sempre).
   - Geração instantânea de QR Code Base64 e Chave PIX (Copia e Cola).
   - Botão no app: *"Copiar Código PIX"* e *"Já Paguei! Verificar Pagamento Agora"*.
   - Webhook oficial `/api/goorno-webhook` que escuta eventos de pagamento aprovado do Asaas (`PAYMENT_RECEIVED`, `PAYMENT_CONFIRMED`) e ativa/renova a licença automaticamente no Firestore (`goorno_licencas`).

2. **Período de Teste Gratuito de 7 Dias (Trial Automático):**
   - Cada novo motorista que instala o app e digita seu WhatsApp ganha automaticamente 7 dias de uso irrestrito do copiloto.
   - Status exibido no topo e na aba *"⭐ Plano Pro"*: `TESTE GRÁTIS ATIVO (Xd restantes)`.

3. **Vínculo Unipessoal por Aparelho (Single-Device Lock & Anti-Fraude):**
   - A licença é vinculada exclusivamente ao Android ID do smartphone do motorista.
   - **Troca Automática de Aparelho pelo Próprio Motorista:** Caso o motorista compre outro celular ou formate o aparelho, ele pode transferir sua licença automaticamente direto pelo app (até **2 vezes por mês**).
   - **Prevenção de Fraude / Rateio:** Se houver 3 ou mais tentativas de troca no mesmo mês, a transferência é bloqueada automaticamente por suspeita de rateio de conta.

4. **Notificação Instantânea de Fraude por E-mail:**
   - Ao detectar tentativas excessivas de troca de aparelho ou uso simultâneo, o sistema dispara um e-mail de alerta detalhado para `contatocreativeam@gmail.com` via Resend API:
     - WhatsApp do motorista
     - Aparelho anterior e novo aparelho alvo
     - Quantidade de trocas no mês
     - IP e data/hora exata

5. **Painel Administrativo Web Responsivo (PC & Smartphone):**
   - Acessível em `https://goorno-app.vercel.app/#admin` ou `https://goorno-app.vercel.app/admin`.
   - Protegido por Senha Master (`goorno2026admin`), mantida em `sessionStorage` após login.
   - **Cards de Métricas em Tempo Real:**
     - MRR Estimado (Faturamento mensal recorrente ativo)
     - Total de Motoristas
     - Pagantes Ativos (Mensal e Vitalício)
     - Motoristas em Período de Teste (7 dias)
     - Licenças Expiradas
     - Alertas de Fraude Detectados
   - **Gestão em 1-Clique por Motorista:**
     - ⚡ `+7 Dias` (Concessão de bônus/cortesia)
     - 📅 `+30 Dias` (Renovação manual ou cortesia mensal)
     - 👑 `Cortesia Vitalícia` (Liberação definitiva de motoristas VIP)
     - 🔄 `Resetar Aparelho` (Desvincula o smartphone e zera o contador de trocas)
     - 🚫 `Bloquear / Desbloquear` (Corte imediato de acesso do motorista)

6. **Nova Aba no App Android Nativo: `11. Plano Pro` (`SubscriptionScreenCard`):**
   - Integração visual com Jetpack Compose no padrão oficial do GoOrNo.
   - Badge `⭐ PRO` clicável no cabeçalho superior direito.
   - Seletor de planos (Mensal R$ 9,90 ou Vitalício R$ 39,90).
   - Renderização visual do QR Code PIX e botão de cópia rápida.
   - Botão para transferir licença para o aparelho atual com transparência de regras.

---

## 2. Credenciais e Variáveis de Ambiente (`.env`)

Configuradas em `D:\GoOrNo\.env` e integradas ao Vercel e Firebase:
- `PORT=3000`
- `FIREBASE_PROJECT_ID=expedicao-brasil`
- `FIREBASE_API_KEY` (Configurado no .env local e Vercel)
- `ASAAS_API_KEY` (Chave de produção Asaas no .env local e Vercel)
- `RESEND_API_KEY` (Chave de alertas Resend no .env local e Vercel)
- `GMAIL_USER=contatocreativeam@gmail.com`
- `ADMIN_PASSWORD=goorno2026admin`

---

## 3. Comando Rápido para Compilar Novas Versões

Para rodar os testes e compilar o APK via pipeline de nuvem:

```powershell
& "D:\APKBuilder\New-GameAPK.ps1" -ProjectPath "D:\GoOrNo" -AppName "GoOrNo" -PackageId "com.creativeam.goorno" -AppUrl "https://expedicao-brasil.web.app" -IconPath "D:\GoOrNo\client\public\icon-512.png" -PrimaryColor "#073B32" -AccentColor "#97B304" -Orientation "portrait"
```
