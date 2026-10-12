package com.creativeam.goorno.util

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Utilitário para abrir DIRETO a visão 360° da rua no Google Street View (`google.streetview:cbll=lat,lng`)
 * a partir do endereço de Origem ou Destino lido da 99 ou Uber.
 */
object StreetViewLauncher {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun openDirectStreetView(context: Context, rawAddress: String) {
        val cleanAddress = rawAddress.trim()
        if (cleanAddress.isEmpty()) return

        mainHandler.post {
            Toast.makeText(
                context,
                "🌍 Abrindo Street View 360°: $cleanAddress",
                Toast.LENGTH_SHORT
            ).show()
        }

        scope.launch {
            val coords = resolveCoordinates(context, cleanAddress)
            mainHandler.post {
                if (coords != null) {
                    val (lat, lng) = coords
                    launchPanoramaAtCoords(context, lat, lng, cleanAddress)
                } else {
                    launchFallbackMapSearch(context, cleanAddress)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveCoordinates(context: Context, address: String): Pair<Double, Double>? {
        val queryWithCountry = if (address.lowercase(Locale.getDefault()).contains("brasil")) {
            address
        } else {
            "$address, Brasil"
        }

        // 1. Tenta o Geocoder nativo do Android (instantâneo)
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale("pt", "BR"))
                val results = geocoder.getFromLocationName(queryWithCountry, 1)
                if (!results.isNullOrEmpty()) {
                    val first = results[0]
                    if (first.hasLatitude() && first.hasLongitude()) {
                        return Pair(first.latitude, first.longitude)
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 2. Fallback: Nominatim OpenStreetMap (gratuito, sem chave)
        try {
            val encoded = Uri.encode(queryWithCountry)
            val url = URL("https://nominatim.openstreetmap.org/search?format=json&limit=1&countrycodes=br&q=$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("User-Agent", "GoOrNoDriverCopilot/2.7 (Android)")
                setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9")
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(body)
                if (arr.length() > 0) {
                    val obj = arr.getJSONObject(0)
                    val lat = obj.optString("lat").toDoubleOrNull()
                    val lon = obj.optString("lon").toDoubleOrNull()
                    if (lat != null && lon != null) {
                        return Pair(lat, lon)
                    }
                }
            }
        } catch (_: Exception) {
        }

        return null
    }

    private fun launchPanoramaAtCoords(
        context: Context,
        lat: Double,
        lng: Double,
        fallbackAddress: String
    ) {
        // 1º Tentativa: Intent nativa direta do Google Street View 360° no app do Google Maps
        try {
            val cbllUri = Uri.parse(String.format(Locale.US, "google.streetview:cbll=%.6f,%.6f", lat, lng))
            val svIntent = Intent(Intent.ACTION_VIEW, cbllUri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (svIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(svIntent)
                return
            }
        } catch (_: Exception) {
        }

        // 2º Tentativa: URL Universal Street View Panorama (`map_action=pano`)
        try {
            val universalPanoUri = Uri.parse(
                String.format(
                    Locale.US,
                    "https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=%.6f,%.6f",
                    lat,
                    lng
                )
            )
            val panoIntent = Intent(Intent.ACTION_VIEW, universalPanoUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(panoIntent)
            return
        } catch (_: Exception) {
        }

        launchFallbackMapSearch(context, fallbackAddress)
    }

    private fun launchFallbackMapSearch(context: Context, address: String) {
        try {
            val encoded = Uri.encode(address)
            val mapUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$encoded")
            val intent = Intent(Intent.ACTION_VIEW, mapUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
