package com.creativeam.goorno.activity

import android.app.Activity
import android.os.Bundle
import com.creativeam.goorno.util.StreetViewLauncher

/**
 * Activity transparente (Trampoline) disparada ao clicar nos botões "Origem" e "Destino" da notificação.
 * Garante que o Android recolha a cortina de notificações automaticamente e exiba o Street View na tela.
 */
class StreetViewTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val address = intent?.getStringExtra("EXTRA_ADDRESS").orEmpty()
        if (address.isNotBlank()) {
            StreetViewLauncher.openDirectStreetView(this, address)
        }
        finish()
    }
}
