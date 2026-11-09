package com.creativeam.goorno.reader.ninetynine

import com.creativeam.goorno.model.CaptureMethod
import com.creativeam.goorno.model.ScreenClassification
import com.creativeam.goorno.model.SourceApp
import com.creativeam.goorno.model.StandardRideOffer
import java.util.Locale
import kotlin.math.abs

/**
 * GoOrNo Leitor 2 LAB01 — motor espacial EXCLUSIVO para ofertas da 99.
 *
 * Transforma fragmentos Android Accessibility / ML Kit com posição na tela
 * em cartões isolados. O parser financeiro existente da 99 recebe somente
 * as linhas de UM cartão, preservando cálculos, semáforo e modelos atuais.
 *
 * Retorno null: não foi possível reconhecer uma estrutura inequívoca e
 * o chamador deve manter o caminho legado (principalmente oferta no mapa).
 * Retorno vazio: layout de lista reconhecido, mas sem ofertas válidas; NÃO
 * misturar cartões ou inventar campos combinando textos de telas distintas.
 */
object NinetyNineStructuredReader {
    data class Fragment(val text: String, val centerY: Int, val leftX: Int)

    private data class Row(val y: Int, val text: String)

    private val header = Regex(
        """^\s*(?:(?:99\s*)?pop(?:\s+(?:nova|expresso|priorit[aá]rio|express|normal))?|(?:99\s*)?plus|(?:99\s*)?negocia|(?:99\s*)?moto|(?:99\s*)?entrega|pgto\.?\s+no\s+app|pagamento\s+no\s+app)\b""",
        RegexOption.IGNORE_CASE
    )
    private val action = Regex("""^\s*(?:escolher|selecionar)\b""", RegexOption.IGNORE_CASE)
    private val fare = Regex("""R\$\s*\d{1,4}(?:[.,]\d{2})""", RegexOption.IGNORE_CASE)
    private val distance = Regex("""\d+(?:[.,]\d+)?\s*(?:km|m)\b""", RegexOption.IGNORE_CASE)
    private val idleOrDead = listOf(
        "esta corrida foi aceita", "corrida aceita", "aceita por outro",
        "isso é tudo por enquanto", "isso e tudo por enquanto"
    )

    /**
     * Só entra em operação quando existe geometria confiável.
     * A leitura da Uber não chama este método em nenhuma circunstância.
     */
    fun parse(
        sourceApp: SourceApp,
        fragments: List<Fragment>,
        captureMethod: CaptureMethod = CaptureMethod.ACCESSIBILITY,
        timestampMillis: Long = System.currentTimeMillis()
    ): List<StandardRideOffer>? {
        if (sourceApp != SourceApp.NINETY_NINE_DRIVER || fragments.isEmpty()) return null

        val clean = fragments.filter { it.text.isNotBlank() && it.centerY > 0 }
        if (clean.size < 4) return null
        val rows = toRows(clean)
        if (rows.isEmpty()) return null

        val actionPositions = rows.indices.filter { action.containsMatchIn(rows[it].text) }
        val headerPositions = rows.indices.filter { header.containsMatchIn(rows[it].text) }
        if (actionPositions.isEmpty() || headerPositions.isEmpty()) return null

        val hasRequestList = rows.any {
            it.text.contains("Solicitações", true) || it.text.contains("Solicitacoes", true)
        }
        // Chamada isolada no mapa continua com leitor comprovado (99 antigo).
        if (!hasRequestList && actionPositions.size < 2) return null

        val result = mutableListOf<StandardRideOffer>()
        var previousAction = -1
        for (actionIndex in actionPositions) {
            val segmentStart = previousAction + 1
            previousAction = actionIndex
            val headers = headerPositions.filter { it in segmentStart..actionIndex }
            if (headers.isEmpty()) {
                // Texto incompleto: preferir não associar uma tarifa ao card errado.
                if (actionPositions.size > 1) return emptyList()
                return null
            }
            // Cabeçalho mais próximo da tarifa, sem misturar o card anterior.
            val h = headers.firstOrNull { index ->
                rows.subList(index, actionIndex + 1).any { fare.containsMatchIn(it.text) }
            } ?: headers.first()
            val candidate = rows.subList(h, actionIndex + 1).map { it.text }
            val candidateJoined = candidate.joinToString(" ").lowercase(Locale.ROOT)

            if (idleOrDead.any { candidateJoined.contains(it) }) continue
            if (!candidate.any { isRealFare(it) } || candidate.count { distance.containsMatchIn(it) } < 2) {
                // Ofertas incompletas não devem virar dados financeiros incorretos.
                continue
            }
            val parsed = NinetyNineOfferReader.parseScreenNodes(
                rawNodes = candidate,
                captureMethod = captureMethod,
                timestampMillis = timestampMillis,
                isSimulated = false,
                isMultiOfferCard = actionPositions.size > 1
            )
            if (parsed.sourceApp == SourceApp.NINETY_NINE_DRIVER &&
                (parsed.screenClassification == ScreenClassification.NEW_OFFER ||
                    parsed.screenClassification == ScreenClassification.UPDATED_OFFER)
            ) {
                result.add(parsed)
            }
        }
        // Retém a ordem física de cima para baixo, não o preço maior/menor.
        return result
    }

    private fun isRealFare(line: String): Boolean {
        val match = fare.find(line) ?: return false
        val suffix = line.substring(match.range.last + 1).trimStart()
        return !suffix.startsWith("/km", true) && !suffix.startsWith("por km", true) &&
            !line.contains("saldo", true) && !line.contains("carteira", true)
    }

    private fun toRows(nodes: List<Fragment>): List<Row> {
        val sorted = nodes.sortedWith(compareBy<Fragment>({ it.centerY }, { it.leftX }))
        val groups = mutableListOf<MutableList<Fragment>>()
        for (node in sorted) {
            val current = groups.lastOrNull()
            // Um mesmo texto em OUTRA posição nunca é removido.
            if (current != null && abs(current.first().centerY - node.centerY) <= 24) {
                if (current.none { it.text == node.text && abs(it.leftX - node.leftX) <= 12 }) {
                    current.add(node)
                }
            } else groups.add(mutableListOf(node))
        }
        return groups.mapNotNull { group ->
            val joined = group.sortedBy { it.leftX }.joinToString(" ") { it.text }.trim()
            if (joined.isBlank()) null else Row(group.minOf { it.centerY }, joined)
        }
    }
}
