package com.creativeam.goorno.overlay

/**
 * Índice do próximo card visível. Nunca apaga os cards restantes da fila
 * quando o temporizador do card atual termina.
 */
object OfferQueueProgression {
    fun nextIndex(currentIndex: Int, queueSize: Int): Int? {
        if (queueSize <= 0 || currentIndex < 0 || currentIndex >= queueSize) return null
        val next = currentIndex + 1
        return if (next < queueSize) next else null
    }
}
