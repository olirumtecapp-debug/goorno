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

        val prices = BrazilianOfferParser.extractAllCurrencies(cleanedLines)
        val hasInvalidCurrencyToken = cleanedLines.any {
            it.contains("R$", ignoreCase = true) && BrazilianOfferParser.parseCurrencyBr(it).isUnrecognized
        }
        val priceField: ExtractedField<Double> = when {
            prices.isNotEmpty() -> ExtractedField.Present(prices.first())
            hasInvalidCurrencyToken -> ExtractedField.Unrecognized("R$ inválido")
            else -> ExtractedField.Missing
        }

        val distances = BrazilianOfferParser.extractAllDistancesKm(cleanedLines)
        var pickupKmField: ExtractedField<Double> = ExtractedField.Missing
        var tripKmField: ExtractedField<Double> = ExtractedField.Missing
        var totalKmField: ExtractedField<Double> = ExtractedField.Missing

        if (distances.size >= 2) {
            pickupKmField = ExtractedField.Present(distances[0])
            tripKmField = ExtractedField.Present(distances[1])
        } else if (distances.size == 1) {
            totalKmField = ExtractedField.Present(distances[0])
        }

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

        val detectedCategory = UBER_CATEGORIES.firstOrNull { cat ->
            cleanedLines.any { it.contains(cat, ignoreCase = true) }
        } ?: "Uber"

        val (origin, destination) = BrazilianOfferParser.extractOriginAndDestination(cleanedLines)

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

        val isOfferScreen = priceField.isPresent && (totalKmVal != null || totalMinVal != null)

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
            confidenceScore = confidence.coerceIn(0.0, 1.0),
            hasFieldConflict = conflictNotes.isNotEmpty(),
            conflictNotes = conflictNotes,
            rawCapturedTexts = cleanedLines,
            dedupFingerprint = fingerprint,
            isSimulated = isSimulated
        )
    }
}
