package com.creativeam.goorno.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.creativeam.goorno.GoOrNoApplication
import com.creativeam.goorno.diagnostics.CopilotRuntimeState
import com.creativeam.goorno.diagnostics.RuntimeDiagnosticsBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receptor de Atualização Automática do Aplicativo (MY_PACKAGE_REPLACED).
 *
 * Executado pelo Android no instante em que uma nova versão do APK é instalada:
 * 1. Limpa caches temporários de OCR e arquivos antigos para evitar congelamentos.
 * 2. Recarrega as configurações do motorista sem exigir 'Forçar Parada' ou limpeza manual.
 * 3. Reinicia os serviços de primeiro plano caso o monitoramento estivesse ativo.
 */
class PackageReplacedReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        Log.i("PackageReplacedReceiver", "🔄 GoOrNo foi atualizado com sucesso! Executando auto-limpeza e reativação...")

        // 1. Limpeza automática de arquivos de cache temporários
        try {
            context.cacheDir.deleteRecursively()
            Log.i("PackageReplacedReceiver", "🧹 Cache temporário limpo com sucesso após atualização.")
        } catch (t: Throwable) {
            Log.w("PackageReplacedReceiver", "Falha ao limpar cache pós-atualização: ${t.message}")
        }

        // 2. Revalidação dos serviços em segundo plano
        receiverScope.launch {
            try {
                val app = context.applicationContext as? GoOrNoApplication
                val cfg = app?.preferencesRepository?.getConfigOnce()

                if (cfg != null && cfg.copilotMonitoringEnabled) {
                    Log.i("PackageReplacedReceiver", "Reativando serviços de primeiro plano do copiloto...")
                    try {
                        GoOrNoForegroundKeepAliveService.start(context)
                    } catch (_: Throwable) {}

                    CopilotNotificationReceiver.showActiveNotification(
                        context = context,
                        showProfit = cfg.showProfitOnOverlay,
                        showCost = cfg.showCostOnOverlay,
                        showShortcutBubble = cfg.showFloatingShortcutBubble
                    )
                    RuntimeDiagnosticsBus.updateRuntimeState(CopilotRuntimeState.READING_IN_PROGRESS)
                }
            } catch (t: Throwable) {
                Log.e("PackageReplacedReceiver", "Erro ao reativar serviços pós-atualização", t)
            }
        }
    }
}
