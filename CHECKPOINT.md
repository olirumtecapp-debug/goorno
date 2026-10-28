# CHECKPOINT GOORNO — Checkpoint #029 (v3.3.62 - Build 83)
**Data:** 28 de Outubro de 2026 (16h30)  
**Status do Projeto:** ✅ Correção Crítica de Leitura 99: Limpeza Total de Endereços (Remoção de prefixos `min/km`, eliminação de tags de rota como `RIO GRANDE`, expurgo de toasts de corrida aceita por outro motorista), Detecção Impecável de Nota do Passageiro 5.00 (`Perfil Premium`) e Bloqueio de Mistura de Ofertas Expiradas.

---

## 1. O que foi consolidado no Checkpoint #029:

### A. Limpeza Profunda e Descontaminação de Endereços (Origem e Destino)
1. **Remoção de Prefixos de Tempo e Distância (`8 min 2,8 km)`, `7 min 1,5 km)`):**
   - **Problema:** Quando o local de embarque ou desembarque era um estabelecimento comercial (ex: `Império Açaí e Tapioca`) ou continha fechamento de parênteses sem abertura (`8 min 2,8 km)`), a validação estrita de prefixos de rua descartava o texto limpo e revertia para a linha bruta contendo o tempo e km.
   - **Solução Implementada:**
     - Em `cleanAddressCandidate`, adicionada regra `0b` para decapitar qualquer prefixo de duração e distância residual (`^[\s\(\)\[\]\-]*(?:\d+h...)?(?:\d+min...)?(?:\d+km...)?[\)\],·•\-:\s]+`).
     - Em `extractOriginAndDestination`, `strippedPrefix` agora é aceito diretamente para qualquer endereço válido (comprimento >= 4), sem reverter para `rawLine`.
     - `LEADING_DURATION_DISTANCE_PREFIX_REGEX` aprimorada para capturar parênteses de fechamento `)` e colchetes `]`.

2. **Eliminação de Badges de Rodovias/Regiões (`RIO GRANDE`, `PEDÁGIO`, `RODOVIA`):**
   - **Problema:** Etiquetas em maiúsculas exibidas pela 99 (como `RIO GRANDE` no ABC paulista) eram anexadas ao final do endereço de destino (`Hospital da Mulher..., Jardim Nov. RIO GRANDE`).
   - **Solução Implementada:**
     - `cleanAddressCandidate` agora purga badges isolados ou sufixos de rodovias (`rio grande`, `pedágio`, `pedagio`, `via anchieta`, `rodoanel`, `imigrantes`).
     - `isOfferBoundary` encerra a captura de endereço imediatamente ao encontrar essas tags.

### B. Bloqueio Rigoroso de Corridas Aceitas por Outro Motorista ("Esta corrida foi aceita...")
1. **Rejeição Imediata de Oferta Morta / Toast de Expiração:**
   - Em `NinetyNineOfferReader.kt` (Seção 0b), telas ou clusters contendo `"esta corrida foi aceita"`, `"corrida foi aceita por outro"`, `"aceita por outro motorista"` são imediatamente classificados como `ScreenClassification.NON_OFFER` e descartados.
2. **Isolamento de Clusters em Scroll (`splitIntoMultipleOfferClusters`):**
   - Ao rolar ofertas na 99, quando uma chamada anterior expira e exibe a mensagem de aceita por outro motorista, ela é usada como divisor de fronteira (`lastAcceptedIdx`), impedindo que resíduos da chamada morta contaminem a chamada seguinte.
   - O delimitador do cartão agora ancora estritamente no **último cabeçalho legítimo antes do botão Escolher** (`indexOfLast`), eliminando a leitura cruzada entre a 2ª e a 3ª oferta.
3. **Corte de Mensagens de Toast em Endereços:**
   - Adicionada regra `0c` em `cleanAddressCandidate` e parada em `isOfferBoundary` para cortar mensagens como `"Esta corrida foi aceita por outro motorista"` e `"Isso é tudo por enquanto"`, impedindo que se fundam ao nome da rua.

### C. Detecção Impecável da Nota do Passageiro 5.00 com Perfil Premium
1. **Suporte a Estrela Circulada (`✪`), Símbolos Especiais e `Perfil Premium`:**
   - `normalizeFragmentedNodes` agora aceita qualquer símbolo especial / não alfanumérico antes da nota e agrupa todos os tokens subsequentes (`• 17 corridas • Perfil Premium`) via laço `while`.
   - Adicionado `premium` e `essencial` como contextos de passageiro nas 3 passadas de avaliação do `BrazilianOfferParser`.
2. **Priorização de `cleanedLines` no `NinetyNineOfferReader`:**
   - `NinetyNineOfferReader` avalia primeiro as linhas do cluster completo da oferta, garantindo que a nota do passageiro nunca seja ignorada caso esteja posicionada acima do preço.

### D. Testes Unitários Automatizados
- Adicionados 4 novos testes de regressão cobrindo todos os cenários reais relatados pelo usuário:
  1. `test 99 passenger rating 5_00 with circled star and perfil premium`
  2. `test address cleaning strips leading duration and distance prefix from establishment and street`
  3. `test address cleaning strips RIO GRANDE badge and accepted toast message`
  4. `test 99 rejection of accepted by another driver offer and exclusion from list`

### E. Preservação Total da Versão 53 para o Google Play Protect
- O binário `https://goorno-app.vercel.app/GoOrNo-v3.3.53.apk` permanece **100% ativo e intocado**.
- Nova versão oficial gerada: **`v3.3.62 (Build 83)`**.

---

## 2. Links Oficiais

| Recurso | URL | Status |
|---|---|---|
| **Download APK v3.3.62** | `https://goorno-app.vercel.app/GoOrNo-v3.3.62.apk` | ✅ Build 83 |
| **Download APK v3.3.53 (Play Protect)** | `https://goorno-app.vercel.app/GoOrNo-v3.3.53.apk` | ✅ Build 74 |
| **Central de Continuidade** | `http://localhost:3010` | ✅ Ativa |
