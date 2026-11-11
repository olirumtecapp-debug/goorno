package com.creativeam.goorno

import com.creativeam.goorno.detector.RideScreenContext
import com.creativeam.goorno.detector.RideScreenContextClassifier
import com.creativeam.goorno.model.SourceApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideScreenContextClassifierTest {

    @Test
    fun test99IncomingCallOnMap_Photo1() {
        // Foto 1: Chamada tocando no mapa da 99
        val lines = listOf(
            "Pgto. no app",
            "R$14,40",
            "R$1,99/km",
            "R$2,26 Tarifa base dinâmica incl.",
            "4,84 • 202 corridas",
            "Perfil Premium",
            "6 min (1,2 km)",
            "Rua Fiorentino Felipe, 630, Baeta Neves",
            "16 min (6,1 km)",
            "Rua Araldo Armani, 356, Vila Sao Silverio"
        )
        val context = RideScreenContextClassifier.classify(SourceApp.NINETY_NINE_DRIVER, lines)
        assertEquals(RideScreenContext.NINETYNINE_INCOMING_SINGLE_OFFER, context)
        assertTrue("Chamada direta no mapa da 99 DEVE ser analisada", context.shouldAnalyze)
    }

    @Test
    fun test99RequestsList_Photo2() {
        // Foto 2: Lista de solicitações da 99
        val lines = listOf(
            "< Solicitações",
            "Escolha uma",
            "Km até o embarque",
            "Pop",
            "R$14,40",
            "R$1,99/km",
            "4,84 • 202 corridas",
            "Perfil Premium",
            "(6 min 1,2 km) Rua Fiorentino Felipe, 630, Baeta Neves",
            "(16 min 6,1 km) Rua Araldo Armani, 356, Vila Sao Silverio",
            "Escolher",
            "Pop",
            "R$9,70",
            "R$1,88/km",
            "4,89 • 407 corridas",
            "Perfil Premium",
            "(9 min 1,8 km) Rua dos Pioneiros, 107, Montanhão",
            "(10 min 3,4 km) Atacadão - São Bernardo Centro",
            "Escolher"
        )
        val context = RideScreenContextClassifier.classify(SourceApp.NINETY_NINE_DRIVER, lines)
        assertEquals(RideScreenContext.NINETYNINE_REQUEST_LIST, context)
        assertFalse("Lista de solicitações da 99 NUNCA deve ser analisada", context.shouldAnalyze)
    }

    @Test
    fun test99MapDetailViaMapinha_Photo3() {
        // Foto 3: Detalhe aberto no mapa após clicar no mapinha da lista da 99
        val lines = listOf(
            "<",
            "BAETA NEVES",
            "BOTUJURU",
            "GOLDEN PARK",
            "Pop Nova",
            "R$15,30",
            "x1,5",
            "R$2,41/km",
            "4,91 • 91 corridas • Cartão verif.",
            "(5 min 742 m) Rua Colombos, 39, Vila Industrial",
            "(13 min 5,6 km) Rua Izabel Cestari, 110 , Bairro dos Casa",
            "Escolher"
        )
        val context = RideScreenContextClassifier.classify(SourceApp.NINETY_NINE_DRIVER, lines)
        assertEquals(RideScreenContext.NINETYNINE_SELECTED_MAP_OFFER, context)
        assertTrue("Visualização individual do mapinha da 99 DEVE ser analisada", context.shouldAnalyze)
    }

    @Test
    fun testUberIncomingDirectCall_Photo4() {
        // Foto 4: Chamada direta no mapa da Uber
        val lines = listOf(
            "Priority",
            "Exclusivo",
            "R$ 20,58",
            "Est. de R$2,67/km",
            "5,00 (36)",
            "Verificado",
            "+R$ 3,75 incluído",
            "+R$ 2,60 incluída para retirada",
            "6 min (1.4 km)",
            "R. 18 de Abril, Alto Industrial, São Bernardo do Campo",
            "1 parada",
            "25 minutos (6.3 km)",
            "R. Mal. Deodoro, 1500, Centro, São Bernardo do Campo",
            "Aceitar"
        )
        val context = RideScreenContextClassifier.classify(SourceApp.UBER_DRIVER, lines)
        assertEquals(RideScreenContext.UBER_INCOMING_SINGLE_OFFER, context)
        assertTrue("Chamada direta da Uber DEVE ser analisada", context.shouldAnalyze)
    }

    @Test
    fun testUberRadarList_Photo5() {
        // Foto 5: Lista geral do Radar de viagens da Uber
        val lines = listOf(
            "<",
            "Radar de viagens",
            "UberX",
            "R$ 11,16",
            "Est. de R$2,19/km",
            "4,94 (1830)",
            "+R$ 2,50 incluído",
            "4 min (1.4 km) Rua Amálio Júlio Guazzelli",
            "12 minutos (3.7 km) Av Nelson Mandela",
            "Tenho interesse",
            "UberX",
            "R$ 9,41",
            "Est. de R$2,14/km",
            "4,96 (209)",
            "+R$ 2,50 incluído",
            "5 min (1.5 km) Rua Giuseppe Lorenzini",
            "8 minutos (2.9 km)",
            "Tenho interesse"
        )
        val context = RideScreenContextClassifier.classify(SourceApp.UBER_DRIVER, lines)
        assertEquals(RideScreenContext.UBER_RADAR_LIST, context)
        assertFalse("Lista geral do Radar da Uber NUNCA deve ser analisada", context.shouldAnalyze)
    }

    @Test
    fun testUberIncomingOnMapWithTenhoInteresse_Photo3() {
        // Foto 3 da Uber: Oferta individual tocando no mapa com botão "Tenho interesse" e barra regressiva
        val lines = listOf(
            "Priority",
            "CORRIDA CURTA",
            "R$ 9,28",
            "Est. de R$2,38/km",
            "4,95 (1929)",
            "Verificado",
            "+R$ 1,70 incluída para retirada",
            "3 min (0.6 km)",
            "Rua Itatiba, Baeta Neves, São Bernardo do Campo",
            "9 minutos (3.3 km)",
            "Rua Doutor Baeta Neves, 369, Baeta Neves, São Bernardo do Campo",
            "Tenho interesse"
        )
        val context = RideScreenContextClassifier.classify(SourceApp.UBER_DRIVER, lines)
        assertEquals(RideScreenContext.UBER_INCOMING_SINGLE_OFFER, context)
        assertTrue("Oferta individual da Uber no mapa com Tenho interesse DEVE ser lida", context.shouldAnalyze)
    }

    @Test
    fun testUberIncomingLongTripOnMapWithTenhoInteresse_Photo4() {
        // Foto 4 da Uber: Oferta individual de viagem longa no mapa com botão "Tenho interesse"
        val lines = listOf(
            "UberX",
            "R$ 72,58",
            "Est. de R$1,82/km",
            "4,88 (720)",
            "Verificado",
            "7 min (2.7 km)",
            "R. Moema, Jardim Alvorada, Santo André",
            "2 paradas",
            "1 h e 42 min (37.1 km)",
            "R. Padre Chico, 224, Perdizes, São Paulo",
            "Viagem longa (mais de 45 min)",
            "Tenho interesse"
        )
        val context = RideScreenContextClassifier.classify(SourceApp.UBER_DRIVER, lines)
        assertEquals(RideScreenContext.UBER_INCOMING_SINGLE_OFFER, context)
        assertTrue("Oferta individual longa da Uber no mapa com Tenho interesse DEVE ser lida", context.shouldAnalyze)
    }

    @Test
    fun testUberIncomingStackedOnMapWithRadarPill_Photo1() {
        // Foto 1 do usuário: Oferta no mapa da Uber com rota, pílula "Radar de Viagens 2" e cards empilhados por trás
        val lines = listOf(
            "Santo André",
            "Radar de Viagens 2",
            "UberX",
            "R$ 20,03",
            "Est. de R$2,13/km",
            "4,95 (422)",
            "4 min (1.0 km)",
            "Rua Wladimir Herzog, Baeta Neves, São Bernardo do Campo",
            "21 minutos (8.4 km)",
            "Estação Prefeito Celso Daniel-Santo André, Centro, Santo André",
            "Tenho interesse"
        )
        val context = RideScreenContextClassifier.classify(SourceApp.UBER_DRIVER, lines)
        assertEquals(RideScreenContext.UBER_INCOMING_SINGLE_OFFER, context)
        assertTrue("Chamada no mapa com Radar de Viagens 2 e cards empilhados por trás DEVE ser lida", context.shouldAnalyze)
    }

    @Test
    fun testIdleScreenDoesNotAnalyze() {
        val lines = listOf(
            "Você está online",
            "Buscando solicitações...",
            "Mapa de chamadas"
        )
        val context = RideScreenContextClassifier.classify(SourceApp.NINETY_NINE_DRIVER, lines)
        assertEquals(RideScreenContext.NO_OFFER, context)
        assertFalse(context.shouldAnalyze)
    }
}
