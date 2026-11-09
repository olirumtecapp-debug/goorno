# GOORNO — CONTINUIDADE ANTIGRAVITY — LEITOR 2 LAB01
Data: 09/11/2026

## Identificação sem confusão
- Nome da experiência: **GoOrNo Leitor 2 — LAB01**
- `versionName`: **3.3.80-LAB01-Leitor2**
- `versionCode`: **101**
- Repositório: https://github.com/olirumtecapp-debug/goorno
- Branch experimental: `feature/leitor2-estruturado-lab01`
- A `main` permanece sem alterações. **NÃO fazer merge nem publicar oficial automaticamente.**
- GitHub Actions: `.github/workflows/build-apk.yml` — nomes únicos de artefato incluem versão, Run e SHA.
- Este é um APK experimental com mesmo applicationId do GoOrNo. Não instala lado a lado; pode substituir o anterior ao instalar. Não apagar dados existentes.

## REGRA ABSOLUTA: UBER NÃO É PARTE DO EXPERIMENTO
**O UberOfferReader.kt NÃO foi modificado nesta feature.**
O novo caminho `NinetyNineStructuredReader` só é executado quando `SourceApp == NINETY_NINE_DRIVER`. A Uber permanece no leitor e no fluxo de OCR originais. Não fazer mudanças no leitor da Uber, algoritmo financeiro, configurações, semáforo, história, licença, identidade visual, ícone, cores ou backend.

## Problemas reais que motivaram a mudança
- Solicitações da 99, dois cards: topo Pop Nova R$10,20, nota 4,99, embarque 6min/1,1km, viagem 7min/2,2km; abaixo Pop Nova R$9,80, nota 4,79, embarque 6min/1,2km, viagem 7min/2,4km com 1 parada.
- O GoOrNo havia selecionado apenas o segundo card e exibido "Nota --".
- Novas chamadas logo depois da anterior às vezes não eram avaliadas; notas ficam ausentes em outras ofertas mesmo visíveis na tela.
- Exigência do usuário: NÃO adicionar API paga/IA generativa; usar apenas Android AccessibilityService e Google ML Kit local já presentes.

## Arquitetura do Leitor 2 (99)
Arquivo novo: `android/app/src/main/java/com/creativeam/goorno/reader/ninetynine/NinetyNineStructuredReader.kt`.
- Recebe fragmentos com texto e coordenadas (centerY, leftX) capturados por Accessibility e OCR local.
- Ordena por posição vertical (em vez de depender da ordem de chegada dos nós).
- Agrupa textos da MESMA linha, preservando títulos e botões repetidos em cartões diferentes.
- Detecta cards da LISTA SOLICITAÇÕES da 99 delimitando cabeçalho legítimo e botão Escolher/Selecionar.
- Isola o conteúdo de cada cartão e entrega **cada card separadamente** ao parser da 99 já existente, para manter regras financeiras e tratamentos prévios de formato.
- Mantém a ordem de cima para baixo e impede que preço/nota/tempo de outros cartões contaminem a análise.
- Se não houver geometria de lista confiável (ex.: oferta única no mapa), retorna null e mantém a leitura antiga da 99. Se for lista reconhecida mas incompleta, não combina dados de cartões diferentes para preencher artificialmente.
- Esse motor é local, sem custo por leitura ou novos servidores.
- A chamada Uber *não* invoca este módulo.

Integração isolada:
`android/app/src/main/java/com/creativeam/goorno/service/GoOrNoAccessibilityService.kt` — somente o ramo `NINETY_NINE_DRIVER` procura o Leitor 2. Ramo Uber chama `extractOffersForApp` original.
Para OCR, a segmentação espacial somente opera quando `effectiveOcrApp` é 99. Caso contrário preserva o código original.

## Testes
Arquivo `android/app/src/test/java/com/creativeam/goorno/NinetyNineStructuredReaderLabTest.kt`:
- Dois cards reais 99: R$10,20 / 4,99 e R$9,80 / 4,79.
- A ordem dos nós recebidos invertida não muda a ordem visual das ofertas.
- Overlay com preço da corrida de baixo sobreposto no topo não pode substituir o preço real do primeiro card.
- Nota ausente na segunda corrida não pode herdar a nota da primeira.
- Lista com um card / chamada única no mapa (fallback controlado).
- Oferta com dados incompletos não deve gerar corrida fictícia.
- Prova de roteamento separado: chamada com `SourceApp.UBER_DRIVER` retorna null no motor novo, enquanto `UberOfferReader.parseScreenNodes` segue funcionando.

## Regras de liberação do APK
1. Verificar o ÚLTIMO commit da branch e o GitHub Actions correspondente. `testDebugUnitTest`, `assembleDebug`, `assembleRelease` devem passar todos.
2. Baixar somente o artefato `GoOrNo-APK-v3.3.80-LAB01-Leitor2-run<N>-<SHA>` da execução bem-sucedida que corresponde ao commit escolhido.
3. Validar em aparelho: 99, tela de ofertas únicas, lista de 2 cards, lista com 3, preço e nota diferentes, corrida com 1 parada, transição mapa → Solicitações, e 3 chamadas novas em sequência.
4. Testar a Uber com chamada real e a mesma configuração utilizada na versão anterior. **Qualquer regressão Uber bloqueia a aprovação do LAB.**
5. Verificar que não houve regressões de persitência, login/licença, histórico, cálculos R$/km e R$/hora, classificação do semáforo e sobreposição.
6. Conferir se a nota realmente aparece no card quando está visível na 99; se não, registrar snapshot de Accessibility e OCR com coordenadas para investigar perda de dados antes de alterar novos filtros.
7. Se falhar qualquer teste, corrigir SOMENTE esta branch, documentar causa/arquivos/testes, recompilar e instalar o APK com o novo Run/SHA.
8. Manter `main` intacta até autorização explícita do usuário.

## Limitações
Sem teste em aparelho, não é possível garantir que a 99 exponha todas as notas à árvore de acessibilidade ou ao OCR. Não inventar nota para eliminar tracinhos: identificar origem exata do problema em snapshot real. Esta implementação reorganiza a SEGMENTAÇÃO da lista da 99, não reescreve o GoOrNo.


## GitHub Actions confirmado — Build 101 (código do LAB)
- Workflow **Run #210**: https://github.com/olirumtecapp-debug/goorno/actions/runs/42642549762
- Commit compilado: `ce017f4b55a8d659730f74e5c4cb2b94109c019c`
- Resultado: `testDebugUnitTest` **SUCESSO**, `assembleDebug` **SUCESSO**, `assembleRelease` **SUCESSO**.
- Artefato confirmado: https://github.com/olirumtecapp-debug/goorno/actions/runs/42642549762/artifacts/13686307125
- Nome: `GoOrNo-APK-v3.3.80-LAB01-Leitor2-run210-ce017f4`.
- Etapa de publicar APK na `main`: **IGNORADA**, por ser uma branch experimental.
- O documento foi adicionado depois do commit compilado; isso não altera o código do APK.
- **Teste no aparelho ainda não feito**: a compilação e os testes JUnit não demonstram sozinhos reconhecimento real da nota da 99 ou ausência de regressões na Uber em campo.

