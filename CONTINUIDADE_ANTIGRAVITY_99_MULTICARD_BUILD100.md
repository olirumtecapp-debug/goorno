# PONTO DE CONTINUIDADE — GOORNO: LISTA 99, NOTA, APK IDENTIFICADO
Data: 09/11/2026

## Referência
- Repositório: https://github.com/olirumtecapp-debug/goorno
- Branch de teste: `fix/99-cards-nota-e-versao-20261109`
- Branch de produção: `main` (NÃO alterada)
- Base da branch: `fix/99-prioridade-novas-chamadas-20261109`
- Versão Android de teste: `3.3.79-teste01`, `versionCode 100`, no `android/app/build.gradle`.
- GitHub Actions: `.github/workflows/build-apk.yml`; artefatos usam versão, número da execução e SHA curto. **Instalar apenas o APK da ÚLTIMA execução com sucesso, correspondente à branch acima.**

## Evidências dos prints de campo
Tela "Solicitações" da 99 mostra dois cards:
- TOP: Pop Nova, R$10,20, nota 4,99, 135 corridas, 6 min/1,1 km + 7 min/2,2 km => 13 min e 3,3 km.
- BAIXO: Pop Nova, R$9,80, nota 4,79, 160 corridas, 6 min/1,2 km + 7 min/2,4 km, 1 parada => 13 min e 3,6 km.
A avaliação GoOrNo mostrou somente o card de baixo com "Nota --" e ignorou a expectativa de exibir o card de cima ao entrar na lista, embora este já tivesse sido lido na tela anterior.

## Falhas encontradas no código
1. `mergeSameRowNodes` concluía com `output.distinct()`, removendo indiscriminadamente "Pop Nova" e "Escolher" repetidos em outros cartões da mesma tela. Isso compromete os delimitadores de múltiplas ofertas.
2. O temporizador do overlay chamava `dismissCurrentOfferCardByUser()` ao zerar, eliminando a fila inteira. `advanceToNextOfferOrFinish()` existia, mas não era usado nesse caminho.
3. O cache `shownOfferTimestamps` elimina uma oferta já mostrada por até 15 segundos; ao sair do mapa para "Solicitações", podia omitir o card superior já reconhecido.
4. OCR suplementar de nota frequentemente é executado com overlay ativo. A 99 exibe duas notas de passageiros e a lista original de nós pode vir incompleta, tornando perigoso aceitar um valor numérico sem associá-lo ao card correto.
5. Workflow nomeava o artefato de APK como v3.3.20, mesmo o código sendo v3.3.78; repetidas compilações não eram identificáveis.

## Implementações realizadas na branch
- `service/PositionedTextRowMerger.kt`: remove duplicatas apenas DENTRO da mesma linha visual; preserva linhas idênticas em cartões separados; utilizado pelo `GoOrNoAccessibilityService.kt`.
- `overlay/OfferQueueProgression.kt` + `FloatingOverlayPanel.kt`: ao esgotar o temporizador, mostrar próximo card em fila; manter fingerprint de cada card atualizado; atualização de nota não reinicia a fila nem volta ao primeiro card.
- `reader/ninetynine/NinetyNineListDisplayPolicy.kt`: detectar troca de contexto do mapa para lista e alterações da lista; reapresentar todos os cards na ordem de tela, inclusive o topo já visto, sem reapresentação infinita a cada polling.
- `reader/ninetynine/NinetyNineVisualRatingMatcher.kt`: correlaciona OCR de nota ao preço da própria oferta via coordenadas verticais das linhas. Rejeita moedas, taxas R$/km, endereços e ambiguidades de preço igual. Conserva preços, km, minutos e endereços originais.
- `GoOrNoAccessibilityService.kt`: passa geometria de OCR à atualização da nota e evita salvar uma cópia de uma corrida já lida quando ela reaparece ao abrir a lista.
- `android/app/build.gradle`: APK nomeado internamente v3.3.79-teste01 (Build 100).
- `.github/workflows/build-apk.yml`: arquivo da execução nomeado `GoOrNo-v<versão>-run<número>-<sha>.apk` e artifact `GoOrNo-APK-v<versão>-run<número>-<sha>`; branch de testes não publica em `main`.
- `NinetyNineMultiCardFlowRegressionTest.kt`: casos baseados nos dois prints de R$10,20/R$9,80, fila sequencial, notas geométricas, ausência de cruzamento entre passageiros, duplicatas de Pop Nova/Escolher e comportamento de mudança de tela.
- Testes anteriores de OCR/99 permanecem.

## Pesquisa comparativa (referências públicas, sem copiar código proprietário)
- `jsfhzlrvr/snapcopilot-app`: lê nós/eventos Accessibility e usa análise de estado e tarefas de processamento fora da thread principal.
  https://github.com/jsfhzlrvr/snapcopilot-app
- `slawekkrol/ScreenTextReader`: arquitetura de captura com uma fonte de estado, reconhecimento de quadros novos e testes de parsing.
  https://github.com/slawekkrol/ScreenTextReader
- `TensorLong/screen-trans`: OCR com agrupamento por geometria e overlays.
  https://github.com/TensorLong/screen-trans
- Documentação oficial ML Kit, campos `line.boundingBox`:
  https://developers.google.com/ml-kit/vision/text-recognition/v2/android
Esses projetos não fornecem código privado de concorrentes nem garantem comportamento da 99 atual.

## CHECKLIST obrigatória do Antigravity antes do APK oficial
1. Fazer checkout desta branch exata; verificar último commit e GitHub Actions mais recente.
2. Confirmar `gradle testDebugUnitTest` e `assembleDebug assembleRelease` com SUCESSO; nunca publicar APK de um workflow parcial ou falho.
3. Instalar artefato da ÚLTIMA execução. Registrar versão 3.3.79-teste01, Build 100, número da execução e SHA curto no relatório.
4. Testar no celular duas ofertas simultâneas com a mesma modalidade Pop Nova. Confirmar ordem TOP R$10,20/nota 4,99, BAIXO R$9,80/nota 4,79; cabeçalhos e botões de escolha não podem sumir da análise.
5. Repetir após o primeiro card ter sido lido na tela do mapa antes de entrar em "Solicitações"; confirmar que o primeiro volta a ser apresentado na lista.
6. Testar três ofertas consecutivas (uma após outra) e expiração de 3 segundos; card não pode travar ou reaparecer indefinidamente.
7. Testar OCR com nota visível, nota ausente, duas notas diferentes e preços iguais: é proibido atribuir nota de passageiro errado.
8. Testar regressão Uber, rendimento por km/h, alerta do semáforo, notificações, histórico, fallback OCR e overlay.
9. Se algum teste falhar, relatar log e corrigir apenas na branch; não apagar dados nem alterar configurações financeiras.
10. SOMENTE depois da homologação em dispositivo e autorização do usuário, coordenar merge na main e publicação pelo GitHub.

**Limitação:** testes automatizados validam os casos simulados e código compilável, não substituem o teste no aparelho com a versão atual da 99.
