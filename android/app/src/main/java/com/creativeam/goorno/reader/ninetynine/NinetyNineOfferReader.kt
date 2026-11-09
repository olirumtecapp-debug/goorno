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

    /**
     * Completa SOMENTE a nota de uma oferta já exibida usando OCR da MESMA
     * oferta. Nunca substitui preço, quilômetros ou minutos por leitura OCR.
     * A comparação estrita evita atribuir a nota de outro card da 99.
     */
    fun enrichRatingsFromOcr(
        displayed: List<StandardRideOffer>,
        visualOffers: List<StandardRideOffer>
    ): List<StandardRideOffer> = displayed.map { original ->
        if (original.passengerRating != null || original.passengerIsNew) return@map original
        val matches = visualOffers.filter { candidate ->
            val originalPrice = original.offeredPrice
            val candidatePrice = candidate.offeredPrice
            val originalKm = original.totalDistanceKmExplicit
                ?: ((original.pickupDistanceKm ?: 0.0) + (original.tripDistanceKm ?: 0.0))
            val candidateKm = candidate.totalDistanceKmExplicit
                ?: ((candidate.pickupDistanceKm ?: 0.0) + (candidate.tripDistanceKm ?: 0.0))
            candidate.passengerRating != null &&
                originalPrice != null && candidatePrice != null &&
                kotlin.math.abs(originalPrice - candidatePrice) < 0.02 &&
                originalKm > 0.0 && candidateKm > 0.0 &&
                kotlin.math.abs(originalKm - candidateKm) < 0.16 &&
                candidate.sourceApp == original.sourceApp
        }
        if (matches.size == 1) original.copy(passengerRating = matches.first().passengerRating)
        else original
    }

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

        // 0b. Rejeição total de telas modais/feedbacks do sistema da 99 e corridas aceitas/expiradas
        if (joinedLower.contains("várias solicitações seguidas recusadas") ||
            joinedLower.contains("agradecemos sua resposta") ||
            joinedLower.contains("por que você recusou") ||
            joinedLower.contains("motivo da recusa") ||
            joinedLower.contains("deseja continuar online") ||
            joinedLower.contains("pausado por") ||
            joinedLower.contains("esta corrida foi aceita") ||
            joinedLower.contains("corrida foi aceita por outro") ||
            joinedLower.contains("aceita por outro motorista") ||
            joinedLower.contains("corrida aceita")
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

        // Se houver linha com categoria ('Pop', 'Pop Expresso', 'Pop Nova', etc.), busca o preço oficial prioritário da oferta
        var primaryPrice: Double? = null
        var primaryPriceLineIdx: Int = -1
        for ((idx, line) in cleanedLines.withIndex()) {
            val l = line.lowercase(Locale.getDefault())
            // Ignora subtarifas inclusas como "Tarifa Expresso inclusa" ou "Tarifa base dinâmica", mas não "Tarifa mais alta"
            val isSubFare = l.contains("inclus") || l.contains("tarifa expresso") || l.contains("tarifa base") || l.contains("tarifa express") || l.contains("/km") || l.contains("deslocamento")
            if (isSubFare) {
                continue
            }
            val isCategoryLine = l.contains("pop") || l.contains("99pop") || l.contains("plus") || l.contains("negocia") || (l.contains("express") && !l.contains("inclus"))
            if (isCategoryLine) {
                val pSameLine = BrazilianOfferParser.extractAllCurrencies(listOf(line)).firstOrNull()
                if (pSameLine != null && pSameLine in 4.0..350.0) {
                    primaryPrice = pSameLine
                    primaryPriceLineIdx = idx
                    break
                }
                // Preço nas linhas seguintes adjacentes à categoria (busca até 3 linhas abaixo para pular badges como "BEM BARATO")
                for (offset in 1..3) {
                    if (idx + offset < cleanedLines.size) {
                        val nextLine = cleanedLines[idx + offset]
                        val nextLower = nextLine.lowercase(Locale.getDefault())
                        val isNextSubFare = nextLower.contains("inclus") || nextLower.contains("tarifa expresso") || nextLower.contains("tarifa base") || nextLower.contains("deslocamento") || nextLower.contains("/km")
                        if (!isNextSubFare) {
                            val pNext = BrazilianOfferParser.extractAllCurrencies(listOf(nextLine)).firstOrNull()
                            if (pNext != null && pNext in 4.0..350.0) {
                                primaryPrice = pNext
                                primaryPriceLineIdx = idx + offset
                                break
                            }
                        }
                    }
                }
                if (primaryPrice != null) break
            }
        }

        val validCandidates = candidatePrices.filter { it in 4.0..350.0 }
        val dists = BrazilianOfferParser.extractAllDistancesKm(cleanedLines)
        val totKm = if (dists.size >= 2) dists[0] + dists[1] else dists.firstOrNull() ?: 1.0

        // Sanity Check Matemático: Extrai taxa por km anunciada (ex: "R$2,81/km" ou "R$ 3,95/km")
        val announcedRatePerKm = cleanedLines.firstNotNullOfOrNull { line ->
            val m = Regex("""R\$\s*(\d{1,2}[.,]\d{1,2})\s*/\s*km""", RegexOption.IGNORE_CASE).find(line)
            m?.let { BrazilianOfferParser.parseBrazilianDecimal(it.groupValues[1]) }
        }

        // Filtra anomalias absurdas (ex: R$ 133,00 para uma corrida de 5,6 km com taxa anunciada de R$ 2,81/km)
        val saneCandidates = validCandidates.filter { candidate ->
            if (announcedRatePerKm != null && totKm > 1.0) {
                val expected = totKm * announcedRatePerKm
                (candidate >= expected * 0.35 && candidate <= expected * 2.50) || (candidate in 4.0..45.0)
            } else {
                true
            }
        }

        // Se houver primaryPrice legítimo ancorado ao card, usa SEMPRE ele.
        // Caso contrário, usa os candidatos validados pelo sanity check, NUNCA maxOrNull cego!
        val finalPriceVal = primaryPrice ?: saneCandidates.firstOrNull() ?: validCandidates.firstOrNull() ?: candidatePrices.firstOrNull()
        if (primaryPriceLineIdx == -1 && finalPriceVal != null) {
            primaryPriceLineIdx = cleanedLines.indexOfFirst { line ->
                BrazilianOfferParser.extractAllCurrencies(listOf(line)).contains(finalPriceVal)
            }
        }

        val priceField: ExtractedField<Double> = when {
            finalPriceVal != null -> ExtractedField.Present(finalPriceVal)
            cleanedLines.any { it.contains("R$", ignoreCase = true) } -> ExtractedField.Unrecognized("R$ inválido")
            else -> ExtractedField.Missing
        }

        // 3. Trechos de Viagem, Distâncias e Durações (ancorados ao preço principal da oferta)
        // Se houver resíduos de scroll acima do preço (ex: trechos da chamada anterior que já rolou),
        // analisa preferencialmente as linhas a partir do preço principal!
        val linesForLegs = if (primaryPriceLineIdx > 0 && primaryPriceLineIdx < cleanedLines.size) {
            cleanedLines.subList(primaryPriceLineIdx, cleanedLines.size)
        } else {
            cleanedLines
        }
        val legs = BrazilianOfferParser.extractRideLegs(linesForLegs).ifEmpty {
            BrazilianOfferParser.extractRideLegs(cleanedLines)
        }
        val allDistances = BrazilianOfferParser.extractAllDistancesKm(linesForLegs).ifEmpty {
            BrazilianOfferParser.extractAllDistancesKm(cleanedLines)
        }
        val allDurations = BrazilianOfferParser.extractAllDurationsMinutes(linesForLegs).ifEmpty {
            BrazilianOfferParser.extractAllDurationsMinutes(cleanedLines)
        }

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
            totalKmField = ExtractedField.Present(legs[0].distanceKm + legs[1].distanceKm)
            totalMinField = ExtractedField.Present(legs[0].durationMin + legs[1].durationMin)
        } else if (legs.size == 1 && allDistances.size >= 2) {
            // Se apenas 1 trecho foi ancorado, mas a tela possui 2 distâncias (ex: 1,3 km e 11,8 km):
            val pickKm = allDistances[0]
            val trKm = allDistances[1]
            pickupKmField = ExtractedField.Present(pickKm)
            tripKmField = ExtractedField.Present(trKm)
            totalKmField = ExtractedField.Present(pickKm + trKm)

            val pickMin = allDurations.getOrNull(0) ?: legs[0].durationMin
            val trMin = allDurations.getOrNull(1) ?: (trKm * 2.2).toInt().coerceAtLeast(3)
            pickupMinField = ExtractedField.Present(pickMin)
            tripMinField = ExtractedField.Present(trMin)
            totalMinField = ExtractedField.Present(pickMin + trMin)

            legOrigin = legs[0].address
        } else if (legs.size == 1) {
            totalKmField = ExtractedField.Present(legs[0].distanceKm)
            totalMinField = ExtractedField.Present(legs[0].durationMin)
            legOrigin = legs[0].address
        } else {
            // Fallback para quando os dados de km e min vierem em nós separados
            if (allDistances.size >= 2) {
                pickupKmField = ExtractedField.Present(allDistances[0])
                tripKmField = ExtractedField.Present(allDistances[1])
                totalKmField = ExtractedField.Present(allDistances[0] + allDistances[1])
            } else if (allDistances.size == 1) {
                totalKmField = ExtractedField.Present(allDistances[0])
            }

            if (allDurations.size >= 2) {
                pickupMinField = ExtractedField.Present(allDurations[0])
                tripMinField = ExtractedField.Present(allDurations[1])
                totalMinField = ExtractedField.Present(allDurations[0] + allDurations[1])
            } else if (allDurations.size == 1) {
                val singleMin = allDurations[0]
                val totKm = totalKmField.valueOrNull()
                if (totKm != null && totKm > 5.0 && singleMin < 10) {
                    // Se a distância total é grande (ex: 13 km) e temos só 7 min, 7 min é apenas o embarque!
                    pickupMinField = ExtractedField.Present(singleMin)
                    val estimatedTripMin = (totKm * 2.2).toInt().coerceAtLeast(5)
                    tripMinField = ExtractedField.Present(estimatedTripMin)
                    totalMinField = ExtractedField.Present(singleMin + estimatedTripMin)
                } else {
                    totalMinField = ExtractedField.Present(singleMin)
                }
            }
        }

        // 5. Modalidade 99
        val detectedCategory = NINETY_NINE_CATEGORIES.firstOrNull { cat ->
            cleanedLines.any { it.contains(cat, ignoreCase = true) }
        } ?: "99Pop"

        // 6. Endereços de Origem e Destino + Avaliação do Passageiro
        val linesForRatingAndAddr = if (primaryPriceLineIdx > 0 && primaryPriceLineIdx < cleanedLines.size) {
            val startIdx = (primaryPriceLineIdx - 1).coerceAtLeast(0)
            cleanedLines.subList(startIdx, cleanedLines.size)
        } else {
            cleanedLines
        }
        val (parsedOrigin, parsedDestination) = BrazilianOfferParser.extractOriginAndDestination(linesForRatingAndAddr)
        val origin = legOrigin ?: parsedOrigin
        val destination = legDestination ?: parsedDestination
        val passengerRating = BrazilianOfferParser.extractPassengerRating(cleanedLines)
            ?: BrazilianOfferParser.extractPassengerRating(linesForRatingAndAddr)

        // 7. Validação de consistência
        val conflictNotes = mutableListOf<String>()
        val totalKmVal = totalKmField.valueOrNull()
            ?: if (pickupKmField.isPresent || tripKmField.isPresent) {
                (pickupKmField.valueOrNull() ?: 0.0) + (tripKmField.valueOrNull() ?: 0.0)
            } else null
        if (totalKmVal != null && !totalKmField.isPresent) {
            totalKmField = ExtractedField.Present(totalKmVal)
        }

        val totalMinVal = totalMinField.valueOrNull()
            ?: if (pickupMinField.isPresent || tripMinField.isPresent) {
                (pickupMinField.valueOrNull() ?: 0) + (tripMinField.valueOrNull() ?: 0)
            } else null
        if (totalMinVal != null && !totalMinField.isPresent) {
            totalMinField = ExtractedField.Present(totalMinVal)
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
        val rawPrice = priceField.valueOrNull() ?: 0.0
        if (totalKmVal != null && rawPrice > 0.0 && (rawPrice / totalKmVal) < 0.50 && totalKmVal > 15.0) {
            conflictNotes.add("Relação Preço/Km absurda (R$ $rawPrice para ${totalKmVal} km)")
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
                line.contains("corridas", ignoreCase = true) ||
                line.contains("Perfil Premium", ignoreCase = true) ||
                line.contains("Prioritário", ignoreCase = true) ||
                line.contains("Prioritario", ignoreCase = true) ||
                line.contains("Pgto. no app", ignoreCase = true) ||
                line.contains("Pagamento no app", ignoreCase = true) ||
                line.contains("Embarque", ignoreCase = true) ||
                line.contains("Desembarque", ignoreCase = true)
        }

        val isSelfReadingGoOrNo = cleanedLines.any { line ->
            line.contains("GoOrNo", ignoreCase = true) ||
                line.contains("R$/km", ignoreCase = true) ||
                line.contains("R$/hora", ignoreCase = true) ||
                line.contains("Suas Faixas", ignoreCase = true)
        } && !cleanedLines.any { it.contains("Aceitar", ignoreCase = true) || it.contains("Escolher", ignoreCase = true) }

        // Trava de Sanidade Rigorosa (rejeita saldos do motorista como R$ 254 em corridas curtas ou durações absurdas como 974 min)
        val isSanePrice = (rawPrice in 4.50..350.0) || (totalKmVal != null && totalKmVal >= 60.0 && rawPrice <= 1500.0)
        val isSaneDuration = totalMinVal != null && totalMinVal in 1..240

        val isOffer = isSanePrice && isSaneDuration && priceField.isPresent && totalKmVal != null && totalKmVal > 0.0 &&
            has99RideTrigger && !isSelfReadingGoOrNo &&
            !BrazilianOfferParser.isNonRideAdOrService(cleanedLines)
        val classification = if (isOffer) {
            ScreenClassification.NEW_OFFER
        } else {
            ScreenClassification.NON_OFFER
        }

        val stopsDesc = BrazilianOfferParser.extractStopsDescription(cleanedLines)
        val (isSupermarket, supermarketName) = BrazilianOfferParser.extractSupermarketInfo(cleanedLines, origin, destination)

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
            passengerRating = if (passengerRating != null && passengerRating > 0.0) passengerRating else null,
            passengerIsNew = (passengerRating != null && passengerRating < 0.0) || cleanedLines.any { 
                it.contains("passageiro novo", ignoreCase = true) || it.contains("novo passageiro", ignoreCase = true) 
            },
            confidenceScore = confidence.coerceIn(0.0, 1.0),
            hasFieldConflict = conflictNotes.isNotEmpty(),
            conflictNotes = conflictNotes,
            rawCapturedTexts = cleanedLines,
            dedupFingerprint = fingerprint,
            isSimulated = isSimulated,
            stopsDescription = stopsDesc,
            isSupermarket = isSupermarket,
            supermarketName = supermarketName
        )
    }
}
