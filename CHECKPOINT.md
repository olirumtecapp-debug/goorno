# CHECKPOINT GOORNO — Checkpoint #026 (v3.3.59 - Build 80)
**Data:** 26 de Outubro de 2026 (17h20)  
**Status do Projeto:** ✅ Card Compacto com Borda Neon Viva (2dp) + Tempo & Distância Restaurados + Números 22sp Sem Quebra; Card Clássico com Preto Absoluto e Cores Vibrantes (Fiel à Imagem 5); Correção da Leitura de Nota 4,86 na 99 (Eliminando 5,00 Fantasma).

---

## 1. O que foi consolidado no Checkpoint #026:

### A. Card Compacto (3 Pilares) 100% Corrigido e Alinhado com o Feedback
1. **Restauração da Linha Inferior (Tempo & Distância):**
   - Na v58, a altura fixa de `78dp` no card container cortava e ocultava a linha inferior (`subRow`). A altura foi alterada para `WRAP_CONTENT` com padding balanceado (`top=10dp, bottom=10dp`), exibindo com máxima clareza o badge `[99]`, o tempo e a distância (`14 min • 4,3 km`), o timer (`7s`) e o botão de fechar (`✕`).
2. **Borda Externa Viva na Cor do Semáforo (2dp):**
   - Implementado contorno vívido de `2dp` (`roundedCardBg(cardBgHex, verdictColorHex, 16f, 2)`), que envolve todo o card na cor exata da avaliação (Verde Neon `#10E548`, Âmbar Elétrico `#FFB800` ou Vermelho Coral `#FF3B30`), exatamente como apontado pelo usuário.
3. **Eliminação de Quebra de Linha nos Valores (ex: `48,43`):**
   - Valores dos pilares ajustados para `22sp Bold` com `isSingleLine = true` e `maxLines = 1`. Números de 4 a 5 dígitos não quebram mais em duas linhas.
4. **Fundo Preto Profundo:**
   - Fundo alterado para preto absoluto `#0C0D11` sólido (100% opacidade), sem tons acinzentados lavados.

### B. Card Clássico com Cores Ultra-Vivas e Preto Absoluto (Fiel à Referência Imagem 5)
1. **Fundo e Contraste:**
   - Fundo do card em preto absoluto `#0C0D11`.
2. **Paleta Neon Vibrante:**
   - Verde (Aceitar): `#10E548` (Verde Elétrico de altíssima saturação).
   - Amarelo (Atenção): `#FFB800` (Âmbar Quente Elétrico).
   - Vermelho (Recusar): `#FF3B30` (Vermelho Coral Puro).
3. **Bloco Lateral Direito Integrado:**
   - Bloco vertical sólido de cor pura (`44dp` de largura x `MATCH_PARENT` de altura), `RoundedCornerShape(topEnd = 16dp, bottomEnd = 16dp)`. Sem texto e sem ícone, puro contraste visual.
4. **Dados da Seção Esquerda:**
   - `R$ X,XX /km` em `22sp Bold`, `R$ XX,XX/h` em `15sp Bold`, estrela dourada `★` com `Nota` e sublinha nítida `[99] tempo • km`.

### C. Correção na Leitura de Nota do Passageiro na 99 (Bug 5,00 vs 4,86 / Nota --)
1. **Causa Raiz Identificada:**
   - Na 99, a linha de avaliação vem como `4,86 · 828 corridas · Cartão verif.`. Em muitas chamadas, o texto vem fragmentado em nós irmãos (`"4,86"`, `"·"`, `"828 corridas"`).
   - O `normalizeFragmentedNodes` parava no primeiro bullet e não encadeava as corridas, fazendo as passadas 1 e 2 falharem. A passada 3 encontrava o `5,00` (nota do perfil do próprio motorista exibida no topo do app da 99) antes do `4,86` e retornava erroneamente `5,00`, ou retornava nulo quando ausente (`Nota | --`).
2. **Correção:**
   - **Passada 0 de Super Alta Precisão:** Faz varredura global com regex explícito `\b([3-5][.,]\d{1,2})\s*(?:[★⭐✪\*]|[·•∙‧⋅\.\-–—]\s*\d+\s*(?:corridas?|viagens?)|\(\d+\s*(?:corridas?|viagens?)?\)|[·•∙‧⋅\.\-–—]\s*(?:cart[ãa]o|verif|perfil)|\d+\s*(?:corridas?|viagens?))`. Se existir `4,86` acompanhado de corridas ou verificado em qualquer lugar do card, ele é retornado imediatamente com prioridade absoluta.
   - **Chaining de nós fragmentados:** Encadeamento de até 4 nós consecutivos com bullets e contagem de corridas.
   - **Proteção contra Nota do Motorista:** Na passada de fallback, notas decimais como `4,xx` têm precedência obrigatória sobre `5,00` puro.

### D. Preservação Total da Versão 53 para o Google Play Protect
- O binário `https://goorno-app.vercel.app/GoOrNo-v3.3.53.apk` permanece **100% ativo e intocado**.
- Nova versão oficial gerada: **`v3.3.59 (Build 80)`**.

---

## 2. Links Oficiais

| Recurso | URL | Status |
|---|---|---|
| **Download APK v3.3.59** | `https://goorno-app.vercel.app/GoOrNo-v3.3.59.apk` | ✅ Build 80 |
| **Download APK v3.3.53 (Play Protect)** | `https://goorno-app.vercel.app/GoOrNo-v3.3.53.apk` | ✅ Build 74 |
| **Central de Continuidade** | `http://localhost:3010` | ✅ Ativa |
