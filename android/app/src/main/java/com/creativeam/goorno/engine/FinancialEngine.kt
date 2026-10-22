package com.creativeam.goorno.engine

import com.creativeam.goorno.model.DriverFinancialConfig
import com.creativeam.goorno.model.FinancialEvaluationResult
import com.creativeam.goorno.model.PassengerRatingVerdict
import com.creativeam.goorno.model.StandardRideOffer
import com.creativeam.goorno.model.TrafficLightVerdict
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * Motor Financeiro e Semáforo de Decisão em Kotlin puro (independente de UI e testável via JUnit).
 */
object FinancialEngine {

    fun classifyPassengerRating(
        rating: Double?,
        config: DriverFinancialConfig
    ): PassengerRatingVerdict {
        if (rating == null || rating <= 0.0 || rating.isNaN()) return PassengerRatingVerdict.UNKNOWN
        val goodThreshold = config.targetGoodPassengerRating.coerceIn(3.0, 5.0)
        val minAcceptable = config.minAcceptablePassengerRating.coerceIn(3.0, goodThreshold)
        return when {
            rating >= goodThreshold -> PassengerRatingVerdict.GOOD
            rating >= minAcceptable -> PassengerRatingVerdict.MEDIUM
            else -> PassengerRatingVerdict.BAD
        }
    }

    fun formatPassengerRatingBadge(
        rating: Double?,
        verdict: PassengerRatingVerdict
    ): String? {
        if (rating == null || verdict == PassengerRatingVerdict.UNKNOWN) return null
        val numStr = String.format(Locale.forLanguageTag("pt-BR"), "%.2f", rating)
        return "${verdict.emoji} ★ $numStr (${verdict.label})"
    }

