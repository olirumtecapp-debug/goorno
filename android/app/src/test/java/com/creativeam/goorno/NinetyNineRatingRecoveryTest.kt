package com.creativeam.goorno

import com.creativeam.goorno.reader.BrazilianOfferParser
import com.creativeam.goorno.reader.ninetynine.NinetyNineOfferReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Capturas de 09/11/2026: as notas estavam visíveis na 99 mas o card
 * GoOrNo mostrava "--". Dados fictícios de endereços, números reais da oferta.
 */
class NinetyNineRatingRecoveryTest {
    private fun card(price: String, ratingLine: String?, pickup: String, trip: String): List<String> =
        listOf("Solicitações", "Escolha uma", "Km até o embarque", "Pop Nova", price, "R$2,95/km") +
            listOfNotNull(ratingLine) +
            listOf(pickup, "Rua de embarque", trip, "Rua de destino", "Escolher")

    @Test
    fun sevenReal99RatingFormatsAreParsed() {
        val cases = listOf(
            Triple("4,94 • 818 corridas • Perfil Essencial", 4.94, "R$14,70"),
            Triple("4,90 • 437 corridas • Cartão verif.", 4.90, "R$12,80"),
            Triple("5,00 • 55 corridas • Perfil Essencial", 5.00, "R$22,40"),
            Triple("4,87 • 329 corridas • Perfil Premium", 4.87, "R$18,30"),
            Triple("4,89 • +999 corridas • Perfil Premium", 4.89, "R$11,90"),
            Triple("4,94 • 728 corridas • Perfil Premium", 4.94, "R$9,65"),
            Triple("4,92 • 308 corridas • Perfil Premium", 4.92, "R$13,60")
        )
        for ((ratingText, expected, price) in cases) {
            val nodes = card(price, ratingText, "(7 min 1,9 km)", "(20 min 5,5 km)")
            val offer = NinetyNineOfferReader.parseScreenNodes(nodes)
            assertEquals("Nota da 99 para '$ratingText'", expected, offer.passengerRating!!, 0.001)
            assertEquals("Preço não pode virar nota", price.replace("R$", "").replace(",", ".").toDouble(), offer.offeredPrice!!, 0.001)
        }
    }

    @Test
    fun fragmentedRatingMayBeRecoveredFromAdjacentPassengerContext() {
        assertEquals(4.94, BrazilianOfferParser.extractPassengerRating(listOf(
            "4,94", "• 728 corridas", "• Perfil Premium"
        ))!!, 0.001)
        assertEquals(5.0, BrazilianOfferParser.extractPassengerRating(listOf(
            "5,00", "55 corridas", "Perfil Essencial"
        ))!!, 0.001)
    }

    @Test
    fun ocrEnrichmentDoesNotModifyFareDistanceOrTime() {
        val base = NinetyNineOfferReader.parseScreenNodes(
            card("R$12,80", null, "(7 min 2,2 km)", "(7 min 2,2 km)")
        )
        val visual = NinetyNineOfferReader.parseScreenNodes(
            card("R$12,80", "4,90 • 437 corridas • Cartão verif.", "(7 min 2,2 km)", "(7 min 2,2 km)")
        )
        assertNull(base.passengerRating)
        val enriched = NinetyNineOfferReader.enrichRatingsFromOcr(listOf(base), listOf(visual)).single()
        assertEquals(4.90, enriched.passengerRating!!, 0.001)
        assertEquals(base.offeredPrice, enriched.offeredPrice)
        assertEquals(base.totalDistanceKmExplicit, enriched.totalDistanceKmExplicit)
        assertEquals(base.totalDurationMinExplicit, enriched.totalDurationMinExplicit)
    }

    @Test
    fun ocrRatingCannotCrossContaminateOtherOffer() {
        val base = NinetyNineOfferReader.parseScreenNodes(
            card("R$18,30", null, "(9 min 2,4 km)", "(20 min 4,7 km)")
        )
        val otherPrice = NinetyNineOfferReader.parseScreenNodes(
            card("R$22,40", "5,00 • 55 corridas", "(9 min 2,4 km)", "(20 min 4,7 km)")
        )
        val otherDistance = NinetyNineOfferReader.parseScreenNodes(
            card("R$18,30", "4,87 • 329 corridas", "(7 min 1,9 km)", "(20 min 5,5 km)")
        )
        val unchanged = NinetyNineOfferReader.enrichRatingsFromOcr(listOf(base), listOf(otherPrice, otherDistance)).single()
        assertNull(unchanged.passengerRating)
    }

