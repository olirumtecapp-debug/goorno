# CHECKPOINT GOORNO — Checkpoint #027 (v3.3.60 - Build 81)
**Data:** 26 de Outubro de 2026 (20h45)  
**Status do Projeto:** ✅ Card 3 Pilares Compacto com Valor da Corrida Incluso na Linha Inferior (`R$ XX,XX • Tempo • Distância`), Remoção do Card Clássico e Catálogo Oficial Padronizado em 3 Opções (Impacto Pro, 3 Pilares e 3 Pilares Compacto).

---

## 1. O que foi consolidado no Checkpoint #027:

### A. Melhoria no Card 3 Pilares Compacto (Valor da Corrida na Linha Inferior)
1. **Dados Completos sem Poluição Visual:**
   - Na linha inferior (`subRow`), além do badge da operadora (`[99]` / `[Uber]`), tempo e distância (`25 min • 8,7 km`), agora é exibido o **Valor Total da Corrida em Reais**:
     `[99] R$ 15,80 • 25 min • 8,7 km` (ou `[Uber] R$ 22,50 • 18 min • 6,1 km`).
   - O motorista visualiza em uma única tela enxuta:
     - **Linha 1 (Pilares em Destaque):** `R$/Km`, `R$/Hora` e `Nota do Passageiro` com números grandes em `22sp Bold` e barrinhas verticais coloridas.
     - **Linha 2 (Resumo Completo):** Badge do app + Valor Total da Oferta + Tempo estimado + Distância total + Timer de contagem regressiva + Botão de fechar `✕`.

### B. Remoção do Card Clássico e Catálogo Oficial em 3 Opções
1. **Opções Oficiais Padronizadas:**
   - Conforme decisão do usuário, o Card Clássico foi retirado da lista de opções, concentrando o app no que funciona com máxima excelência:
     1. **Card Impacto Pro:** Design moderno com valor gigante no topo, semáforo sólido de alto contraste e métricas completas.
     2. **Card 3 Pilares:** Resumo de valor e operadora no topo com 3 colunas e barras verticais coloridas.
     3. **Card 3 Pilares Compacto (Recomendado):** Formato ultra-enxuto com os 3 pilares em 22sp, valor, tempo e distância na linha inferior e borda viva 2dp.
2. **Compatibilidade com Usuários Antigos:**
   - Se o motorista tinha o `Card Clássico` salvo nas preferências do aparelho, o sistema faz o fallback automático e limpo para o `Card 3 Pilares Compacto`.

### C. Preservação Total da Versão 53 para o Google Play Protect
- O binário `https://goorno-app.vercel.app/GoOrNo-v3.3.53.apk` permanece **100% ativo e intocado**.
- Nova versão oficial gerada: **`v3.3.60 (Build 81)`**.

---

## 2. Links Oficiais

| Recurso | URL | Status |
|---|---|---|
| **Download APK v3.3.60** | `https://goorno-app.vercel.app/GoOrNo-v3.3.60.apk` | ✅ Build 81 |
| **Download APK v3.3.53 (Play Protect)** | `https://goorno-app.vercel.app/GoOrNo-v3.3.53.apk` | ✅ Build 74 |
| **Central de Continuidade** | `http://localhost:3010` | ✅ Ativa |
