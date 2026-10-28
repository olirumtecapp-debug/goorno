# CHECKPOINT GOORNO — Checkpoint #028 (v3.3.61 - Build 82)
**Data:** 28 de Outubro de 2026 (12h35)  
**Status do Projeto:** ✅ Correção de Mistura de Ofertas em Scroll na 99 (Isolamento de nós órfãos e ancoragem de percurso ao preço principal) e Reconhecimento Abrangente de Nota do Passageiro com Ícone 👤 e Cartão Verificado.

---

## 1. O que foi consolidado no Checkpoint #028:

### A. Fim da Mistura de Dados em Múltiplas Ofertas com Scroll na 99
1. **Ancoragem ao Preço Principal (`linesForLegs`):**
   - Quando o motorista rola a tela da 99 e existem múltiplas ofertas, os fragmentos de texto do topo (da oferta anterior que já subiu, ex: `57 min • 30 km`) não são mais erroneamente associados ao preço da oferta inferior (`R$ 15,90`).
   - `NinetyNineOfferReader` localiza o índice do preço principal da oferta atual (`primaryPriceLineIdx`) e busca os trechos de trajeto (`legs`, distâncias e durações) estritamente a partir do preço para baixo.
2. **Reconhecimento de Cabeçalho Dinâmico no Fatiamento (`splitIntoMultipleOfferClusters`):**
   - Removida a restrição que descartava cabeçalhos contendo `/km` ou tarifas por km (muito comuns em preços dinâmicos da 99).
   - O fatiador garante que o cluster de cada oferta comece no preço legítimo ou na categoria, eliminando contaminação cruzada.

### B. Leitura Robusta da Nota do Passageiro (Suporte ao Ícone 👤 e Delimitadores da 99)
1. **Suporte a Emojis e Ícones Nativos da 99:**
   - A 99 frequentemente exibe o perfil com o ícone de usuário `👤` (`\uD83D\uDC64`) em vez de estrela (`★`/`⭐`), como em `👤 4,95 • Cartão verif.` ou `👤 5,00`.
   - Adicionado suporte a `👤`, `👥`, `🧑`, `👨`, `👩` e expressões como `Cartão verif.`, `novo`, `passageiro`, `usuário`.
2. **Reconstrução de Fragmentos no `normalizeFragmentedNodes`:**
   - Adicionado o caso de agrupamento quando `👤 4,95` vem separado do marcador `• Cartão verif.`.
3. **Prevenção de Tracinho `-`:**
   - A primeira oferta e ofertas com cartão verificado agora extraem a nota perfeitamente, eliminando o hífen indesejado no pilar de Nota.

### C. Testes Unitários Automatizados
- Adicionados testes de regressão específicos em `GoOrNoEngineAndParserTest.kt`:
  - `test 99 multiple offers when scrolled - prevent mixing of second and third offer` (simulando a tela real com 15 nós de acessibilidade).
  - `test 99 passenger rating extraction with user icon and isolated rating numbers`.

### D. Preservação Total da Versão 53 para o Google Play Protect
- O binário `https://goorno-app.vercel.app/GoOrNo-v3.3.53.apk` permanece **100% ativo e intocado**.
- Nova versão oficial gerada: **`v3.3.61 (Build 82)`**.

---

## 2. Links Oficiais

| Recurso | URL | Status |
|---|---|---|
| **Download APK v3.3.61** | `https://goorno-app.vercel.app/GoOrNo-v3.3.61.apk` | ✅ Build 82 |
| **Download APK v3.3.53 (Play Protect)** | `https://goorno-app.vercel.app/GoOrNo-v3.3.53.apk` | ✅ Build 74 |
| **Central de Continuidade** | `http://localhost:3010` | ✅ Ativa |
