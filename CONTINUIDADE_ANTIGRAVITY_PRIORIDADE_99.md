# PONTO DE CONTINUIDADE — GOORNO (09/11/2026)
## OBJETIVO
Resolver duas falhas observadas pelo motorista na versão experimental do GoOrNo: (1) a primeira oferta 99 era avaliada rapidamente, mas a oferta seguinte às vezes não aparecia; (2) "Nota --" persistia apesar da nota estar visível na tela. Prioridade máxima: **nova oferta nunca deve esperar OCR da nota anterior**.

## Branch / GitHub APK
- Repositório: https://github.com/olirumtecapp-debug/goorno
- Branch nova para teste: `fix/99-prioridade-novas-chamadas-20261109`
- Branch anterior (correção OCR inicial): `fix/99-nota-ocr-20261109`
- Produção: `main` — não alterar até homologação.
- Workflow automático: `.github/workflows/build-apk.yml` (push em android/** dispara teste JUnit + geração APK, upload de artefato GitHub).
- O workflow foi protegido na branch anterior para **não enviar APK de branch de teste diretamente à main**.
- Verificar conclusão dos testes e existência do artefato do ÚLTIMO commit da branch, nunca distribuir APK de execução anterior.

## Diagnóstico e correções nesta branch
1. `GoOrNoAccessibilityService.kt`: removida solicitação imediata de screenshot da nota de dentro do scan principal. Agora somente uma oferta efetivamente exibida no overlay pode solicitar OCR suplementar. Desse modo, texto e decisão financeira têm prioridade.
2. `GoOrNoAccessibilityService.kt`: `lastOfferProcessedTimeMillis` deixa de atualizar em leituras duplicadas descartadas, reduzindo bloqueios artificiais nas solicitações de screenshot para nova chamada.
3. `GoOrNoAccessibilityService.kt`: OCR parcial de nota passa também os textos lidos à recuperação segura; não cria uma nova corrida nem grava duplicata.
4. `NinetyNineOfferReader.kt`: `recoverSingleOfferRatingFromOcr` recupera nota textual do passageiro com contexto "corridas", "perfil", "cartão" etc., quando OCR não reconhece o preço encoberto pelo overlay. Exige UM único card exibido, não mais que um botão "Escolher", não mais que um cabeçalho de categoria e nota única. Rejeita dados monetários R$, R$/km e decimais em endereços. Não altera preço/km/min.
5. `NinetyNineRatingRecoveryTest.kt`: novos testes de OCR parcial, não-contaminação entre cards e prevenção de falsas notas.

## Pendências para homologação
- Acompanhar GitHub Actions e corrigir testes/compilação que falharem, apenas nesta branch.
- Teste real no telefone 99: sucessão rápida de três chamadas diferentes, com e sem nota; aguardar entre 2-4 segundos e confirmar resposta em todas, sem card preso.
- Testar um card de Pop Nova com nota 4,90 + corridas, e múltiplos cards simultâneos. Resultado incorreto de nota NUNCA é aceitável; se nota ambígua não for possível inferir, declarar indisponibilidade em vez de inventar.
- Testar Uber no mesmo APK.
- Revisar o bloqueio de repetição `shownOfferTimestamps` de 15 segundos se ainda perder chamadas semelhantes, com prints/logs: não mudar arbitrariamente antes de distinguir *mesma oferta* de *nova oferta*.
- Conferir possível divergência de R$14,90/20min overlay contra R$14,70/13min na tela, vista num print anterior. Não foi corrigido nesta rodada.
- Não mexer no semáforo, motor de cálculo, cores, UI, configurações ou backend de licenciamento.
- Sem merge nem publicação oficial sem aprovação e teste em dispositivo.

## Fluxo de publicação
GitHub Actions → teste JUnit → APK experimental como Artifact → homologação em campo → aprovação do usuário → Antigravity coordena incremento versionCode/versionName, merge na main e publicação via GitHub já existente.

## Segurança
Esta é versão experimental. Nunca alegar que todas as notas serão identificadas quando não há texto disponível no OCR/Accessibility. Em caso de ambiguidade, não atribuir nota de outro cartão.
