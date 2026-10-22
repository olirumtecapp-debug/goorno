package com.creativeam.goorno.reader.uber

import com.creativeam.goorno.model.CaptureMethod
import com.creativeam.goorno.model.ExtractedField
import com.creativeam.goorno.model.ScreenClassification
import com.creativeam.goorno.model.SourceApp
import com.creativeam.goorno.model.StandardRideOffer
import com.creativeam.goorno.reader.BrazilianOfferParser
import java.util.Locale
import java.util.UUID

/**
 * Leitor independente de ofertas da Uber Driver (com suporte a oferta única ou Radar de Viagens com múltiplas ofertas).
 */
object UberOfferReader {

    private val UBER_CATEGORIES = listOf(
        "UberX", "Uber Comfort", "Comfort", "Uber Black", "Black",
        "Uber Flash", "Flash", "Uber Promo", "Uber Bag", "Prioridade"
    )

    private val ONGOING_TRIP_KEYWORDS = listOf(
        "iniciar viagem", "iniciar uber", "deslize para finalizar",
        "encerrar viagem", "aguardando o passageiro", "chegou ao local de embarque"
    )

    fun parseMultipleOffersFromScreen(
        rawNodes: List<String>,
        captureMethod: CaptureMethod = CaptureMethod.ACCESSIBILITY,
        timestampMillis: Long = System.currentTimeMillis(),
        isSimulated: Boolean = false
    ): List<StandardRideOffer> {
        val clusters = BrazilianOfferParser.splitIntoMultipleOfferClusters(rawNodes)
        if (clusters.isEmpty()) return emptyList()

        return clusters.map { cluster ->
            parseScreenNodes(cluster, captureMethod, timestampMillis, isSimulated)
        }.filter {
            it.screenClassification == ScreenClassification.NEW_OFFER ||
                it.screenClassification == ScreenClassification.UPDATED_OFFER
        }
    }

