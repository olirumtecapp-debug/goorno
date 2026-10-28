# 📌 Ponto de Continuidade — Projeto GoOrNo (v3.3.61 - Build 82)

**Data do Checkpoint:** 28/10/2026 (13h00)  
**Diretório do Projeto:** `D:\GoOrNo`  
**Código-Fonte Nativo Android:** `D:\GoOrNo\android`  
**Pacote Android:** `com.creativeam.goorno`  
**Última Versão Compilada:** `GoOrNo-v3.3.61.apk` (`versionCode 82` | `versionName "3.3.61"`)  
**Caminho do APK Atual:** `D:\GoOrNo\GoOrNo-v3.3.61.apk` e `D:\GoOrNo\GoOrNo.apk`  
**Download Direto:** `https://goorno-app.vercel.app/GoOrNo-v3.3.61.apk`  
**Versão Estável Play Protect:** `https://goorno-app.vercel.app/GoOrNo-v3.3.53.apk`  
**Painel Web Administrativo:** `https://goorno-app.vercel.app/#admin` (ou `/admin`)  
**Repositório GitHub (CI/CD Actions):** `https://github.com/olirumtecapp-debug/goorno`

---

## 1. Estado Atual do Projeto (100% Funcional, Compilado e Publicado)

### 🚀 Novidades da `v3.3.61` (Checkpoint #028 - Build 82):

1. **Correção de Mistura de Ofertas em Scroll na 99 (Eliminação de Chamadas Frankensteins):**
   - **Problema:** Quando a tela da 99 continha 3 ofertas e o motorista rolava para cima, os trechos de trajeto órfãos da oferta superior (`51 min • 28,4 km`) eram lidos e fundidos com o preço da oferta inferior (`R$ 15,90`), gerando cálculos bizarros como `0,53 R$/km`.
   - **Solução Implementada:**
     - Em [`NinetyNineOfferReader.kt`](file:///D:/GoOrNo/android/app/src/main/java/com/creativeam/goorno/reader/ninetynine/NinetyNineOfferReader.kt), a busca de percurso (`legs`, distâncias e durações) foi **ancorada estritamente a partir do índice da linha do preço principal para baixo** (`linesForLegs`), descartando qualquer resíduo que tenha rolado no topo.
     - Em [`BrazilianOfferParser.kt`](file:///D:/GoOrNo/android/app/src/main/java/com/creativeam/goorno/reader/BrazilianOfferParser.kt), o fatiamento por `Escolher` agora reconhece linhas de preços com dinâmico como cabeçalhos válidos caso não haja badge de categoria, mantendo a prioridade correta e sem absorver sobras de preços de ofertas anteriores.
   - **Testes:** Adicionado teste de regressão cobrindo o caso real de tela rolada com 15 nós. Todos os 46 testes unitários passaram com sucesso no GitHub Actions.

2. **Reconhecimento Abrangente da Nota do Passageiro (Ícone 👤 e Verificações da 99):**
   - **Problema:** Na primeira oferta ou em ofertas com passageiros com cartão verificado, a 99 exibe o ícone de silhueta de usuário `👤` (`\uD83D\uDC64`) seguido da nota e delimitadores como `• Cartão verif.` ou `• Novo passageiro`, sem utilizar estrelas ou a palavra `corridas`, resultando em um hífen `-` no pilar de Nota.
   - **Solução Implementada:**
     - Regex atualizada para contemplar ícones de passageiro (`👤`, `👥`, `🧑`, etc.) e palavras-chave de contexto (`Cartão verif.`, `novo`, `passageiro`, `usuário`, `verificado`).
     - Reconstrução de nós fragmentados no `normalizeFragmentedNodes` para garantir que `👤 4,95` não seja descartado.
     - Fallback aprimorado para priorizar a nota da corrida e ignorar valores do cabeçalho do motorista.

3. **Arquitetura de Cards Padronizada em 3 Opções:**
   - **Card 3 Pilares Compacto (Padrão Oficial):** Exibe `R$/Km`, `R$/Hora` e `Nota` em destaque superior com barrinhas verticais coloridas, e na linha inferior: `[Operadora] R$ XX,XX • Tempo • Distância` + botão fechar.
   - **Card 3 Pilares:** Formato clássico de 3 colunas com valor total no topo.
   - **Card Impacto Pro:** Formato com valor gigante e semáforo sólido de alto contraste.

---

## 2. Instruções para Continuidade no GoOrNo
Ao retornar para o GoOrNo:
1. Trabalhar em `D:\GoOrNo`.
2. O APK v3.3.61 está disponível localmente e publicado na Vercel / GitHub.
3. Se precisar alterar código Android, editar em `android/app/src/main/java/...`, atualizar a versão em `android/app/build.gradle` e commitar no branch `main` para acionar o workflow do GitHub Actions.
