# 📌 Ponto de Continuidade — Projeto GoOrNo (Copiloto de Lucro para Motoristas)
**Data de Registro:** 17 de Outubro de 2026  
**Última Versão Estável:** `v3.3.13` (Build 34)  
**Repositório Local:** `D:\GoOrNo`  
**Deploy Web:** [https://goorno-app.vercel.app](https://goorno-app.vercel.app)  
**Repositório GitHub:** `https://github.com/olirumtecapp-debug/goorno`

---

## 1. Status Geral da Versão Atual (v3.3.13 / Build 34)

- **APK de Produção:** `GoOrNo-v3.3.13.apk`.
- **Compatibilidade:** Android 8.0 até Android 14+ (Samsung One UI, Xiaomi HyperOS/MIUI, Motorola, etc.).
- **Download Direto:**
  - `https://goorno-app.vercel.app/GoOrNo-v3.3.13.apk`
  - `https://goorno-app.vercel.app/GoOrNo.apk`
- **Redirecionamento Vercel:** Configurado em `vercel.json` e rotas Express com suporte universal a `/(.*)\.apk`.

---

## 2. Blindagem de Estabilidade em 2º Plano (Resolução Definitiva do LMK com Spotify/Instagram)

1. **Foreground Service Persistente (`GoOrNoForegroundKeepAliveService`):**
   - Ancorado com notificação de alta prioridade na barra de status e `START_STICKY`.
   - Utiliza `foregroundServiceType="dataSync"`, em 100% de conformidade com a Google Play e Android 14+.
   - Reduz a pontuação do `oom_score_adj` para nível de serviço em primeiro plano (~200), impedindo que o Android mate o processo quando o motorista abrir aplicativos pesados.
2. **`android:largeHeap="true"` & `stopWithTask="false"`:**
   - Adicionados ao `AndroidManifest.xml`, garantindo teto de memória expandido para o motorista rodar sem gargalos.
3. **Pausa Inteligente em Aplicativos de Streaming e Redes Sociais:**
   - Detecção automática de apps como Spotify (`com.spotify.music`), Instagram (`com.instagram.android`), TikTok e YouTube.
   - Quando o motorista estiver nesses apps, o loop de 900ms é pausado e uma coleta de lixo (`System.gc()`) é disparada, liberando CPU e RAM.
   - Assim que o motorista retorna para a Uber, 99 ou Waze, a leitura reativa volta à velocidade máxima instantaneamente.

---

## 3. Novos Planos de Assinatura & Promoção de Lançamento (30 Dias)

### 🏷️ Os 4 Planos Oficiais

| Plano | Preço Normal | Preço Promoção (1ª Compra) | Equivalente Mensal (Promo) | Economia vs. Mensal Normal |
|---|---|---|---|---|
| **Mensal** | R$ 7,99 / mês | **R$ 3,99** (1º mês) | R$ 3,99 / mês | R$ 4,00 de desconto (50% OFF) |
| **Trimestral** | R$ 21,90 / 3 meses | **R$ 18,90** (1º ciclo) | R$ 6,30 / mês | Economia de R$ 5,07 |
| **Semestral** | R$ 41,90 / 6 meses | **R$ 34,90** (1º ciclo) | R$ 5,81 / mês | Economia de R$ 13,04 |
| **Anual (Destaque)** | R$ 79,90 / ano | **R$ 59,90** (1º ano) | **R$ 4,99 / mês** | **Economia de R$ 35,98** |

### 🛡️ Regras Comerciais e Promoção de Lançamento
- **Campanha de 30 Dias:** Controlada de forma centralizada no backend via `PROMO_CONFIG` (`startDate` e `endDate` configuráveis, sem datas hardcoded espalhadas).
- **Elegibilidade Estrita:** O desconto promocional é exclusivo para a **primeira contratação**. Se o motorista já possui pagamentos anteriores registrados no Asaas/Firebase, a API cobra automaticamente o valor normal.
- **Transparência Total:** A tela exibe antes do pagamento o Resumo da Contratação com o valor a pagar agora, a duração e o valor da próxima renovação.

### 🚫 Exclusão Completa do Plano Vitalício
- Totalmente removido da tela de assinaturas (`SubscriptionScreen.kt`), do backend (`asaas.ts`), da Landing Page e menus.
- **Preservação de Direitos Adquiridos:** Motoristas que já possuíam plano vitalício no Firebase (`plano: "vitalicio"`) continuam com status ativo e validade 2099 estritamente preservados; nenhuma nova contratação de vitalício pode ser gerada.

---

## 4. Comandos e Tokens Operacionais

- **Git:** `C:\Users\Murilo Pai\AppData\Local\github-copilot-git-2.53.0-4\cmd\git.exe`
- **GitHub Token:** `[CONFIGURADO NO GITHUB SECRETS / AMBIENTE OPERACIONAL]`
- **Keystore Android:** `android/app/goorno-fixed.keystore` (alias: `goornokey`, senha: `goorno123`).
- **Build Web/API:** `npm run build`
- **Checagem de Tipos:** `npx tsc --noEmit`
