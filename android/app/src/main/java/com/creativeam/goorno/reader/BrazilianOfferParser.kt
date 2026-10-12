package com.creativeam.goorno.reader

import com.creativeam.goorno.model.CaptureMethod
import com.creativeam.goorno.model.ExtractedField
import com.creativeam.goorno.model.ScreenClassification
import com.creativeam.goorno.model.SourceApp
import com.creativeam.goorno.model.StandardRideOffer
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/**
 * Utilitário puro em Kotlin para conversão de moeda brasileira, distâncias (km),
 * durações (min/horas), extração de endereços (Origem/Destino), suporte ao formato
 * real da 99 ("R$8,10", "R$2,18/km", "R$1,16 por deslocamento incl.", "5 min (1,2 km)")
 * e separação de múltiplas ofertas na mesma tela (Lista de Ofertas 99 / Radar Uber).
 */
object BrazilianOfferParser {

    // Remove trechos que NÃO são o preço principal da oferta (ex: "R$2,18/km", "R$ 1,16 por deslocamento", "R$ 45/h")
    private val NON_OFFER_PRICE_NOISE_REGEX = Regex(
        pattern = """R\$\s*\d{1,3}(?:[.,]\d{1,2})?\s*(?:/\s*km|/\s*h|por\s+km|por\s+deslocamento|de\s+deslocamento|incl\.|inclu[íi]do)""",
        option = RegexOption.IGNORE_CASE
    )

    // Ex: "R$ 12,50", "R$8,10", "R$ 1.250,00", "R$18,00", "R$ 24"
    private val CURRENCY_REGEX = Regex(
        pattern = """R\$\s*(\d{1,3}(?:\.\d{3})*(?:,\d{1,2})?|\d+(?:[.,]\d{1,2})?)""",
        option = RegexOption.IGNORE_CASE
    )

    // Padrão clássico 99/Uber: "5 min (1,2 km)" ou "1,2 km (5 min)"
    private val MIN_AND_KM_PAIR_REGEX = Regex(
        pattern = """(\d{1,3})\s*min(?:utos?|s)?\s*\(?\s*(\d{1,3}(?:[.,]\d{1,2})?)\s*km\s*\)?""",
        option = RegexOption.IGNORE_CASE
    )

    // Ex: "3,8 km", "12 km", "1,25 km" (mas não "/km" ou "R$2,18/km")
    private val KM_REGEX = Regex(
        pattern = """(?<!/)\b(\d{1,3}(?:[.,]\d{1,2})?)\s*km\b""",
        option = RegexOption.IGNORE_CASE
    )

    // Ex: "1 h 15 min", "1h 20m", "2h05min"
    private val HOURS_AND_MIN_REGEX = Regex(
        pattern = """(\d{1,2})\s*h(?:oras?)?\s*(?:e\s*)?(\d{1,2})\s*m(?:in(?:utos?)?)?\b""",
        option = RegexOption.IGNORE_CASE
    )

