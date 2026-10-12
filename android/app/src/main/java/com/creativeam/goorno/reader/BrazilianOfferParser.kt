package com.creativeam.goorno.reader

import com.creativeam.goorno.model.ExtractedField
import java.util.Locale
import kotlin.math.abs

/**
 * Utilitário puro em Kotlin para conversão de moeda brasileira, distâncias (km),
 * durações (min/horas), extração de endereços (Origem/Destino) e deduplicação.
 */
object BrazilianOfferParser {

    // Ex: "R$ 12,50", "R$ 1.250,00", "R$18,00", "R$ 24"
    private val CURRENCY_REGEX = Regex(
        pattern = """R\$\s*(\d{1,3}(?:\.\d{3})*(?:,\d{1,2})?|\d+(?:[.,]\d{1,2})?)""",
        option = RegexOption.IGNORE_CASE
    )

    // Ex: "3,8 km", "12 km", "1,25 km"
    private val KM_REGEX = Regex(
        pattern = """(\d{1,3}(?:[.,]\d{1,2})?)\s*km\b""",
        option = RegexOption.IGNORE_CASE
    )

    // Ex: "1 h 15 min", "1h 20m", "2h05min"
    private val HOURS_AND_MIN_REGEX = Regex(
        pattern = """(\d{1,2})\s*h(?:oras?)?\s*(?:e\s*)?(\d{1,2})\s*m(?:in(?:utos?)?)?\b""",
        option = RegexOption.IGNORE_CASE
    )

    // Ex: "14 min", "24 minutos", "8 mins"
    private val MINUTES_REGEX = Regex(
        pattern = """(\d{1,3})\s*min(?:utos?|s)?\b""",
        option = RegexOption.IGNORE_CASE
    )

    // Ex: "1 hora", "2 horas" isoladas
    private val HOURS_ONLY_REGEX = Regex(
        pattern = """(\d{1,2})\s*horas?\b""",
        option = RegexOption.IGNORE_CASE
    )

    private val STREET_PREFIX_REGEX = Regex(
        pattern = """^(Rua|R\.|Av\.|Avenida|Alameda|Al\.|Travessa|Tv\.|Praça|Pç\.|Estrada|Estr\.|Rodovia|Rod\.|Via|Largo|Terminal|Aeroporto|Shopping)\b.*""",
        option = RegexOption.IGNORE_CASE
    )

    /**
     * Converte uma string contendo valor em Reais (ex: "R$ 1.250,50" ou "R$ 18,00") para Double.
     */
    fun parseCurrencyBr(text: String?): ExtractedField<Double> {
        if (text.isNullOrBlank()) return ExtractedField.Missing
        val match = CURRENCY_REGEX.find(text)
        if (match == null) {
            return if (text.contains("R$", ignoreCase = true)) {
                ExtractedField.Unrecognized(text.trim())
            } else {
                ExtractedField.Missing
            }
        }
        val rawNumber = match.groupValues[1].trim()
        val parsed = parseBrazilianDecimal(rawNumber)
            ?: return ExtractedField.Unrecognized(match.value)

        if (parsed < 0.0 || parsed > 10000.0) {
            return ExtractedField.Unrecognized(match.value)
        }
        return ExtractedField.Present(parsed)
    }

    /**
     * Extrai todos os valores monetários válidos (R$) encontrados em uma lista de textos.
     */
    fun extractAllCurrencies(lines: List<String>): List<Double> {
        val results = mutableListOf<Double>()
        for (line in lines) {
            // Ignora linhas que claramente são tarifa dinâmica multiplicadora ou meta promocional
            val lower = line.lowercase(Locale.getDefault())
            if (lower.contains("/km") || lower.contains("/h") || lower.contains("meta")) continue
            CURRENCY_REGEX.findAll(line).forEach { m ->
                val v = parseBrazilianDecimal(m.groupValues[1])
                if (v != null && v >= 0.0 && v <= 5000.0) {
                    results.add(v)
                }
            }
        }
        return results
    }

    /**
     * Converte uma string de distância brasileira (ex: "3,8 km", "8 km") em quilômetros.
     */
    fun parseDistanceKm(text: String?): ExtractedField<Double> {
        if (text.isNullOrBlank()) return ExtractedField.Missing
        val match = KM_REGEX.find(text)
        if (match == null) {
            return if (text.contains("km", ignoreCase = true)) {
                ExtractedField.Unrecognized(text.trim())
            } else {
                ExtractedField.Missing
            }
        }
        val value = parseBrazilianDecimal(match.groupValues[1])
            ?: return ExtractedField.Unrecognized(match.value)
        if (value < 0.0 || value > 2000.0) {
            return ExtractedField.Unrecognized(match.value)
        }
        return ExtractedField.Present(value)
    }

