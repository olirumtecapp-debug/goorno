package com.creativeam.goorno.reader

import com.creativeam.goorno.model.CaptureMethod
import com.creativeam.goorno.model.ExtractedField
import com.creativeam.goorno.model.ScreenClassification
import com.creativeam.goorno.model.SourceApp
import com.creativeam.goorno.model.StandardRideOffer
import java.util.Locale
import kotlin.math.abs

/**
 * Utilitário puro em Kotlin para conversão de moeda brasileira, distâncias (km),
 * durações (min/horas), reconstrução de nós de texto fragmentados na árvore de Acessibilidade
 * da 99/Uber (ex: ["R$", "8,10"] -> "R$ 8,10", ["5", "min", "(1,2", "km)"] -> "5 min (1,2 km)"),
 * extração de endereços (Origem/Destino) e separação de múltiplas ofertas na mesma tela.
 */
object BrazilianOfferParser {

    // Remove bônus de dinâmico com sinal de "+" (ex: "+R$ 1,00 incluído", "+R$ 6,25", "+ R$ 4,50")
    // que aparecem no mapa da Uber ou dentro do card do Radar de Viagens e NUNCA são o valor total da corrida.
    private val PLUS_SURGE_PRICE_REGEX = Regex(
        pattern = """\+\s*R\$\s*\d{1,3}(?:\.\d{3})*(?:\s*[.,]\s*\d{1,2})?(?:\s*(?:inclu[íi]do|incl\.?))?""",
        option = RegexOption.IGNORE_CASE
    )

    // Remove trechos que NÃO são o preço principal da oferta (ex: "R$2,18/km", "R$1,44/km aprox.", "R$ 1,16 por deslocamento", "R$3,06 Tarifa base dinâmica incl.", "R$ 45/h")
    private val NON_OFFER_PRICE_NOISE_REGEX = Regex(
        pattern = """R\$\s*\d{1,3}(?:[.,]\d{1,2})?\s*(?:/\s*km(?:\s*aprox\.?)?|/\s*h|por\s+km|por\s+deslocamento|de\s+deslocamento|incl\.|inclu[íi]do|aprox\.?|adicional|b[ôo]nus|tarifa\s+base|din[âa]mica|a\s+mais.*)""",
        option = RegexOption.IGNORE_CASE
    )

    // Ex: "R$ 12,50", "R$8,10", "R$ 1.250,00", "R$18,00", "R$ 24", "R$ 15,84"
    private val CURRENCY_REGEX = Regex(
        pattern = """(?<!\+)R\$\s*(\d{1,3}(?:\.\d{3})*(?:\s*[.,]\s*\d{1,2})?|\d+(?:\s*[.,]\s*\d{1,2})?)""",
        option = RegexOption.IGNORE_CASE
    )

    // Padrão clássico 99/Uber: "5 min (1,2 km)", "(6 min 1,3 km)", "3 min (459 m)" ou "1,2 km (5 min)"
    private val MIN_AND_KM_PAIR_REGEX = Regex(
        pattern = """\(?\s*\d{1,3}\s*min(?:utos?|s)?\s*\(?\s*\d{1,4}(?:[.,]\d{1,2})?\s*(?:km|m)\s*\)?""",
        option = RegexOption.IGNORE_CASE
    )

    // Ex: "3,8 km", "12 km", "(1,2 km)", "1,25 km" (mas não "/km" ou "R$2,18/km")
    private val KM_REGEX = Regex(
        pattern = """(?<![/\d.,])(\d{1,3}(?:[.,]\d{1,2})?)\s*km\b""",
        option = RegexOption.IGNORE_CASE
    )

    // Ex: "459 m", "(459 m)", "800m" (converte metros para quilômetros: 459 m -> 0.459 km)
    private val METERS_REGEX = Regex(
        pattern = """(?<![/\d.,])(\d{2,4})\s*m\b(?!in)""",
        option = RegexOption.IGNORE_CASE
    )

    // Token ordenado de distância (km ou m) na ordem exata em que aparece na tela
    private val DISTANCE_TOKEN_REGEX = Regex(
        pattern = """(?<![/\d.,])(\d{1,4}(?:[.,]\d{1,2})?)\s*(km|m\b(?!in))""",
        option = RegexOption.IGNORE_CASE
    )

    // Ex: "1 h 15 min", "1h 20m", "2h05min"
    private val HOURS_AND_MIN_REGEX = Regex(
        pattern = """(\d{1,2})\s*h(?:oras?)?\s*(?:e\s*)?(\d{1,2})\s*m(?:in(?:utos?)?)?\b""",
        option = RegexOption.IGNORE_CASE
    )

    // Ex: "14 min", "24 minutos", "5 min"
    private val MINUTES_REGEX = Regex(
        pattern = """(\d{1,3})\s*min(?:utos?|s)?\b""",
        option = RegexOption.IGNORE_CASE
    )

    // Ex: "1 hora", "2 horas" isoladas
    private val HOURS_ONLY_REGEX = Regex(
        pattern = """(\d{1,2})\s*horas?\b""",
        option = RegexOption.IGNORE_CASE
    )

    private val STREET_PREFIX_REGEX = Regex(
        pattern = """^(Rua|R\.|Av\.|Avenida|Alameda|Al\.|Travessa|Tv\.|Praça|Pç\.|Estrada|Estr\.|Rodovia|Rod\.|Via|Largo|Terminal|Aeroporto|Shopping|Condomínio|Hospital|Estação)(?:\b|\s).*""",
        option = RegexOption.IGNORE_CASE
    )

    private val LEADING_DURATION_DISTANCE_PREFIX_REGEX = Regex(
        pattern = """^[\s•·🟢🟠⬆️⬇️\(\)]*(?:\d{1,3}\s*min(?:utos?|s)?[\s\(\)·•-]*)*(?:\d{1,4}(?:[.,]\d{1,2})?\s*(?:km|m\b(?!in))[\s\(\)·•-]*)+""",
        option = RegexOption.IGNORE_CASE
    )

    private val PURE_NUMBER_START_REGEX = Regex("""^\(?(\d{1,4}(?:[.,]\d{1,2})?)\)?.*""")
    private val PURE_DECIMAL_PRICE_TOKEN_REGEX = Regex("""^\d{1,3}[.,]\d{2}$""")

