package br.origem.linkedplayers;

import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.util.*;

/**
 * Vinculo self-service em duas etapas.
 *
 * A pessoa pede na conta A informando o nick da conta B, recebe um codigo,
 * entra na conta B e confirma. Isso prova que ela controla as duas contas --
 * sem essa prova, qualquer um poderia se vincular ao inventario alheio.
 */
public final class LinkService {

    private static final SecureRandom RNG = new SecureRandom();

    private final GroupManager groups;
    private final SyncEngine engine;
    private final Map<String, LinkRequest> byCode = new HashMap<>();
    private final long timeoutMillis;

    public LinkService(GroupManager groups, SyncEngine engine, long timeoutSeconds) {
        this.groups = groups;
        this.engine = engine;
        this.timeoutMillis = timeoutSeconds * 1000L;
    }

    public sealed interface Result {
        record CodeIssued(String code, String targetName, long seconds) implements Result {}
        record Linked(UUID otherId, String otherName, String group) implements Result {}
        record Error(String message) implements Result {}
    }

    /** Etapa 1: conta A pede vinculo com o nick da conta B. */
    public Result request(Player requester, String targetName) {
        if (targetName == null || targetName.isBlank()) {
            return new Result.Error("Informe o nick da outra conta.");
        }
        if (targetName.equalsIgnoreCase(requester.getName())) {
            return new Result.Error("Voce nao pode vincular uma conta com ela mesma.");
        }
        LinkGroup existing = groups.of(requester.getUniqueId());
        if (existing != null) {
            return new Result.Error("Sua conta ja esta vinculada (grupo '" + existing.name()
                    + "'). Peca a um admin para desfazer antes.");
        }
        purge();
        byCode.values().removeIf(r -> r.requesterId().equals(requester.getUniqueId()));

        String code = String.format("%06d", RNG.nextInt(1_000_000));
        byCode.put(code, new LinkRequest(code, requester.getUniqueId(), requester.getName(),
                targetName, System.currentTimeMillis() + timeoutMillis));
        return new Result.CodeIssued(code, targetName, timeoutMillis / 1000);
    }

    /** Etapa 2: conta B confirma com o codigo. */
    public Result confirm(Player confirmer, String code) {
        purge();
        LinkRequest req = byCode.get(code == null ? "" : code.trim());
        if (req == null) return new Result.Error("Codigo invalido ou expirado.");

        if (!req.targetName().equalsIgnoreCase(confirmer.getName())) {
            return new Result.Error("Esse codigo foi gerado para a conta '" + req.targetName()
                    + "', e voce esta em '" + confirmer.getName() + "'.");
        }
        if (req.requesterId().equals(confirmer.getUniqueId())) {
            return new Result.Error("As duas pontas sao a mesma conta.");
        }
        if (groups.of(confirmer.getUniqueId()) != null) {
            return new Result.Error("Sua conta ja esta vinculada.");
        }
        // A conta que pediu pode ter sido vinculada por um admin nesse meio tempo.
        if (groups.of(req.requesterId()) != null) {
            byCode.remove(code);
            return new Result.Error("A conta '" + req.requesterName() + "' foi vinculada enquanto isso.");
        }

        byCode.remove(code);

        String groupName = ("link-" + req.requesterName()).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "");
        LinkGroup g = groups.byName(groupName);
        if (g == null) g = groups.create(groupName);
        if (g == null) return new Result.Error("Nao consegui criar o grupo '" + groupName + "'.");

        groups.addMember(g, req.requesterId(), req.requesterName());
        groups.addMember(g, confirmer.getUniqueId(), confirmer.getName());
        groups.save();

        // Quem confirmou adota o estado de quem pediu -- nao o contrario.
        // Assim a conta ja jogada nao perde inventario para uma conta nova.
        engine.onJoin(confirmer);

        return new Result.Linked(req.requesterId(), req.requesterName(), g.name());
    }

    public void purge() { byCode.values().removeIf(LinkRequest::expired); }
}
