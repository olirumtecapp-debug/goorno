# PONTO DE CONTINUIDADE — GOORNO v3.3.65 (BUILD 86)
**Data/Hora:** 02/11/2026 - 13:30 (Horário de Brasília)
**Status:** ✅ Correções Críticas Aplicadas, Testes Validados e Pronto para Produção

---

## 🎯 1. Resumo Executivo das Correções (v3.3.65 - Build 86)

Nesta versão foram resolvidas as 4 questões críticas apontadas pelo usuário:

1. **Posicionamento das Pílulas / Abinhas de Alerta SEMPRE Embaixo do Card:**
   - Todas as pílulas (Supermercado, Embarque Distante, Corrida Curta, Rodízio SP e Área de Risco) agora são acopladas **exclusivamente na parte inferior** do card (`rootLayout.addView(auxPill)`), eliminando qualquer inserção no índice 0 (topo).
   - O alerta de Área de Risco foi padronizado em fundo sólido vermelho `#DC2626` de alto contraste com ícone `🚨` e texto branco.

2. **Restauração da Velocidade Instantânea de Leitura no Uber (~0.1s):**
   - Corrigido o retorno em `FinancialEngine.kt` (linhas 96 e 126) para que ofertas com deslocamento até o passageiro retornem `pickupKm` e `pickupMin`.
   - Eliminado o veredito indesejado `GRAY_NEUTRAL` que descartava a leitura de acessibilidade e disparava loops repetitivos de captura de tela OCR a cada 950ms.

3. **Eliminação do Congelamento e Diálogo de ANR ("Fechar app ou aguardar"):**
   - Ao manter o processamento leve e direto via árvore de acessibilidade nativa sem recorrer a OCR contínuo na thread principal, a CPU e memória do dispositivo voltam a operar fluidas sem travamento.

4. **Correção do Contador Regressivo Travado e Botão "✕" Inoperante:**
   - Vinculado `activeTimerLabel = tvTimer` no layout `MINI_BALL` (estava `null`), permitindo a contagem decrescente normal segundo a segundo (`12s` -> `11s` -> ...).
   - Ao atingir `0s`, o runnable agora chama `dismissCurrentOfferCardByUser()`, garantindo o fechamento imediato.
   - O botão "✕" teve sua área de toque ampliada (`minWidth = 34dp`, `minHeight = 28dp`, padding generoso) e recebeu `setOnTouchListener` explícito para capturar o toque antes de qualquer propagação do layout pai, fechando a janela instantaneamente na mesma thread (`removeWindowViewOnly()`).

5. **Aprimoramento na Extração da Nota do Passageiro:**
   - O método `extractPassengerRating` em `BrazilianOfferParser.kt` agora conta com 5 etapas de extração, incluindo suporte a "classificação", "estrelas", nós isolados de acessibilidade e tolerância a nós mesclados na mesma linha com distância/tempo, evitando os traços ("★ --").

---

## 📦 2. Arquivos Modificados
- `android/app/src/main/java/com/creativeam/goorno/engine/FinancialEngine.kt`
- `android/app/src/main/java/com/creativeam/goorno/overlay/FloatingOverlayPanel.kt`
- `android/app/src/main/java/com/creativeam/goorno/reader/BrazilianOfferParser.kt`
- `android/app/src/test/java/com/creativeam/goorno/GoOrNoEngineAndParserTest.kt`
- `android/app/build.gradle` (versionCode 86, versionName "3.3.65")
- `api/app-version.ts`
- `server/index.ts`
- `package.json`
- `client/src/App.tsx`
- `client/src/DownloadLandingPage.tsx`
