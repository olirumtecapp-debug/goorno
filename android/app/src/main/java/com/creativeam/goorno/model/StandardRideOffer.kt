package com.creativeam.goorno.model

/**
 * Origem do aplicativo da oferta.
 */
enum class SourceApp(val displayName: String, val knownPackages: List<String>) {
    UBER_DRIVER(
        displayName = "Uber Driver",
        knownPackages = listOf("com.ubercab.driver", "com.ubercab")
    ),
    NINETY_NINE_DRIVER(
        displayName = "99 Motorista",
        knownPackages = listOf(
            "com.taxis99.driver",
            "com.didiglobal.driver",
            "com.didi.brazil.driver",
            "com.xiaojukeji.didi.brazil.driver",
            "com.taxis99"
        )
    ),
    SIMULATION(
        displayName = "Modo Simulação",
        knownPackages = emptyList()
    ),
    UNKNOWN(
        displayName = "Desconhecido",
        knownPackages = emptyList()
    );

    companion object {
        fun fromPackageName(pkg: String?): SourceApp {
            if (pkg.isNullOrBlank()) return UNKNOWN
            val normalized = pkg.trim().lowercase()
            return when {
                UBER_DRIVER.knownPackages.any { it.lowercase() == normalized } || normalized.contains("ubercab") -> UBER_DRIVER
                NINETY_NINE_DRIVER.knownPackages.any { it.lowercase() == normalized } ||
                    normalized.contains("taxis99") || normalized.contains("didi") || normalized.contains("99") -> NINETY_NINE_DRIVER
                else -> UNKNOWN
            }
        }
    }
}

/**
 * Método utilizado para capturar a oferta.
 */
enum class CaptureMethod(val label: String) {
    ACCESSIBILITY("Acessibilidade"),
    OCR("OCR de Tela"),
    SIMULATION("Simulação de Teste")
}

/**
 * Classificação do estado da tela do aplicativo-alvo.
 */
enum class ScreenClassification(val label: String) {
    NEW_OFFER("Nova Oferta"),
    UPDATED_OFFER("Oferta Atualizada"),
    ONGOING_TRIP("Viagem em Andamento"),
    NON_OFFER("Tela Sem Oferta")
}

/**
 * Estado explícito de cada campo extraído (Distingue Campo Ausente, Não Reconhecido e Valor Presente/Zero).
 */
sealed class ExtractedField<out T> {
    data class Present<T>(val value: T) : ExtractedField<T>()
    data class Unrecognized(val rawToken: String) : ExtractedField<Nothing>()
    data object Missing : ExtractedField<Nothing>()

    fun valueOrNull(): T? = (this as? Present<T>)?.value
    val isMissing: Boolean get() = this is Missing
    val isUnrecognized: Boolean get() = this is Unrecognized
    val isPresent: Boolean get() = this is Present<*>
}

/**
 * Modelo Padronizado de Oferta (independente de layout visual específico da Uber ou 99).
 */
data class StandardRideOffer(
    val offerId: String,
    val sourceApp: SourceApp,
    val captureMethod: CaptureMethod,
    val screenClassification: ScreenClassification,
    val timestampMillis: Long,
    val offeredPriceField: ExtractedField<Double>,
    val pickupDistanceKmField: ExtractedField<Double> = ExtractedField.Missing,
    val tripDistanceKmField: ExtractedField<Double> = ExtractedField.Missing,
    val totalDistanceKmField: ExtractedField<Double> = ExtractedField.Missing,
    val pickupDurationMinField: ExtractedField<Int> = ExtractedField.Missing,
    val tripDurationMinField: ExtractedField<Int> = ExtractedField.Missing,
    val totalDurationMinField: ExtractedField<Int> = ExtractedField.Missing,
    val rideCategory: String? = null,
    val originAddress: String? = null,
    val destinationAddress: String? = null,
    val passengerRating: Double? = null,
    val confidenceScore: Double = 1.0,
    val hasFieldConflict: Boolean = false,
    val conflictNotes: List<String> = emptyList(),
    val rawCapturedTexts: List<String> = emptyList(),
    val dedupFingerprint: String = "",
    val isSimulated: Boolean = false
) {
    val offeredPrice: Double? get() = offeredPriceField.valueOrNull()
    val pickupDistanceKm: Double? get() = pickupDistanceKmField.valueOrNull()
    val tripDistanceKm: Double? get() = tripDistanceKmField.valueOrNull()
    val totalDistanceKmExplicit: Double? get() = totalDistanceKmField.valueOrNull()
    val pickupDurationMin: Int? get() = pickupDurationMinField.valueOrNull()
    val tripDurationMin: Int? get() = tripDurationMinField.valueOrNull()
    val totalDurationMinExplicit: Int? get() = totalDurationMinField.valueOrNull()
}

/**
 * Classificação visual e de segurança da Nota do Passageiro (Verde = Boa, Amarela = Média, Vermelha = Ruim).
 */
enum class PassengerRatingVerdict(val label: String, val emoji: String, val hexColor: String) {
    GOOD("Boa", "🟢", "#16A34A"),
    MEDIUM("Média", "🟡", "#D97706"),
    BAD("Ruim", "🔴", "#DC2626"),
    UNKNOWN("Sem Nota", "⚪", "#64748B")
}

/**
 * Formato visual da janelinha flutuante quando toca corrida.
 */
