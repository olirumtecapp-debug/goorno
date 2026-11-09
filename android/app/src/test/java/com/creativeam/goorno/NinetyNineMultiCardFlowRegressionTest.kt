package com.creativeam.goorno

import com.creativeam.goorno.overlay.OfferQueueProgression
import com.creativeam.goorno.service.PositionedTextRowMerger
import com.creativeam.goorno.reader.ninetynine.NinetyNineListDisplayPolicy
import com.creativeam.goorno.reader.ninetynine.NinetyNineOfferReader
import com.creativeam.goorno.reader.ninetynine.NinetyNineVisualRatingMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NinetyNineMultiCardFlowRegressionTest {
    private val screenshot99 = listOf(
        "Solicitações", "Escolha uma", "Km até o embarque",
        "Pop Nova", "R$10,20", "R$3,07/km",
        "4,99 • 135 corridas • Perfil Essencial",
        "(6 min 1,1 km) Rua Almeida Leme, 246",
        "(7 min 2,2 km) Estrada Cata Preta, 8",
        "Escolher",
        "Pop Nova", "R$9,80", "R$2,75/km",
        "4,79 • 160 corridas • Perfil Essencial",
        "(6 min 1,2 km) Rua Itaúba, 188",
        "1 parada(s)",
        "(7 min 2,4 km) Rua Itaúba, 188",
        "Escolher"
    )

    @Test
    fun samePopNovaAndEscolherLabelsMustBePreservedAtDifferentRows() {
        val fragments = screenshot99.mapIndexed { i, label ->
            PositionedTextRowMerger.Fragment(label, 60 + i * 65, 25)
        }
        val merged = PositionedTextRowMerger.merge(fragments)
        assertEquals(2, merged.count { it == "Pop Nova" })
        assertEquals(2, merged.count { it == "Escolher" })
        val offers = NinetyNineOfferReader.parseMultipleOffersFromScreen(merged)
        assertEquals(2, offers.size)
        assertEquals(10.20, offers[0].offeredPrice!!, 0.001)
        assertEquals(9.80, offers[1].offeredPrice!!, 0.001)
    }

    @Test
    fun sameRowDuplicatesAreRemovedButDifferentRowsStay() {
        val fragments = listOf(
            PositionedTextRowMerger.Fragment("Pop Nova", 100, 10),
            PositionedTextRowMerger.Fragment("Pop Nova", 100, 10),
            PositionedTextRowMerger.Fragment("R$10,20", 160, 10),
            PositionedTextRowMerger.Fragment("4,99", 225, 10),
            PositionedTextRowMerger.Fragment("135 corridas", 228, 110),
            PositionedTextRowMerger.Fragment("Escolher", 500, 10),
            PositionedTextRowMerger.Fragment("Pop Nova", 700, 10),
            PositionedTextRowMerger.Fragment("R$9,80", 770, 10),
            PositionedTextRowMerger.Fragment("Escolher", 1020, 10)
        )
        val merged = PositionedTextRowMerger.merge(fragments)
        assertEquals(2, merged.count { it == "Pop Nova" })
        assertEquals(2, merged.count { it == "Escolher" })
        assertTrue(merged.any { it == "4,99 135 corridas" })
    }

    @Test
    fun switchingFromMapToRequestsMustReplayTopCardOnce() {
        val oldPrice = "NINETY_NINE_DRIVER|10.2|3.5|..."
        val bothCards = oldPrice + ";NINETY_NINE_DRIVER|9.8|3.5|..."
        assertTrue(NinetyNineListDisplayPolicy.shouldRefresh(false, oldPrice, bothCards))
        assertTrue(!NinetyNineListDisplayPolicy.shouldRefresh(true, bothCards, bothCards))
        assertTrue(NinetyNineListDisplayPolicy.shouldRefresh(true, bothCards, oldPrice))
    }

    @Test
    fun realTwoCardScreenshotKeepsTopFirstWithDistinctRatings() {
        val offers = NinetyNineOfferReader.parseMultipleOffersFromScreen(screenshot99)
        assertEquals("Duas ofertas devem existir na fila", 2, offers.size)
        assertEquals(10.20, offers[0].offeredPrice!!, 0.001)
        assertEquals(4.99, offers[0].passengerRating!!, 0.001)
        assertEquals(3.3, offers[0].totalDistanceKmExplicit!!, 0.001)
        assertEquals(13, offers[0].totalDurationMinExplicit)
        assertEquals(9.80, offers[1].offeredPrice!!, 0.001)
        assertEquals(4.79, offers[1].passengerRating!!, 0.001)
        assertEquals(3.6, offers[1].totalDistanceKmExplicit!!, 0.001)
        assertEquals(13, offers[1].totalDurationMinExplicit)
    }

    @Test
    fun countdownMustAdvanceThroughEveryOfferWithoutDiscardingTheRest() {
        assertEquals(1, OfferQueueProgression.nextIndex(0, 2))
        assertNull(OfferQueueProgression.nextIndex(1, 2))
        assertNull(OfferQueueProgression.nextIndex(0, 1))
        assertNull(OfferQueueProgression.nextIndex(0, 0))
        assertNull(OfferQueueProgression.nextIndex(-1, 2))
    }

    @Test
    fun geometryRestoresCorrectRatingsWithOverlayTextAboveCards() {
        val base = NinetyNineOfferReader.parseMultipleOffersFromScreen(
            screenshot99.filterNot { it.contains("4,99") || it.contains("4,79") }
        )
        assertEquals(2, base.size)
        assertNull(base[0].passengerRating)
        assertNull(base[1].passengerRating)
        val fragments = listOf(
            // Nota "--" e preço do próprio overlay aparecem no topo da imagem.
            NinetyNineVisualRatingMatcher.VisualText("R$9,80 · 13 min · 3,6 km", 190),
            NinetyNineVisualRatingMatcher.VisualText("Nota --", 290),
            NinetyNineVisualRatingMatcher.VisualText("Pop Nova", 330),
            NinetyNineVisualRatingMatcher.VisualText("R$10,20", 375),
            NinetyNineVisualRatingMatcher.VisualText("4,99", 455),
            NinetyNineVisualRatingMatcher.VisualText("135 corridas • Perfil Essencial", 455),
            NinetyNineVisualRatingMatcher.VisualText("Escolher", 780),
            NinetyNineVisualRatingMatcher.VisualText("Pop Nova", 925),
            NinetyNineVisualRatingMatcher.VisualText("R$9,80", 995),
            NinetyNineVisualRatingMatcher.VisualText("4,79", 1060),
            NinetyNineVisualRatingMatcher.VisualText("160 corridas • Perfil Essencial", 1060),
            NinetyNineVisualRatingMatcher.VisualText("Escolher", 1450)
        )
        val recovered = NinetyNineVisualRatingMatcher.enrich(base, fragments)
        assertEquals(4.99, recovered[0].passengerRating!!, 0.001)
        assertEquals(4.79, recovered[1].passengerRating!!, 0.001)
        assertEquals(base[0].offeredPrice, recovered[0].offeredPrice)
        assertEquals(base[1].offeredPrice, recovered[1].offeredPrice)
        assertEquals(base[1].totalDurationMinExplicit, recovered[1].totalDurationMinExplicit)
    }

    @Test
    fun overlayPriceThatMatchesBottomCardDoesNotStealTopPassengerRating() {
        val base = NinetyNineOfferReader.parseMultipleOffersFromScreen(
            screenshot99.filterNot { it.contains("4,99") || it.contains("4,79") }
        )
        val fragments = listOf(
            NinetyNineVisualRatingMatcher.VisualText("R$9,80", 350), // overlay
            NinetyNineVisualRatingMatcher.VisualText("R$10,20", 390),
            NinetyNineVisualRatingMatcher.VisualText("4,99 • 135 corridas", 455),
            NinetyNineVisualRatingMatcher.VisualText("R$9,80", 970), // card real
            NinetyNineVisualRatingMatcher.VisualText("4,79 • 160 corridas", 1030)
        )
        val result = NinetyNineVisualRatingMatcher.enrich(base, fragments)
        assertEquals(4.99, result[0].passengerRating!!, 0.001)
        assertEquals(4.79, result[1].passengerRating!!, 0.001)
    }

    @Test
    fun lowerCardRatingDoesNotContaminateTopWhenTopPriceIsHidden() {
        val base = NinetyNineOfferReader.parseMultipleOffersFromScreen(
            screenshot99.filterNot { it.contains("4,99") || it.contains("4,79") }
        )
        val fragments = listOf(
            NinetyNineVisualRatingMatcher.VisualText("R$9,80 · 13 min", 180),
            NinetyNineVisualRatingMatcher.VisualText("Nota --", 250),
            NinetyNineVisualRatingMatcher.VisualText("4,99 • 135 corridas", 450),
            NinetyNineVisualRatingMatcher.VisualText("R$9,80", 995),
            NinetyNineVisualRatingMatcher.VisualText("4,79 • 160 corridas", 1060)
        )
        val result = NinetyNineVisualRatingMatcher.enrich(base, fragments)
        assertNull(result[0].passengerRating)
        assertEquals(4.79, result[1].passengerRating!!, 0.001)
    }

    @Test
    fun currencyRatesAndAddressDecimalsNeverBecomePassengerRatings() {
        val base = NinetyNineOfferReader.parseMultipleOffersFromScreen(
            screenshot99.filterNot { it.contains("4,99") || it.contains("4,79") }
        )
        val fragments = listOf(
            NinetyNineVisualRatingMatcher.VisualText("R$10,20", 380),
            NinetyNineVisualRatingMatcher.VisualText("R$3,07/km", 395),
            NinetyNineVisualRatingMatcher.VisualText("Rua 4,99", 450),
            NinetyNineVisualRatingMatcher.VisualText("135 corridas", 450),
            NinetyNineVisualRatingMatcher.VisualText("R$9,80", 960),
            NinetyNineVisualRatingMatcher.VisualText("R$2,75/km", 965),
            NinetyNineVisualRatingMatcher.VisualText("1 parada(s)", 1040)
        )
        val result = NinetyNineVisualRatingMatcher.enrich(base, fragments)
        assertNull(result[0].passengerRating)
        assertNull(result[1].passengerRating)
        assertTrue(result.map { it.offeredPrice } == base.map { it.offeredPrice })
    }
}