    fun parseScreenNodes(
        rawNodes: List<String>,
        captureMethod: CaptureMethod = CaptureMethod.ACCESSIBILITY,
        timestampMillis: Long = System.currentTimeMillis(),
        isSimulated: Boolean = false
    ): StandardRideOffer {
        val cleanedLines = rawNodes.map { it.trim() }.filter { it.isNotEmpty() }
        val joinedLower = cleanedLines.joinToString(" | ").lowercase(Locale.getDefault())

        // 0. Rejeição total de telas do próprio GoOrNo (Histórico, Ganhos, Janela, Ajustes)
        if (joinedLower.contains("goorno") ||
            joinedLower.contains("histórico") ||
            joinedLower.contains("lucro estim") ||
            joinedLower.contains("km bruto") ||
            joinedLower.contains("hora bruta") ||
            joinedLower.contains("auditoria de chamadas") ||
            joinedLower.contains("janela histórico ajustes")
        ) {
            return StandardRideOffer(
                offerId = UUID.randomUUID().toString(),
                sourceApp = if (isSimulated) SourceApp.SIMULATION else SourceApp.UBER_DRIVER,
                captureMethod = captureMethod,
                screenClassification = ScreenClassification.NON_OFFER,
                timestampMillis = timestampMillis,
                offeredPriceField = ExtractedField.Missing,
                confidenceScore = 0.0,
                rawCapturedTexts = cleanedLines,
                isSimulated = isSimulated
            )
        }

        if (ONGOING_TRIP_KEYWORDS.any { joinedLower.contains(it) } && !joinedLower.contains("radar")) {
            return StandardRideOffer(
                offerId = UUID.randomUUID().toString(),
                sourceApp = if (isSimulated) SourceApp.SIMULATION else SourceApp.UBER_DRIVER,
                captureMethod = captureMethod,
                screenClassification = ScreenClassification.ONGOING_TRIP,
                timestampMillis = timestampMillis,
                offeredPriceField = ExtractedField.Missing,
                confidenceScore = 0.0,
                rawCapturedTexts = cleanedLines,
                isSimulated = isSimulated
            )
        }

        // Filtro rigoroso de Anúncios e Serviços (ex: postos de combustível, Uber Pro Card, empréstimos, etc.)
        if (BrazilianOfferParser.isNonRideAdOrService(cleanedLines)) {
            return StandardRideOffer(
                offerId = UUID.randomUUID().toString(),
                sourceApp = if (isSimulated) SourceApp.SIMULATION else SourceApp.UBER_DRIVER,
                captureMethod = captureMethod,
                screenClassification = ScreenClassification.NON_OFFER,
                timestampMillis = timestampMillis,
                offeredPriceField = ExtractedField.Missing,
                confidenceScore = 0.0,
                rawCapturedTexts = cleanedLines,
                isSimulated = isSimulated
            )
        }

        // Descarta nós de mapa ou cabeçalhos soltos acima do card da Uber
        val cardStartIdx = cleanedLines.indexOfFirst { line ->
            val l = line.lowercase(Locale.getDefault())
            l.contains("r$") || l.contains("exclusivo") || l.contains("radar") ||
                UBER_CATEGORIES.any { cat -> l.contains(cat.lowercase(Locale.getDefault())) }
        }
        val cardLines = if (cardStartIdx > 0) cleanedLines.subList(cardStartIdx, cleanedLines.size) else cleanedLines

        val prices = BrazilianOfferParser.extractAllCurrencies(cardLines)
        val hasInvalidCurrencyToken = cleanedLines.any {
            it.contains("R$", ignoreCase = true) && BrazilianOfferParser.parseCurrencyBr(it).isUnrecognized
        }
        val priceField: ExtractedField<Double> = when {
            prices.isNotEmpty() -> ExtractedField.Present(prices.maxOrNull() ?: prices.first())
            hasInvalidCurrencyToken -> ExtractedField.Unrecognized("R$ inválido")
            else -> ExtractedField.Missing
        }

        val distances = BrazilianOfferParser.extractAllDistancesKm(cardLines)
        var pickupKmField: ExtractedField<Double> = ExtractedField.Missing
        var tripKmField: ExtractedField<Double> = ExtractedField.Missing
        var totalKmField: ExtractedField<Double> = ExtractedField.Missing

        if (distances.size >= 2) {
            pickupKmField = ExtractedField.Present(distances[0])
            tripKmField = ExtractedField.Present(distances[1])
        } else if (distances.size == 1) {
            totalKmField = ExtractedField.Present(distances[0])
        }

        val durations = BrazilianOfferParser.extractAllDurationsMinutes(cardLines)
        var pickupMinField: ExtractedField<Int> = ExtractedField.Missing
        var tripMinField: ExtractedField<Int> = ExtractedField.Missing
        var totalMinField: ExtractedField<Int> = ExtractedField.Missing

        if (durations.size >= 2) {
            pickupMinField = ExtractedField.Present(durations[0])
            tripMinField = ExtractedField.Present(durations[1])
        } else if (durations.size == 1) {
            totalMinField = ExtractedField.Present(durations[0])
        }

        val legs = BrazilianOfferParser.extractRideLegs(cardLines)
        if (legs.size >= 2) {
            if (!pickupKmField.isPresent) pickupKmField = ExtractedField.Present(legs[0].distanceKm)
            if (!tripKmField.isPresent) tripKmField = ExtractedField.Present(legs[1].distanceKm)
            if (!pickupMinField.isPresent) pickupMinField = ExtractedField.Present(legs[0].durationMin)
            if (!tripMinField.isPresent) tripMinField = ExtractedField.Present(legs[1].durationMin)
        }

        val detectedCategory = UBER_CATEGORIES.firstOrNull { cat ->
            cleanedLines.any { it.contains(cat, ignoreCase = true) }
        } ?: "Uber"

        val legsExplicit = BrazilianOfferParser.extractRideLegs(cardLines)
        val (origin, destination) = if (legsExplicit.size >= 2 && !legsExplicit[0].address.isNullOrBlank() && !legsExplicit[1].address.isNullOrBlank()) {
            Pair(legsExplicit[0].address, legsExplicit[1].address)
        } else {
            BrazilianOfferParser.extractOriginAndDestination(cardLines)
        }
        val passengerRating = BrazilianOfferParser.extractPassengerRating(cleanedLines)

        val conflictNotes = mutableListOf<String>()
        var totalKmVal = if (pickupKmField.isPresent && tripKmField.isPresent) {
            (pickupKmField.valueOrNull() ?: 0.0) + (tripKmField.valueOrNull() ?: 0.0)
        } else {
            totalKmField.valueOrNull() ?: if (pickupKmField.isPresent || tripKmField.isPresent) {
                (pickupKmField.valueOrNull() ?: 0.0) + (tripKmField.valueOrNull() ?: 0.0)
            } else null
        }
        if (totalKmVal != null) {
            totalKmField = ExtractedField.Present(totalKmVal)
        }

        var totalMinVal = if (pickupMinField.isPresent && tripMinField.isPresent) {
            (pickupMinField.valueOrNull() ?: 0) + (tripMinField.valueOrNull() ?: 0)
        } else {
            totalMinField.valueOrNull() ?: if (pickupMinField.isPresent || tripMinField.isPresent) {
                (pickupMinField.valueOrNull() ?: 0) + (tripMinField.valueOrNull() ?: 0)
            } else null
        }
        if (totalMinVal != null) {
            totalMinField = ExtractedField.Present(totalMinVal)
        }

        // Proteção contra leitura parcial de tempo (ex: capturar apenas os minutos de embarque como se fosse a viagem toda):
        // Se a distância for >= 3.0 km e a duração der velocidade urbana > 48 km/h com apenas 1 token de duração:
        if (totalKmVal != null && totalMinVal != null && totalKmVal >= 3.0 && totalMinVal > 0) {
            val impliedSpeedKmH = totalKmVal / (totalMinVal / 60.0)
            if (impliedSpeedKmH > 48.0) {
                if (durations.size == 1 && (pickupMinField.isMissing || tripMinField.isMissing)) {
                    val realisticTotalMin = (totalMinVal + (totalKmVal * 2.2).toInt()).coerceIn(12, 180)
                    totalMinVal = realisticTotalMin
                    totalMinField = ExtractedField.Present(realisticTotalMin)
                } else if (impliedSpeedKmH > 85.0) {
                    conflictNotes.add("Velocidade média (${impliedSpeedKmH.toInt()} km/h) inconsistente com corrida urbana")
                }
            }
        }

        if (totalKmVal != null && totalKmVal <= 0.0) {
            conflictNotes.add("Distância igual ou menor que zero")
        }
        if (totalMinVal != null && totalMinVal <= 0) {
            conflictNotes.add("Duração igual ou menor que zero")
        }
        if (totalKmVal != null && totalMinVal != null && totalKmVal > 80.0 && totalMinVal < 10) {
            conflictNotes.add("Distância (${totalKmVal} km) inconsistente com tempo (${totalMinVal} min)")
        }

        // Toda oferta real da Uber (seja chamada direta ou Radar de Viagens) possui obrigatoriamente:
        // 1. Preço válido (R$)
        // 2. Distância total maior que zero (> 0 km)
        // 3. Gatilho explícito de corrida (Aceitar, Exclusivo, Radar, Viagem, Corrida, Categoria Uber, etc.)
        // 4. Excluir leituras da própria interface do GoOrNo
        val hasUberRideTrigger = cleanedLines.any { line ->
            UBER_CATEGORIES.any { cat -> line.contains(cat, ignoreCase = true) } ||
                line.contains("Aceitar", ignoreCase = true) ||
                line.contains("Exclusivo", ignoreCase = true) ||
                line.contains("Radar", ignoreCase = true) ||
                line.contains("Viagem", ignoreCase = true) ||
                line.contains("Corrida", ignoreCase = true) ||
                line.contains("Embarque", ignoreCase = true) ||
                line.contains("Desembarque", ignoreCase = true)
        }

        val isSelfReadingGoOrNo = cleanedLines.any { line ->
            line.contains("GoOrNo", ignoreCase = true) ||
                line.contains("R$/km", ignoreCase = true) ||
                line.contains("R$/hora", ignoreCase = true) ||
                line.contains("Suas Faixas", ignoreCase = true)
        } && !cleanedLines.any { it.contains("Aceitar", ignoreCase = true) || it.contains("Exclusivo", ignoreCase = true) }

        val isSanePrice = (priceField.valueOrNull() ?: 0.0) >= 4.50

        val isOfferScreen = isSanePrice && priceField.isPresent && totalKmVal != null && totalKmVal > 0.0 &&
            hasUberRideTrigger && !isSelfReadingGoOrNo &&
            !BrazilianOfferParser.isNonRideAdOrService(cleanedLines)

        var confidence = 0.0
        if (priceField.isPresent) confidence += 0.45
        if (totalKmVal != null && totalKmVal > 0) confidence += 0.30
        if (totalMinVal != null && totalMinVal > 0) confidence += 0.20
        if (!origin.isNullOrBlank() || !destination.isNullOrBlank()) confidence += 0.05
        if (captureMethod == CaptureMethod.OCR) confidence *= 0.90
        if (conflictNotes.isNotEmpty()) confidence *= 0.50

        val classification = if (isOfferScreen) {
            ScreenClassification.NEW_OFFER
        } else {
            ScreenClassification.NON_OFFER
        }

        val fingerprint = BrazilianOfferParser.buildFingerprint(
            sourceAppName = "UBER",
            price = priceField.valueOrNull(),
            totalKm = totalKmVal,
            totalMin = totalMinVal,
            origin = origin,
            destination = destination
        )

        return StandardRideOffer(
            offerId = UUID.randomUUID().toString(),
            sourceApp = if (isSimulated) SourceApp.SIMULATION else SourceApp.UBER_DRIVER,
            captureMethod = captureMethod,
            screenClassification = classification,
            timestampMillis = timestampMillis,
            offeredPriceField = priceField,
            pickupDistanceKmField = pickupKmField,
            tripDistanceKmField = tripKmField,
            totalDistanceKmField = totalKmField,
            pickupDurationMinField = pickupMinField,
            tripDurationMinField = tripMinField,
            totalDurationMinField = totalMinField,
            rideCategory = detectedCategory,
            originAddress = origin,
            destinationAddress = destination,
            passengerRating = passengerRating,
            confidenceScore = confidence.coerceIn(0.0, 1.0),
            hasFieldConflict = conflictNotes.isNotEmpty(),
            conflictNotes = conflictNotes,
            rawCapturedTexts = cleanedLines,
            dedupFingerprint = fingerprint,
            isSimulated = isSimulated
        )
    }
}
