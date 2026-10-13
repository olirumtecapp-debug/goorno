# 📌 Ponto de Continuidade — Projeto GoOrNo (v3.3.2)

**Data do Checkpoint:** 13/10/2026  
**Diretório do Projeto:** `D:\GoOrNo`  
**Código-Fonte Nativo Android:** `D:\GoOrNo\android`  
**Pacote Android:** `com.creativeam.goorno`  
**Última Versão Compilada:** `GoOrNo-v3.3.2.apk` (`versionCode 19` | `versionName "3.3.2"`)  
**Caminho do APK Atual:** `D:\GoOrNo\GoOrNo-v3.3.2.apk`  
**Painel Web Administrativo:** `https://goorno-app.vercel.app/#admin` (ou `/admin`)  
**Repositório GitHub (CI/CD Actions):** `https://github.com/olirumtecapp-debug/goorno`

---

## 1. Estado Atual do Projeto (100% Funcional e Integrado)

### 🚀 Novidades da `v3.3.2` (Painel Clean MeuAutônomo + Sistema Completo de Vouchers/Cupons):
1. **Painel Admin Clean Inspirado no MeuAutônomo:**
   - Visual limpo e moderno usando a paleta exata:
     - Fundo suave `#f5f7f2`
     - Tom de destaque/títulos verde-escuro petróleo `#173a34`
     - Realces vibrantes em lime/chartreuse `#d9f56a`
     - Textos secundários `#71867f` e bordas `#dce5dc`
     - Cards em branco com cantos arredondados (`rounded-2xl`).
   - 3 Abas claras e organizadas:
     - 📊 **Visão Geral & Faturamento** (MRR, Total de motoristas, pagantes, gráficos e métricas).
     - 👥 **Motoristas & Licenças** (busca, filtros, concessão de cortesia e reset de aparelho).
     - 🎟️ **Cupons & Vouchers** (gestão completa de vouchers promocionais e de cortesia).

2. **Sistema Completo de Vouchers / Cupons de Cortesia:**
   - **Criação pelo Painel Admin:**
     - Código customizado ou gerador automático em caixa alta (ex: `BEMVINDO7`, `LANCAMENTO30`, `PARCEIROVIP`).
     - Duração flexível: 7 dias, 15 dias, 30 dias ou Acesso Vitalício.
     - Limite de usos (ex: primeiros 50 motoristas) ou ilimitado.
     - Descrição ou notas do cupom.
   - **Gestão no Painel:**
     - Cópia com 1 clique para colar no WhatsApp.
     - Ativação / desativação instantânea com toggle.
     - Contador de usos em tempo real.
     - Exclusão com confirmação.
   - **Resgate pelo Motorista no App Android:**
     - Campo dedicado na aba *"⭐ Plano Pro"* com botão *"Resgatar Cupom"*.
     - Validação instantânea contra o backend `/api/redeem-voucher`.
     - Prevenção de resgate duplo pelo mesmo número de WhatsApp.
     - Ativação imediata da licença cortesia/vitalícia no aparelho do motorista.

### 🚀 Novidades da `v3.3.1` (Filtro Anti-Propaganda, Destaque R$/km e R$/h e Novos Tempos):
1. **Filtro Anti-Propaganda e Postos de Combustível (`isNonRideAdOrService`):**
   - Ignora anúncios como "99 Abastece", "Auto Posto", descontos de combustível, troca de óleo, empréstimos, cartões e telas em modo de busca ("Buscando") sem ação de chamada.
   - Aplica tanto no leitor da 99 quanto da Uber.
2. **Destaque Visual com Quadradinhos no Flutuante (`kmVerdict` e `hourVerdict`):**
   - R$/km e R$/h agora possuem caixas/tags individuais coloridas com contorno 2dp e fundo suave:
     - 🟢 **Verde:** atende ou supera a meta.
     - 🟡 **Amarelo:** na faixa de tolerância próxima à meta.
     - 🔴 **Vermelho:** abaixo da meta.
   - Veredito geral e cores de fundo do card preservados como no design original aprovado.
3. **Novas Opções de Tempo da Janelinha:**
   - Opções estritas de **3s, 5s, 8s e 10s** (removidos 12s e 15s).

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
