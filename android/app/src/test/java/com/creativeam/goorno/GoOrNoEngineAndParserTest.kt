package com.creativeam.goorno

import com.creativeam.goorno.engine.FinancialEngine
import com.creativeam.goorno.model.*
import com.creativeam.goorno.reader.BrazilianOfferParser
import com.creativeam.goorno.reader.ninetynine.NinetyNineOfferReader
import com.creativeam.goorno.reader.uber.UberOfferReader
import org.junit.Assert.*
import org.junit.Test

/**
 * Suíte completa de Testes Automatizados JUnit 4 exigida nas Seções 5.4 e 10,
 * incluindo o teste exato do print real da 99 Motorista e leitura de múltiplas viagens simultâneas.
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
            fuelPricePerLiter = 6.00,
            maintenanceCostPerKm = 0.25,
            fixedExtraCostPerRide = 0.0,
            targetGrossPerKm = 2.00,
            targetGrossPerHour = 40.00
        )

        val result = FinancialEngine.evaluate(offer, config)

        assertEquals(2.25, result.grossPerKm!!, 0.001)
        assertEquals(45.00, result.grossPerHour!!, 0.001)
        assertEquals(6.80, result.estimatedTotalCost!!, 0.001)
        assertEquals(11.20, result.estimatedNetProfit!!, 0.001)
        assertEquals(TrafficLightVerdict.GREEN, result.verdict)
    }

    // 2. TESTE EXATO DO PRINT REAL DA 99 MOTORISTA ENVIADO PELO USUÁRIO
    @Test
    fun `test real 99 screenshot format with R8_10 R2_18_per_km and displacement bonus`() {
        val real99ScreenNodes = listOf(
            "Pgto. no app",
            "R$8,10 ⚡1,1x R$2,18/km",
            "R$1,16 por deslocamento incl.",
            "4,82 · 211 corridas",
            "Perfil Essencial",
            "5 min (1,2 km)",
            "Rua Antônio Zanetti, 84, Jardim Las Vegas",
            "6 min (2,5 km)",
            "Rua Solimões, 534, Vila Cecilia Maria"
        )

        val offers = NinetyNineOfferReader.parseMultipleOffersFromScreen(real99ScreenNodes)
        assertEquals(1, offers.size)

        val offer = offers.first()
        val eval = FinancialEngine.evaluate(offer, DriverFinancialConfig())

        assertEquals(8.10, offer.offeredPrice!!, 0.001)
        assertEquals(1.2, offer.pickupDistanceKm!!, 0.001)
        assertEquals(2.5, offer.tripDistanceKm!!, 0.001)
        assertEquals(3.7, eval.totalConsideredDistanceKm!!, 0.001)
        assertEquals(11, eval.totalConsideredDurationMin)
        assertEquals(2.19, eval.grossPerKm!!, 0.01)
        assertEquals("Rua Antônio Zanetti, 84, Jardim Las Vegas", offer.originAddress)
        assertEquals("Rua Solimões, 534, Vila Cecilia Maria", offer.destinationAddress)
    }

    // 3. TESTE DE MÚLTIPLAS VIAGENS SIMULTÂNEAS NA TELA DA 99 (LISTA DE OFERTAS)
    @Test
    fun `test multiple simultaneous offers on 99 screen`() {
        val multiOfferNodes = listOf(
            "Pgto. no app",
            "R$8,10 ⚡1,1x",
            "R$2,18/km",
            "R$1,16 por deslocamento incl.",
            "5 min (1,2 km)",
            "Rua Antônio Zanetti, 84, Jardim Las Vegas",
            "6 min (2,5 km)",
            "Rua Solimões, 534, Vila Cecilia Maria",
            "Pgto. no app",
            "R$15,40",
            "R$1,54/km",
            "4 min (1,5 km)",
            "Av. Goiás, 1200, São Caetano do Sul",
            "16 min (8,5 km)",
            "Terminal Sacomã, São Paulo"
        )

        val offers = NinetyNineOfferReader.parseMultipleOffersFromScreen(multiOfferNodes)
        assertEquals(2, offers.size)

        assertEquals(8.10, offers[0].offeredPrice!!, 0.001)
        assertEquals("Rua Antônio Zanetti, 84, Jardim Las Vegas", offers[0].originAddress)
        assertEquals("Rua Solimões, 534, Vila Cecilia Maria", offers[0].destinationAddress)

        assertEquals(15.40, offers[1].offeredPrice!!, 0.001)
        assertEquals("Av. Goiás, 1200, São Caetano do Sul", offers[1].originAddress)
        assertEquals("Terminal Sacomã, São Paulo", offers[1].destinationAddress)
    }

    // 4. CONVERSÃO DE MOEDA BRASILEIRA
    @Test
    fun `test brazilian currency parsing`() {
        assertEquals(12.50, BrazilianOfferParser.parseCurrencyBr("R$ 12,50").valueOrNull()!!, 0.001)
        assertEquals(1250.00, BrazilianOfferParser.parseCurrencyBr("R$ 1.250,00").valueOrNull()!!, 0.001)
        assertEquals(18.00, BrazilianOfferParser.parseCurrencyBr("R$18,00").valueOrNull()!!, 0.001)
        assertTrue(BrazilianOfferParser.parseCurrencyBr("Texto sem valor").isMissing)
    }

    // 5. INTERPRETAÇÃO DE QUILÔMETROS E MINUTOS
    @Test
    fun `test distance km and duration minutes parsing`() {
        assertEquals(3.8, BrazilianOfferParser.parseDistanceKm("3,8 km até o local").valueOrNull()!!, 0.001)
        assertEquals(12.0, BrazilianOfferParser.parseDistanceKm("Viagem de 12 km").valueOrNull()!!, 0.001)
        assertEquals(14, BrazilianOfferParser.parseDurationMinutes("14 min").valueOrNull())
        assertEquals(75, BrazilianOfferParser.parseDurationMinutes("1 h 15 min").valueOrNull())
    }

    // 6. SOMA DE EMBARQUE + VIAGEM SEM DUPLICAR E EXTRAÇÃO DE ORIGEM/DESTINO
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

    // 7. CLASSIFICAÇÃO POR SEMÁFORO (VERDE, AMARELO, VERMELHO)
    @Test
    fun `test traffic light green yellow and red classifications`() {
        val config = DriverFinancialConfig(
            targetGrossPerKm = 2.00,
            targetGrossPerHour = 40.00,
            yellowToleranceRatio = 0.80,
            requireGrossPerKm = true,
            requireGrossPerHour = true
        )

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

        val redOffer = yellowOffer.copy(
            offerId = "r1",
            offeredPriceField = ExtractedField.Present(12.00),
            totalDurationMinField = ExtractedField.Present(30)
        )
        assertEquals(TrafficLightVerdict.RED, FinancialEngine.evaluate(redOffer, config).verdict)
    }

    // 8. CAMPOS AUSENTES E VALORES INVÁLIDOS (DIVISÃO POR ZERO)
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

    // 9. CONFIANÇA INSUFICIENTE E DISTÂNCIAS INCONSISTENTES
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

        val inconsistentNodes = listOf("99Pop", "R$ 30,00", "95,0 km", "4 min", "Aceitar")
        val inconsistentOffer = NinetyNineOfferReader.parseScreenNodes(inconsistentNodes)
        assertTrue(inconsistentOffer.hasFieldConflict)
        assertEquals(TrafficLightVerdict.GRAY_NEUTRAL, FinancialEngine.evaluate(inconsistentOffer, config).verdict)
    }

    // 10. LEITURAS DUPLICADAS E ATUALIZAÇÃO DE OFERTA
    @Test
    fun `test duplicate offer detection and dynamic price update`() {
        val nodes1 = listOf("UberX", "R$ 20,00", "2,0 km", "6,0 km", "5 min", "15 min", "Aceitar")
        val offer1 = UberOfferReader.parseScreenNodes(nodes1, timestampMillis = 10_000L)
        val offer1Duplicate = UberOfferReader.parseScreenNodes(nodes1, timestampMillis = 11_000L)

        assertEquals(
            BrazilianOfferParser.DuplicateCheckResult.EXACT_DUPLICATE,
            BrazilianOfferParser.isSameOrUpdatedOffer(offer1, offer1Duplicate)
        )

        val nodesUpdated = listOf("UberX", "R$ 24,50", "2,0 km", "6,0 km", "5 min", "15 min", "Aceitar")
        val offerUpdated = UberOfferReader.parseScreenNodes(nodesUpdated, timestampMillis = 13_000L)
        assertEquals(
            BrazilianOfferParser.DuplicateCheckResult.UPDATED_OFFER,
            BrazilianOfferParser.isSameOrUpdatedOffer(offer1, offerUpdated)
        )
    }

    // 11. TESTE DE NÓS FRAGMENTADOS DA ÁRVORE DE ACESSIBILIDADE DA 99 ("R$" separado de "8,10")
    @Test
    fun `test fragmented 99 accessibility nodes with standalone R_dollar and split km min`() {
        val fragmentedNodes = listOf(
            "Pgto. no app",
            "R$",
            "8,10",
            "⚡1,1x",
            "R$2,18/km",
            "R$1,16 por deslocamento incl.",
            "5",
            "min",
            "(1,2",
            "km)",
            "Rua Antônio Zanetti, 84, Jardim Las Vegas",
            "6",
            "min",
            "(2,5",
            "km)",
            "Rua Solimões, 534, Vila Cecilia Maria"
        )
        val normalized = BrazilianOfferParser.normalizeFragmentedNodes(fragmentedNodes)
        val offers = NinetyNineOfferReader.parseMultipleOffersFromScreen(normalized)
        assertEquals(1, offers.size)
        val offer = offers.first()
        assertEquals(8.10, offer.offeredPrice!!, 0.001)
        assertEquals(1.2, offer.pickupDistanceKm!!, 0.001)
        assertEquals(2.5, offer.tripDistanceKm!!, 0.001)
        assertEquals(5, offer.pickupDurationMin)
        assertEquals(6, offer.tripDurationMin)
        assertEquals("Rua Antônio Zanetti, 84, Jardim Las Vegas", offer.originAddress)
        assertEquals("Rua Solimões, 534, Vila Cecilia Maria", offer.destinationAddress)
    }
}

