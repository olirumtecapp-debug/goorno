# CHECKPOINT GOORNO — v3.3.14 (Build 35)
**Data:** 17 de Outubro de 2026  
**Status do Projeto:** ✅ 100% Estável, Compilado, Testado e Publicado

---

## 1. Problemas Críticos Resolvidos Nesta Versão

### A. Fim do Looping Infinito de Notificações
- **Causa Raiz:** O método `CopilotNotificationReceiver.buildActiveNotification()` invocava `GoOrNoForegroundKeepAliveService.start(context)`. Dentro do `onStartCommand()`, o serviço invocava de volta o `CopilotNotificationReceiver.buildActiveNotification()` em prioridade máxima (`PRIORITY_MAX`). Isso gerava uma recursão mútua em segundo plano disparando centenas de notificações, apitos e vibrações por segundo.
- **Correções Aplicadas:**
  1. Desacoplamento no `CopilotNotificationReceiver.kt`: remoção da chamada cruzada de serviço dentro da montagem da notificação e do `showActiveNotification`.
  2. Trava de execução única no `GoOrNoForegroundKeepAliveService.kt`: inclusão de `@Volatile private var isRunning = false` para impedir reinício recursivo e remoção de reinício em cascata no `onDestroy`.
  3. Notificação fixada em modo silencioso e não intrusivo com `.setOnlyAlertOnce(true)` e `.setPriority(NotificationCompat.PRIORITY_LOW)`, sem alarmes sonoros em loop.

### B. Identificação do Motorista por PIN de 4 Dígitos & Recuperação sem Códigos Aleatórios
- **Arquitetura 100% Autônoma:**
  1. **Cadastro/Validação:** O motorista informa seu WhatsApp, um PIN de 4 dígitos de sua escolha e uma Palavra Secreta / Placa do Veículo (ex: `BRA2E19`).
  2. **Troca de Aparelho / Reinstalação:** Ao abrir o app em outro celular e digitar o número de WhatsApp, o sistema solicita o PIN de 4 dígitos para autorizar o acesso. Uma vez autorizado, todas as configurações (custos do carro, metas, áreas de risco) são restauradas automaticamente da nuvem (`goorno_cloud_store/driver_<whatsapp>`).
  3. **Recuperação de PIN:** Se o motorista esquecer o PIN, ele clica em *"Esqueci meu PIN"*, digita a Palavra Secreta / Placa cadastrada e define um novo PIN de 4 dígitos na hora — sem depender de SMS, WhatsApp API externa ou e-mail com códigos que atrasam.

---

## 2. Links de Download & Distribuição Oficiais

| Canal | URL | Status |
|---|---|---|
| **Download Direto (Vercel)** | `https://goorno-app.vercel.app/GoOrNo-v3.3.14.apk` | ✅ HTTP 200 (52.6 MB) |
| **Download Permanente** | `https://goorno-app.vercel.app/GoOrNo.apk` | ✅ HTTP 200 (52.6 MB) |
| **Repositório GitHub (Raw)** | `https://github.com/olirumtecapp-debug/goorno/raw/main/GoOrNo-v3.3.14.apk` | ✅ HTTP 200 (52.6 MB) |
| **Endpoint de Versões** | `https://goorno-app.vercel.app/api/app-version` | ✅ v3.3.14 / Code 35 |

---

## 3. Arquivos Modificados & Commits
- `android/app/build.gradle`: `versionCode 35`, `versionName "3.3.14"`.
- `android/app/src/main/java/com/creativeam/goorno/service/CopilotNotificationReceiver.kt`: desacoplamento recursivo, canal de notificação dedicado, flag silenciosa.
- `android/app/src/main/java/com/creativeam/goorno/service/GoOrNoForegroundKeepAliveService.kt`: trava `@Volatile isRunning` e parada limpa.
- `android/app/src/main/java/com/creativeam/goorno/data/license/LicenseManager.kt`: suporte a `pin`, `recoverySecret`, `recoverPinOnline` e cache local.
- `android/app/src/main/java/com/creativeam/goorno/ui/SubscriptionScreen.kt`: campos de PIN, Placa/Palavra, alertas de PIN incorreto e diálogo "Esqueci meu PIN".
- `server/licenseStore.ts`: persistência de PIN e recuperação por palavra secreta.
- `server/index.ts`: rotas `/api/license/recover-pin` e `/api/app-version` v3.3.14.
- `vercel.json`: rotas de download atualizadas.
- `.github/workflows/build-apk.yml`: build automatizado com auto-commit do APK na branch `main`.

---
*Gerado em 17/10/2026.*
