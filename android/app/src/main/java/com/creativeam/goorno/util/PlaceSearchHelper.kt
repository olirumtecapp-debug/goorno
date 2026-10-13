package com.creativeam.goorno.util

import android.content.Context
import android.location.Address
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Representa um local ou endereço real validado via serviço de mapas (Geocoder).
 */
data class GeocodedPlace(
    val title: String,
    val subtitle: String,
    val fullFormattedAddress: String,
    val termToBlock: String,
    val district: String? = null,
    val street: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

/**
 * Auxiliar para busca e validação de endereços reais no GoOrNo estilo Google Maps.
 */
object PlaceSearchHelper {

    suspend fun searchPlaces(context: Context, query: String, maxResults: Int = 6): List<GeocodedPlace> {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 3) return emptyList()

        return withContext(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) return@withContext emptyList()
                val geocoder = Geocoder(context, Locale.forLanguageTag("pt-BR"))
                @Suppress("DEPRECATION")
                val addresses: List<Address> = geocoder.getFromLocationName(cleanQuery, maxResults) ?: emptyList()

                addresses.mapNotNull { addr ->
                    val thoroughfare = addr.thoroughfare?.trim() // Nome da Rua/Avenida
                    val subLocality = addr.subLocality?.trim()   // Bairro
                    val locality = (addr.locality ?: addr.subAdminArea)?.trim() // Cidade
                    val adminArea = addr.adminArea?.trim()       // Estado
                    val fullLine = addr.getAddressLine(0)?.trim().orEmpty()

                    val cleanStreet = thoroughfare?.replace(",", "")?.trim()
                    val cleanDistrict = subLocality?.replace(",", "")?.trim()

                    val mainTitle = when {
                        !cleanStreet.isNullOrBlank() -> cleanStreet
                        !cleanDistrict.isNullOrBlank() -> cleanDistrict
                        !addr.featureName.isNullOrBlank() -> addr.featureName.trim().replace(",", "")
                        else -> cleanQuery.replace(",", "")
                    }

                    val subList = mutableListOf<String>()
                    if (!cleanDistrict.isNullOrBlank() && cleanDistrict != mainTitle) {
                        subList.add(cleanDistrict)
                    }
                    if (!locality.isNullOrBlank()) {
                        subList.add(locality)
                    }
                    if (!adminArea.isNullOrBlank()) {
                        subList.add(adminArea)
                    }
                    val subtitle = if (subList.isNotEmpty()) subList.joinToString(" - ") else "Brasil"

                    // Bloqueia SOMENTE o termo específico pesquisado (nunca concatena rua e bairro com vírgula)
                    val termToBlock = when {
                        !cleanStreet.isNullOrBlank() -> cleanStreet
                        !cleanDistrict.isNullOrBlank() -> cleanDistrict
                        !addr.featureName.isNullOrBlank() -> addr.featureName.trim().replace(",", "")
                        else -> cleanQuery.replace(",", "")
                    }

                    GeocodedPlace(
                        title = mainTitle,
                        subtitle = subtitle,
                        fullFormattedAddress = fullLine.ifBlank { "$mainTitle, $subtitle" },
                        termToBlock = termToBlock,
                        district = subLocality,
                        street = thoroughfare,
                        latitude = addr.latitude,
                        longitude = addr.longitude
                    )
                }.distinctBy { it.fullFormattedAddress }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
