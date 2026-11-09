package com.creativeam.goorno

import com.creativeam.goorno.model.SourceApp
import com.creativeam.goorno.reader.ninetynine.NinetyNineStructuredReader
import com.creativeam.goorno.reader.uber.UberOfferReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** GoOrNo Leitor 2 LAB01: cenários reais da 99 e contrato de NÃO intervenção na Uber. */
class NinetyNineStructuredReaderLabTest {
    private fun fragment(label: String, y: Int, x: Int = 28) =
        NinetyNineStructuredReader.Fragment(label, y, x)

    private val real99Cards = listOf(
        fragment("Solicitações", 105),
        fragment("Escolha uma", 210),
        fragment("Km até o embarque", 210, 355),
        fragment("Pop Nova", 325),
        fragment("R$10,20", 400),
        fragment("R$3,07/km", 400, 265),
        fragment("4,99", 470),
        fragment("135 corridas • Perfil Essencial", 470, 125),
        fragment("(6 min 1,1 km)", 550),
        fragment("Rua Almeida Leme, 246", 590),
        fragment("(7 min 2,2 km)", 645),
        fragment("Estrada Cata Preta, 8", 690),
        fragment("Escolher", 775),
        fragment("Pop Nova", 925),
        fragment("R$9,80", 1005),
        fragment("R$2,75/km", 1005, 265),
        fragment("4,79", 1080),
        fragment("160 corridas • Perfil Essencial", 1080, 125),
        fragment("(6 min 1,2 km)", 1180),
        fragment("Rua Itaúba, 188", 1220),
        fragment("1 parada(s)", 1290),
        fragment("(7 min 2,4 km)", 1375),
        fragment("Rua Itaúba, 188", 1410),
        fragment("Escolher", 1535)
    )

    @Test
    fun twoCardsRemainTopFirstAndKeepTheirOwnRatings() {
        val result = NinetyNineStructuredReader.parse(SourceApp.NINETY_NINE_DRIVER, real99Cards)
        assertNotNull(result)
        val offers = result!!
        assertEquals("Dois cards independentes", 2, offers.size)
        assertEquals(10.20, offers[0].offeredPrice!!, 0.01)
        assertEquals(4.99, offers[0].passengerRating!!, 0.01)
        assertEquals(3.3, offers[0].totalDistanceKmExplicit!!, 0.01)
        assertEquals(13, offers[0].totalDurationMinExplicit)
        assertEquals(9.80, offers[1].offeredPrice!!, 0.01)
        assertEquals(4.79, offers[1].passengerRating!!, 0.01)
        assertEquals(3.6, offers[1].totalDistanceKmExplicit!!, 0.01)
        assertEquals(13, offers[1].totalDurationMinExplicit)
    }

    @Test
    fun reversedAccessibilityNodeOrderStillUsesScreenGeometry() {
        val result = NinetyNineStructuredReader.parse(SourceApp.NINETY_NINE_DRIVER, real99Cards.reversed())!!
        assertEquals(2, result.size)
        assertEquals(10.20, result[0].offeredPrice!!, 0.01)
        assertEquals(9.80, result[1].offeredPrice!!, 0.01)
    }

    @Test
    fun overlayFareAboveFirstCardCannotReplaceRealFare() {
        val extra = listOf(fragment("R$9,80 · 13 min · 3,6 km", 175), fragment("Nota --", 255))
        val result = NinetyNineStructuredReader.parse(SourceApp.NINETY_NINE_DRIVER, extra + real99Cards)!!
        assertEquals(2, result.size)
        assertEquals(10.20, result[0].offeredPrice!!, 0.01)
        assertEquals(4.99, result[0].passengerRating!!, 0.01)
    }

    @Test
    fun missingSecondCardRatingCannotTakeFirstRating() {
        val withoutSecondRating = real99Cards.filterNot {
            it.text == "4,79" || it.text.startsWith("160 corridas")
        }
        val result = NinetyNineStructuredReader.parse(SourceApp.NINETY_NINE_DRIVER, withoutSecondRating)!!
        assertEquals(2, result.size)
        assertEquals(4.99, result[0].passengerRating!!, 0.01)
        assertNull(result[1].passengerRating)
    }

    @Test
    fun singleCardOnRequestsListIsSupported() {
        val firstOnly = real99Cards.filter { it.centerY <= 775 }
        val result = NinetyNineStructuredReader.parse(SourceApp.NINETY_NINE_DRIVER, firstOnly)!!
        assertEquals(1, result.size)
        assertEquals(10.20, result[0].offeredPrice!!, 0.01)
    }

    @Test
    fun isolatedMapCallFallsBackToThePrevious99Reader() {
        val map = listOf(
            fragment("Pop Nova", 300), fragment("R$11,90", 350),
            fragment("4,95 • 240 corridas", 400),
            fragment("(5 min 1,2 km)", 520),
            fragment("(9 min 3,3 km)", 620),
            fragment("Escolher", 700)
        )
        assertNull(NinetyNineStructuredReader.parse(SourceApp.NINETY_NINE_DRIVER, map))
    }

    @Test
    fun incompleteListDoesNotInventASecondRide() {
        val missingBottomDestination = real99Cards.filterNot {
            it.centerY == 1375 || it.centerY == 1410
        }
        val offers = NinetyNineStructuredReader.parse(SourceApp.NINETY_NINE_DRIVER, missingBottomDestination)!!
        assertEquals(1, offers.size)
        assertEquals(10.20, offers[0].offeredPrice!!, 0.01)
    }

    @Test
    fun uberIsNeverRoutedThroughThisReader() {
        val rejected = NinetyNineStructuredReader.parse(SourceApp.UBER_DRIVER, real99Cards)
        assertNull(rejected)
        val uber = UberOfferReader.parseScreenNodes(listOf(
            "UberX", "R$ 25,00", "2,0 km", "8,0 km", "5 min", "15 min",
            "Origem: Av. Paulista, 1000", "Destino: Rua Augusta, 500", "Aceitar"
        ))
        assertEquals(SourceApp.UBER_DRIVER, uber.sourceApp)
        assertEquals(25.0, uber.offeredPrice!!, 0.01)
        assertTrue(uber.totalDistanceKmExplicit!! > 9.9)
        assertFalse(uber.hasFieldConflict)
    }
}