    @Test
    fun partialOcrWithoutPriceRecoversOnlyOneVisiblePassengerRating() {
        val base = NinetyNineOfferReader.parseScreenNodes(
            card("R$12,80", null, "(7 min 2,2 km)", "(7 min 2,2 km)")
        )
        val lines = listOf(
            "Solicitações", "Pop Nova", "Nota --",
            "4,90 • 437 corridas • Cartão verif.",
            "(7 min 2,2 km)", "Rua de embarque",
            "(7 min 2,2 km)", "Rua de destino", "Escolher"
        )
        val recovered = NinetyNineOfferReader.recoverSingleOfferRatingFromOcr(
            listOf(base), lines
        ).single()
        assertEquals(4.90, recovered.passengerRating!!, 0.001)
        assertEquals(base.offeredPrice, recovered.offeredPrice)
        assertEquals(base.totalDurationMinExplicit, recovered.totalDurationMinExplicit)
    }

    @Test
    fun partialOcrCannotMixRatingsBetweenTwoCards() {
        val a = NinetyNineOfferReader.parseScreenNodes(
            card("R$22,40", null, "(7 min 1,9 km)", "(20 min 5,5 km)")
        )
        val b = NinetyNineOfferReader.parseScreenNodes(
            card("R$31,40", null, "(3 min 918 m)", "(37 min 9,7 km)")
        )
        val screen = listOf(
            "Pop Nova", "R$22,40", "5,00 • 55 corridas", "Escolher",
            "Pop", "R$31,40", "4,89 • 259 corridas", "Escolher"
        )
        val unchanged = NinetyNineOfferReader.recoverSingleOfferRatingFromOcr(
            listOf(a, b), screen
        )
        assertNull(unchanged[0].passengerRating)
        assertNull(unchanged[1].passengerRating)
        val ambiguous = NinetyNineOfferReader.recoverSingleOfferRatingFromOcr(
            listOf(a), screen
        )
        assertNull(ambiguous[0].passengerRating)
    }

    @Test
    fun partialOcrDoesNotMistakeRatesOrMultipliersForPassengerRatings() {
        val base = NinetyNineOfferReader.parseScreenNodes(
            card("R$12,80", null, "(7 min 2,2 km)", "(7 min 2,2 km)")
        )
        val screen = listOf(
            "Pop Nova", "R$12,80", "R$3,68/km", "x1,9",
            "55 corridas", "Rua 4,90", "Escolher"
        )
        val unchanged = NinetyNineOfferReader.recoverSingleOfferRatingFromOcr(
            listOf(base), screen
        )
        assertNull(unchanged.single().passengerRating)
    }

    @Test
    fun multipleVisualCardsMustMatchOnlyTheirOwnRatings() {
        val first = NinetyNineOfferReader.parseScreenNodes(
            card("R$22,40", null, "(7 min 1,9 km)", "(20 min 5,5 km)")
        )
        val second = NinetyNineOfferReader.parseScreenNodes(
            card("R$31,40", null, "(3 min 918 m)", "(37 min 9,7 km)")
        )
        val firstOcr = NinetyNineOfferReader.parseScreenNodes(
            card("R$22,40", "5,00 • 55 corridas", "(7 min 1,9 km)", "(20 min 5,5 km)")
        )
        val secondOcr = NinetyNineOfferReader.parseScreenNodes(
            card("R$31,40", "4,89 • 259 corridas", "(3 min 918 m)", "(37 min 9,7 km)")
        )
        val updated = NinetyNineOfferReader.enrichRatingsFromOcr(
            listOf(first, second), listOf(secondOcr, firstOcr)
        )
        assertEquals(5.0, updated[0].passengerRating!!, 0.001)
        assertEquals(4.89, updated[1].passengerRating!!, 0.001)
        assertTrue(updated[0].offeredPrice != updated[1].offeredPrice)
    }
}
