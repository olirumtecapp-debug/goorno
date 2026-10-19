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

    // 12. TESTE EXATO DO PRINT 1 ENVIADO PELO MOTORISTA: TELA "SOLICITAÇÕES" DA 99 ("Pop Nova", "(6 min 1,3 km) Rua dos Vianas...")
    @Test
    fun `test 99 solicitacoes list card with inline parentheses duration km and wrapped street`() {
        val solicitacoesLines = listOf(
            "Solicitações",
            "Escolha uma",
            "Km até o embarque",
            "Pop Nova",
            "R$11,50 x1,5 R$3,18/km",
            "4,88 · 33 corridas · Perfil Essencial",
            "(6 min 1,3 km) Rua dos Vianas, 1545,",
            "Baeta Neves",
            "(7 min 2,3 km) Rua Américo Vieira",
            "de Souza, 72, Vila Industrial",
            "Escolher"
        )
        val offers = NinetyNineOfferReader.parseMultipleOffersFromScreen(solicitacoesLines)
        assertEquals(1, offers.size)
        val offer = offers.first()
        val eval = FinancialEngine.evaluate(offer, DriverFinancialConfig())

        assertEquals(11.50, offer.offeredPrice!!, 0.001)
        assertEquals(1.3, offer.pickupDistanceKm!!, 0.001)
        assertEquals(2.3, offer.tripDistanceKm!!, 0.001)
        assertEquals(3.6, eval.totalConsideredDistanceKm!!, 0.001)
        assertEquals(13, eval.totalConsideredDurationMin)
        assertEquals("Rua dos Vianas, 1545, Baeta Neves", offer.originAddress)
        assertEquals("Rua Américo Vieira de Souza, 72, Vila Industrial", offer.destinationAddress)
    }

    // 13. TESTE EXATO DO PRINT 2 ENVIADO PELO MOTORISTA: TELA DE MAPA/CHAMADA COM METROS ("3 min (459 m)" + "9 min (3 km)")
    @Test
    fun `test 99 map detail and ringing screen with meters 459m plus 3km`() {
        val mapDetailLines = listOf(
            "R$11,50 ⚡1,5x",
            "R$3,37/km",
            "Alta demanda no destino",
            "R$1,16 por deslocamento incl.",
            "4,89 · 925 corridas",
            "Perfil Premium",
            "3 min (459 m)",
            "Rua Itapiruna, 193, Jardim Petroni",
            "9 min (3 km)",
            "Rua Estr. dos Vianas, 321 , Sítio dos",
            "Vianas"
        )
        val offers = NinetyNineOfferReader.parseMultipleOffersFromScreen(mapDetailLines)
        assertEquals(1, offers.size)
        val offer = offers.first()
        val eval = FinancialEngine.evaluate(offer, DriverFinancialConfig())

        assertEquals(11.50, offer.offeredPrice!!, 0.001)
        assertEquals(0.459, offer.pickupDistanceKm!!, 0.001)
        assertEquals(3.0, offer.tripDistanceKm!!, 0.001)
        assertEquals(3.459, eval.totalConsideredDistanceKm!!, 0.001)
        assertEquals(12, eval.totalConsideredDurationMin)
        assertEquals("Rua Itapiruna, 193, Jardim Petroni", offer.originAddress)
        assertEquals("Rua Estr. dos Vianas, 321 , Sítio dos Vianas", offer.destinationAddress)
    }

    // 14. TESTE EXATO DO PRINT REAL DO "RADAR DE VIAGENS" DA UBER ENVIADO PELO MOTORISTA
    @Test
    fun `test uber radar de viagens screenshot with map noise surge included and dot decimals`() {
        val uberRadarNodes = listOf(
            "R$ 6,25",
            "5 min",
            "Radar de viagens",
            "UberX",
            "R$ 15,84",
            "R$1,44/km aprox.",
            "★ 4,97 (595)",
            "Verificado",
            "+R$ 1,00 incluído",
            "8 min (3.2 km)",
            "Av. Armando Ítalo Setti, Baeta",
            "Neves, São Bernardo do Campo",
            "20 minutos (7.8 km)",
            "Travessa Salvador, 40, Canhema,",
            "Diadema",
            "Selecionar"
        )

        val offers = UberOfferReader.parseMultipleOffersFromScreen(uberRadarNodes)
        assertEquals(1, offers.size)

        val offer = offers.first()
        val eval = FinancialEngine.evaluate(offer, DriverFinancialConfig())

        assertEquals(15.84, offer.offeredPrice!!, 0.001)
        assertEquals(3.2, offer.pickupDistanceKm!!, 0.001)
        assertEquals(7.8, offer.tripDistanceKm!!, 0.001)
        assertEquals(11.0, eval.totalConsideredDistanceKm!!, 0.001)
        assertEquals(28, eval.totalConsideredDurationMin)
        assertEquals(1.44, eval.grossPerKm!!, 0.01)
        assertEquals(4.97, offer.passengerRating!!, 0.001)
        assertEquals(PassengerRatingVerdict.GOOD, eval.passengerRatingVerdict)
        assertEquals("Av. Armando Ítalo Setti, Baeta Neves, São Bernardo do Campo", offer.originAddress)
        assertEquals("Travessa Salvador, 40, Canhema, Diadema", offer.destinationAddress)
        assertNotEquals(TrafficLightVerdict.GRAY_NEUTRAL, eval.verdict)
    }

    // 15. TESTE DO PRINT 1 ENVIADO PELO MOTORISTA: R$ 7,70 COM NAVEGAÇÃO "61 m" NO TOPO E CORRIDA CURTA
    @Test
    fun `test user screenshot 1 with top navigation arrow 61m noise and short ride R7_70`() {
        val screenNodes = listOf(
            "61 m",
            "Av. Armando Ítalo Setti",
            "Não afeta a TA",
            "Pgto. no app",
            "Prioritário",
            "R$7,70 ⚡1,1x",
            "R$2,14/km",
            "R$1,41 Tarifa base dinâmica incl.",
            "★ 4,96 · 287 corridas",
            "Perfil Premium",
            "5 min (1,2 km)",
            "Rua Miguel Rubino, 139, Vila Vianas",
            "10 min (2,4 km)",
            "AmorSaúde são Bernardo, Rua Santa Filomena, 856 - Centro"
        )

        val offers = NinetyNineOfferReader.parseMultipleOffersFromScreen(screenNodes)
        assertEquals(1, offers.size)

        val offer = offers.first()
        val config = DriverFinancialConfig(
            targetGrossPerKm = 2.00,
            targetGrossPerHour = 40.00,
            requireGrossPerKm = true,
            requireGrossPerHour = false
        )
        val eval = FinancialEngine.evaluate(offer, config)

        // Deve ignorar completamente os 61m da barra de navegação!
        assertEquals(7.70, offer.offeredPrice!!, 0.001)
        assertEquals(1.2, offer.pickupDistanceKm!!, 0.001)
        assertEquals(2.4, offer.tripDistanceKm!!, 0.001)
        assertEquals(3.6, eval.totalConsideredDistanceKm!!, 0.001)
        assertEquals(15, eval.totalConsideredDurationMin)
        assertEquals(2.14, eval.grossPerKm!!, 0.01)
        assertEquals("Rua Miguel Rubino, 139, Vila Vianas", offer.originAddress)
        assertTrue(offer.destinationAddress!!.startsWith("AmorSaúde são Bernardo"))
        // Como paga R$ 2,14/km (acima da meta de R$ 2,00), deve classificar como Aceitar!
        assertEquals(TrafficLightVerdict.GREEN, eval.verdict)
    }

    // 16. TESTE DO PRINT 2 ENVIADO PELO MOTORISTA: R$ 9,60 COM NOTA 4,69 QUE NÃO DEVE FORÇAR RECUSAR
    @Test
    fun `test user screenshot 2 with R9_60 passenger rating 4_69 does not reject good financial offer`() {
        val screenNodes = listOf(
            "Pgto. no app",
            "R$9,60 ⚡1,1x",
            "R$2,72/km",
            "R$1,97 por embarque distante in...",
            "★ 4,69 · 67 corridas",
            "Perfil Essencial",
            "9 min (2,4 km)",
            "Tv. Púrpura, 18, Jardim Las Vegas",
            "3 min (1,1 km)",
            "EMEB Professora Maria Therezinha Besana, Rua dos Vianas, 2897 - ..."
        )

        val offers = NinetyNineOfferReader.parseMultipleOffersFromScreen(screenNodes)
        assertEquals(1, offers.size)

        val offer = offers.first()
        val config = DriverFinancialConfig(
            targetGrossPerKm = 2.00,
            targetGrossPerHour = 40.00,
            minAcceptablePassengerRating = 4.70,
            requireGrossPerKm = true,
            requireGrossPerHour = false,
            forceRedOnBadPassengerRating = false
        )
        val eval = FinancialEngine.evaluate(offer, config)

        assertEquals(9.60, offer.offeredPrice!!, 0.001)
        assertEquals(2.4, offer.pickupDistanceKm!!, 0.001)
        assertEquals(1.1, offer.tripDistanceKm!!, 0.001)
        assertEquals(3.5, eval.totalConsideredDistanceKm!!, 0.001)
        assertEquals(12, eval.totalConsideredDurationMin)
        assertEquals(2.74, eval.grossPerKm!!, 0.01)
        assertEquals(4.69, offer.passengerRating!!, 0.001)
        assertEquals("Tv. Púrpura, 18, Jardim Las Vegas", offer.originAddress)
        assertTrue(offer.destinationAddress!!.startsWith("EMEB Professora Maria Therezinha Besana"))

        // O semáforo financeiro é bom (R$ 2,74/km). Com a nota do passageiro em 4,69, deve ser Avaliar (Amarelo) e NUNCA Recusar!
        assertEquals(TrafficLightVerdict.YELLOW, eval.verdict)
        assertEquals(PassengerRatingVerdict.BAD, eval.passengerRatingVerdict)
    }

    // 17. TESTE DO PRINT 3: LIMPEZA DE SUJEIRA DE BOTÕES E TEXTOS ALEATÓRIOS DE NOTIFICAÇÃO
    @Test
    fun `test user screenshot 3 address cleaning discards action buttons and system garbage`() {
        val dirtyOrigin = "Zanela, 90, Centro Escolher R$9,60 1k"
        val dirtyDest = "s.E os empos das cardes s af. F os empos dos cardes t R$9,60"
        val systemNoise = "silencioso está ativado"

        val cleanedOrigin = BrazilianOfferParser.cleanAddressCandidate(dirtyOrigin)
        val cleanedDest = BrazilianOfferParser.cleanAddressCandidate(dirtyDest)
        val cleanedNoise = BrazilianOfferParser.cleanAddressCandidate(systemNoise)

        // Origem deve ser limpa preservando apenas o endereço real
        assertEquals("Zanela, 90, Centro", cleanedOrigin)
        // Destino com sujeira de cardes deve ser descartado
        assertNull(cleanedDest)
        // Linha de notificação de sistema deve ser descartada
        assertNull(cleanedNoise)
    }

    // 18. TESTE DO PRINT 1 DO USUÁRIO: BLOQUEIO DE PROPAGANDA DE COMBUSTÍVEL / 99 ABASTECE
    @Test
    fun `test fuel station ad 99 abastece is strictly ignored as non-offer`() {
        val fuelAdNodes = listOf(
            "99 Abastece",
            "Mais >",
            "Auto Posto Malu dos Vianas",
            "R$3,39",
            "R$3,59",
            "R. dos Vianas, 3397 - Baeta ...",
            "1.3km",
            "Buscando"
        )

        // Verifica função central de detecção de anúncio
        assertTrue(BrazilianOfferParser.isNonRideAdOrService(fuelAdNodes))

        // Verifica que o leitor da 99 descarta o anúncio como NON_OFFER
        val singleResult = NinetyNineOfferReader.parseScreenNodes(fuelAdNodes)
        assertEquals(ScreenClassification.NON_OFFER, singleResult.screenClassification)

        val multipleOffers = NinetyNineOfferReader.parseMultipleOffersFromScreen(fuelAdNodes)
        assertTrue(multipleOffers.isEmpty())
    }

    // 19. TESTE DO PRINT 2 DO USUÁRIO: VEREDITOS INDIVIDUAIS DE KM E HORA
    @Test
    fun `test user screenshot 2 individual km and hour verdicts`() {
        val popRideNodes = listOf(
            "Pop Nova",
            "R$9,48",
            "R$2,46/km",
            "4,85 · 470 corridas · Perfil Premium",
            "(5 min 1 km) Rua dos Vianas, 241, Baeta Neves",
            "(10 min 2,9 km) Rua Diretora Maria Cleuza, 87, Jardim Nascimento",
            "Escolher"
        )

        val offers = NinetyNineOfferReader.parseMultipleOffersFromScreen(popRideNodes)
        assertEquals(1, offers.size)

        val offer = offers.first()
        val config = DriverFinancialConfig(
            targetGrossPerKm = 2.00,
            targetGrossPerHour = 40.00,
            yellowToleranceRatio = 0.80, // amarelo entre 32.00 e 39.99/h
            requireGrossPerKm = true,
            requireGrossPerHour = true
        )
        val eval = FinancialEngine.evaluate(offer, config)

        // 3.9 km e 15 min -> R$ 2,43/km e R$ 37,92/h
        assertEquals(9.48, offer.offeredPrice!!, 0.001)
        assertEquals(3.9, eval.totalConsideredDistanceKm!!, 0.001)
        assertEquals(15, eval.totalConsideredDurationMin)
        assertEquals(2.43, eval.grossPerKm!!, 0.01)
        assertEquals(37.92, eval.grossPerHour!!, 0.01)

        // R$/km (2,43 >= 2,00) -> VERDE
        assertEquals(TrafficLightVerdict.GREEN, eval.kmVerdict)

        // R$/h (37,92 >= 32,00 e < 40,00) -> AMARELO
        assertEquals(TrafficLightVerdict.YELLOW, eval.hourVerdict)

        // Veredito geral com meta combinada -> AVALIAR (AMARELO)
        assertEquals(TrafficLightVerdict.YELLOW, eval.verdict)
    }

    // 25. TESTE DO CASO REAL DA 99: PREÇO TOTAL R$10,80 COM "R$3,06 Tarifa base dinâmica incl." E R$2,18/km
    @Test
    fun `test 99 real screenshot with dynamic fare ignores 3_06 subfare and captures 10_80 main fare`() {
        val real99DynamicNodes = listOf(
            "Pgto. no app",
            "R$10,80",
            "R$3,06 Tarifa base dinâmica incl.",
            "R$2,18/km",
            "5,0 ★ • 49 corridas",
            "7 min (1,6 km)",
            "Rua Barão de Ladário, 770 - Brás",
            "5 min (1,6 km)",
            "Rua da Cantareira, 306 - Centro",
            "99Pop",
            "Recusar",
            "Aceitar"
        )

        val offers = NinetyNineOfferReader.parseMultipleOffersFromScreen(real99DynamicNodes)
        assertEquals(1, offers.size)

        val offer = offers.first()
        val eval = FinancialEngine.evaluate(offer, DriverFinancialConfig())

        // Preço principal capturado DEVE SER R$ 10,80 e NUNCA a sub-tarifa R$ 3,06!
        assertEquals(10.80, offer.offeredPrice!!, 0.001)
        assertEquals(1.6, offer.pickupDistanceKm!!, 0.001)
        assertEquals(1.6, offer.tripDistanceKm!!, 0.001)
        assertEquals(3.2, eval.totalConsideredDistanceKm!!, 0.001)
        assertEquals(12, eval.totalConsideredDurationMin)
        assertEquals(3.375, eval.grossPerKm!!, 0.01)
        assertEquals("Rua Barão de Ladário, 770 - Brás", offer.originAddress)
        assertEquals("Rua da Cantareira, 306 - Centro", offer.destinationAddress)
    }
}