    /**
     * Extrai todas as distâncias em km de uma linha ou conjunto de linhas.
     */
    fun extractAllDistancesKm(lines: List<String>): List<Double> {
        val list = mutableListOf<Double>()
        for (line in lines) {
            val lower = line.lowercase(Locale.getDefault())
            if (lower.contains("r$/km") || lower.contains("/km")) continue
            KM_REGEX.findAll(line).forEach { m ->
                val v = parseBrazilianDecimal(m.groupValues[1])
                if (v != null && v >= 0.0 && v <= 2000.0) {
                    list.add(v)
                }
            }
        }
        return list
    }

    /**
     * Converte uma string de tempo (ex: "24 min", "1 h 15 min") para minutos totais.
     */
    fun parseDurationMinutes(text: String?): ExtractedField<Int> {
        if (text.isNullOrBlank()) return ExtractedField.Missing

        val hmMatch = HOURS_AND_MIN_REGEX.find(text)
        if (hmMatch != null) {
            val hours = hmMatch.groupValues[1].toIntOrNull()
            val mins = hmMatch.groupValues[2].toIntOrNull()
            if (hours != null && mins != null && hours >= 0 && mins in 0..59) {
                return ExtractedField.Present(hours * 60 + mins)
            }
            return ExtractedField.Unrecognized(hmMatch.value)
        }

        val minMatch = MINUTES_REGEX.find(text)
        if (minMatch != null) {
            val mins = minMatch.groupValues[1].toIntOrNull()
            if (mins != null && mins in 0..1440) {
                return ExtractedField.Present(mins)
            }
            return ExtractedField.Unrecognized(minMatch.value)
        }

        val hourOnlyMatch = HOURS_ONLY_REGEX.find(text)
        if (hourOnlyMatch != null) {
            val hours = hourOnlyMatch.groupValues[1].toIntOrNull()
            if (hours != null && hours in 1..24) {
                return ExtractedField.Present(hours * 60)
            }
        }

        if (text.contains("min", ignoreCase = true)) {
            return ExtractedField.Unrecognized(text.trim())
        }
        return ExtractedField.Missing
    }

    /**
     * Extrai todas as durações em minutos de uma lista de textos.
     */
    fun extractAllDurationsMinutes(lines: List<String>): List<Int> {
        val results = mutableListOf<Int>()
        for (line in lines) {
            val hmMatches = HOURS_AND_MIN_REGEX.findAll(line).toList()
            if (hmMatches.isNotEmpty()) {
                for (hm in hmMatches) {
                    val h = hm.groupValues[1].toIntOrNull() ?: 0
                    val m = hm.groupValues[2].toIntOrNull() ?: 0
                    val total = h * 60 + m
                    if (total in 0..1440) results.add(total)
                }
                continue
            }
            MINUTES_REGEX.findAll(line).forEach { m ->
                val v = m.groupValues[1].toIntOrNull()
                if (v != null && v in 0..1440) {
                    results.add(v)
                }
            }
        }
        return results
    }

    /**
     * Extrai endereços de Origem (Embarque) e Destino (Desembarque) das linhas capturadas.
     */
    fun extractOriginAndDestination(lines: List<String>): Pair<String?, String?> {
        var explicitOrigin: String? = null
        var explicitDestination: String? = null
        val streetCandidates = mutableListOf<String>()

        for (i in lines.indices) {
            val clean = lines[i].trim()
            if (clean.length < 5) continue
            val lower = clean.lowercase(Locale.getDefault())

            if (lower.startsWith("origem:") || lower.startsWith("embarque:") || lower.startsWith("buscar em:")) {
                val afterColon = clean.substringAfter(":").trim()
                if (afterColon.length >= 4) {
                    explicitOrigin = afterColon
                } else if (i + 1 < lines.size) {
                    explicitOrigin = lines[i + 1].trim()
                }
                continue
            }

            if (lower.startsWith("destino:") || lower.startsWith("desembarque:") || lower.startsWith("levar até:")) {
                val afterColon = clean.substringAfter(":").trim()
                if (afterColon.length >= 4) {
                    explicitDestination = afterColon
                } else if (i + 1 < lines.size) {
                    explicitDestination = lines[i + 1].trim()
                }
                continue
            }

            if (STREET_PREFIX_REGEX.matches(clean) ||
                (clean.contains(",") && clean.any { it.isDigit() } && !clean.contains("R$", ignoreCase = true) && !KM_REGEX.containsMatchIn(clean))
            ) {
                if (!streetCandidates.contains(clean)) {
                    streetCandidates.add(clean)
                }
            }
        }

        val origin = explicitOrigin ?: streetCandidates.getOrNull(0)
        val destination = explicitDestination ?: streetCandidates.getOrNull(1)
        return Pair(origin, destination)
    }

