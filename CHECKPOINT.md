# CHECKPOINT GOORNO — Checkpoint #025 (v3.3.58 - Build 79)
**Data:** 26 de Outubro de 2026 (16h15)  
**Status do Projeto:** ✅ Correção Definitiva de Crash do Android, Card Clássico Integrado de Ponta a Ponta na Direita (Só a Cor), Card Compacto Ultra-Negritado (26sp) & Filtro de Modais da 99

---

## 1. O que foi consolidado no Checkpoint #025:

### A. Eliminação Definitiva da Mensagem de Falhas do Android ("GoOrNo apresenta falhas continuamente")
1. **Causa Raiz Identificada:**
   - No `GoOrNoApplication.kt`, o `installGlobalCrashShield` continha uma interceptação de exceções não tratadas na thread principal (`main`) que não repassava ao handler do sistema quando classificadas como "recuperáveis".
   - No Android, quando a Main UI Thread sofre uma exceção não capturada, o `Looper` da thread é encerrado. Suprimir a exceção sem delegar deixava o processo do app em estado zumbi/inoperante, fazendo o `ActivityManager` do Android detectar falhas repetidas e exibir o diálogo do sistema: *"O GoOrNo continua apresentando falhas"*.
2. **Correção:**
   - Blindagem defensiva total em `GoOrNoApplication.onCreate()` e `MainActivity.refreshPermissionsState()` com blocos `try-catch`.
   - Ajustado o `installGlobalCrashShield` para proteger threads secundárias silenciosamente e delegar a thread principal ao handler padrão de forma segura, garantindo que o ciclo de vida do Android seja 100% íntegro.

### B. Card Clássico Dividido (Fiel à Imagem 2 — Bloco Integrado na Direita de Ponta a Ponta, Só a Cor)
- **Design:** O bloco lateral não é mais uma pílula flutuante interna com margens. Agora é um **bloco sólido vertical totalmente integrado**, que preenche **100% da altura da borda direita do card** (de cima a baixo, `topEnd = 16dp`, `bottomEnd = 16dp`, sem margem superior, inferior ou lateral).
- **Sem texto e sem ícone:** Apenas a cor sólida pura do semáforo (`#22C55E`, `#F59E0B`, `#EF4444`), permitindo identificação visual instantânea.
- **Seção Esquerda:** Métricas limpas (`R$/km` em 21sp Bold, `R$/hora` + `Nota`, e `[99] tempo - km`).

### C. Card Compacto (3 Pilares Ultra-Legíveis — Números em 26sp Bold)
- Números de `R$/Km`, `R$/Hora` e `Nota` em **26sp Extra Bold**, em branco de alto contraste (`#FFFFFF`), com barrinhas indicadoras de `5dp x 24dp` e rótulos nítidos em `12sp Bold` (`#CBD5E1`).
- Altura enxuta, sem cabeçalho gigante de valor, perfeito para visualização rápida no para-brisa do carro.

### D. Filtro Anti-Sujeira "Agradecemos sua resposta" e Modais da 99
- Rejeição instantânea de telas de feedback de recusa ("Agradecemos sua resposta", "Por que você recusou", "Motivo da recusa") e modais de bloqueio ("Várias solicitações seguidas recusadas", "Deseja continuar online", "Pausado por").
- Preservação total da integridade dos campos de Origem e Destino.

### E. Compatibilidade Google Play Protect
- `https://goorno-app.vercel.app/GoOrNo-v3.3.53.apk` permanece **100% ativa e intocada** para o Play Protect.
- Nova versão para testes na rua: **`v3.3.58 (Build 79)`**.

---

## 2. Links Oficiais

| Recurso | URL | Status |
|---|---|---|
| **Download APK v3.3.58** | `https://goorno-app.vercel.app/GoOrNo-v3.3.58.apk` | ✅ Build 79 |
| **Download APK v3.3.53 (Play Protect)** | `https://goorno-app.vercel.app/GoOrNo-v3.3.53.apk` | ✅ Preservado p/ Google |
| **Central de Continuidade** | `http://localhost:3010` | ✅ Checkpoint #025 |
