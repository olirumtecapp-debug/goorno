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
 * Leitor independente de ofertas da Uber Driver (com.ubercab.driver).
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

    private val OFFER_TRIGGER_KEYWORDS = listOf(
        "aceitar", "confirmar", "exclusivo", "radar", "viagem", "min", "km", "r$"
    )

    fun parseScreenNodes(
        rawNodes: List<String>,
        captureMethod: CaptureMethod = CaptureMethod.ACCESSIBILITY,
        timestampMillis: Long = System.currentTimeMillis(),
        isSimulated: Boolean = false
    ): StandardRideOffer {
        val cleanedLines = rawNodes.map { it.trim() }.filter { it.isNotEmpty() }
        val joinedLower = cleanedLines.joinToString(" | ").lowercase(Locale.getDefault())

        // 1. Verifica se é uma viagem em andamento (não é oferta nova)
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

        // 2. Extração de Preço (R$)
        val prices = BrazilianOfferParser.extractAllCurrencies(cleanedLines)
        val hasInvalidCurrencyToken = cleanedLines.any {
            it.contains("R$", ignoreCase = true) && BrazilianOfferParser.parseCurrencyBr(it).isUnrecognized
        }
        val priceField: ExtractedField<Double> = when {
            prices.isNotEmpty() -> ExtractedField.Present(prices.maxOrNull() ?: prices.first())
            hasInvalidCurrencyToken -> ExtractedField.Unrecognized("R$ inválido")
            else -> ExtractedField.Missing
        }

        // 3. Extração de Distâncias (Embarque vs Viagem vs Total)
        val distances = BrazilianOfferParser.extractAllDistancesKm(cleanedLines)
        var pickupKmField: ExtractedField<Double> = ExtractedField.Missing
        var tripKmField: ExtractedField<Double> = ExtractedField.Missing
        var totalKmField: ExtractedField<Double> = ExtractedField.Missing

        if (distances.size >= 2) {
            // Na Uber Driver, o primeiro par (min / km) costuma ser até o embarque e o segundo a viagem
            pickupKmField = ExtractedField.Present(distances[0])
            tripKmField = ExtractedField.Present(distances[1])
        } else if (distances.size == 1) {
            totalKmField = ExtractedField.Present(distances[0])
        }

        // 4. Extração de Durações (Embarque vs Viagem vs Total)
        val durations = BrazilianOfferParser.extractAllDurationsMinutes(cleanedLines)
        var pickupMinField: ExtractedField<Int> = ExtractedField.Missing
        var tripMinField: ExtractedField<Int> = ExtractedField.Missing
        var totalMinField: ExtractedField<Int> = ExtractedField.Missing

        if (durations.size >= 2) {
            pickupMinField = ExtractedField.Present(durations[0])
            tripMinField = ExtractedField.Present(durations[1])
        } else if (durations.size == 1) {
            totalMinField = ExtractedField.Present(durations[0])
        }

        // 5. Categoria da corrida
        val detectedCategory = UBER_CATEGORIES.firstOrNull { cat ->
            cleanedLines.any { it.contains(cat, ignoreCase = true) }
        } ?: "Uber"

        // 6. Endereços de Origem e Destino (para abrir no Street View)
        val (origin, destination) = BrazilianOfferParser.extractOriginAndDestination(cleanedLines)

        // 7. Validação de consistência e confiança
        val conflictNotes = mutableListOf<String>()
        val totalKmVal = totalKmField.valueOrNull()
            ?: if (pickupKmField.isPresent || tripKmField.isPresent) {
                (pickupKmField.valueOrNull() ?: 0.0) + (tripKmField.valueOrNull() ?: 0.0)
            } else null

        val totalMinVal = totalMinField.valueOrNull()
            ?: if (pickupMinField.isPresent || tripMinField.isPresent) {
                (pickupMinField.valueOrNull() ?: 0) + (tripMinField.valueOrNull() ?: 0)
            } else null

        if (totalKmVal != null && totalKmVal <= 0.0) {
            conflictNotes.add("Distância igual ou menor que zero")
        }
        if (totalMinVal != null && totalMinVal <= 0) {
            conflictNotes.add("Duração igual ou menor que zero")
        }
        if (totalKmVal != null && totalMinVal != null && totalKmVal > 80.0 && totalMinVal < 10) {
            conflictNotes.add("Distância (${totalKmVal} km) inconsistente com tempo (${totalMinVal} min)")
        }

        val hasOfferKeywords = OFFER_TRIGGER_KEYWORDS.count { joinedLower.contains(it) } >= 2
        val isOfferScreen = priceField.isPresent && (totalKmVal != null || totalMinVal != null) && hasOfferKeywords

        var confidence = 0.0
        if (priceField.isPresent) confidence += 0.45
        if (totalKmVal != null && totalKmVal > 0) confidence += 0.30
        if (totalMinVal != null && totalMinVal > 0) confidence += 0.20
        if (!origin.isNullOrBlank() || !destination.isNullOrBlank()) confidence += 0.05
        if (captureMethod == CaptureMethod.OCR) confidence *= 0.90
        if (conflictNotes.isNotEmpty()) confidence *= 0.50

        val classification = if (isOfferScreen || (priceField.isPresent && isSimulated)) {
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
            confidenceScore = confidence.coerceIn(0.0, 1.0),
            hasFieldConflict = conflictNotes.isNotEmpty(),
            conflictNotes = conflictNotes,
            rawCapturedTexts = cleanedLines,
            dedupFingerprint = fingerprint,
            isSimulated = isSimulated
        )
    }
}
