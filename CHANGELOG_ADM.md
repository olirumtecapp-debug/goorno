# 📋 Livro Mestre de Auditoria e Continuidade — GoOrNo

> **Regra Mandatória:** Registro de alterações de engenharia, melhorias de precisão, pontos de restauração e integridade matemática sem remendos.

---

## 📌 Checkpoint #008 — Correções de Precisão de Leitura e Latência (Uber & 99)
- **Data/Hora:** 23/10/2026 - 11:15
- **Versão:** `v3.3.48` (Evolução a partir da Golden `v3.3.47-ESTAVEL-GOLDEN`)
- **Ponto de Restauração Anterior:** `v3.3.47-ESTAVEL-GOLDEN` (Branch: `backup-v3.3.47-estavel`, Zip físico: `D:\GoOrNo\backups\GoOrNo_v3.3.47_ESTAVEL_FUNCIONANDO.zip`)
- **Frase de Rollback Rápido:** `"Antigravity, deu ruim, volte para o ponto estável!"`

### 🔍 Diagnóstico e Resoluções Implementadas:

1. **Latência de Leitura no Card da 99 (Imagens 1 e 2):**
   - **Problema:** Atraso perceptível entre o card da 99 surgir e o GoOrNo exibir o card de cálculo.
   - **Causa:** Trava de debounce de 260ms na acessibilidade associada ao polling secundário de 900ms, somado à tentativa prematura de acionamento do OCR ótico pesado.
   - **Solução:** Implementado bypass imediato de throttle (`forceThrottleBypass = true`) quando o evento de acessibilidade contiver indicadores diretos de oferta de corrida (`R$`, `Pop`, `Aceitar`, `Escolher`, `min`). Reduzido debounce de repouso para 180ms e expandida a margem de unificação de linhas (`mergeSameRowNodes`) para 38px, eliminando a fragmentação vertical de nós e permitindo resposta sub-50ms.

2. **Cálculo de R$/Hora Incorreto na 99 (Imagem 3):**
   - **Problema:** Oferta de R$ 12,40 com `(9 min 3,2 km)` e `(14 min 5 km)` calculando R$/Hora = 83!
   - **Causa:** O loop de captura de endereço absorveu indevidamente o segundo trecho `(14 min 5 km)` como continuação de texto do endereço anterior porque o delimitador exigia simultaneamente duração E distância na mesma linha com `&&`. Ao encontrar apenas 1 trecho (`legs.size == 1`), o GoOrNo computou a corrida inteira como durando apenas 9 minutos (o tempo de embarque).
   - **Solução:** 
     - Em `cleanAddressCandidate`, qualquer texto composto predominantemente por tempo/distância é rejeitado como endereço.
     - Em `extractRideLegs`, a fronteira de trecho agora reconhece imediatamente marcadores de tempo OU distância (`||`), permitindo que trechos em linhas únicas ou consecutivas sejam sempre separados.
     - Em `NinetyNineOfferReader.kt`, caso 2 distâncias e 2 durações existam na tela, ambas são sempre preservadas e somadas (`pickup + trip`).
     - **Resultado:** 9 min + 14 min = **23 min**; 3,2 km + 5,0 km = **8,2 km**; Ganho/Hora = **R$ 32,35/h** e Ganho/Km = **R$ 1,51/km**.

3. **Cálculo de R$/Hora Incorreto na Uber (Imagem 4):**
   - **Problema:** Oferta de R$ 52,32 com `5 min (1.5 km)` e `1 h e 19 min (28.3 km)` calculando R$/Hora = 131 e duração = 24 min!
   - **Causa:** A fragmentação de nós separava `1 h` de `e 19 min`. O parser capturava apenas o `19 min`, descartando completamente os 60 minutos da hora. Com `5 + 19 = 24 min`, o sistema calculava R$ 52,32 / (24/60) = R$ 130,80/h!
   - **Solução:**
     - Adicionado caso 2b e 2c no `normalizeFragmentedNodes` para unificar automaticamente nós com horas fragmentadas (`1 h` + `e` + `19 min (28.3 km)` -> `1 h e 19 min (28.3 km)`).
     - Atualizada regex `LEADING_DURATION_DISTANCE_PREFIX_REGEX` e `HOURS_ONLY_REGEX` para reconhecer e converter horas compostas em minutos (`1*60 + 19 = 79 min`).
     - **Resultado:** 5 min + 79 min = **84 min** (1,4h); 1,5 km + 28,3 km = **29,8 km**; Ganho/Hora = **R$ 37,37/h** e Ganho/Km = **R$ 1,76/km**.

### 🧪 Testes Automatizados Validados:
- `test real image 3 99 scenario R12_40 with 9 min 3_2 km and 14 min 5 km` ✅
- `test real image 4 uber scenario R52_32 with 5 min 1_5 km and 1 h e 19 min 28_3 km` ✅
- `test brazilian currency parsing` ✅
- `test distance km and duration minutes parsing` ✅
- `test multiple simultaneous offers on 99 screen` ✅

---
