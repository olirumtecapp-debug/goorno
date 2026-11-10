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

    @Test
    fun incompleteNinetyNineCallShouldNotBeHardBlocked() {
        val context = RideScreenContextClassifier.classify(
            SourceApp.NINETY_NINE_DRIVER,
            listOf("Pgto. no app", "R$14,40")
        )
        assertEquals(RideScreenContext.UNKNOWN, context)
    }

    @Test
    fun incompleteUberCallShouldNotBeHardBlocked() {
        val context = RideScreenContextClassifier.classify(
            SourceApp.UBER_DRIVER,
            listOf("Priority", "R$20,58")
        )
        assertEquals(RideScreenContext.UNKNOWN, context)
    }

    @Test
    fun singleCardInUberRadarIsStillAList() {
        val context = RideScreenContextClassifier.classify(
            SourceApp.UBER_DRIVER,
            listOf("Radar de viagens", "UberX", "R$11,16", "4 min (1.4 km)", "Tenho interesse")
        )
        assertEquals(RideScreenContext.UBER_RADAR_LIST, context)
    }

    @Test
    fun selectedUberRadarOfferWithExplicitAcceptActionIsReadable() {
        val context = RideScreenContextClassifier.classify(
            SourceApp.UBER_DRIVER,
            listOf("Radar de viagens", "UberX", "R$11,16", "4 min (1.4 km)",
                "12 min (3.7 km)", "Aceitar")
        )
        assertEquals(RideScreenContext.UBER_RADAR_SELECTED_OFFER, context)
    }

    @Test
    fun singleCardInNinetyNineRequestsIsStillAList() {
        val context = RideScreenContextClassifier.classify(
            SourceApp.NINETY_NINE_DRIVER,
            listOf("Solicitações", "Pop", "R$14,40", "6 min (1,2 km)", "Escolher")
        )
        assertEquals(RideScreenContext.NINETYNINE_REQUEST_LIST, context)
    }

}
