package com.creativeam.goorno.reader.ninetynine

import com.creativeam.goorno.model.CaptureMethod
import com.creativeam.goorno.model.ExtractedField
import com.creativeam.goorno.model.ScreenClassification
import com.creativeam.goorno.model.SourceApp
import com.creativeam.goorno.model.StandardRideOffer
import com.creativeam.goorno.reader.BrazilianOfferParser
import java.util.Locale
import java.util.UUID

/**
 * Leitor independente de ofertas da 99 Motorista (com suporte a oferta única ou lista com múltiplas ofertas).
 */
object NinetyNineOfferReader {

    private val NINETY_NINE_CATEGORIES = listOf(
        "99Pop", "Pop", "99Plus", "Plus", "99Negocia", "Negocia",
        "99Entrega", "Entrega", "99Moto", "99Táxi", "Poupa99", "Turbo", "Perfil Essencial"
    )

    private val ONGOING_TRIP_KEYWORDS = listOf(
        "cheguei", "iniciar corrida", "finalizar corrida", "deslize para iniciar",
        "a caminho do passageiro", "em viagem"
    )

    /**
     * Extrai TODAS as ofertas visíveis na tela da 99 (1 ou várias viagens simultâneas na lista de ofertas).
     */
    fun parseMultipleOffersFromScreen(
        rawNodes: List<String>,
        captureMethod: CaptureMethod = CaptureMethod.ACCESSIBILITY,
        timestampMillis: Long = System.currentTimeMillis(),
        isSimulated: Boolean = false
    ): List<StandardRideOffer> {
        val clusters = BrazilianOfferParser.splitIntoMultipleOfferClusters(rawNodes)
        if (clusters.isEmpty()) return emptyList()

        val isMultiCluster = clusters.size > 1
        val validOffers = clusters.map { cluster ->
            parseScreenNodes(cluster, captureMethod, timestampMillis, isSimulated, isMultiOfferCard = isMultiCluster)
        }.filter {
            it.screenClassification == ScreenClassification.NEW_OFFER ||
                it.screenClassification == ScreenClassification.UPDATED_OFFER
        }

        return validOffers
    }

