package com.creativeam.goorno.reader.ninetynine

/**
 * Entrar na lista Solicitações é um novo contexto visual, ainda que o
 * primeiro card tenha sido apresentado no mapa há poucos segundos.
 * A mesma lista inalterada não deve ser apresentada a cada polling.
 */
object NinetyNineListDisplayPolicy {
    fun shouldRefresh(wasViewingList: Boolean, previousSignature: String, currentSignature: String): Boolean =
        currentSignature.isNotBlank() && (!wasViewingList || previousSignature != currentSignature)
}