    // Ex: "14 min", "24 minutos", "5 min"
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
        pattern = """^(Rua|R\.|Av\.|Avenida|Alameda|Al\.|Travessa|Tv\.|Praça|Pç\.|Estrada|Estr\.|Rodovia|Rod\.|Via|Largo|Terminal|Aeroporto|Shopping|Condomínio)\b.*""",
        option = RegexOption.IGNORE_CASE
    )

    /**
     * Limpa ruídos como "R$2,18/km" e "R$1,16 por deslocamento incl." antes de buscar o valor da oferta.
     */
    fun stripNonOfferPriceNoise(text: String): String {
        return NON_OFFER_PRICE_NOISE_REGEX.replace(text, " ")
    }

    /**
     * Converte uma string contendo valor em Reais (ex: "R$ 1.250,50", "R$8,10" ou "R$ 18,00") para Double.
     */
    fun parseCurrencyBr(text: String?): ExtractedField<Double> {
        if (text.isNullOrBlank()) return ExtractedField.Missing
        val sanitized = stripNonOfferPriceNoise(text)
        val match = CURRENCY_REGEX.find(sanitized)
        if (match == null) {
            return if (sanitized.contains("R$", ignoreCase = true)) {
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
     * Extrai todos os valores monetários principais de oferta (ignorando R$/km e bônus de deslocamento incluso).
     */
    fun extractAllCurrencies(lines: List<String>): List<Double> {
        val results = mutableListOf<Double>()
        for (line in lines) {
            val lower = line.lowercase(Locale.getDefault())
            if (lower.contains("por deslocamento") || lower.contains("deslocamento incl")) continue
            val sanitized = stripNonOfferPriceNoise(line)
            CURRENCY_REGEX.findAll(sanitized).forEach { m ->
                val v = parseBrazilianDecimal(m.groupValues[1])
                if (v != null && v >= 3.0 && v <= 5000.0) {
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
        val sanitized = stripNonOfferPriceNoise(text)
        val match = KM_REGEX.find(sanitized)
        if (match == null) {
            return if (sanitized.contains("km", ignoreCase = true)) {
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
     * Extrai todas as distâncias em km de uma lista de textos (ignorando "R$2,18/km").
     */
    fun extractAllDistancesKm(lines: List<String>): List<Double> {
        val list = mutableListOf<Double>()
        for (line in lines) {
            val sanitized = stripNonOfferPriceNoise(line)
            // Evita capturar "R$2,18/km" caso tenha barra antes de km
            val withoutPerKm = sanitized.replace(Regex("""\d+(?:[.,]\d+)?\s*/\s*km""", RegexOption.IGNORE_CASE), " ")
            KM_REGEX.findAll(withoutPerKm).forEach { m ->
                val v = parseBrazilianDecimal(m.groupValues[1])
                if (v != null && v > 0.0 && v <= 2000.0) {
                    list.add(v)
                }
            }
        }
        return list
    }

    /**
     * Converte uma string de tempo (ex: "24 min", "5 min (1,2 km)", "1 h 15 min") para minutos totais.
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
                    if (total in 1..1440) results.add(total)
                }
                continue
            }
            MINUTES_REGEX.findAll(line).forEach { m ->
                val v = m.groupValues[1].toIntOrNull()
                if (v != null && v in 1..1440) {
                    results.add(v)
                }
            }
        }
        return results
    }

    /**
     * Extrai endereços de Origem (Embarque) e Destino (Desembarque) das linhas capturadas.
     * Suporta tanto prefixos ("Origem:", "Destino:") quanto o layout real da 99 e Uber
     * ("Rua Antônio Zanetti, 84, Jardim Las Vegas" / "Rua Solimões, 534, Vila Cecilia Maria").
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

            // Ignora linhas de tarifa ou avaliação de passageiro ("4,82 · 211 corridas", "R$1,16 por deslocamento")
            if (lower.contains("corridas") || lower.contains("deslocamento") ||
                lower.contains("pgto.") || lower.contains("perfil ") ||
                MIN_AND_KM_PAIR_REGEX.containsMatchIn(clean)
            ) {
                continue
            }

            if (STREET_PREFIX_REGEX.matches(clean) ||
                (clean.contains(",") && clean.any { it.isDigit() } &&
                    !clean.contains("R$", ignoreCase = true) &&
                    !KM_REGEX.containsMatchIn(clean) &&
                    !MINUTES_REGEX.containsMatchIn(clean))
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
     * Separa os nós de texto de uma tela em MÚLTIPLOS blocos quando há mais de uma oferta visível
     * ao mesmo tempo (ex: Lista de Ofertas da 99 ou Radar de Viagens da Uber).
     */
    fun splitIntoMultipleOfferClusters(rawNodes: List<String>): List<List<String>> {
        val cleaned = rawNodes.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return emptyList()

        // Identifica os índices das linhas que contêm um preço principal de oferta (ex: "R$8,10", "R$ 18,00")
        val offerHeaderIndices = mutableListOf<Int>()
        for (i in cleaned.indices) {
            val line = cleaned[i]
            val lower = line.lowercase(Locale.getDefault())
            if (lower.contains("por deslocamento") || lower.contains("deslocamento incl") || lower.contains("meta")) {
                continue
            }
            val pricesInLine = extractAllCurrencies(listOf(line))
            if (pricesInLine.isNotEmpty()) {
                offerHeaderIndices.add(i)
            }
        }

        if (offerHeaderIndices.size <= 1) {
            return listOf(cleaned)
        }

        val clusters = mutableListOf<List<String>>()
        for (idxPos in offerHeaderIndices.indices) {
            val startIdx = (offerHeaderIndices[idxPos] - 1).coerceAtLeast(0)
            val endIdx = if (idxPos + 1 < offerHeaderIndices.size) {
                offerHeaderIndices[idxPos + 1]
            } else {
                cleaned.size
            }
            if (startIdx < endIdx) {
                val slice = cleaned.subList(startIdx, endIdx)
                // Só considera um cluster válido de oferta se tiver pelo menos 1 distância (km) ou tempo (min)
                if (extractAllDistancesKm(slice).isNotEmpty() || extractAllDurationsMinutes(slice).isNotEmpty()) {
                    clusters.add(slice)
                }
            }
        }

        return if (clusters.isNotEmpty()) clusters else listOf(cleaned)
    }

    /**
     * Detecta automaticamente se os textos da tela pertencem a uma chamada da 99 ou da Uber,
     * mesmo quando o pacote do Android varia por região/versão.
     */
    fun inferSourceAppFromScreenContent(packageName: String?, lines: List<String>): SourceApp {
        val byPkg = SourceApp.fromPackageName(packageName)
        if (byPkg != SourceApp.UNKNOWN) return byPkg

        val joined = lines.joinToString(" | ").lowercase(Locale.getDefault())
        if (joined.contains("por deslocamento incl") ||
            joined.contains("pgto. no app") ||
            joined.contains("perfil essencial") ||
            joined.contains("99pop") ||
            joined.contains("99plus") ||
            joined.contains("negocia") ||
            joined.contains("poupa99")
        ) {
            return SourceApp.NINETY_NINE_DRIVER
        }

        if (joined.contains("uberx") ||
            joined.contains("uber comfort") ||
            joined.contains("uber black") ||
            joined.contains("uber flash") ||
            joined.contains("radar de viagens")
        ) {
            return SourceApp.UBER_DRIVER
        }

        // Se tem R$ + min + km na tela de outro app de transporte, classifica como 99/Uber
        val hasPrice = extractAllCurrencies(lines).isNotEmpty()
        val hasKm = extractAllDistancesKm(lines).isNotEmpty()
        val hasMin = extractAllDurationsMinutes(lines).isNotEmpty()
        if (hasPrice && hasKm && hasMin) {
            return SourceApp.NINETY_NINE_DRIVER
        }

        return SourceApp.UNKNOWN
    }

    /**
     * Converte número decimal no padrão brasileiro ("1.250,50" -> 1250.50, "3,8" -> 3.8).
     */
    fun parseBrazilianDecimal(raw: String): Double? {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return null

        val normalized = when {
            cleaned.contains(".") && cleaned.contains(",") -> {
                cleaned.replace(".", "").replace(",", ".")
            }
            cleaned.contains(",") -> {
                cleaned.replace(",", ".")
            }
            cleaned.contains(".") -> {
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
        previous: StandardRideOffer?,
        current: StandardRideOffer,
        dedupWindowMillis: Long = 12_000L
    ): DuplicateCheckResult {
        if (previous == null) return DuplicateCheckResult.NEW_OFFER
        val elapsed = abs(current.timestampMillis - previous.timestampMillis)
        if (elapsed > dedupWindowMillis) return DuplicateCheckResult.NEW_OFFER

        if (previous.dedupFingerprint == current.dedupFingerprint && current.dedupFingerprint.isNotBlank()) {
            return DuplicateCheckResult.EXACT_DUPLICATE
        }

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
