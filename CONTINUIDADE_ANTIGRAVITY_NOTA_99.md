# PONTO DE CONTINUIDADE — GOORNO — 09/11/2026

## Repositório e versão
- Repo: https://github.com/olirumtecapp-debug/goorno
- Branch de correção: `fix/99-nota-ocr-20261109`
- Branch de produção: `main` (NÃO alterada nesta sessão).
- Build automática: `.github/workflows/build-apk.yml`, executada em push que altera `android/**` e via `workflow_dispatch`.
- GitHub Action pode gerar artefato de APK na branch; não confundir artefato experimental com publicação homologada.

## Evidência real dos prints (99, 09/11/2026)
A 99 mostra notas 4,94 / 4,90 / 5,00 / 4,87 / 4,89 / 4,94 / 4,92 mas overlay mostra "Nota --". Preços, km e minutos em geral são lidos. Um print mostrou R$14,90/20min/4,9km no overlay vs R$14,70/13min/4,028km na 99; investigar se foi transição ou resíduo, não declarar resolvido.

## Causa técnica identificada
`GoOrNoAccessibilityService.kt` só acionava OCR se a oferta estivesse incompleta em preço/duração/distância. Oferta completa nessas métricas, porém sem nota, era exibida sem OCR auxiliar. OCR também era impedido depois da exibição do card.

## Alterações nesta branch
1. `GoOrNoAccessibilityService.kt`: se uma oferta 99 válida não trouxer nota por Accessibility, dispara OCR assíncrono suplementar com throttle de 8 segundos, sem atrasar o overlay; OCR marcado como `ratingOnly` não é anunciado como nova corrida. Se encontrar nota válida da mesma oferta, atualiza o card exibido sem salvar segunda oferta.
2. `NinetyNineOfferReader.kt`: `enrichRatingsFromOcr` preserva integralmente preços, km, minutos e endereços originais; atribui nota SOMENTE com mesma fonte, preço (tolerância < 0,02) e distância total (tolerância < 0,16), e uma correspondência OCR única.
3. `NinetyNineRatingRecoveryTest.kt`: testes de sete padrões reais da nota, fragmentação de nós, isolamento entre cards e preservação dos cálculos.

## Limitações importantes
- Não promete que toda nota será capturada: OCR depende do texto visual disponível; screenshots podem conter o overlay e ocultar parte do preço; a proteção contra cruzamento de notas prefere manter "--" a atribuir uma nota errada.
- Não foi feita alteração de cálculo de valor/tempo/km nem refatoração do parser da Uber.
- Investigar divergência no primeiro print separadamente com logs de transição/fingerprint se voltar a ocorrer.

## Procedimento de continuidade para Antigravity
1. Fazer checkout da branch `fix/99-nota-ocr-20261109`, ler este documento e revisar o diff em relação a `main`.
2. Verificar status de GitHub Actions, em particular os testes `testDebugUnitTest` e tarefas `assembleDebug assembleRelease`; corrigir problemas de compilação APENAS na branch e sem mexer no que já está funcionando.
3. Não publicar APK automaticamente nem efetuar merge antes de teste físico em 99, 99 Lista de Solicitações, Uber, card único e dois cards; validar que nota correta aparece, sem troca entre cards.
4. Se confirmado, incrementar `versionCode` e `versionName` em `android/app/build.gradle`, coordenar processo oficial de APK do GitHub e publicar pelo fluxo existente após aprovação.
5. Se nota continuar em "--", usar `RuntimeDiagnosticsBus` para comparar `normalizedTexts` do Accessibility e OCR, verificando se texto da nota e preço do MESMO card estão presentes. Não desativar restrições contra nota falsa.
6. Verificar logs e fingerprints ao trocar entre ofertas, em especial possível card com preço R$14,90 enquanto tela mostra R$14,70.
7. Manter sem qualquer alteração: semáforo, motor financeiro, tolerâncias configuradas, UX, cores, overlay layout, áudio, persistência, leitura Uber, rotina 99 de valores/distâncias/tempo e backend de licenciamento.

## Segurança
Não apagar dados, não executar migrações, não forçar merge, não afirmar que screenshot/Android foram homologados sem testes reais.
