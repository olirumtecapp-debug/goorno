# 📌 Ponto de Continuidade — Projeto GoOrNo (v3.3.64 - Build 85)

**Data do Checkpoint:** 02/11/2026 (10h40)  
**Diretório do Projeto:** `D:\GoOrNo`  
**Código-Fonte Nativo Android:** `D:\GoOrNo\android`  
**Pacote Android:** `com.creativeam.goorno`  
**Versão Atualizada:** `GoOrNo-v3.3.64.apk` (`versionCode 85` | `versionName "3.3.64"`)  
**Download Direto Oficial:** `https://goorno-app.vercel.app/GoOrNo.apk` e `https://goorno-app.vercel.app/GoOrNo-v3.3.64.apk`  
**Painel Web / Landing Page:** `https://goorno-app.vercel.app` (Admin em `/#admin`)  
**Repositório GitHub (CI/CD Actions):** `https://github.com/olirumtecapp-debug/goorno`

---

## 1. Destaques da Versão Oficial `v3.3.64` (Build 85):

1. **🏷️ Novo Padrão Visual de Pílulas Flutuantes:**
   - Todas as notificações do GoOrNo foram padronizadas como pílulas acopladas de alto contraste, fundo sólido ou borda neon e ícones cristalinos:
     - 🛒 `MERCADO` (Vermelho)
     - ⚡ `CORRIDA CURTA` (Azul Celeste)
     - ⚠️ `EMBARQUE DISTANTE` (Grafite escuro com borda)
     - 🚨 `ENDEREÇO DE RISCO` (Borda vermelha viva no topo do card)
     - ⚠️ `RODÍZIO NO DESTINO / ORIGEM` (Âmbar/Laranja)
     - ⭐ `REGIÃO FAVORITA` (Verde Esmeralda)
     - ⏱️ `VIAGEM LONGA` (Roxo/Índigo)
     - 🛑 `MÚLTIPLAS PARADAS` (Azul Petróleo)
     - 🔁 `CHAMADO REPETIDO` (Laranja)
     - 👤 `NOTA BAIXA` (Âmbar)

2. **🚗 Rodízio Municipal de Veículos de São Paulo (CET-SP):**
   - Campo para cadastro da Placa do Veículo (padrão tradicional ou Mercosul).
   - Extração automática do dígito final e indicação do dia de rodízio (Segunda a Sexta).
   - Avaliador em tempo real `RodizioEvaluator.kt`: detecta se origem ou destino está no Centro Expandido de SP durante os horários restritos (07h-10h e 17h-20h) e aciona o alerta no card.

3. **📍 Cadastro Flexível de Risco e Favorito (Rua vs. Bairro com Campo Editável):**
   - Novo modal inteligente com 2 chips de escolha rápida:
     - `[ 📍 Rua: "Nome da Rua" ]`
     - `[ 🏘️ Bairro: "Nome do Bairro" ]`
   - Campo de texto `OutlinedTextField` livremente editável antes de salvar.
   - O motorista nunca mais fica refém do bairro inteiro ao negativar ou favoritar um endereço específico.

4. **🎯 Refinamento do Parser da Nota do Passageiro:**
   - Suporte a múltiplos formatos reais: estrela antes/depois, parênteses `(Boa)`, `(500+)`, número solto ou adjacente.
   - Eliminação dos tracinhos `--` quando a nota estiver visível na tela.

5. **⚡ R$/Km e R$/Hora Impecáveis:**
   - Isolamento estrito entre trecho de embarque e viagem com passageiro: leituras parciais que possuem apenas tempo/km de embarque não inflam mais o cálculo de rentabilidade.

---

## 2. Arquivos Modificados / Criados:
- `android/app/src/main/java/com/creativeam/goorno/model/StandardRideOffer.kt`
- `android/app/src/main/java/com/creativeam/goorno/data/preferences/DriverPreferencesRepository.kt`
- `android/app/src/main/java/com/creativeam/goorno/engine/RodizioEvaluator.kt` (NOVO)
- `android/app/src/main/java/com/creativeam/goorno/engine/RiskAreaEvaluator.kt`
- `android/app/src/main/java/com/creativeam/goorno/engine/FavoriteAreaEvaluator.kt`
- `android/app/src/main/java/com/creativeam/goorno/engine/FinancialEngine.kt`
- `android/app/src/main/java/com/creativeam/goorno/reader/BrazilianOfferParser.kt`
- `android/app/src/main/java/com/creativeam/goorno/overlay/FloatingOverlayPanel.kt`
- `android/app/src/main/java/com/creativeam/goorno/ui/GoOrNoComposeApp.kt`
- `android/app/src/test/java/com/creativeam/goorno/GoOrNoEngineAndParserTest.kt`
- `android/app/build.gradle` (v3.3.64 - Build 85)
- `api/app-version.ts`
- `server/index.ts`
- `client/src/App.tsx`
- `client/src/DownloadLandingPage.tsx`
