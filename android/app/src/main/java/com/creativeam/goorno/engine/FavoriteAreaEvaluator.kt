package com.creativeam.goorno.engine

import com.creativeam.goorno.model.DriverFinancialConfig
import java.text.Normalizer
import java.util.Locale

/**
 * Resultado da avaliação de Região Favorita / Bairros Preferidos.
 */
data class FavoriteZoneAssessment(
    val isFavorite: Boolean,
    val originMatchedZone: String? = null,
    val destinationMatchedZone: String? = null,
    val shortBadge: String = "",
    val detailedMessage: String = ""
)

/**
 * Avaliador Inteligente de Bairros Favoritos / Região Preferida do Motorista.
 *
 * Cruza a Origem e o Destino lidos da Uber ou 99 com a lista personalizada
 * de bairros, cidades ou regiões cadastradas pelo motorista.
 */
object FavoriteAreaEvaluator {

    fun evaluate(
        originAddress: String?,
        destinationAddress: String?,
        config: DriverFinancialConfig
    ): FavoriteZoneAssessment {
        if (!config.favoriteZonesEnabled || config.customFavoriteZones.isBlank()) {
            return FavoriteZoneAssessment(isFavorite = false)
        }

        val favoriteCandidates = parseCustomZones(config.customFavoriteZones)
        if (favoriteCandidates.isEmpty()) {
            return FavoriteZoneAssessment(isFavorite = false)
        }

        val normOrigin = normalizeText(originAddress ?: "")
        val normDest = normalizeText(destinationAddress ?: "")

        val matchedDest = findMatchingTerm(normDest, favoriteCandidates)
        val matchedOrigin = findMatchingTerm(normOrigin, favoriteCandidates)

        val isFav = matchedDest != null || matchedOrigin != null
        if (!isFav) {
            return FavoriteZoneAssessment(isFavorite = false)
        }

        val shortBadge = when {
            matchedDest != null && matchedOrigin != null -> "Origem & Destino"
            matchedDest != null -> matchedDest
            else -> matchedOrigin ?: ""
        }

        val detailedMessage = when {
            matchedDest != null && matchedOrigin != null ->
                "⭐ Região Favorita: Embarque em '$matchedOrigin' e Destino em '$matchedDest'!"
            matchedDest != null ->
                "⭐ Região Favorita: Destino em '$matchedDest' (sua área preferida de trabalho)."
            else ->
                "⭐ Região Favorita: Embarque em '$matchedOrigin' (sua área preferida de trabalho)."
        }

        return FavoriteZoneAssessment(
            isFavorite = true,
            originMatchedZone = matchedOrigin,
            destinationMatchedZone = matchedDest,
            shortBadge = shortBadge,
            detailedMessage = detailedMessage
        )
    }

    fun parseCustomZones(rawCsv: String): List<String> {
        if (rawCsv.isBlank()) return emptyList()
        return rawCsv
            .split(",", ";", "\n", "|")
            .map { it.trim() }
            .filter { it.length >= 3 }
            .distinctBy { normalizeText(it) }
    }

    fun parseCustomZonesList(rawCsv: String): List<String> = parseCustomZones(rawCsv)

    /**
     * Extrai uma sugestão limpa de rua ou bairro a partir de um endereço completo
     * para facilitar o botão de "Marcar como Região Favorita" com 1 toque no Histórico.
     */
    fun extractCleanStreetOrDistrict(fullAddress: String): String {
        return RiskAreaEvaluator.extractCleanStreetOrDistrict(fullAddress)
    }

    private fun findMatchingTerm(normalizedAddress: String, candidateTerms: List<String>): String? {
        if (normalizedAddress.isBlank()) return null
        for (term in candidateTerms) {
            val normTerm = normalizeText(term)
            if (normTerm.length < 3) continue
            val regex = Regex("""\b${Regex.escape(normTerm)}\b""")
            if (regex.containsMatchIn(normalizedAddress) || (normTerm.length >= 6 && normalizedAddress.contains(normTerm))) {
                return term
            }
        }
        return null
    }

    fun normalizeText(input: String): String {
        val nfd = Normalizer.normalize(input.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
        return nfd.replace(Regex("""\p{InCombiningDiacriticalMarks}+"""), "")
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