enum class OverlayDisplayStyle(val title: String, val subtitle: String) {
    FULL_CARD(
        title = "Janela Moderna 3 Pilares + Balão Veredito (Recomendado)",
        subtitle = "Balão de Veredito (Aceitar/Avaliar/Recusar) + Pilares R$/km, R$/h e Nota com barras verticais coloridas"
    ),
    CLASSIC_V334(
        title = "Card Clássico v3.3.4 (Tradicional)",
        subtitle = "Formato tradicional com valor grande, pílulas emolduradas de R$/km e R$/h, nota e botão Fechar"
    ),
    SEMAFORO_COMPACT(
        title = "Janela Semáforo Compacta",
        subtitle = "Pílula horizontal com bolinha semáforo, valor, distância, tempo e métricas essenciais"
    ),
    MINI_BALL(
        title = "Só Bolinha Semáforo + R$/km (Ultra Discreto)",
        subtitle = "Pílula mínima alinhada com bolinha colorida, valor, distância e R$/km"
    );

    companion object {
        fun fromName(name: String?): OverlayDisplayStyle {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: FULL_CARD
        }
    }
}

/**
 * Configurações financeiras, de segurança e de avaliação do passageiro.
 */
data class DriverFinancialConfig(
    val driverName: String = "Motorista",
    val vehicleModel: String = "Veículo Padrão",
    val fuelConsumptionKmPerLiter: Double = 10.5,
    val fuelPricePerLiter: Double = 5.89,
    val maintenanceCostPerKm: Double = 0.25,
    val fixedExtraCostPerRide: Double = 0.0,
    val targetGrossPerKm: Double = 2.00,
    val targetGrossPerHour: Double = 40.00,
    val targetNetPerKm: Double = 1.30,
    val targetNetPerHour: Double = 28.00,
    val yellowToleranceRatio: Double = 0.80,
    val requireGrossPerKm: Boolean = true,
    val requireGrossPerHour: Boolean = true,
    val requireNetPerKm: Boolean = false,
    val requireNetPerHour: Boolean = false,
    val requirePassengerRating: Boolean = true,
    val forceRedOnBadPassengerRating: Boolean = true,
    val targetGoodPassengerRating: Double = 4.85,
    val minAcceptablePassengerRating: Double = 4.70,
    val minConfidenceThreshold: Double = 0.65,
    val overlayOpacityPercent: Int = 92,
    val overlayAutoMinimizeSec: Int = 5,
    val overlayDisplayStyle: String = OverlayDisplayStyle.FULL_CARD.name,
    val overlayBubblePosition: String = "BELOW",
    val showFloatingShortcutBubble: Boolean = true,
    val showProfitOnOverlay: Boolean = false,
    val showCostOnOverlay: Boolean = false,
    val riskAlertEnabled: Boolean = true,
    val forceRedOnRiskArea: Boolean = true,
    val useDefaultRiskKeywords: Boolean = true,
    val customRiskZones: String = "",
    val copilotMonitoringEnabled: Boolean = true,
    val saveHistoryEnabled: Boolean = true,
    val cloudBackupConsent: Boolean = false,
    val driverRecoveryCode: String = ""
) {
    val fuelCostPerKm: Double
        get() = if (fuelConsumptionKmPerLiter > 0.0) {
            fuelPricePerLiter / fuelConsumptionKmPerLiter
        } else {
            0.0
        }

    val totalVariableCostPerKm: Double
        get() = fuelCostPerKm + maintenanceCostPerKm.coerceAtLeast(0.0)
}

/**
 * Classificação do Semáforo de Decisão.
 */
enum class TrafficLightVerdict(val label: String, val shortAction: String) {
    GREEN("Aceitar", "Atende às suas metas financeiras"),
    YELLOW("Avaliar", "Atende parcialmente às metas"),
    RED("Recusar", "Abaixo das metas definidas"),
    GRAY_NEUTRAL("DADOS INSUFICIENTES", "Leitura incompleta ou sem dados suficientes")
}

/**
 * Resultado completo do Motor Financeiro + Semáforo de Decisão + Avaliação do Passageiro + Área de Risco.
 */
data class FinancialEvaluationResult(
    val offer: StandardRideOffer,
    val totalConsideredDistanceKm: Double?,
    val totalConsideredDurationMin: Int?,
    val isDistancePartial: Boolean,
    val isDurationPartial: Boolean,
    val isAnalysisComplete: Boolean,
    val grossPerKm: Double?,
    val grossPerHour: Double?,
    val estimatedFuelCost: Double?,
    val estimatedMaintenanceCost: Double?,
    val estimatedFixedCost: Double,
    val estimatedTotalCost: Double?,
    val estimatedNetProfit: Double?,
    val netProfitPerKm: Double?,
    val netProfitPerHour: Double?,
    val verdict: TrafficLightVerdict,
    val kmVerdict: TrafficLightVerdict = TrafficLightVerdict.GRAY_NEUTRAL,
    val hourVerdict: TrafficLightVerdict = TrafficLightVerdict.GRAY_NEUTRAL,
    val reasons: List<String>,
    val warnings: List<String>,
    val passengerRating: Double? = null,
    val passengerRatingVerdict: PassengerRatingVerdict = PassengerRatingVerdict.UNKNOWN,
    val passengerRatingBadge: String? = null,
    val hasRiskAlert: Boolean = false,
    val riskAlertBadge: String? = null
)
