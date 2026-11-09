package com.creativeam.goorno.reader.ninetynine

import com.creativeam.goorno.model.SourceApp
import com.creativeam.goorno.model.StandardRideOffer
import java.util.Locale
import kotlin.math.abs

/**
 * Complementa somente a nota na 99 a partir das coordenadas do OCR.
 *
 * A leitura do preço e da nota deve vir do MESMO cartão (posição vertical).
 * O preço do overlay pode aparecer no topo da captura: a distância máxima
 * entre âncora de preço e nota impede que seja confundido com um card abaixo.
 * Em caso de dúvida, mantém a nota desconhecida em vez de inventar valores.
 */
object NinetyNineVisualRatingMatcher {
    data class VisualText(val text: String, val centerY: Int)

    private data class PriceAnchor(val price: Double, val y: Int)
    private data class RatingAnchor(val rating: Double, val y: Int)

    private val currencyRegex = Regex("""R\$\s*(\d{1,4}[.,]\d{2})""", RegexOption.IGNORE_CASE)
    private val ratingRegex = Regex("""(?<![\d.,/])([3-5][.,]\d{2})(?!\s*(?:/\s*km|km\b|m\b|min\b|x\b))""")
    private val passengerKeywords = listOf("corrida", "viagen", "perfil", "cartão", "cartao", "verif", "passageir")

    fun enrich(
        displayed: List<StandardRideOffer>,
        visualTexts: List<VisualText>
    ): List<StandardRideOffer> {
        if (displayed.isEmpty() || visualTexts.isEmpty()) return displayed
        val prices = mutableListOf<PriceAnchor>()
        for (fragment in visualTexts) {
            for (match in currencyRegex.findAll(fragment.text)) {
                val suffix = fragment.text.substring(match.range.last + 1).trimStart()
                if (suffix.startsWith("/km", ignoreCase = true) ||
                    suffix.startsWith("por km", ignoreCase = true)) continue
                val price = match.groupValues[1].replace(",", ".").toDoubleOrNull() ?: continue
                if (price in 4.0..350.0) prices.add(PriceAnchor(price, fragment.centerY))
            }
        }
        val ratings = mutableListOf<RatingAnchor>()
        for (fragment in visualTexts) {
            val text = fragment.text
            if (text.contains("R$", ignoreCase = true) || text.contains("/km", ignoreCase = true)) continue
            val sameRow = visualTexts.any { other ->
                abs(other.centerY - fragment.centerY) <= 30 &&
                    passengerKeywords.any { keyword ->
                        other.text.lowercase(Locale.ROOT).contains(keyword)
                    }
            }
            if (!sameRow) continue
            for (match in ratingRegex.findAll(text)) {
                val rating = match.groupValues[1].replace(",", ".").toDoubleOrNull() ?: continue
                if (rating in 3.0..5.0) ratings.add(RatingAnchor(rating, fragment.centerY))
            }
        }

        return displayed.map { offer ->
            if (offer.sourceApp != SourceApp.NINETY_NINE_DRIVER ||
                offer.passengerRating != null || offer.passengerIsNew) return@map offer

            val wantedPrice = offer.offeredPrice ?: return@map offer
            // Quando dois cartões têm o mesmo preço, o preço sozinho não identifica o passageiro.
            if (displayed.count { other ->
                    other.offeredPrice != null && abs(other.offeredPrice!! - wantedPrice) < 0.02
                } > 1) return@map offer

            val anchors = prices.filter { abs(it.price - wantedPrice) < 0.02 }
            if (anchors.isEmpty()) return@map offer

            val possibleRatings = mutableSetOf<Double>()
            for (anchor in anchors) {
                val nearest = ratings.filter { it.y - anchor.y in 8..210 }
                    .minByOrNull { it.y - anchor.y } ?: continue

                // Entre o preço e a avaliação não pode haver o preço de outra oferta.
                val crossingAnotherCard = prices.any {
                    abs(it.price - wantedPrice) >= 0.02 && it.y > anchor.y + 5 &&
                        it.y < nearest.y - 5
                }
                if (!crossingAnotherCard) possibleRatings.add(nearest.rating)
            }
            if (possibleRatings.size == 1) offer.copy(passengerRating = possibleRatings.single())
            else offer
        }
    }
}