    /**
     * Reconstrução inteligente de nós fragmentados pela árvore de Acessibilidade da 99 e Uber.
     * Ex: Na 99/Uber, o símbolo "R$" costuma ficar em um TextView menor e "15,84" em outro TextView maior ao lado;
     * ou "+" separado de "R$ 1,00" e "incluído"; ou "5" e "min" e "(1,2" e "km)" separados.
     */
    fun normalizeFragmentedNodes(rawNodes: List<String>): List<String> {
        val flatTokens = rawNodes
            .flatMap { it.replace('\u00A0', ' ').replace('\u202F', ' ').split("\n") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (flatTokens.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        var i = 0
        while (i < flatTokens.size) {
            var current = flatTokens[i]

            // Caso 0: Sinal "+" isolado antes de "R$" (ex: "+" + "R$ 1,00 incluído" -> "+R$ 1,00 incluído")
            if ((current == "+" || current.endsWith("+")) &&
                i + 1 < flatTokens.size &&
                flatTokens[i + 1].startsWith("R$", ignoreCase = true)
            ) {
                current = "$current${flatTokens[i + 1]}"
                i++
            }

            // Caso 0b: Número decimal de preço (ex: "15,84" com fonte gigante cujo topY veio antes) seguido de "R$" isolado
            if (PURE_DECIMAL_PRICE_TOKEN_REGEX.matches(current) &&
                i + 1 < flatTokens.size &&
                flatTokens[i + 1].equals("R$", ignoreCase = true)
            ) {
                current = "R$ $current"
                i++
            }

            // Caso 1: Nó atual é apenas "R$" (ou "+R$") ou termina com "R$" e o próximo começa com número (ex: "R$" + "15,84")
            if ((current.equals("R$", ignoreCase = true) ||
                    current.equals("+R$", ignoreCase = true) ||
                    current.endsWith("R$", ignoreCase = true)) &&
                i + 1 < flatTokens.size && PURE_NUMBER_START_REGEX.matches(flatTokens[i + 1])
            ) {
                current = "$current ${flatTokens[i + 1]}"
                i++
                // Se o próximo após o número for "/km", "por deslocamento", "incluído" ou "aprox", junta também para o filtro de ruído reconhecer!
                if (i + 1 < flatTokens.size) {
                    val nextLower = flatTokens[i + 1].lowercase(Locale.getDefault())
                    if (nextLower.startsWith("/km") ||
                        nextLower.startsWith("por deslocamento") ||
                        nextLower.startsWith("inclu") ||
                        nextLower.startsWith("aprox")
                    ) {
                        current = "$current ${flatTokens[i + 1]}"
                        i++
                    }
                }
            }

            // Caso 1b: Nó atual já tem "R$ X,XX" e o nó seguinte é "incluído", "/km" ou "aprox."
            if (current.contains("R$", ignoreCase = true) && i + 1 < flatTokens.size) {
                val nextLower = flatTokens[i + 1].lowercase(Locale.getDefault())
                if (nextLower.startsWith("inclu") ||
                    nextLower.startsWith("/km") ||
                    nextLower.startsWith("por deslocamento") ||
                    nextLower.startsWith("aprox")
                ) {
                    current = "$current ${flatTokens[i + 1]}"
                    i++
                }
            }

            // Caso 2: Nó atual é um número isolado (ex: "5" ou "(1,2" ou "(459") e o próximo é "min", "km", "m", "km)", "m)" ou "/km"
            if (i + 1 < flatTokens.size) {
                val next = flatTokens[i + 1].trim()
                val nextLower = next.lowercase(Locale.getDefault())
                if (PURE_NUMBER_START_REGEX.matches(current) &&
                    (nextLower == "km" || nextLower == "km)" || nextLower == "m" || nextLower == "m)" ||
                        nextLower == "min" || nextLower == "min)" || nextLower == "minutos" || nextLower.startsWith("/km"))
                ) {
                    current = "$current $next"
                    i++
                }
            }

            result.add(current)
            i++
        }
        return result
    }

    /**
     * Limpa ruídos como "+R$ 1,00 incluído", "+R$ 6,25", "R$1,44/km aprox." e "R$1,16 por deslocamento incl." antes de buscar o valor da oferta.
     */
    fun stripNonOfferPriceNoise(text: String): String {
        val withoutPlusSurge = PLUS_SURGE_PRICE_REGEX.replace(text, " ")
        return NON_OFFER_PRICE_NOISE_REGEX.replace(withoutPlusSurge, " ")
    }

    /**
     * Converte uma string contendo valor em Reais (ex: "R$ 1.250,50", "R$8,10" ou "R$ 18,00") para Double.
     */
    fun parseCurrencyBr(text: String?): ExtractedField<Double> {
        if (text.isNullOrBlank()) return ExtractedField.Missing
        val sanitized = stripNonOfferPriceNoise(text.replace('\u00A0', ' '))
        val match = CURRENCY_REGEX.find(sanitized)
        if (match == null) {
            return if (sanitized.contains("R$", ignoreCase = true)) {
                ExtractedField.Unrecognized(text.trim())
            } else {
                ExtractedField.Missing
            }
        }
        val rawNumber = match.groupValues[1].replace(" ", "").trim()
        val parsed = parseBrazilianDecimal(rawNumber)
            ?: return ExtractedField.Unrecognized(match.value)

        if (parsed < 0.0 || parsed > 10000.0) {
            return ExtractedField.Unrecognized(match.value)
        }
        return ExtractedField.Present(parsed)
    }

    /**
     * Extrai todos os valores monetários principais de oferta (ignorando R$/km, +R$ dinâmico e bônus de deslocamento incluso).
     */
    fun extractAllCurrencies(lines: List<String>): List<Double> {
        val normalized = normalizeFragmentedNodes(lines)
        val results = mutableListOf<Double>()
        for (line in normalized) {
            val lower = line.lowercase(Locale.getDefault())
            // Ignora linhas que são exclusivamente subtarifas ou bônus de dinâmica/deslocamento
            if (lower.contains("por deslocamento") ||
                lower.contains("deslocamento incl") ||
                lower.contains("tarifa base dinâmica") ||
                lower.contains("tarifa base dinamica") ||
                lower.contains("dinâmica incl") ||
                lower.contains("dinamica incl") ||
                lower.contains("a mais por corrida")
            ) {
                continue
            }
            val sanitized = stripNonOfferPriceNoise(line)
            CURRENCY_REGEX.findAll(sanitized).forEach { m ->
                val v = parseBrazilianDecimal(m.groupValues[1].replace(" ", ""))
                if (v != null && v >= 4.0 && v <= 5000.0) {
                    results.add(v)
                }
            }
        }
        return results
    }

    /**
     * Converte uma string de distância brasileira (ex: "3,8 km", "8 km", "459 m") em quilômetros.
     */
    fun parseDistanceKm(text: String?): ExtractedField<Double> {
        if (text.isNullOrBlank()) return ExtractedField.Missing
        val sanitized = stripNonOfferPriceNoise(text.replace('\u00A0', ' '))
        val kmMatch = KM_REGEX.find(sanitized)
        if (kmMatch != null) {
            val value = parseBrazilianDecimal(kmMatch.groupValues[1])
                ?: return ExtractedField.Unrecognized(kmMatch.value)
            if (value < 0.0 || value > 2000.0) {
                return ExtractedField.Unrecognized(kmMatch.value)
            }
            return ExtractedField.Present(value)
        }

        val mMatch = METERS_REGEX.find(sanitized)
        if (mMatch != null) {
            val meters = parseBrazilianDecimal(mMatch.groupValues[1])
            if (meters != null && meters in 10.0..9999.0) {
                return ExtractedField.Present(meters / 1000.0)
            }
        }

        return if (sanitized.contains("km", ignoreCase = true)) {
            ExtractedField.Unrecognized(text.trim())
        } else {
            ExtractedField.Missing
        }
    }

    data class RideLegInfo(
        val durationMin: Int,
        val distanceKm: Double,
        val address: String?
    )

    /**
     * Extrai todas as distâncias em km de uma lista de textos (incluindo conversão automática de metros como "459 m" -> 0.459 km).
     * Ignora ruídos de GPS de conversão de rua (ex: "↰ 61 m").
     */
    fun extractAllDistancesKm(lines: List<String>): List<Double> {
        val normalized = normalizeFragmentedNodes(lines)
        val list = mutableListOf<Double>()
        for (line in normalized) {
            val lower = line.lowercase(Locale.getDefault())
            // Ignora instruções isoladas de GPS de conversão de rua no topo da tela (ex: "↰ 61 m")
            if (lower.contains("↰") || lower.contains("↱") || lower.contains("↑") ||
                lower.contains("→") || lower.contains("←")
            ) {
                continue
            }
            val sanitized = stripNonOfferPriceNoise(line)
            val withoutPerKm = sanitized.replace(Regex("""\d+(?:[.,]\d+)?\s*/\s*km""", RegexOption.IGNORE_CASE), " ")
            DISTANCE_TOKEN_REGEX.findAll(withoutPerKm).forEach { m ->
                val rawNum = parseBrazilianDecimal(m.groupValues[1])
                val unit = m.groupValues[2].lowercase(Locale.ROOT)
                if (rawNum != null && rawNum > 0.0) {
                    if (unit == "km" && rawNum <= 2000.0) {
                        list.add(rawNum)
                    } else if (unit.startsWith("m") && rawNum in 20.0..9999.0) {
                        // Metros (ex: 459 m) só são distância de corrida se vierem entre parênteses "(459 m)"
                        // ou se a linha contiver "min", "embarque" ou "deslocamento"!
                        // Um "61 m" isolado sem min/parênteses é navegação GPS e deve ser descartado!
                        val isPairedWithTimeOrParens = sanitized.contains("(") || sanitized.contains("min") ||
                            sanitized.contains("embarque") || sanitized.contains("deslocamento")
                        if (isPairedWithTimeOrParens) {
                            list.add(rawNum / 1000.0)
                        }
                    }
                }
            }
        }
        return list
    }

    /**
     * Converte uma string de tempo (ex: "24 min", "5 min (1,2 km)", "1 h 15 min") para minutos totais.
     */
    fun parseDurationMinutes(text: String?): ExtractedField<Int> {
        if (text.isNullOrBlank()) return ExtractedField.Missing

        val hmMatch = HOURS_AND_MIN_REGEX.find(text)
        if (hmMatch != null) {
            val hours = hmMatch.groupValues[1].toIntOrNull()
            val mins = hmMatch.groupValues[2].toIntOrNull()
            if (hours != null && mins != null && hours >= 0 && mins in 0..59) {
                return ExtractedField.Present(hours * 60 + mins)
            }
            return ExtractedField.Unrecognized(hmMatch.value)
        }

        val minMatch = MINUTES_REGEX.find(text)
        if (minMatch != null) {
            val mins = minMatch.groupValues[1].toIntOrNull()
            if (mins != null && mins in 0..1440) {
                return ExtractedField.Present(mins)
            }
            return ExtractedField.Unrecognized(minMatch.value)
        }

        val hourOnlyMatch = HOURS_ONLY_REGEX.find(text)
        if (hourOnlyMatch != null) {
            val hours = hourOnlyMatch.groupValues[1].toIntOrNull()
            if (hours != null && hours in 1..24) {
                return ExtractedField.Present(hours * 60)
            }
        }

        if (text.contains("min", ignoreCase = true)) {
            return ExtractedField.Unrecognized(text.trim())
        }
        return ExtractedField.Missing
    }

    /**
     * Extrai todas as durações em minutos de uma lista de textos.
     */
    fun extractAllDurationsMinutes(lines: List<String>): List<Int> {
        val normalized = normalizeFragmentedNodes(lines)
        val results = mutableListOf<Int>()
        for (line in normalized) {
            val hmMatches = HOURS_AND_MIN_REGEX.findAll(line).toList()
            if (hmMatches.isNotEmpty()) {
                for (hm in hmMatches) {
                    val h = hm.groupValues[1].toIntOrNull() ?: 0
                    val m = hm.groupValues[2].toIntOrNull() ?: 0
                    val total = h * 60 + m
                    if (total in 1..1440) results.add(total)
                }
                continue
            }
            MINUTES_REGEX.findAll(line).forEach { m ->
                val v = m.groupValues[1].toIntOrNull()
                if (v != null && v in 1..1440) {
                    results.add(v)
                }
            }
        }
        return results
    }

    private val EXPLICIT_STAR_RATING_REGEX = Regex(
        pattern = """(?:[★⭐✪\*]|nota|avalia[çc][ãa]o)\s*[:\-]?\s*([34][.,]\d{1,2}|5[.,]0{1,2})\b|\b([34][.,]\d{1,2}|5[.,]0{1,2})\s*(?:[★⭐✪\*]|\(\d+\s*(?:corridas?|viagens?)?\)|[·•\-]\s*\d+\s*(?:corridas?|viagens?))""",
        option = RegexOption.IGNORE_CASE
    )

    private val TWO_DECIMAL_RATING_REGEX = Regex(
        pattern = """(?<![R\$\d.,/])([34][.,]\d{2}|5[.,]00)\b(?!\s*(?:km|m\b|min|h\b|x\b|/km|/h))""",
        option = RegexOption.IGNORE_CASE
    )

    private val ONE_DECIMAL_RATING_CONTEXT_REGEX = Regex(
        pattern = """(?<![R\$\d.,/])([34][.,]\d|5[.,]0)\b(?!\s*(?:km|m\b|min|h\b|x\b|/km|/h))""",
        option = RegexOption.IGNORE_CASE
    )

    /**
     * Extrai a Avaliação / Nota do Passageiro (ex: "★ 4,92", "4,85 (42 corridas)", "4.94", "⭐ 4,72")
     * ignorando preços (R$), distâncias (km) e multiplicadores de dinâmico (ex: 1,2x).
     */
    fun extractPassengerRating(lines: List<String>): Double? {
        val normalized = normalizeFragmentedNodes(lines)
        if (normalized.isEmpty()) return null

        // 1ª Passada: Padrão explícito com estrela (★ / ⭐), palavra "Nota" ou seguido de "(XX corridas)"
        for (line in normalized) {
            val clean = stripNonOfferPriceNoise(line)
                .replace(CURRENCY_REGEX, " ")
                .replace(DISTANCE_TOKEN_REGEX, " ")
                .replace(Regex("""\d+[.,]\d+\s*x\b""", RegexOption.IGNORE_CASE), " ")

            val explicitMatch = EXPLICIT_STAR_RATING_REGEX.find(clean)
            if (explicitMatch != null) {
                val token = explicitMatch.groupValues[1].ifBlank { explicitMatch.groupValues[2] }
                val rating = parseBrazilianDecimal(token)
                if (rating != null && rating in 3.00..5.00) {
                    return rating
                }
            }
        }

        // 2ª Passada: Número decimal típico de avaliação (ex: 4,92 / 4,85 / 4,68 / 5,00) fora de preço/km/dinâmico
        for (line in normalized) {
            val lineLower = line.lowercase(Locale.getDefault())
            if (line.contains("R$") ||
                lineLower.contains("posto") ||
                lineLower.contains("abastece") ||
                lineLower.contains("combust") ||
                lineLower.contains("desconto")
            ) {
                continue
            }

            val clean = stripNonOfferPriceNoise(line)
                .replace(CURRENCY_REGEX, " ")
                .replace(DISTANCE_TOKEN_REGEX, " ")
                .replace(Regex("""\d+[.,]\d+\s*x\b""", RegexOption.IGNORE_CASE), " ")

            val twoDecMatch = TWO_DECIMAL_RATING_REGEX.find(clean)
            if (twoDecMatch != null) {
                val rating = parseBrazilianDecimal(twoDecMatch.groupValues[1])
                // Sem contexto explícito de estrela/corridas, notas válidas ficam entre 4.00 e 5.00
                // (evita que preços como R$ 3,39 / 3,59 sejam interpretados como avaliação do passageiro)
                if (rating != null && rating in 4.00..5.00) {
                    return rating
                }
            }

            // Linha curta ou com contexto de passageiro ("corridas", "viagens", "verificado", "novo", "perfil") com 1 casa (ex: "4,9")
            val lower = clean.lowercase(Locale.getDefault())
            if (clean.trim().length <= 6 ||
                lower.contains("corrida") ||
                lower.contains("viagen") ||
                lower.contains("verificad") ||
                lower.contains("passageir") ||
                lower.contains("usuári")
            ) {
                val oneDecMatch = ONE_DECIMAL_RATING_CONTEXT_REGEX.find(clean)
                if (oneDecMatch != null) {
                    val rating = parseBrazilianDecimal(oneDecMatch.groupValues[1])
                    if (rating != null && rating in 3.50..5.00) {
                        return rating
                    }
                }
            }
        }

        return null
    }

    /**
     * Limpa candidato a endereço removendo botões de ação e descartando sujeira de UI/sistema.
     */
    fun cleanAddressCandidate(rawText: String): String? {
        var clean = rawText.trim()
        if (clean.length < 4) return null

        val lower = clean.lowercase(Locale.getDefault())

        // Descarta linhas puras de controle ou notificações de sistema do Android
        if (lower.contains("silencioso está ativado") || lower.contains("retornar chamada") ||
            lower.contains("não afeta a ta") || lower.contains("nao afeta a ta") ||
            lower.contains("taxa de aceitação") || lower.contains("taxa de cancelamento") ||
            lower.contains("perfil essencial") || lower.contains("perfil premium") ||
            lower.contains("tarifa base dinâmica") || lower.contains("alta demanda") ||
            lower.contains("em andamento") || lower.contains("buscando...") ||
            lower.contains("segure p/ mover") || lower.contains("toque em qualquer") ||
            lower.contains("os empos das cardes") || lower.contains("tempo dos cards") ||
            lower.contains("solicitações") || lower.contains("solicitacoes") ||
            lower.contains("radar de viagens")
        ) {
            return null
        }

        // Se a linha tem um botão de ação no final (ex: "Zanela, 90, Centro Escolher R$9,60 1k" ou "Escolher"),
        // corta o texto no início do botão para salvar o endereço real!
        val actionMatch = Regex("""\b(escolher|selecionar|aceitar|recusar|rejeitar)\b""", RegexOption.IGNORE_CASE).find(clean)
        if (actionMatch != null) {
            clean = clean.substring(0, actionMatch.range.first).trim()
        }

        // Remove resquícios de preços e distâncias coladas
        clean = clean.replace(Regex("""R\s*[\$5§]\s*\d+.*""", RegexOption.IGNORE_CASE), "").trim()
        clean = clean.replace(Regex("""\b\d+k\b""", RegexOption.IGNORE_CASE), "").trim()
        clean = clean.replace(Regex("""\s+"""), " ").trim()
        clean = clean.trimEnd(',', '-', '·', '•', ' ')

        if (clean.length < 5) return null
        return clean
    }

    /**
     * Extrai os trechos da corrida (Embarque e Desembarque) diretamente dos nós da 99 e Uber,
     * ancorando os endereços logo após seus respectivos indicadores de tempo/distância.
     */
    fun extractRideLegs(lines: List<String>): List<RideLegInfo> {
        val normalized = normalizeFragmentedNodes(lines)
        val legs = mutableListOf<RideLegInfo>()

        var i = 0
        while (i < normalized.size) {
            val line = normalized[i].trim()
            val lower = line.lowercase(Locale.getDefault())

            // Ignora instruções isoladas de GPS do topo da tela (ex: "↰ 61 m")
            if (lower.contains("↰") || lower.contains("↱") || lower.contains("↑") ||
                lower.contains("→") || lower.contains("←")
            ) {
                i++
                continue
            }

            val durations = extractAllDurationsMinutes(listOf(line))
            val distances = extractAllDistancesKm(listOf(line))

            if (durations.isNotEmpty() && distances.isNotEmpty()) {
                val durMin = durations.first()
                val distKm = distances.first()

                // 1. Tenta pegar o endereço no restante da MESMA linha
                val strippedPrefix = LEADING_DURATION_DISTANCE_PREFIX_REGEX.replace(line, "").trim()
                var legAddress: String? = null

                if (strippedPrefix.length >= 5 && (STREET_PREFIX_REGEX.matches(strippedPrefix) || strippedPrefix.contains(","))) {
                    var fullAddr = strippedPrefix
                    var rawLast = strippedPrefix
                    while (i + 1 < normalized.size) {
                        val nextLine = normalized[i + 1].trim()
                        val nextLower = nextLine.lowercase(Locale.getDefault())
                        val isLegMarker = (extractAllDurationsMinutes(listOf(nextLine)).isNotEmpty() &&
                            extractAllDistancesKm(listOf(nextLine)).isNotEmpty()) ||
                            LEADING_DURATION_DISTANCE_PREFIX_REGEX.containsMatchIn(nextLine)
                        val isOfferBoundary = isLegMarker ||
                            nextLower.contains("pgto") ||
                            nextLower.contains("uber") ||
                            nextLower.contains("99") ||
                            nextLower.contains("r$") ||
                            nextLower.contains("escolher") ||
                            nextLower.contains("selecionar") ||
                            nextLower.contains("aceitar") ||
                            nextLower.contains("recusar") ||
                            nextLower.contains("verificado") ||
                            nextLower.contains("perfil") ||
                            nextLower.contains("corridas") ||
                            nextLower.contains("deslocamento") ||
                            STREET_PREFIX_REGEX.matches(nextLine)

                        if (isOfferBoundary) break

                        val cleanedNext = cleanAddressCandidate(nextLine)
                        if (cleanedNext != null && cleanedNext.length in 2..50) {
                            val separator = if (rawLast.endsWith(",") || fullAddr.endsWith(",")) ", " else " "
                            fullAddr = "$fullAddr$separator$cleanedNext".replace(Regex(""",\s*,"""), ",").replace(Regex("""\s+"""), " ")
                            rawLast = nextLine
                            i++
                        } else {
                            break
                        }
                    }
                    legAddress = cleanAddressCandidate(fullAddr)
                } else if (i + 1 < normalized.size) {
                    // 2. Se a linha era apenas "5 min (1,2 km)", o endereço está na PRÓXIMA linha!
                    val rawNext = normalized[i + 1].trim()
                    val initialCand = cleanAddressCandidate(rawNext)
                    if (initialCand != null) {
                        var cand: String = initialCand
                        var rawLast = rawNext
                        i++
                        // Verifica continuação na linha seguinte (bairro, complemento ou cidade)
                        while (i + 1 < normalized.size) {
                            val nextNext = normalized[i + 1].trim()
                            val nextNextLower = nextNext.lowercase(Locale.getDefault())
                            val isLegMarker = (extractAllDurationsMinutes(listOf(nextNext)).isNotEmpty() &&
                                extractAllDistancesKm(listOf(nextNext)).isNotEmpty()) ||
                                LEADING_DURATION_DISTANCE_PREFIX_REGEX.containsMatchIn(nextNext)
                            val isOfferBoundary = isLegMarker ||
                                nextNextLower.contains("pgto") ||
                                nextNextLower.contains("uber") ||
                                nextNextLower.contains("99") ||
                                nextNextLower.contains("r$") ||
                                nextNextLower.contains("escolher") ||
                                nextNextLower.contains("selecionar") ||
                                nextNextLower.contains("aceitar") ||
                                nextNextLower.contains("recusar") ||
                                nextNextLower.contains("verificado") ||
                                nextNextLower.contains("perfil") ||
                                nextNextLower.contains("corridas") ||
                                nextNextLower.contains("deslocamento") ||
                                STREET_PREFIX_REGEX.matches(nextNext)

                            if (isOfferBoundary) break

                            val cleanedCont = cleanAddressCandidate(nextNext)
                            if (cleanedCont != null && cleanedCont.length in 2..50) {
                                val separator = if (rawLast.endsWith(",") || cand.endsWith(",")) ", " else " "
                                cand = "$cand$separator$cleanedCont".replace(Regex(""",\s*,"""), ",").replace(Regex("""\s+"""), " ")
                                rawLast = nextNext
                                i++
                            } else {
                                break
                            }
                        }
                        legAddress = cand
                    }
                }

                legs.add(RideLegInfo(durMin, distKm, legAddress))
            }
            i++
        }
        return legs
    }

    /**
     * Extrai endereços de Origem (Embarque) e Destino (Desembarque) das linhas capturadas,
     * suportando ancoragem posicional nos trechos de tempo/distância da 99 e Uber,
     * bem como prefixos explícitos e o formato da 99 Solicitações.
     */
    fun extractOriginAndDestination(lines: List<String>): Pair<String?, String?> {
        val normalized = normalizeFragmentedNodes(lines)

        // 1ª Tentativa de Alta Precisão: Ancoragem direta nos trechos de corrida
        val legs = extractRideLegs(normalized)
        if (legs.size >= 2 && !legs[0].address.isNullOrBlank() && !legs[1].address.isNullOrBlank()) {
            return Pair(legs[0].address, legs[1].address)
        }

        var explicitOrigin: String? = legs.getOrNull(0)?.address
        var explicitDestination: String? = legs.getOrNull(1)?.address
        val streetCandidates = mutableListOf<String>()

        var i = 0
        while (i < normalized.size) {
            val rawLine = normalized[i].trim()
            if (rawLine.length < 4) {
                i++
                continue
            }
            val lower = rawLine.lowercase(Locale.getDefault())

            if (lower.startsWith("origem:") || lower.startsWith("embarque:") || lower.startsWith("buscar em:")) {
                val afterColon = rawLine.substringAfter(":").trim()
                val cleaned = cleanAddressCandidate(afterColon)
                if (cleaned != null) {
                    explicitOrigin = cleaned
                } else if (i + 1 < normalized.size) {
                    explicitOrigin = cleanAddressCandidate(normalized[i + 1])
                }
                i++
                continue
            }

            if (lower.startsWith("destino:") || lower.startsWith("desembarque:") || lower.startsWith("levar até:")) {
                val afterColon = rawLine.substringAfter(":").trim()
                val cleaned = cleanAddressCandidate(afterColon)
                if (cleaned != null) {
                    explicitDestination = cleaned
                } else if (i + 1 < normalized.size) {
                    explicitDestination = cleanAddressCandidate(normalized[i + 1])
                }
                i++
                continue
            }

            if (lower.contains("corridas") || lower.contains("deslocamento") ||
                lower.contains("pgto.") || lower.contains("perfil ") ||
                lower.contains("alta demanda") || lower.contains("escolha uma") ||
                lower.contains("km até o embarque") || lower.contains("isso é tudo")
            ) {
                i++
                continue
            }

            // Se a linha começa com "(6 min 1,3 km) Rua dos Vianas, 1545", removemos o prefixo "(6 min 1,3 km)" para preservar o endereço!
            val strippedPrefix = LEADING_DURATION_DISTANCE_PREFIX_REGEX.replace(rawLine, "").trim()
            val candidate = if (strippedPrefix.length >= 5 && STREET_PREFIX_REGEX.matches(strippedPrefix)) {
                strippedPrefix
            } else {
                rawLine
            }

            // Se ainda sobrou apenas tempo/distância pura sem nome de rua, pula
            if (KM_REGEX.containsMatchIn(candidate) || METERS_REGEX.containsMatchIn(candidate) || MINUTES_REGEX.containsMatchIn(candidate)) {
                i++
                continue
            }

            val cleanedCandidate = cleanAddressCandidate(candidate)
            if (cleanedCandidate != null && (STREET_PREFIX_REGEX.matches(cleanedCandidate) ||
                (cleanedCandidate.length >= 10 && cleanedCandidate.contains(",") && cleanedCandidate.any { it.isDigit() } &&
                    cleanedCandidate.any { it.isLetter() } &&
                    !cleanedCandidate.contains("R$", ignoreCase = true)))
            ) {
                var fullAddress = cleanedCandidate
                // Verifica se a linha seguinte é a continuação do bairro/número (ex: "de Souza, 72, Vila Industrial" ou "Baeta Neves")
                if (i + 1 < normalized.size) {
                    val nextLine = normalized[i + 1].trim()
                    val cleanedNext = cleanAddressCandidate(nextLine)
                    val nextLower = nextLine.lowercase(Locale.getDefault())
                    val isOfferBoundary = nextLower.contains("pgto") ||
                        nextLower.contains("pop") ||
                        nextLower.contains("uber") ||
                        nextLower.contains("escolha") ||
                        nextLower.contains("escolher") ||
                        nextLower.contains("selecionar") ||
                        nextLower.contains("aceitar") ||
                        nextLower.contains("verificado") ||
                        nextLower.contains("isso é tudo") ||
                        nextLower.contains("corridas") ||
                        nextLower.contains("perfil ") ||
                        nextLower.contains("deslocamento")
                    val isNextContinuation = cleanedNext != null && cleanedNext.length in 3..45 &&
                        !isOfferBoundary &&
                        !STREET_PREFIX_REGEX.matches(cleanedNext) &&
                        !LEADING_DURATION_DISTANCE_PREFIX_REGEX.containsMatchIn(nextLine) &&
                        !nextLine.contains("R$", ignoreCase = true) &&
                        !KM_REGEX.containsMatchIn(nextLine) &&
                        !METERS_REGEX.containsMatchIn(nextLine) &&
                        !MINUTES_REGEX.containsMatchIn(nextLine)

                    val looksIncompleteFirstLine = fullAddress.endsWith(",") ||
                        fullAddress.lowercase(Locale.getDefault()).endsWith(" dos") ||
                        fullAddress.lowercase(Locale.getDefault()).endsWith(" das") ||
                        fullAddress.lowercase(Locale.getDefault()).endsWith(" de") ||
                        fullAddress.lowercase(Locale.getDefault()).endsWith(" do") ||
                        fullAddress.lowercase(Locale.getDefault()).endsWith(" da") ||
                        !fullAddress.any { it.isDigit() }

                    if (isNextContinuation && (looksIncompleteFirstLine || nextLine.contains(","))) {
                        fullAddress = "$fullAddress $cleanedNext".replace(Regex("""\s+"""), " ")
                        i++
                    }
                }

                if (!streetCandidates.contains(fullAddress)) {
                    streetCandidates.add(fullAddress)
                }
            }
            i++
        }

        val origin = explicitOrigin ?: streetCandidates.getOrNull(0)
        val destination = explicitDestination ?: streetCandidates.getOrNull(1)
        return Pair(origin, destination)
    }

    /**
     * Separa os nós de texto de uma tela em MÚLTIPLOS blocos quando há mais de uma oferta visível
     * ao mesmo tempo (ex: Lista de Ofertas da 99 ou Radar de Viagens da Uber).
     */
    fun splitIntoMultipleOfferClusters(rawNodes: List<String>): List<List<String>> {
        val normalized = normalizeFragmentedNodes(rawNodes)
        if (normalized.isEmpty()) return emptyList()

        val joinedAll = normalized.joinToString(" ").lowercase(Locale.getDefault())

        // 1. Rejeição imediata se for tela ociosa da 99 ("Ainda buscando solicitações") sem botões de oferta
        val hasAction = joinedAll.contains("escolher") || joinedAll.contains("aceitar") || joinedAll.contains("selecionar")
        if ((joinedAll.contains("buscando solicitações") || joinedAll.contains("buscando solicitacoes") ||
             joinedAll.contains("ainda buscando") || joinedAll.contains("mapa de chamadas")) && !hasAction) {
            return emptyList()
        }

        // Se a tela possui o cabeçalho "Radar de viagens" ou "Solicitações", ignora qualquer texto de mapa/ganhos do fundo
        val listHeaderIdx = normalized.indexOfFirst { line ->
            val l = line.lowercase(Locale.getDefault())
            l.contains("radar de viagens") || l == "solicitações" || l == "solicitacoes"
        }
        val cleaned = if (listHeaderIdx > 0 && listHeaderIdx < normalized.size - 1) {
            normalized.subList(listHeaderIdx, normalized.size)
        } else {
            normalized
        }

        // 2. Fatiamento de ALTA PRECISÃO para lista de solicitações da 99 usando o delimitador "Escolher"
        val chooseIndices = mutableListOf<Int>()
        for (i in cleaned.indices) {
            val l = cleaned[i].trim().lowercase(Locale.getDefault())
            if (l == "escolher" || l.startsWith("escolher")) {
                chooseIndices.add(i)
            }
        }

        if (chooseIndices.size >= 1) {
            val clusters = mutableListOf<List<String>>()
            var start = 0
            for (chooseIdx in chooseIndices) {
                val end = chooseIdx + 1
                if (end > start) {
                    val slice = cleaned.subList(start, end)
                    if (extractAllDistancesKm(slice).isNotEmpty() && extractAllCurrencies(slice).isNotEmpty()) {
                        clusters.add(slice)
                    }
                }
                start = end
            }
            if (clusters.isNotEmpty()) return clusters
        }

        // 3. Fatiamento por categorias da 99 ou preços para outras telas com múltiplas ofertas
        val offerHeaderIndices = mutableListOf<Int>()
        for (i in cleaned.indices) {
            val line = cleaned[i]
            val lower = line.lowercase(Locale.getDefault())
            if (lower.contains("por deslocamento") ||
                lower.contains("deslocamento incl") ||
                lower.contains("tarifa base dinâmica") ||
                lower.contains("tarifa base dinamica") ||
                lower.contains("dinâmica incl") ||
                lower.contains("dinamica incl") ||
                lower.contains("a mais por corrida") ||
                lower.contains("meta") ||
                lower.contains("saldo") ||
                lower.contains("ganhos") ||
                lower.contains("carteira")
            ) {
                continue
            }
            val pricesInLine = extractAllCurrencies(listOf(line))
            if (pricesInLine.isNotEmpty()) {
                offerHeaderIndices.add(i)
            }
        }

        if (offerHeaderIndices.size <= 1) {
            return listOf(cleaned)
        }

        val clusters = mutableListOf<List<String>>()
        for (idxPos in offerHeaderIndices.indices) {
            val startIdx = (offerHeaderIndices[idxPos] - 1).coerceAtLeast(0)
            val endIdx = if (idxPos + 1 < offerHeaderIndices.size) {
                offerHeaderIndices[idxPos + 1]
            } else {
                cleaned.size
            }
            if (startIdx < endIdx) {
                val slice = cleaned.subList(startIdx, endIdx)
                if (extractAllDistancesKm(slice).isNotEmpty()) {
                    clusters.add(slice)
                }
            }
        }

        return if (clusters.isNotEmpty()) clusters else listOf(cleaned)
    }

    /**
     * Detecta automaticamente se os textos da tela pertencem a uma chamada da 99 ou da Uber.
     */
    fun inferSourceAppFromScreenContent(packageName: String?, lines: List<String>): SourceApp {
        val byPkg = SourceApp.fromPackageName(packageName)
        if (byPkg != SourceApp.UNKNOWN) return byPkg

        val pkgLower = packageName?.lowercase(Locale.ROOT) ?: ""
        if (pkgLower.contains("com.creativeam.goorno") ||
            pkgLower.contains("systemui") ||
            pkgLower.contains("settings") ||
            pkgLower.contains("whatsapp") ||
            pkgLower.contains("launcher")
        ) {
            return SourceApp.UNKNOWN
        }

        val normalized = normalizeFragmentedNodes(lines)
        val joined = normalized.joinToString(" | ").lowercase(Locale.getDefault())

        if (joined.contains("por deslocamento incl") ||
            joined.contains("pgto. no app") ||
            joined.contains("perfil essencial") ||
            joined.contains("99pop") ||
            joined.contains("99plus") ||
            joined.contains("negocia") ||
            joined.contains("poupa99")
        ) {
            return SourceApp.NINETY_NINE_DRIVER
        }

        if (joined.contains("uberx") ||
            joined.contains("uber comfort") ||
            joined.contains("uber black") ||
            joined.contains("uber flash") ||
            joined.contains("radar de viagens") ||
            joined.contains("selecionar")
        ) {
            return SourceApp.UBER_DRIVER
        }

        // SÓ infere 99 se houver botões ou termos inequívocos de corrida do app 99
        val has99Action = joined.contains("escolher") || joined.contains("pop") || joined.contains("99pop") || joined.contains("perfil essencial")
        val hasKm = extractAllDistancesKm(normalized).isNotEmpty()
        val hasPrice = extractAllCurrencies(normalized).isNotEmpty()
        if (has99Action && hasKm && hasPrice) {
            return SourceApp.NINETY_NINE_DRIVER
        }

        return SourceApp.UNKNOWN
    }

    /**
     * Converte número decimal no padrão brasileiro ("1.250,50" -> 1250.50, "3,8" -> 3.8).
     */
    fun parseBrazilianDecimal(raw: String): Double? {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return null

        val normalized = when {
            cleaned.contains(".") && cleaned.contains(",") -> {
                cleaned.replace(".", "").replace(",", ".")
            }
            cleaned.contains(",") -> {
                cleaned.replace(",", ".")
            }
            cleaned.contains(".") -> {
                val parts = cleaned.split(".")
                if (parts.size == 2 && parts[1].length == 3 && parts[0].isNotEmpty()) {
                    cleaned.replace(".", "")
                } else {
                    cleaned
                }
            }
            else -> cleaned
        }
        return normalized.toDoubleOrNull()
    }

    /**
     * Gera assinatura única (fingerprint) da oferta para evitar recálculos e registros duplicados.
     */
    fun buildFingerprint(
        sourceAppName: String,
        price: Double?,
        totalKm: Double?,
        totalMin: Int?,
        origin: String?,
        destination: String?
    ): String {
        val p = price?.let { String.format(Locale.US, "%.2f", it) } ?: "na"
        val k = totalKm?.let { String.format(Locale.US, "%.1f", it) } ?: "na"
        val m = totalMin?.toString() ?: "na"
        val o = origin?.trim()?.lowercase(Locale.ROOT)?.take(18) ?: ""
        val d = destination?.trim()?.lowercase(Locale.ROOT)?.take(18) ?: ""
        return "$sourceAppName|$p|$k|$m|$o|$d"
    }

    fun isSameOrUpdatedOffer(
        previous: StandardRideOffer?,
        current: StandardRideOffer,
        dedupWindowMillis: Long = 12_000L
    ): DuplicateCheckResult {
        if (previous == null) return DuplicateCheckResult.NEW_OFFER
        val elapsed = abs(current.timestampMillis - previous.timestampMillis)
        if (elapsed > dedupWindowMillis) return DuplicateCheckResult.NEW_OFFER

        if (previous.dedupFingerprint == current.dedupFingerprint && current.dedupFingerprint.isNotBlank()) {
            return DuplicateCheckResult.EXACT_DUPLICATE
        }

        val sameApp = previous.sourceApp == current.sourceApp
        val prevKm = previous.totalDistanceKmExplicit ?: ((previous.pickupDistanceKm ?: 0.0) + (previous.tripDistanceKm ?: 0.0))
        val currKm = current.totalDistanceKmExplicit ?: ((current.pickupDistanceKm ?: 0.0) + (current.tripDistanceKm ?: 0.0))
        val sameDistance = prevKm > 0 && currKm > 0 && abs(prevKm - currKm) < 0.25
        val priceChanged = previous.offeredPrice != null && current.offeredPrice != null &&
            abs(previous.offeredPrice!! - current.offeredPrice!!) >= 0.10

        if (sameApp && sameDistance && priceChanged) {
            return DuplicateCheckResult.UPDATED_OFFER
        }

        return DuplicateCheckResult.NEW_OFFER
    }

    enum class DuplicateCheckResult {
        NEW_OFFER,
        UPDATED_OFFER,
        EXACT_DUPLICATE
    }

    /**
     * Identifica e filtra anúncios que NÃO são ofertas reais de passageiros, como:
     * - "99 Abastece" / Descontos em Postos de Combustível (ex: Auto Posto Malu dos Vianas, R$ 3,39, 1.3 km)
     * - Troca de óleo, lava rápido, pneus, seguros, empréstimos, cartões ou indicações
     * - Telas em modo de busca ocioso ("Buscando", "Procurando viagens") sem botões de ação de chamada.
     */
    fun isNonRideAdOrService(lines: List<String>): Boolean {
        if (lines.isEmpty()) return false
        val joined = lines.joinToString(" ").lowercase(Locale.ROOT)

        // 1. Palavras-chave explícitas de postos, combustíveis e serviços automotivos
        val nonRideKeywords = listOf(
            "abastece", "abasteça", "abastecer", "abastecimento",
            "auto posto", "autoposto", "posto de gasolina", "posto de combustível", "posto de combustivel",
            "combustível", "combustivel", "gasolina", "etanol", "diesel", "gnv",
            "desconto no combustível", "desconto combustível", "desconto combustivel",
            "troca de óleo", "troca de oleo", "lava rápido", "lava rapido",
            "loja 99", "99pay", "uber pro card", "empréstimo", "emprestimo",
            "indique e ganhe", "compre com desconto", "compre seu carro", "aluguel de carro",
            "fatura", "recarga de celular", "seguro auto"
        )
        if (nonRideKeywords.any { joined.contains(it) }) {
            return true
        }

        val hasRideAction = joined.contains("escolher") ||
            joined.contains("aceitar") ||
            joined.contains("selecionar") ||
            joined.contains("deslize") ||
            joined.contains("nova corrida")

        // 2. Ocorrência de "posto" isolado sem botão de ação de corrida
        if ((joined.contains("posto ") || joined.contains(" postos") || joined.startsWith("posto")) &&
            !hasRideAction && !joined.contains("embarque")
        ) {
            return true
        }

        // 3. Telas em modo de busca ("Buscando" / "Procurando viagens") sem chamada ativa
        val isSearchingOrIdle = joined.contains("buscando") ||
            joined.contains("procurando viagens") ||
            joined.contains("procurando passageiros") ||
            joined.contains("você está online") ||
            joined.contains("voce esta online")

        if (isSearchingOrIdle && !hasRideAction) {
            return true
        }

        // 4. Auto-leitura da própria interface do GoOrNo (quando não há botão de ação do app de mobilidade)
        val isGoOrNoSelfText = (joined.contains("goorno") || joined.contains("r$/km") || joined.contains("r$/hora") || joined.contains("suas faixas")) && !hasRideAction
        if (isGoOrNoSelfText) {
            return true
        }

        return false
    }
}
