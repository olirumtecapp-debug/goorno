package com.creativeam.goorno.engine

import com.creativeam.goorno.model.DriverFinancialConfig
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale

/**
 * Resultado da avaliação de segurança geográfica (Área de Risco) da Origem e do Destino.
 */
data class RiskAssessment(
    val hasRisk: Boolean,
    val originMatchedZone: String? = null,
    val destinationMatchedZone: String? = null,
    val isNightHour: Boolean = false,
    val shortBadge: String = "",
    val detailedMessage: String = ""
)

/**
 * Avaliador Inteligente de Áreas de Risco em Tempo Real (v3.0.0).
 *
 * Cruza o endereço de Origem e o endereço de Destino lidos da 99 ou Uber com:
 * 1. Termos estruturais de atenção (vielas, becos, invasões, comunidades, etc.) e base nacional de áreas críticas.
 * 2. Lista personalizada de bairros/ruas evitados pelo próprio motorista (cadastrados na tela de Metas/Risco
 *    ou marcados com 1 toque diretamente no Histórico).
 * 3. Contexto de horário noturno (20h às 05h59).
 */
object RiskAreaEvaluator {

    // Termos viários e áreas críticas conhecidas (comparados sem acento e case-insensitive)
    private val DEFAULT_RISK_TERMS = listOf(
        "viela",
        "beco",
        "favela",
        "invasao",
        "ocupacao",
        "comunidade",
        "estrada de terra",
        "servidao",
        // Grande SP / Capital (zonas de alta atenção relatadas por motoristas)
        "paraisopolis",
        "heliopolis",
        "capao redondo",
        "jardim angela",
        "brasilandia",
        "jardim elisa maria",
        "cidade tiradentes",
        "grajau",
        "sapopemba",
        "pantanal",
        "cracolandia",
        "rua helvetia",
        "rua dos gusmoes",
        "jardim sao luis",
        "pedreira",
        "vila nova cachoeirinha",
        // RJ / Grande Rio
        "complexo do alemao",
        "complexo da mare",
        "rocinha",
        "cidade de deus",
        "jacarezinho",
        "manguinhos",
        "costa barros",
        "chapadao",
        "acari",
        "vila kennedy",
        "belford roxo",
        "caxias",
        // Outras Capitais (BH, Salvador, Fortaleza, Recife, Curitiba, POA)
        "cabana do pai tomas",
        "aglomerado da serra",
        "pedreira prado lopes",
        "nordeste de amaralina",
        "valeria",
        "bairro da paz",
        "bom jardim",
        "jangurussu",
        "parolin",
        "vila torres",
        "rubem berta",
        "restinga",
        "lomba do pinheiro"
    )

    fun evaluate(
        originAddress: String?,
        destinationAddress: String?,
        config: DriverFinancialConfig,
        hourOfDay: Int = currentHourOfDay()
    ): RiskAssessment {
        if (!config.riskAlertEnabled) {
            return RiskAssessment(hasRisk = false)
        }

        val origRaw = originAddress?.trim().orEmpty()
        val destRaw = destinationAddress?.trim().orEmpty()
        if (origRaw.isEmpty() && destRaw.isEmpty()) {
            return RiskAssessment(hasRisk = false)
        }

        val customZones = parseCustomZones(config.customRiskZones)
        val activeTerms = mutableListOf<String>()
        activeTerms.addAll(customZones)
        if (config.useDefaultRiskKeywords) {
            activeTerms.addAll(DEFAULT_RISK_TERMS)
        }

        if (activeTerms.isEmpty()) {
            return RiskAssessment(hasRisk = false)
        }

        val origNorm = normalizeText(origRaw)
        val destNorm = normalizeText(destRaw)

        val matchedOrigin = findMatchingTerm(origNorm, activeTerms)
        val matchedDest = findMatchingTerm(destNorm, activeTerms)

        if (matchedOrigin == null && matchedDest == null) {
            return RiskAssessment(hasRisk = false)
        }

        val isNight = hourOfDay >= 20 || hourOfDay <= 5
        val nightTag = if (isNight) " (🌙 Noite)" else ""

        val shortBadge = when {
            matchedOrigin != null && matchedDest != null ->
                "⚠️ RISCO Origem e Destino ($matchedDest)$nightTag"
            matchedDest != null ->
                "⚠️ RISCO no Destino: ${ matchedDest.uppercase(Locale.getDefault()) }$nightTag"
            else ->
                "⚠️ RISCO na Origem: ${ matchedOrigin!!.uppercase(Locale.getDefault()) }$nightTag"
        }

        val detailedMessage = buildString {
            append("⚠️ ALERTA DE ÁREA DE RISCO")
            if (isNight) append(" · HORÁRIO NOTURNO")
            append(": ")
            if (matchedOrigin != null) {
                append("Embarque contém '$matchedOrigin'")
                if (matchedDest != null) append(" e ")
            }
            if (matchedDest != null) {
                append("Destino contém '$matchedDest'")
            }
        }

        return RiskAssessment(
            hasRisk = true,
            originMatchedZone = matchedOrigin,
            destinationMatchedZone = matchedDest,
            isNightHour = isNight,
            shortBadge = shortBadge,
            detailedMessage = detailedMessage
        )
    }

    fun parseCustomZones(rawCsv: String): List<String> {
        if (rawCsv.isBlank()) return emptyList()
        return rawCsv
            .split(",", ";", "\n", "|")
            .map { it.trim() }
            .filter { it.length >= 3 }
            .distinctBy { normalizeText(it) }
    }

    fun parseCustomZonesList(rawCsv: String): List<String> = parseCustomZones(rawCsv)

    /**
     * Extrai uma sugestão inteligente de rua ou bairro a partir de um endereço completo
     * para facilitar o botão de "Marcar como Área de Risco" com 1 toque no Histórico.
     */
    fun extractCleanStreetOrDistrict(fullAddress: String): String {
        val cleaned = fullAddress
            .replace(Regex("""^\d+\s*min\s*\([\d.,]+\s*(?:km|m)\)\s*[-•·]?\s*""", RegexOption.IGNORE_CASE), "")
            .trim()
        // Pega o nome da rua/bairro sem o número predial se houver vírgula ou hífen
        val parts = cleaned.split("-", ",", "·").map { it.trim() }.filter { it.length >= 3 }
        if (parts.size >= 2) {
            // Se a segunda parte parece um bairro (não começa com dígito), retorna o bairro ou a rua
            val second = parts[1]
            if (!second.first().isDigit() && second.length >= 4) {
                return second
            }
        }
        return parts.firstOrNull()?.take(40) ?: cleaned.take(40)
    }

    private fun findMatchingTerm(normalizedAddress: String, candidateTerms: List<String>): String? {
        if (normalizedAddress.isBlank()) return null
        for (term in candidateTerms) {
            val normTerm = normalizeText(term)
            if (normTerm.length < 3) continue
            // Verifica palavra/trecho inteiro para evitar falsos positivos em palavras curtas
            val regex = Regex("""\b${Regex.escape(normTerm)}\b""")
            if (regex.containsMatchIn(normalizedAddress) || (normTerm.length >= 6 && normalizedAddress.contains(normTerm))) {
                return term
            }
        }
        return null
    }

    fun normalizeText(input: String): String {
        val nfd = Normalizer.normalize(input.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
        return nfd.replace(Regex("""\p{InCombiningDiacriticalMarks}+"""), "")
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun currentHourOfDay(): Int {
        return try {
            Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        } catch (_: Exception) {
            12
        }
    }
}