    fun parseScreenNodes(
        rawNodes: List<String>,
        captureMethod: CaptureMethod = CaptureMethod.ACCESSIBILITY,
        timestampMillis: Long = System.currentTimeMillis(),
        isSimulated: Boolean = false,
        isMultiOfferCard: Boolean = false
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
                sourceApp = if (isSimulated) SourceApp.SIMULATION else SourceApp.NINETY_NINE_DRIVER,
                captureMethod = captureMethod,
                screenClassification = ScreenClassification.NON_OFFER,
                timestampMillis = timestampMillis,
                offeredPriceField = ExtractedField.Missing,
                confidenceScore = 0.0,
                rawCapturedTexts = cleanedLines,
                isSimulated = isSimulated
            )
        }

        // 1. Verifica se é viagem em andamento
        if (ONGOING_TRIP_KEYWORDS.any { joinedLower.contains(it) }) {
            return StandardRideOffer(
                offerId = UUID.randomUUID().toString(),
                sourceApp = if (isSimulated) SourceApp.SIMULATION else SourceApp.NINETY_NINE_DRIVER,
                captureMethod = captureMethod,
                screenClassification = ScreenClassification.ONGOING_TRIP,
                timestampMillis = timestampMillis,
                offeredPriceField = ExtractedField.Missing,
                confidenceScore = 0.0,
                rawCapturedTexts = cleanedLines,
                isSimulated = isSimulated
            )
        }

        // 1b. Filtro rigoroso de Anúncios e Serviços (ex: "99 Abastece", postos de combustível, empréstimos, etc.)
        if (BrazilianOfferParser.isNonRideAdOrService(cleanedLines)) {
            return StandardRideOffer(
                offerId = UUID.randomUUID().toString(),
                sourceApp = if (isSimulated) SourceApp.SIMULATION else SourceApp.NINETY_NINE_DRIVER,
                captureMethod = captureMethod,
                screenClassification = ScreenClassification.NON_OFFER,
                timestampMillis = timestampMillis,
                offeredPriceField = ExtractedField.Missing,
                confidenceScore = 0.0,
                rawCapturedTexts = cleanedLines,
                isSimulated = isSimulated
            )
        }

        // 2. Preço oferecido principal (prioriza o preço associado à categoria Pop / 99Plus / Negocia e descarta saldo/ganhos de carteira)
        val candidatePrices = mutableListOf<Double>()
        for (line in cleanedLines) {
            val l = line.lowercase(Locale.getDefault())
            // Ignora linhas que são exclusivamente saldo ou ganhos acumulados de carteira
            if (l.contains("saldo") || l.contains("ganhos") || l.contains("carteira")) {
                continue
            }
            val extracted = BrazilianOfferParser.extractAllCurrencies(listOf(line))
            candidatePrices.addAll(extracted)
        }

        // Se houver linha com 'Pop' ou 'Pop Expresso' com preço, esse é o valor oficial prioritário
        var primaryPrice: Double? = null
        for (line in cleanedLines) {
            val l = line.lowercase(Locale.getDefault())
            if (l.contains("pop") || l.contains("99pop") || l.contains("plus") || l.contains("negocia") || l.contains("express")) {
                val p = BrazilianOfferParser.extractAllCurrencies(listOf(line)).firstOrNull()
                if (p != null && p in 4.0..350.0) {
                    primaryPrice = p
                    break
                }
            }
        }

        val finalPriceVal = primaryPrice ?: candidatePrices.firstOrNull { it in 4.0..350.0 } ?: candidatePrices.firstOrNull()

        val priceField: ExtractedField<Double> = when {
            finalPriceVal != null -> ExtractedField.Present(finalPriceVal)
            cleanedLines.any { it.contains("R$", ignoreCase = true) } -> ExtractedField.Unrecognized("R$ inválido")
            else -> ExtractedField.Missing
        }

        // 3. Trechos de Viagem, Distâncias e Durações (Ex: "5 min (1,2 km)" + "10 min (2,4 km)")
        val legs = BrazilianOfferParser.extractRideLegs(cleanedLines)
        var pickupKmField: ExtractedField<Double> = ExtractedField.Missing
        var tripKmField: ExtractedField<Double> = ExtractedField.Missing
        var totalKmField: ExtractedField<Double> = ExtractedField.Missing

        var pickupMinField: ExtractedField<Int> = ExtractedField.Missing
        var tripMinField: ExtractedField<Int> = ExtractedField.Missing
        var totalMinField: ExtractedField<Int> = ExtractedField.Missing

        var legOrigin: String? = null
        var legDestination: String? = null

        if (legs.size >= 2) {
            pickupKmField = ExtractedField.Present(legs[0].distanceKm)
            tripKmField = ExtractedField.Present(legs[1].distanceKm)
            pickupMinField = ExtractedField.Present(legs[0].durationMin)
            tripMinField = ExtractedField.Present(legs[1].durationMin)
            legOrigin = legs[0].address
            legDestination = legs[1].address
        } else if (legs.size == 1) {
            totalKmField = ExtractedField.Present(legs[0].distanceKm)
            totalMinField = ExtractedField.Present(legs[0].durationMin)
            legOrigin = legs[0].address
        } else {
            // Fallback para quando os dados de km e min vierem em nós separados
            val distances = BrazilianOfferParser.extractAllDistancesKm(cleanedLines)
            if (distances.size >= 2) {
                pickupKmField = ExtractedField.Present(distances[0])
                tripKmField = ExtractedField.Present(distances[1])
            } else if (distances.size == 1) {
                totalKmField = ExtractedField.Present(distances[0])
            }

            val durations = BrazilianOfferParser.extractAllDurationsMinutes(cleanedLines)
            if (durations.size >= 2) {
                pickupMinField = ExtractedField.Present(durations[0])
                tripMinField = ExtractedField.Present(durations[1])
            } else if (durations.size == 1) {
                totalMinField = ExtractedField.Present(durations[0])
            }
        }

        // 5. Modalidade 99
        val detectedCategory = NINETY_NINE_CATEGORIES.firstOrNull { cat ->
            cleanedLines.any { it.contains(cat, ignoreCase = true) }
        } ?: "99Pop"

        // 6. Endereços de Origem e Destino + Avaliação do Passageiro
        val (parsedOrigin, parsedDestination) = BrazilianOfferParser.extractOriginAndDestination(cleanedLines)
        val origin = legOrigin ?: parsedOrigin
        val destination = legDestination ?: parsedDestination
        val passengerRating = BrazilianOfferParser.extractPassengerRating(cleanedLines)

        // 7. Validação de consistência
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

        var confidence = 0.0
        if (priceField.isPresent) confidence += 0.45
        if (totalKmVal != null && totalKmVal > 0) confidence += 0.30
        if (totalMinVal != null && totalMinVal > 0) confidence += 0.20
        if (!origin.isNullOrBlank() || !destination.isNullOrBlank()) confidence += 0.05
        if (captureMethod == CaptureMethod.OCR) confidence *= 0.90
        if (conflictNotes.isNotEmpty()) confidence *= 0.50

        val has99RideTrigger = isMultiOfferCard || cleanedLines.any { line ->
            NINETY_NINE_CATEGORIES.any { cat -> line.contains(cat, ignoreCase = true) } ||
                line.contains("Aceitar", ignoreCase = true) ||
                line.contains("Recusar", ignoreCase = true) ||
                line.contains("Escolher", ignoreCase = true) ||
                line.contains("Solicitações", ignoreCase = true) ||
                line.contains("Solicitacoes", ignoreCase = true) ||
                line.contains("Pgto. no app", ignoreCase = true) ||
                line.contains("Pagamento no app", ignoreCase = true) ||
                line.contains("Embarque", ignoreCase = true) ||
                line.contains("Desembarque", ignoreCase = true) ||
                line.contains("corridas", ignoreCase = true)
        }

        val isSelfReadingGoOrNo = cleanedLines.any { line ->
            line.contains("GoOrNo", ignoreCase = true) ||
                line.contains("R$/km", ignoreCase = true) ||
                line.contains("R$/hora", ignoreCase = true) ||
                line.contains("Suas Faixas", ignoreCase = true)
        } && !cleanedLines.any { it.contains("Aceitar", ignoreCase = true) || it.contains("Escolher", ignoreCase = true) }

        // Trava de Sanidade Rigorosa (rejeita saldos do motorista como R$ 254 em corridas curtas ou durações absurdas como 974 min)
        val rawPrice = priceField.valueOrNull() ?: 0.0
        val isSanePrice = (rawPrice in 4.50..350.0) || (totalKmVal != null && totalKmVal >= 60.0 && rawPrice <= 1500.0)
        val isSaneDuration = totalMinVal == null || totalMinVal in 1..240

        val isOffer = isSanePrice && isSaneDuration && priceField.isPresent && totalKmVal != null && totalKmVal > 0.0 &&
            has99RideTrigger && !isSelfReadingGoOrNo &&
            !BrazilianOfferParser.isNonRideAdOrService(cleanedLines)
        val classification = if (isOffer) {
            ScreenClassification.NEW_OFFER
        } else {
            ScreenClassification.NON_OFFER
        }

        val fingerprint = BrazilianOfferParser.buildFingerprint(
            sourceAppName = "99",
            price = priceField.valueOrNull(),
            totalKm = totalKmVal,
            totalMin = totalMinVal,
            origin = origin,
            destination = destination
        )

        return StandardRideOffer(
            offerId = UUID.randomUUID().toString(),
            sourceApp = if (isSimulated) SourceApp.SIMULATION else SourceApp.NINETY_NINE_DRIVER,
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
