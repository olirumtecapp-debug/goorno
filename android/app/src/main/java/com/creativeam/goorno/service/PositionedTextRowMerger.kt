package com.creativeam.goorno.service

import kotlin.math.abs

/**
 * Agrupa fragmentos horizontais de uma mesma linha, sem apagar linhas
 * idênticas em posições diferentes da tela (ex.: dois "Escolher" na 99).
 */
object PositionedTextRowMerger {
    data class Fragment(
        val text: String,
        val centerY: Int,
        val leftX: Int
    )

    fun merge(nodes: List<Fragment>): List<String> {
        if (nodes.isEmpty()) return emptyList()
        val sorted = nodes.sortedWith(compareBy<Fragment>({ it.centerY }, { it.leftX }))
        val rows = mutableListOf<MutableList<Fragment>>()
        for (item in sorted) {
            val row = rows.lastOrNull()
            if (row != null && abs(row.first().centerY - item.centerY) <= 38) {
                if (row.none { it.text == item.text }) row.add(item)
            } else {
                rows.add(mutableListOf(item))
            }
        }
        return rows.mapNotNull { row ->
            row.sortedBy { it.leftX }.joinToString(" ") { it.text }
                .trim().takeIf { it.isNotEmpty() }
        }
    }
}
