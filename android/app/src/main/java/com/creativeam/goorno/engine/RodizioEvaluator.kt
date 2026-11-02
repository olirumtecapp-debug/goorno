package com.creativeam.goorno.engine

import com.creativeam.goorno.model.DriverFinancialConfig
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale

enum class RodizioMatchType {
    NONE,
    DESTINATION,
    ORIGIN,
    BOTH
}

data class RodizioAssessment(
    val isRodizio: Boolean,
    val matchType: RodizioMatchType = RodizioMatchType.NONE,
    val badge: String? = null,
    val message: String? = null,
    val finalDigit: Int? = null,
    val restrictedDayName: String? = null
)

/**
 * Avaliador Oficial do Rodízio Municipal de Veículos de São Paulo (CET-SP).
 *
 * Regras:
 * - Segunda-feira: Placas com final 1 e 2
 * - Terça-feira: Placas com final 3 e 4
 * - Quarta-feira: Placas com final 5 e 6
 * - Quinta-feira: Placas com final 7 e 8
 * - Sexta-feira: Placas com final 9 e 0
 * - Sábados, domingos e feriados: Rodízio suspenso
 *
 * Horários de Restrição:
 * - Manhã: 07h00 às 10h00
 * - Tarde/Noite: 17h00 às 20h00
 *
 * Área Delimitada: Centro Expandido de São Paulo (Minianel Viário)
 */
object RodizioEvaluator {

    // Principais vias e bairros que integram o Centro Expandido de São Paulo / Minianel Viário
    private val SP_CENTRO_EXPANDIDO_KEYWORDS = listOf(
        "centro", "se", "sé", "bela vista", "consolacao", "consolação", "republica", "república",
        "liberdade", "cambuci", "bom retiro", "bras", "brás", "pari", "mooca", "ipiranga",
        "aclimacao", "aclimação", "vila mariana", "paraiso", "paraíso", "itaim bibi", "itaim",
        "moema", "vila olimpia", "vila olímpia", "jardins", "jardim paulista", "cerqueira cesar",
        "cerqueira césar", "pinheiros", "vila madalena", "perdizes", "lapa", "barra funda",
        "santa cecilia", "santa cecília", "higienopolis", "higienópolis", "brooklin", "campo belo",
        "vila leopoldina", "pacaembu", "jardim europa", "jardim america", "jardim américa",
        "jardim paulistano", "chacara santo antonio", "chácara santo antônio", "vila nova conceicao",
        "vila nova conceição", "paulista", "marginal pinheiros", "marginal tiete", "marginal tietê",
        "23 de maio", "vinte e tres de maio", "rubem berta", "bandeirantes", "roberto marinho",
        "salim farah maluf", "radial leste", "avenida do estado", "cruzeiro do sul", "tiradentes",
        "santos dumont", "santo amaro", "bela alianca", "sumare", "sumaré", "perdizes", "butanta"
    )

    fun extractFinalPlateDigit(rawPlate: String?): Int? {
        if (rawPlate.isNullOrBlank()) return null
        val clean = rawPlate.trim().uppercase(Locale.getDefault())
        // Em placas tradicionais (ABC-1234) e padrão Mercosul (BRA2E19),
        // o último caractere é SEMPRE um dígito numérico (0 a 9)
        val lastDigitChar = clean.reversed().firstOrNull { it.isDigit() }
        return lastDigitChar?.digitToIntOrNull()
    }

    fun getDayRestrictedDigits(dayOfWeek: Int): List<Int> {
        return when (dayOfWeek) {
            Calendar.MONDAY -> listOf(1, 2)
            Calendar.TUESDAY -> listOf(3, 4)
            Calendar.WEDNESDAY -> listOf(5, 6)
            Calendar.THURSDAY -> listOf(7, 8)
            Calendar.FRIDAY -> listOf(9, 0)
            else -> emptyList()
        }
    }

    fun getDayNameForDigit(digit: Int): String {
        return when (digit) {
            1, 2 -> "Segunda-feira"
            3, 4 -> "Terça-feira"
            5, 6 -> "Quarta-feira"
            7, 8 -> "Quinta-feira"
            9, 0 -> "Sexta-feira"
            else -> ""
        }
    }

