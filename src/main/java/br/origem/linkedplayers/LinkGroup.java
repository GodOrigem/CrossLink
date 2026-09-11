package br.origem.linkedplayers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Um conjunto de contas que dividem o mesmo estado. */
public final class LinkGroup {

    private final String name;
    /** UUID -> ultimo nome conhecido, so pra exibir em /plink list. */
    private final Map<UUID, String> members = new LinkedHashMap<>();
    private SharedState state = new SharedState();

    public LinkGroup(String name) { this.name = name; }

    public String name() { return name; }
    public Map<UUID, String> members() { return members; }
    public boolean has(UUID id) { return members.containsKey(id); }
    public void add(UUID id, String displayName) { members.put(id, displayName); }
    public void remove(UUID id) { members.remove(id); }

    public SharedState state() { return state; }
    public void state(SharedState s) { this.state = s; }
}
