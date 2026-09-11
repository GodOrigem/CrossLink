package br.origem.linkedplayers;

import java.util.UUID;

/** Pedido de vinculo aguardando confirmacao na outra conta. */
public record LinkRequest(
        String code,
        UUID requesterId,
        String requesterName,
        String targetName,
        long expiresAt
) {
    public boolean expired() { return System.currentTimeMillis() > expiresAt; }
}
