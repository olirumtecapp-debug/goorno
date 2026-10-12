package com.creativeam.goorno.detector

import android.content.Context
import android.content.pm.PackageManager
import com.creativeam.goorno.model.SourceApp

/**
 * Módulo de detecção de aplicativos de transporte instalados e em primeiro plano.
 */
object ForegroundAppDetector {

    data class InstalledAppStatus(
        val uberInstalled: Boolean,
        val uberPackageFound: String?,
        val ninetyNineInstalled: Boolean,
        val ninetyNinePackageFound: String?
    )

    /**
     * Verifica no aparelho real quais pacotes da Uber Driver e 99 Motorista estão instalados.
     */
    fun checkInstalledRideApps(context: Context): InstalledAppStatus {
        val pm = context.packageManager
        val uberPkg = SourceApp.UBER_DRIVER.knownPackages.firstOrNull { isPackageInstalled(pm, it) }
        val ninetyNinePkg = SourceApp.NINETY_NINE_DRIVER.knownPackages.firstOrNull { isPackageInstalled(pm, it) }

        return InstalledAppStatus(
            uberInstalled = uberPkg != null,
            uberPackageFound = uberPkg,
            ninetyNineInstalled = ninetyNinePkg != null,
            ninetyNinePackageFound = ninetyNinePkg
        )
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Identifica se o pacote da janela ativa pertence à Uber Driver ou à 99 Motorista.
     */
    fun identifyForegroundApp(packageName: CharSequence?): SourceApp {
        return SourceApp.fromPackageName(packageName?.toString())
    }
}