    fun evaluate(
        offer: StandardRideOffer,
        config: DriverFinancialConfig
    ): FinancialEvaluationResult {
        val reasons = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val price = offer.offeredPrice
        if (price == null || price <= 0.0 || price.isNaN() || price.isInfinite()) {
            warnings.add("Valor da oferta ausente ou inválido.")
            return buildIncompleteResult(offer, config, reasons, warnings)
        }

        if (offer.hasFieldConflict) {
            warnings.addAll(offer.conflictNotes)
            warnings.add("Conflito detectado entre os campos lidos.")
            return buildIncompleteResult(offer, config, reasons, warnings)
        }

        if (offer.confidenceScore < config.minConfidenceThreshold) {
            warnings.add(
                String.format(
                    Locale.forLanguageTag("pt-BR"),
                    "Confiança da leitura insuficiente (%.0f%% < %.0f%%).",
                    offer.confidenceScore * 100,
                    config.minConfidenceThreshold * 100
                )
            )
            return buildIncompleteResult(offer, config, reasons, warnings)
        }

        // 1. Cálculo de Distância Total Considerada (sem duplicar distância já somada)
        val pickupKm = offer.pickupDistanceKm
        val tripKm = offer.tripDistanceKm
        val explicitTotalKm = offer.totalDistanceKmExplicit

        var isDistancePartial = false
        val totalDistanceKm: Double? = when {
            pickupKm != null && tripKm != null -> {
                if (pickupKm < 0.0 || tripKm < 0.0) null
                else {
                    // Se o total explícito já for igual à soma, não soma duas vezes
                    val sum = pickupKm + tripKm
                    if (explicitTotalKm != null && abs(explicitTotalKm - sum) < 0.05) {
                        explicitTotalKm
                    } else {
                        sum
                    }
                }
            }
            explicitTotalKm != null -> {
                if (explicitTotalKm > 0.0) explicitTotalKm else null
            }
            pickupKm != null && pickupKm > 0.0 -> {
                isDistancePartial = true
                warnings.add("Apenas distância de embarque informada (distância parcial).")
                pickupKm
            }
            tripKm != null && tripKm > 0.0 -> {
                isDistancePartial = true
                warnings.add("Deslocamento até o passageiro não informado na oferta.")
                tripKm
            }
            else -> null
        }

        // 2. Cálculo de Duração Total Considerada (sem duplicar tempo já somado)
        val pickupMin = offer.pickupDurationMin
        val tripMin = offer.tripDurationMin
        val explicitTotalMin = offer.totalDurationMinExplicit

        var isDurationPartial = false
        val totalDurationMin: Int? = when {
            pickupMin != null && tripMin != null -> {
                if (pickupMin < 0 || tripMin < 0) null
                else {
                    val sum = pickupMin + tripMin
                    if (explicitTotalMin != null && explicitTotalMin == sum) explicitTotalMin else sum
                }
            }
            explicitTotalMin != null -> {
                if (explicitTotalMin > 0) explicitTotalMin else null
            }
            pickupMin != null && pickupMin > 0 -> {
                isDurationPartial = true
                warnings.add("Apenas tempo até o passageiro informado (tempo parcial).")
                pickupMin
            }
            tripMin != null && tripMin > 0 -> {
                isDurationPartial = true
                warnings.add("Tempo até o passageiro não informado na oferta.")
                tripMin
            }
            else -> null
        }

        if (totalDistanceKm != null && totalDistanceKm <= 0.0) {
            warnings.add("Distância total inválida (<= 0 km).")
            return buildIncompleteResult(offer, config, reasons, warnings)
        }
        if (totalDurationMin != null && totalDurationMin <= 0) {
            warnings.add("Duração total inválida (<= 0 min).")
            return buildIncompleteResult(offer, config, reasons, warnings)
        }

        // Se nem distância nem tempo existem, não há como avaliar
        if (totalDistanceKm == null && totalDurationMin == null) {
            warnings.add("Distância e duração ausentes na oferta.")
            return buildIncompleteResult(offer, config, reasons, warnings)
        }

        // 3. Indicadores Financeiros
        val grossPerKm: Double? = if (totalDistanceKm != null && totalDistanceKm > 0.0) {
            round2(price / totalDistanceKm)
        } else null

        val durationHours: Double? = if (totalDurationMin != null && totalDurationMin > 0) {
            totalDurationMin.toDouble() / 60.0
        } else null

        val grossPerHour: Double? = if (durationHours != null && durationHours > 0.0) {
            round2(price / durationHours)
        } else null

        val fuelCost: Double? = if (totalDistanceKm != null && config.fuelConsumptionKmPerLiter > 0.0) {
            round2(totalDistanceKm * (config.fuelPricePerLiter / config.fuelConsumptionKmPerLiter))
        } else null

        val maintenanceCost: Double? = if (totalDistanceKm != null) {
            round2(totalDistanceKm * config.maintenanceCostPerKm.coerceAtLeast(0.0))
        } else null

        val fixedCost = round2(config.fixedExtraCostPerRide.coerceAtLeast(0.0))

        val totalCost: Double? = if (fuelCost != null && maintenanceCost != null) {
            round2(fuelCost + maintenanceCost + fixedCost)
        } else null

        val netProfit: Double? = if (totalCost != null) {
            round2(price - totalCost)
        } else null

        val netPerKm: Double? = if (netProfit != null && totalDistanceKm != null && totalDistanceKm > 0.0) {
            round2(netProfit / totalDistanceKm)
        } else null

        val netPerHour: Double? = if (netProfit != null && durationHours != null && durationHours > 0.0) {
            round2(netProfit / durationHours)
        } else null

        val isAnalysisComplete = (totalDistanceKm != null && totalDurationMin != null && !isDistancePartial && !isDurationPartial)

        // 4. Avaliação do Semáforo Financeiro Base
        val baseVerdict = determineTrafficLight(
            grossPerKm = grossPerKm,
            grossPerHour = grossPerHour,
            netProfit = netProfit,
            netPerKm = netPerKm,
            netPerHour = netPerHour,
            isDistancePartial = isDistancePartial,
            isDurationPartial = isDurationPartial,
            config = config,
            reasons = reasons,
            warnings = warnings
        )

        // 5. Avaliação da Nota do Passageiro (Verde = Boa, Amarela = Média, Vermelha = Ruim)
        val rating = offer.passengerRating
        val ratingVerdict = classifyPassengerRating(rating, config)
        val ratingBadge = formatPassengerRatingBadge(rating, ratingVerdict)
        val ptBr = Locale.forLanguageTag("pt-BR")

        val verdictAfterRating = when (ratingVerdict) {
            PassengerRatingVerdict.GOOD -> {
                reasons.add(
                    String.format(
                        ptBr,
                        "🟢 Passageiro Nota ★ %.2f (Boa — mínimo %.2f)",
                        rating,
                        config.targetGoodPassengerRating
                    )
                )
                baseVerdict
            }
            PassengerRatingVerdict.MEDIUM -> {
                val msg = String.format(
                    ptBr,
                    "🟡 Passageiro Nota ★ %.2f (Média — entre %.2f e %.2f)",
                    rating,
                    config.minAcceptablePassengerRating,
                    config.targetGoodPassengerRating
                )
                warnings.add(msg)
                if (config.requirePassengerRating && baseVerdict == TrafficLightVerdict.GREEN) {
                    reasons.add(0, "Nota do passageiro MÉDIA (★ ${String.format(ptBr, "%.2f", rating)}) — Semáforo em Avaliar")
                    TrafficLightVerdict.YELLOW
                } else {
                    baseVerdict
                }
            }
            PassengerRatingVerdict.BAD -> {
                val msg = String.format(
                    ptBr,
                    "🔴 Passageiro Nota ★ %.2f (Ruim — abaixo de %.2f)",
                    rating,
                    config.minAcceptablePassengerRating
                )
                warnings.add(0, msg)
                if (config.requirePassengerRating) {
                    if (config.forceRedOnBadPassengerRating && baseVerdict != TrafficLightVerdict.GREEN) {
                        reasons.add(0, "🔴 Nota do passageiro RUIM (★ ${String.format(ptBr, "%.2f", rating)}) — Semáforo em Recusar")
                        TrafficLightVerdict.RED
                    } else {
                        reasons.add(0, "🟡 Nota do passageiro baixa (★ ${String.format(ptBr, "%.2f", rating)}) — Semáforo em Avaliar")
                        if (baseVerdict == TrafficLightVerdict.GREEN) TrafficLightVerdict.YELLOW else baseVerdict
                    }
                } else {
                    baseVerdict
                }
            }
            PassengerRatingVerdict.UNKNOWN -> baseVerdict
        }

        // 6. Avaliação de Área de Risco (Origem / Destino)
        val riskAssessment = RiskAreaEvaluator.evaluate(
            originAddress = offer.originAddress,
            destinationAddress = offer.destinationAddress,
            config = config
        )

        if (riskAssessment.hasRisk) {
            warnings.add(0, riskAssessment.detailedMessage)
            val zoneName = (riskAssessment.destinationMatchedZone ?: riskAssessment.originMatchedZone).orEmpty()
            val zoneTag = if (zoneName.isNotBlank()) " ($zoneName)" else ""
            reasons.add(0, "${riskAssessment.shortBadge}$zoneTag")
        }
        val finalVerdict = verdictAfterRating

        // 7. Avaliação de Região Favorita (Origem / Destino)
        val favoriteAssessment = FavoriteAreaEvaluator.evaluate(
            originAddress = offer.originAddress,
            destinationAddress = offer.destinationAddress,
            config = config
        )

        if (favoriteAssessment.isFavorite) {
            reasons.add(0, "⭐ ${favoriteAssessment.shortBadge} — Região preferida de trabalho")
        }

        val yellowRatio = config.yellowToleranceRatio.coerceIn(0.50, 0.99)
        val kmVerdict = when {
            grossPerKm == null -> TrafficLightVerdict.GRAY_NEUTRAL
            grossPerKm >= config.targetGrossPerKm -> TrafficLightVerdict.GREEN
            grossPerKm >= round2(config.targetGrossPerKm * yellowRatio) -> TrafficLightVerdict.YELLOW
            else -> TrafficLightVerdict.RED
        }

        val hourVerdict = when {
            grossPerHour == null -> TrafficLightVerdict.GRAY_NEUTRAL
            grossPerHour >= config.targetGrossPerHour -> TrafficLightVerdict.GREEN
            grossPerHour >= round2(config.targetGrossPerHour * yellowRatio) -> TrafficLightVerdict.YELLOW
            else -> TrafficLightVerdict.RED
        }

        val pickupThreshold = config.distantPickupKmThreshold.coerceIn(0.5, 10.0)
        val isDistantPickup = if (pickupKm != null) {
            pickupKm >= pickupThreshold
        } else if (pickupMin != null) {
            pickupMin >= (pickupThreshold * 3.5).toInt().coerceAtLeast(10)
        } else {
            false
        }
        val isLongTrip = (totalDistanceKm != null && totalDistanceKm >= 25.0) || (totalDurationMin != null && totalDurationMin >= 45)

        val operationalAlertParts = mutableListOf<String>()
        if (offer.isRepeatedOffer) {
            operationalAlertParts.add("🔁 Chamado Repetido")
            reasons.add(0, "🔁 Chamado repetido: oferta reenviada pelo aplicativo.")
        }
        if (!offer.stopsDescription.isNullOrBlank()) {
            operationalAlertParts.add("🛑 ${offer.stopsDescription}")
            reasons.add("🛑 Viagem com ${offer.stopsDescription}")
        }
        if (offer.isSupermarket) {
            val sName = offer.supermarketName ?: "Supermercado"
            operationalAlertParts.add("🛒 $sName")
            reasons.add("🛒 Corrida em $sName (Atenção com compras)")
        }
        if (isDistantPickup) {
            val kmText = pickupKm?.let { String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", it) } ?: ""
            operationalAlertParts.add("⚠️ Emb. Longe ($kmText)".trim())
            warnings.add("⚠️ Embarque distante: $kmText até o passageiro.")
        }
        if (isLongTrip && operationalAlertParts.size < 2) {
            val timeText = if (totalDurationMin != null && totalDurationMin >= 60) {
                val h = totalDurationMin / 60
                val m = totalDurationMin % 60
                if (m > 0) "${h}h ${m}m" else "${h}h"
            } else {
                "${totalDurationMin ?: 0} min"
            }
            operationalAlertParts.add("⏱️ Viagem Longa ($timeText)")
            warnings.add("⏱️ Viagem longa: duração estimada em $timeText.")
        }

        val operationalBadge = if (operationalAlertParts.isNotEmpty()) {
            operationalAlertParts.take(2).joinToString("  •  ")
        } else null

        return FinancialEvaluationResult(
            offer = offer,
            totalConsideredDistanceKm = totalDistanceKm?.let { round2(it) },
            totalConsideredDurationMin = totalDurationMin,
            isDistancePartial = isDistancePartial,
            isDurationPartial = isDurationPartial,
            isAnalysisComplete = isAnalysisComplete,
            grossPerKm = grossPerKm,
            grossPerHour = grossPerHour,
            estimatedFuelCost = fuelCost,
            estimatedMaintenanceCost = maintenanceCost,
            estimatedFixedCost = fixedCost,
            estimatedTotalCost = totalCost,
            estimatedNetProfit = netProfit,
            netProfitPerKm = netPerKm,
            netProfitPerHour = netPerHour,
            verdict = finalVerdict,
            isRepeatedOffer = offer.isRepeatedOffer,
            kmVerdict = kmVerdict,
            hourVerdict = hourVerdict,
            reasons = reasons,
            warnings = warnings,
            passengerRating = rating,
            passengerRatingVerdict = ratingVerdict,
            passengerRatingBadge = ratingBadge,
            hasRiskAlert = riskAssessment.hasRisk,
            riskAlertBadge = if (riskAssessment.hasRisk) riskAssessment.shortBadge else null,
            isFavoriteZone = favoriteAssessment.isFavorite,
            favoriteZoneBadge = if (favoriteAssessment.isFavorite) favoriteAssessment.shortBadge else null,
            operationalBadge = operationalBadge,
            stopsDescription = offer.stopsDescription,
            isSupermarket = offer.isSupermarket,
            supermarketName = offer.supermarketName,
            isDistantPickup = isDistantPickup,
            isLongTrip = isLongTrip
        )
    }

    private fun determineTrafficLight(
        grossPerKm: Double?,
        grossPerHour: Double?,
        netProfit: Double?,
        netPerKm: Double?,
        netPerHour: Double?,
        isDistancePartial: Boolean,
        isDurationPartial: Boolean,
        config: DriverFinancialConfig,
        reasons: MutableList<String>,
        warnings: MutableList<String>
    ): TrafficLightVerdict {
        // Verifica se faltam dados para algum critério obrigatório
        if (config.requireGrossPerKm && grossPerKm == null) {
            warnings.add("Falta distância para calcular meta obrigatória de R$/km bruto.")
            return TrafficLightVerdict.GRAY_NEUTRAL
        }
        if (config.requireGrossPerHour && grossPerHour == null) {
            warnings.add("Falta tempo para calcular meta obrigatória de R$/h bruto.")
            return TrafficLightVerdict.GRAY_NEUTRAL
        }
        if (config.requireNetPerKm && netPerKm == null) {
            warnings.add("Falta distância para calcular meta obrigatória de lucro/km.")
            return TrafficLightVerdict.GRAY_NEUTRAL
        }
        if (config.requireNetPerHour && netPerHour == null) {
            warnings.add("Falta tempo para calcular meta obrigatória de lucro/h.")
            return TrafficLightVerdict.GRAY_NEUTRAL
        }

        // Prejuízo líquido direto -> Vermelho imediato
        if (netProfit != null && netProfit <= 0.0) {
            reasons.add(formatBr("Prejuízo estimado: lucro líquido R$ %.2f", netProfit))
            return TrafficLightVerdict.RED
        }

        data class CriterionCheck(
            val name: String,
            val actual: Double,
            val target: Double
        )

        val checks = mutableListOf<CriterionCheck>()
        if (config.requireGrossPerKm && grossPerKm != null) {
            checks.add(CriterionCheck("Bruto/km", grossPerKm, config.targetGrossPerKm))
        }
        if (config.requireGrossPerHour && grossPerHour != null) {
            checks.add(CriterionCheck("Bruto/h", grossPerHour, config.targetGrossPerHour))
        }
        if (config.requireNetPerKm && netPerKm != null) {
            checks.add(CriterionCheck("Lucro/km", netPerKm, config.targetNetPerKm))
        }
        if (config.requireNetPerHour && netPerHour != null) {
            checks.add(CriterionCheck("Lucro/h", netPerHour, config.targetNetPerHour))
        }

        // Se o motorista desmarcou todos os checks obrigatórios, usa Bruto/km como padrão
        if (checks.isEmpty() && grossPerKm != null) {
            checks.add(CriterionCheck("Bruto/km", grossPerKm, config.targetGrossPerKm))
        }

        if (checks.isEmpty()) {
            return TrafficLightVerdict.GRAY_NEUTRAL
        }

        var allGreen = true
        var anyRed = false
        val yellowRatio = config.yellowToleranceRatio.coerceIn(0.50, 0.99)

        for (c in checks) {
            val yellowFloor = round2(c.target * yellowRatio)
            when {
                c.actual >= c.target -> {
                    reasons.add(formatBr("%s: R$ %.2f >= meta R$ %.2f (OK)", c.name, c.actual, c.target))
                }
                c.actual >= yellowFloor -> {
                    allGreen = false
                    reasons.add(formatBr("%s: R$ %.2f (parcial, meta R$ %.2f)", c.name, c.actual, c.target))
                }
                else -> {
                    allGreen = false
                    // Se o R$/km atinge ou supera a meta, o R$/h abaixo não pode forçar Vermelho direto (vira Avaliar/Amarelo)
                    val isHourlyCheck = c.name.contains("/h")
                    val hasGoodKmRate = grossPerKm != null && grossPerKm >= config.targetGrossPerKm
                    if (isHourlyCheck && hasGoodKmRate) {
                        reasons.add(formatBr("%s: R$ %.2f abaixo da meta R$ %.2f (R$/km é bom)", c.name, c.actual, c.target))
                    } else {
                        anyRed = true
                        reasons.add(formatBr("%s: R$ %.2f abaixo do mínimo R$ %.2f", c.name, c.actual, c.target))
                    }
                }
            }
        }

        return when {
            anyRed -> TrafficLightVerdict.RED
            allGreen && (isDistancePartial || isDurationPartial) -> {
                reasons.add("Leitura parcial: confirme distância/tempo de embarque.")
                TrafficLightVerdict.YELLOW
            }
            allGreen -> TrafficLightVerdict.GREEN
            else -> TrafficLightVerdict.YELLOW
        }
    }

    private fun buildIncompleteResult(
        offer: StandardRideOffer,
        config: DriverFinancialConfig,
        reasons: List<String>,
        warnings: List<String>
    ): FinancialEvaluationResult {
        return FinancialEvaluationResult(
            offer = offer,
            totalConsideredDistanceKm = null,
            totalConsideredDurationMin = null,
            isDistancePartial = false,
            isDurationPartial = false,
            isAnalysisComplete = false,
            grossPerKm = null,
            grossPerHour = null,
            estimatedFuelCost = null,
            estimatedMaintenanceCost = null,
            estimatedFixedCost = round2(config.fixedExtraCostPerRide),
            estimatedTotalCost = null,
            estimatedNetProfit = null,
            netProfitPerKm = null,
            netProfitPerHour = null,
            verdict = TrafficLightVerdict.GRAY_NEUTRAL,
            kmVerdict = TrafficLightVerdict.GRAY_NEUTRAL,
            hourVerdict = TrafficLightVerdict.GRAY_NEUTRAL,
            reasons = reasons,
            warnings = warnings
        )
    }

    private fun round2(value: Double): Double = round(value * 100.0) / 100.0

    private fun formatBr(pattern: String, vararg args: Any): String =
        String.format(Locale.forLanguageTag("pt-BR"), pattern, *args)
}
