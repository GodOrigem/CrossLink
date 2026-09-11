package br.origem.crosslink;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Um conjunto de contas que dividem o mesmo estado.
 *
 * Uma delas e a PRIMARIA e manda: e a dona dos dados. As secundarias
 * espelham, nunca sobrescrevem. Na pratica a primaria e a conta Java, porque
 * so ela existe na Mojang -- a conta Bedrock e um espelho.
 */
public final class LinkGroup {

    private final String name;
    private final Map<UUID, String> members = new LinkedHashMap<>();
    private UUID primary;
    private SharedState state = new SharedState();
    /** Ultima textura conhecida da conta Java, para reaplicar sem rede. */
    private String skinValue;
    private String skinSignature;

    public LinkGroup(String name) { this.name = name; }

    public String name() { return name; }
    public Map<UUID, String> members() { return members; }
    public boolean has(UUID id) { return members.containsKey(id); }
    public void add(UUID id, String displayName) { members.put(id, displayName); }
    public void remove(UUID id) {
        members.remove(id);
        if (id.equals(primary)) primary = members.isEmpty() ? null : members.keySet().iterator().next();
    }

    public UUID primary() { return primary; }
    public void primary(UUID id) { this.primary = id; }
    public boolean isPrimary(UUID id) { return id != null && id.equals(primary); }

    public String skinValue() { return skinValue; }
    public String skinSignature() { return skinSignature; }
    public boolean hasSkin() { return skinValue != null && !skinValue.isEmpty(); }
    public void skin(String value, String signature) {
        this.skinValue = value;
        this.skinSignature = signature;
    }

    public SharedState state() { return state; }
    public void state(SharedState s) { this.state = s; }
}
