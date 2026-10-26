# CHECKPOINT GOORNO — Checkpoint #021 (v3.3.54 - Build 75)
**Data:** 26 de Outubro de 2026 (12h00)  
**Status do Projeto:** ✅ Novo Card Compacto 3 Pilares, Novo Card Clássico Lateral, Critério Inteligente de Nota & Filtro Anti-Glitch

---

## 1. O que foi consolidado no Checkpoint #021:

### A. Critério Inteligente de Nota (Score Ponderado Lucro x Segurança)
1. **Regra do Lucro Compensa:**
   - Quando o `R$/km` e o `R$/hora` superam a meta do motorista com folga, uma nota média de passageiro (ex: 4.83) **NÃO rebaixa mais o card para Amarelo**.
   - O card principal permanece **VERDE (Aceita)** com a nota tolerada em destaque.
2. **Segurança Preservada:**
   - Apenas notas verdadeiramente de risco (< 4.70) ou sem lucro forte acionam o semáforo de atenção/recusa.

### B. Filtro Anti-Glitch de Transições de Tela
1. **Eliminação de Picos Fantasmas (R$ 131/h ou R$ 99/h):**
   - Implementado filtro de sanidade em `FinancialEngine.kt` que descarta leituras transitórias onde o tempo é lido como 1 ou 2 min para distâncias superiores a 1.5 km (ou velocidade estimada > 130 km/h).
   - O cálculo aguarda a estabilização completa da viagem sem travar nem gerar números irreais.

### C. Novo Card Compacto 3 Pilares (Fiel à Imagem 2)
1. **Estrutura Ultra-Fina:**
   - Removido o valor em dinheiro grande do topo.
   - 3 Colunas principais com barrinhas indicadoras coloridas: `R$/Km`, `R$/Hora` e `Nota`.
   - Linha inferior com tempo e distância (`— 14 min • 4,3 km`), tag do app e temporizador.
2. **Desacoplamento Total de Alertas:**
   - Zero poluição ou texto encavalado no card. Todos os avisos (Área de Risco, Favorito, Embarque Longe, Paradas, Supermercado) abrem em suas próprias janelinhas auxiliares independentes logo abaixo.

### D. Novo Card Clássico Lateral (Fiel à Imagem 3 Adaptada)
1. **Divisão de Alto Contraste:**
   - **Lado Esquerdo:** Dados detalhados da corrida (Preço total, R$/km, R$/h, nota, tempo, km e endereço).
   - **Lado Direito:** Bloco sólido de cor vibrante (Verde Neon, Âmbar Ouro, Vermelho Vivo) **sem texto**, para associação reflexiva imediata no canto do olho do motorista.
2. **Avisos Desacoplados:** Utiliza o mesmo sistema de pílulas auxiliares independentes.

### E. Compatibilidade Google Play Protect
- O link `https://goorno-app.vercel.app/GoOrNo-v3.3.53.apk` permanece **100% ativo e intocado** nos servidores para a equipe do Google analisar a submissão enviada ontem.
- Versão interna e do APK atualizada para **`v3.3.54 (Build 75)`**.

---

## 2. Links Oficiais e Painéis

| Recurso | URL | Status |
|---|---|---|
| **Produção Web (Vercel)** | `https://goorno-app.vercel.app` | ✅ No Ar & Atualizado |
| **Download APK v3.3.54** | `https://goorno-app.vercel.app/GoOrNo-v3.3.54.apk` | ✅ Build 75 |
| **Download APK v3.3.53 (Play Protect)** | `https://goorno-app.vercel.app/GoOrNo-v3.3.53.apk` | ✅ Preservado p/ Google |
| **Central de Continuidade** | `http://localhost:3010` | ✅ Checkpoint #021 Ativo |
| **Copiloto Web Local** | `http://localhost:3008` | ✅ Ativo na porta 3008 |
| **Repositório GitHub** | `https://github.com/olirumtecapp-debug/goorno.git` | ✅ Branch main |

---

## 3. Frase de Restauração Segura (Rollback)
> *"Antigravity, deu ruim no GoOrNo! Volte para o Checkpoint #020!"*
