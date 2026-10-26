# CHECKPOINT GOORNO — Checkpoint #024 (v3.3.57 - Build 78)
**Data:** 26 de Outubro de 2026 (15h10)  
**Status do Projeto:** ✅ Filtro "Agradecemos sua resposta", Separação de Endereços, Bloco na Direita (Só a Cor), Card Compacto Grande (25sp) & Estabilidade Total

---

## 1. O que foi consolidado no Checkpoint #024:

### A. Filtro Anti-Sujeira "Agradecemos sua resposta"
1. **Origem do Problema:**
   - Na 99, ao recusar uma corrida anterior ou responder a um motivo de recusa, o app exibe a mensagem de feedback/toast: *"Agradecemos sua resposta"*.
   - Quando a nova corrida tocou, essa mensagem ainda estava em tela/memória de acessibilidade e acabou sendo engolida pelo parser como se fosse parte da rua, fundindo a Origem com o Destino.
2. **Solução Aplicada em `BrazilianOfferParser.kt`:**
   - Adicionada limpeza e corte automático em `cleanAddressCandidate` e `isOfferBoundary` para *"Agradecemos sua resposta"*, *"Por que você recusou"*, *"Motivo da recusa"*.
   - Removido o prefixo `0 (9 min 3,1 km)` que sobrava no início da rua.
   - Origem (Embarque) e Destino (Desembarque) agora ficam estritamente separados em seus respectivos campos.

### B. Card Clássico Dividido (Bloco na DIREITA — Só a Cor)
- Bloco vertical sólido posicionado no **lado direito** (`dp(40)` x `dp(66)`), **sem texto e sem ícone nenhum**, na cor pura do semáforo.
- Dados da corrida alinhados na esquerda com fundo escuro grafite `#131418` e borda sutil de 1dp.

### C. Card Compacto 3 Pilares (Super Legível — Números Grandes 25sp)
- Números de `R$/Km`, `R$/Hora` e `Nota` em **25sp Bold**, com barrinhas nítidas e altura total ultra-compacta.

### D. Eliminação de Crashes do Android
- Removido `clipToOutline` incompatível com gradientes e estabilizado o serviço de acessibilidade.

### E. Compatibilidade Google Play Protect
- `https://goorno-app.vercel.app/GoOrNo-v3.3.53.apk` permanece **100% ativa e intocada** para o Play Protect.
- Nova versão para testes na rua: **`v3.3.57 (Build 78)`**.

---

## 2. Links Oficiais

| Recurso | URL | Status |
|---|---|---|
| **Download APK v3.3.57** | `https://goorno-app.vercel.app/GoOrNo-v3.3.57.apk` | ✅ Build 78 |
| **Download APK v3.3.53 (Play Protect)** | `https://goorno-app.vercel.app/GoOrNo-v3.3.53.apk` | ✅ Preservado p/ Google |
| **Central de Continuidade** | `http://localhost:3010` | ✅ Checkpoint #024 |

---

## 3. Frase de Emergência / Rollback:
> *"Antigravity, deu ruim no GoOrNo! Volte para o Checkpoint #024!"*
