package com.creativeam.goorno

import com.creativeam.goorno.engine.FinancialEngine
import com.creativeam.goorno.model.*
import com.creativeam.goorno.reader.BrazilianOfferParser
import com.creativeam.goorno.reader.ninetynine.NinetyNineOfferReader
import com.creativeam.goorno.reader.uber.UberOfferReader
import org.junit.Assert.*
import org.junit.Test

/**
 * Suíte completa de Testes Automatizados JUnit 4 exigida nas Seções 5.4 e 10.
 */
class GoOrNoEngineAndParserTest {

    // 1. EXEMPLO DE REFERÊNCIA OBRIGATÓRIO DA SEÇÃO 5.4:
    // Oferta: R$ 18,00 | Distância: 8 km | Duração: 24 minutos
    // Esperado: Ganho bruto/km = R$ 2,25/km | Ganho bruto/hora = R$ 45,00/h
    @Test
    fun `test section 5_4 reference example 18 reais 8 km 24 min`() {
        val offer = StandardRideOffer(
            offerId = "ref-5-4",
            sourceApp = SourceApp.UBER_DRIVER,
            captureMethod = CaptureMethod.ACCESSIBILITY,
            screenClassification = ScreenClassification.NEW_OFFER,
            timestampMillis = 1000L,
            offeredPriceField = ExtractedField.Present(18.00),
            totalDistanceKmField = ExtractedField.Present(8.0),
            totalDurationMinField = ExtractedField.Present(24),
            confidenceScore = 0.95
        )

        val config = DriverFinancialConfig(
            fuelConsumptionKmPerLiter = 10.0,
            fuelPricePerLiter = 6.00, // R$ 0,60/km de combustível
            maintenanceCostPerKm = 0.25, // R$ 0,25/km de manutenção -> R$ 0,85/km total
            fixedExtraCostPerRide = 0.0,
            targetGrossPerKm = 2.00,
            targetGrossPerHour = 40.00
        )

        val result = FinancialEngine.evaluate(offer, config)

        assertEquals(2.25, result.grossPerKm!!, 0.001)
        assertEquals(45.00, result.grossPerHour!!, 0.001)
        // Custo esperado para 8 km a R$ 0,85/km = R$ 6,80; Lucro líquido = 18,00 - 6,80 = R$ 11,20
        assertEquals(6.80, result.estimatedTotalCost!!, 0.001)
        assertEquals(11.20, result.estimatedNetProfit!!, 0.001)
        assertEquals(TrafficLightVerdict.GREEN, result.verdict)
    }

    // 2. CONVERSÃO DE MOEDA BRASILEIRA
    @Test
    fun `test brazilian currency parsing`() {
        assertEquals(12.50, BrazilianOfferParser.parseCurrencyBr("R$ 12,50").valueOrNull()!!, 0.001)
        assertEquals(1250.00, BrazilianOfferParser.parseCurrencyBr("R$ 1.250,00").valueOrNull()!!, 0.001)
        assertEquals(18.00, BrazilianOfferParser.parseCurrencyBr("R$18,00").valueOrNull()!!, 0.001)
        assertTrue(BrazilianOfferParser.parseCurrencyBr("Texto sem valor").isMissing)
    }

    // 3. INTERPRETAÇÃO DE QUILÔMETROS E MINUTOS
    @Test
    fun `test distance km and duration minutes parsing`() {
        assertEquals(3.8, BrazilianOfferParser.parseDistanceKm("3,8 km até o local").valueOrNull()!!, 0.001)
        assertEquals(12.0, BrazilianOfferParser.parseDistanceKm("Viagem de 12 km").valueOrNull()!!, 0.001)
        assertEquals(14, BrazilianOfferParser.parseDurationMinutes("14 min").valueOrNull())
        assertEquals(75, BrazilianOfferParser.parseDurationMinutes("1 h 15 min").valueOrNull())
    }

    // 4. SOMA DE EMBARQUE + VIAGEM SEM DUPLICAR E EXTRAÇÃO DE ORIGEM/DESTINO
    @Test
    fun `test uber reader pickup plus trip distance and street view addresses`() {
        val nodes = listOf(
            "UberX",
            "R$ 25,00",
            "2,0 km",
            "8,0 km",
            "5 min",
            "15 min",
            "Origem: Av. Paulista, 1000 - São Paulo",
            "Destino: Rua Augusta, 500 - São Paulo",
            "Aceitar"
        )
        val offer = UberOfferReader.parseScreenNodes(nodes)
        val result = FinancialEngine.evaluate(offer, DriverFinancialConfig())

        assertEquals(10.0, result.totalConsideredDistanceKm!!, 0.001)
        assertEquals(20, result.totalConsideredDurationMin)
        assertEquals(2.50, result.grossPerKm!!, 0.001)
        assertEquals(75.00, result.grossPerHour!!, 0.001)
        assertEquals("Av. Paulista, 1000 - São Paulo", offer.originAddress)
        assertEquals("Rua Augusta, 500 - São Paulo", offer.destinationAddress)
    }

