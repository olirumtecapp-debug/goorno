package com.creativeam.goorno.detector

import com.creativeam.goorno.model.SourceApp
import java.util.Locale

/**
 * Estados do Classificador Central de Contexto de Tela (Conforme Especificação GoOrNo).
 */
enum class RideScreenContext {
    UBER_INCOMING_SINGLE_OFFER,
    UBER_RADAR_LIST,
    UBER_RADAR_SELECTED_OFFER,
    NINETYNINE_INCOMING_SINGLE_OFFER,
    NINETYNINE_REQUEST_LIST,
    NINETYNINE_SELECTED_MAP_OFFER,
    NO_OFFER,
    UNKNOWN;

    val shouldAnalyze: Boolean
        get() = this != UBER_RADAR_LIST &&
                this != NINETYNINE_REQUEST_LIST &&
                this != NO_OFFER
}

/**
 * Classificador Central de Contexto de Tela para Uber Driver e 99 Motorista.
 * Reconhece a estrutura da tela ANTES de autorizar qualquer processamento financeiro.
 */
object RideScreenContextClassifier {

    /**
     * Classifica o contexto da tela a partir do app alvo e das linhas de texto extraídas.
     */
    fun classify(
        targetApp: SourceApp,
        lines: List<String>
    ): RideScreenContext {
        if (lines.isEmpty()) return RideScreenContext.NO_OFFER

        val lowerLines = lines.map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotBlank() }
        val joined = lowerLines.joinToString(" ")

        // 1. Checagem de tela ociosa (sem corrida)
        val hasPrice = lowerLines.any { it.contains("r$") || it.startsWith("r$") }
        val isIdleScreen = (joined.contains("buscando solicitações") ||
                joined.contains("buscando solicitacoes") ||
                joined.contains("procurando viagens") ||
                joined.contains("você está online") ||
                joined.contains("voce esta online") ||
                joined.contains("mapa de chamadas") ||
                (joined.contains("conectar") && !hasPrice) ||
                (joined.contains("abastece") && !hasPrice))

        if (isIdleScreen && !hasPrice) {
            return RideScreenContext.NO_OFFER
        }

        return when (targetApp) {
            SourceApp.NINETY_NINE_DRIVER -> classifyNinetyNine(lowerLines, joined)
            SourceApp.UBER_DRIVER -> classifyUber(lowerLines, joined)
            else -> {
                if (joined.contains("99") || joined.contains("pop") || joined.contains("solicita")) {
                    classifyNinetyNine(lowerLines, joined)
                } else if (joined.contains("uber") || joined.contains("radar")) {
                    classifyUber(lowerLines, joined)
                } else {
                    RideScreenContext.UNKNOWN
                }
            }
        }
    }

    private fun classifyNinetyNine(
        lines: List<String>,
        joined: String
    ): RideScreenContext {
        // TELA 2: LISTA DE SOLICITAÇÕES DA 99
        // A lista possui cabeçalho de lista ("Solicitações", "Escolha uma", "Km até o embarque")
        // ou múltiplos botões "Escolher" / múltiplos cartões empilhados.
        val hasListHeader = lines.any {
            it == "solicitações" || it == "solicitacoes" ||
            it.startsWith("< solicita") || it.startsWith("solicitações") || it.startsWith("solicitacoes") ||
            it == "escolha uma" || it.contains("escolha uma") ||
            it.contains("km até o embarque") || it.contains("km ate o embarque") ||
            it.contains("lista de solicita")
        }

        val escolherCount = lines.count { it == "escolher" || it.startsWith("escolher") }
        
        // Conta apenas valores principais de corrida (ex: "R$ 14,40", "R$9,70"), ignorando taxas unitárias ("R$1,99/km", "+R$ 2,00")
        val mainPriceCount = lines.count { line ->
            line.contains("r$") && !line.contains("/km") && !line.contains("incl") && !line.contains("dinâmica") && !line.contains("dinamica")
        }

        // Se tiver cabeçalho de lista OU 2+ botões escolher OU 2+ corridas principais com escolher, É LISTA!
        if (hasListHeader || escolherCount >= 2 || (mainPriceCount >= 2 && escolherCount >= 1)) {
            return RideScreenContext.NINETYNINE_REQUEST_LIST
        }

        // TELA 3: DETALHE DA 99 PELO MAPINHA (Visualização individual no mapa com botão "Escolher")
        // Não é lista (hasListHeader é falso e escolherCount <= 1).
        // Possui o botão "escolher" E dados da corrida individual.
        val hasEscolherBtn = lines.any { it == "escolher" || it.startsWith("escolher") }
        val hasRideData = lines.any { it.contains("r$") } &&
                lines.any { it.contains("min") || it.contains("km") || it.contains(" m") }

        if (hasEscolherBtn && hasRideData) {
            return RideScreenContext.NINETYNINE_SELECTED_MAP_OFFER
        }

        // TELA 1: CHAMADA INDIVIDUAL AUTOMÁTICA DA 99 (Tocou no mapa com rota e tempo regressivo)
        val hasPaymentTag = joined.contains("pgto. no app") || joined.contains("pagamento no app") ||
                joined.contains("dinheiro") || joined.contains("tarifa base") || joined.contains("dinâmica")
        val hasRideCategory = joined.contains("pop") || joined.contains("99pop") || joined.contains("plus") ||
                joined.contains("comfort") || joined.contains("compartilhado")

        if (hasRideData) {
            return RideScreenContext.NINETYNINE_INCOMING_SINGLE_OFFER
        }

        return RideScreenContext.UNKNOWN
    }

    private fun classifyUber(
        lines: List<String>,
        joined: String
    ): RideScreenContext {
        // TELA DE RADAR DE VIAGENS DA UBER (LISTA GERAL DE DISPUTA COM VIAGENS UMA ACIMA DA OUTRA)
        // Só é classificado como lista de disputa se houver 2 ou mais corridas visíveis simultaneamente na vertical
        // (ex: 2+ botões "Tenho interesse" ou 2+ categorias/preços em lista de rolagem com cabeçalho de radar).
        // Se houver apenas 1 corrida ativa em exibição (mesmo com a pílula "Radar de Viagens 2" e cards empilhados por trás em profundidade),
        // DEVE SER LIDA COMO OFERTA INDIVIDUAL!
        val interestCount = lines.count {
            it == "tenho interesse" || it.contains("tenho interesse") || it.startsWith("tenho interesse")
        }

        val hasDisputeListHeader = lines.any {
            it.startsWith("← radar") || it.startsWith("< radar") ||
            it == "radar de viagens" || it == "radar de viagem"
        }

        val priceCount = lines.count { line ->
            line.contains("r$") && !line.contains("/km") && !line.contains("inclu")
        }

        // É a lista de disputa da Uber SOMENTE se houver 2+ botões de interesse OU
        // se tiver o cabeçalho de lista com 2+ preços visíveis na mesma tela (uma corrida em cima e outra embaixo)
        val isDisputeScrollList = interestCount >= 2 || (hasDisputeListHeader && priceCount >= 2)

        if (isDisputeScrollList) {
            return RideScreenContext.UBER_RADAR_LIST
        }

        // Caso contrário, é oferta individual (seja com botão "Aceitar" ou com "Tenho interesse" diretamente no mapa!)
        val hasRideData = lines.any { it.contains("r$") } &&
                lines.any { it.contains("min") || it.contains("km") }

        if (hasRideData) {
            return RideScreenContext.UBER_INCOMING_SINGLE_OFFER
        }

        return RideScreenContext.UNKNOWN
    }
}