    /**
     * Converte número decimal no padrão brasileiro ("1.250,50" -> 1250.50, "3,8" -> 3.8).
     */
    fun parseBrazilianDecimal(raw: String): Double? {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return null

        val normalized = when {
            cleaned.contains(".") && cleaned.contains(",") -> {
                // "1.250,50" -> remove pontos de milhar e troca vírgula decimal por ponto
                cleaned.replace(".", "").replace(",", ".")
            }
            cleaned.contains(",") -> {
                cleaned.replace(",", ".")
            }
            cleaned.contains(".") -> {
                // Verifica se o ponto é separador de milhar (ex: "1.250") ou decimal ("3.8")
                val parts = cleaned.split(".")
                if (parts.size == 2 && parts[1].length == 3 && parts[0].isNotEmpty()) {
                    cleaned.replace(".", "")
                } else {
                    cleaned
                }
            }
            else -> cleaned
        }
        return normalized.toDoubleOrNull()
    }

    /**
     * Gera assinatura única (fingerprint) da oferta para evitar recálculos e registros duplicados.
     */
    fun buildFingerprint(
        sourceAppName: String,
        price: Double?,
        totalKm: Double?,
        totalMin: Int?,
        origin: String?,
        destination: String?
    ): String {
        val p = price?.let { String.format(Locale.US, "%.2f", it) } ?: "na"
        val k = totalKm?.let { String.format(Locale.US, "%.1f", it) } ?: "na"
        val m = totalMin?.toString() ?: "na"
        val o = origin?.trim()?.lowercase(Locale.ROOT)?.take(18) ?: ""
        val d = destination?.trim()?.lowercase(Locale.ROOT)?.take(18) ?: ""
        return "$sourceAppName|$p|$k|$m|$o|$d"
    }

    /**
     * Verifica se duas ofertas são essencialmente a mesma oferta ou uma atualização da mesma rota.
     */
    fun isSameOrUpdatedOffer(
        previous: com.creativeam.goorno.model.StandardRideOffer?,
        current: com.creativeam.goorno.model.StandardRideOffer,
        dedupWindowMillis: Long = 15_000L
    ): DuplicateCheckResult {
        if (previous == null) return DuplicateCheckResult.NEW_OFFER
        val elapsed = abs(current.timestampMillis - previous.timestampMillis)
        if (elapsed > dedupWindowMillis) return DuplicateCheckResult.NEW_OFFER

        if (previous.dedupFingerprint == current.dedupFingerprint && current.dedupFingerprint.isNotBlank()) {
            return DuplicateCheckResult.EXACT_DUPLICATE
        }

        // Mesma rota / distância mas preço atualizado (ex: dinâmico atualizou)
        val sameApp = previous.sourceApp == current.sourceApp
        val prevKm = previous.totalDistanceKmExplicit ?: ((previous.pickupDistanceKm ?: 0.0) + (previous.tripDistanceKm ?: 0.0))
        val currKm = current.totalDistanceKmExplicit ?: ((current.pickupDistanceKm ?: 0.0) + (current.tripDistanceKm ?: 0.0))
        val sameDistance = prevKm > 0 && currKm > 0 && abs(prevKm - currKm) < 0.25
        val priceChanged = previous.offeredPrice != null && current.offeredPrice != null &&
            abs(previous.offeredPrice!! - current.offeredPrice!!) >= 0.10

        if (sameApp && sameDistance && priceChanged) {
            return DuplicateCheckResult.UPDATED_OFFER
        }

        return DuplicateCheckResult.NEW_OFFER
    }

    enum class DuplicateCheckResult {
        NEW_OFFER,
        UPDATED_OFFER,
        EXACT_DUPLICATE
    }
}