    // 5. CLASSIFICAÇÃO POR SEMÁFORO (VERDE, AMARELO, VERMELHO)
    @Test
    fun `test traffic light green yellow and red classifications`() {
        val config = DriverFinancialConfig(
            targetGrossPerKm = 2.00,
            targetGrossPerHour = 40.00,
            yellowToleranceRatio = 0.80, // Amarelo até R$ 1,60/km e R$ 32,00/h
            requireGrossPerKm = true,
            requireGrossPerHour = true
        )

        // Amarelo: R$ 17,00 para 10 km (R$ 1,70/km) em 20 min (R$ 51,00/h)
        val yellowOffer = StandardRideOffer(
            offerId = "y1",
            sourceApp = SourceApp.NINETY_NINE_DRIVER,
            captureMethod = CaptureMethod.ACCESSIBILITY,
            screenClassification = ScreenClassification.NEW_OFFER,
            timestampMillis = 1000L,
            offeredPriceField = ExtractedField.Present(17.00),
            totalDistanceKmField = ExtractedField.Present(10.0),
            totalDurationMinField = ExtractedField.Present(20),
            confidenceScore = 0.95
        )
        assertEquals(TrafficLightVerdict.YELLOW, FinancialEngine.evaluate(yellowOffer, config).verdict)

        // Vermelho: R$ 12,00 para 10 km (R$ 1,20/km) em 30 min (R$ 24,00/h)
        val redOffer = yellowOffer.copy(
            offerId = "r1",
            offeredPriceField = ExtractedField.Present(12.00),
            totalDurationMinField = ExtractedField.Present(30)
        )
        assertEquals(TrafficLightVerdict.RED, FinancialEngine.evaluate(redOffer, config).verdict)
    }

    // 6. CAMPOS AUSENTES E VALORES INVÁLIDOS (DIVISÃO POR ZERO)
    @Test
    fun `test missing fields and zero division protection return gray neutral`() {
        val config = DriverFinancialConfig()

        val missingPriceOffer = StandardRideOffer(
            offerId = "m1",
            sourceApp = SourceApp.UBER_DRIVER,
            captureMethod = CaptureMethod.ACCESSIBILITY,
            screenClassification = ScreenClassification.NEW_OFFER,
            timestampMillis = 1000L,
            offeredPriceField = ExtractedField.Missing,
            totalDistanceKmField = ExtractedField.Present(5.0),
            totalDurationMinField = ExtractedField.Present(12)
        )
        assertEquals(TrafficLightVerdict.GRAY_NEUTRAL, FinancialEngine.evaluate(missingPriceOffer, config).verdict)

        val zeroDistanceOffer = missingPriceOffer.copy(
            offerId = "z1",
            offeredPriceField = ExtractedField.Present(15.0),
            totalDistanceKmField = ExtractedField.Present(0.0),
            totalDurationMinField = ExtractedField.Present(0)
        )
        assertEquals(TrafficLightVerdict.GRAY_NEUTRAL, FinancialEngine.evaluate(zeroDistanceOffer, config).verdict)
    }

    // 7. CONFIANÇA INSUFICIENTE E DISTÂNCIAS INCONSISTENTES
    @Test
    fun `test low confidence and inconsistent distance duration return gray neutral`() {
        val config = DriverFinancialConfig(minConfidenceThreshold = 0.70)

        val lowConfidenceOffer = StandardRideOffer(
            offerId = "lc1",
            sourceApp = SourceApp.NINETY_NINE_DRIVER,
            captureMethod = CaptureMethod.OCR,
            screenClassification = ScreenClassification.NEW_OFFER,
            timestampMillis = 1000L,
            offeredPriceField = ExtractedField.Present(22.0),
            totalDistanceKmField = ExtractedField.Present(8.0),
            totalDurationMinField = ExtractedField.Present(18),
            confidenceScore = 0.45
        )
        assertEquals(TrafficLightVerdict.GRAY_NEUTRAL, FinancialEngine.evaluate(lowConfidenceOffer, config).verdict)

        // Distância absurda (95 km em 4 min) detectada pelo leitor
        val inconsistentNodes = listOf("99Pop", "R$ 30,00", "95,0 km", "4 min", "Aceitar")
        val inconsistentOffer = NinetyNineOfferReader.parseScreenNodes(inconsistentNodes)
        assertTrue(inconsistentOffer.hasFieldConflict)
        assertEquals(TrafficLightVerdict.GRAY_NEUTRAL, FinancialEngine.evaluate(inconsistentOffer, config).verdict)
    }

    // 8. LEITURAS DUPLICADAS E ATUALIZAÇÃO DE OFERTA
    @Test
    fun `test duplicate offer detection and dynamic price update`() {
        val nodes1 = listOf("UberX", "R$ 20,00", "2,0 km", "6,0 km", "5 min", "15 min", "Aceitar")
        val offer1 = UberOfferReader.parseScreenNodes(nodes1, timestampMillis = 10_000L)
        val offer1Duplicate = UberOfferReader.parseScreenNodes(nodes1, timestampMillis = 12_000L)

        assertEquals(
            BrazilianOfferParser.DuplicateCheckResult.EXACT_DUPLICATE,
            BrazilianOfferParser.isSameOrUpdatedOffer(offer1, offer1Duplicate)
        )

        // Mesma distância (8 km), mas preço subiu para R$ 24,50 na mesma janela
        val nodesUpdated = listOf("UberX", "R$ 24,50", "2,0 km", "6,0 km", "5 min", "15 min", "Aceitar")
        val offerUpdated = UberOfferReader.parseScreenNodes(nodesUpdated, timestampMillis = 14_000L)
        assertEquals(
            BrazilianOfferParser.DuplicateCheckResult.UPDATED_OFFER,
            BrazilianOfferParser.isSameOrUpdatedOffer(offer1, offerUpdated)
        )
    }
}