    fun isRestrictedHour(calendar: Calendar = Calendar.getInstance()): Boolean {
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val totalMinutes = hour * 60 + minute

        // Manhã: 07:00 às 10:00 (420 min a 599 min)
        val morningRestricted = totalMinutes in (7 * 60)..<(10 * 60)
        // Tarde/Noite: 17:00 às 20:00 (1020 min a 1199 min)
        val eveningRestricted = totalMinutes in (17 * 60)..<(20 * 60)

        return morningRestricted || eveningRestricted
    }

    fun evaluate(
        originAddress: String?,
        destinationAddress: String?,
        config: DriverFinancialConfig,
        calendar: Calendar = Calendar.getInstance()
    ): RodizioAssessment {
        if (!config.rodizioAlertEnabled) {
            return RodizioAssessment(isRodizio = false)
        }

        val plateDigit = extractFinalPlateDigit(config.vehiclePlate) ?: return RodizioAssessment(isRodizio = false)
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val restrictedDigitsToday = getDayRestrictedDigits(dayOfWeek)

        // Se hoje não for o dia de rodízio da placa do motorista, dispensa
        if (!restrictedDigitsToday.contains(plateDigit)) {
            return RodizioAssessment(
                isRodizio = false,
                finalDigit = plateDigit,
                restrictedDayName = getDayNameForDigit(plateDigit)
            )
        }

        // Se hoje é dia de rodízio, verifica se estamos na janela de horário pico
        if (!isRestrictedHour(calendar)) {
            return RodizioAssessment(
                isRodizio = false,
                finalDigit = plateDigit,
                restrictedDayName = getDayNameForDigit(plateDigit)
            )
        }

        val normOrigin = normalizeText(originAddress.orEmpty())
        val normDest = normalizeText(destinationAddress.orEmpty())

        val destInZone = isLocationInSpRodizioZone(normDest)
        val originInZone = isLocationInSpRodizioZone(normOrigin)

        if (!destInZone && !originInZone) {
            return RodizioAssessment(
                isRodizio = false,
                finalDigit = plateDigit,
                restrictedDayName = getDayNameForDigit(plateDigit)
            )
        }

        val matchType = when {
            destInZone && originInZone -> RodizioMatchType.BOTH
            destInZone -> RodizioMatchType.DESTINATION
            else -> RodizioMatchType.ORIGIN
        }

        val badge = when (matchType) {
            RodizioMatchType.DESTINATION -> "RODÍZIO NO DESTINO"
            RodizioMatchType.ORIGIN -> "RODÍZIO NA ORIGEM"
            RodizioMatchType.BOTH -> "RODÍZIO SP"
            RodizioMatchType.NONE -> null
        }

        val message = when (matchType) {
            RodizioMatchType.DESTINATION -> "⚠️ Alerta Rodízio SP: Destino dentro do Centro Expandido no horário restrito da sua placa final $plateDigit!"
            RodizioMatchType.ORIGIN -> "⚠️ Alerta Rodízio SP: Embarque dentro do Centro Expandido no horário restrito da sua placa final $plateDigit!"
            RodizioMatchType.BOTH -> "⚠️ Alerta Rodízio SP: Origem e Destino dentro do Centro Expandido no horário restrito da sua placa final $plateDigit!"
            RodizioMatchType.NONE -> null
        }

        return RodizioAssessment(
            isRodizio = true,
            matchType = matchType,
            badge = badge,
            message = message,
            finalDigit = plateDigit,
            restrictedDayName = getDayNameForDigit(plateDigit)
        )
    }

    private fun isLocationInSpRodizioZone(normalizedAddress: String): Boolean {
        if (normalizedAddress.length < 3) return false
        // Deve ser São Paulo / SP ou conter um dos bairros característicos do Centro Expandido
        for (kw in SP_CENTRO_EXPANDIDO_KEYWORDS) {
            val normKw = normalizeText(kw)
            val regex = Regex("""\b${Regex.escape(normKw)}\b""")
            if (regex.containsMatchIn(normalizedAddress) || (normKw.length >= 6 && normalizedAddress.contains(normKw))) {
                return true
            }
        }
        return false
    }

    private fun normalizeText(input: String): String {
        val nfd = Normalizer.normalize(input.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
        return nfd.replace(Regex("""\p{InCombiningDiacriticalMarks}+"""), "")
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
