package br.origem.crosslink.compat;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * APIs que mudaram de nome ou de lugar entre as versoes suportadas.
 * Tudo por reflexao para caber num jar unico de 1.18 ate 26.x.
 */
public final class Compat {

    private Compat() {}

    /**
     * Vida maxima do jogador.
     *
     * A constante do atributo foi renomeada: GENERIC_MAX_HEALTH ate a 1.21.2,
     * MAX_HEALTH da 1.21.3 em diante. Referenciar qualquer uma das duas em
     * codigo daria NoSuchFieldError na outra ponta do alcance, entao a busca e
     * pelo nome em tempo de execucao.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static double maxHealth(Player p) {
        // Caminho antigo e direto, presente desde sempre (embora depreciado).
        try {
            Method m = p.getClass().getMethod("getMaxHealth");
            Object v = m.invoke(p);
            if (v instanceof Number n && n.doubleValue() > 0) return n.doubleValue();
        } catch (Exception ignored) { }

        try {
            Class<?> attrClass = Class.forName("org.bukkit.attribute.Attribute");
            Object attr = null;
            for (String nome : new String[]{"MAX_HEALTH", "GENERIC_MAX_HEALTH"}) {
                try {
                    attr = Enum.valueOf((Class<Enum>) attrClass, nome);
                    break;
                } catch (IllegalArgumentException ignored) {
                    try { attr = attrClass.getField(nome).get(null); break; }
                    catch (Exception ignored2) { }
                }
            }
            if (attr == null) return 20.0;
            Method getAttribute = p.getClass().getMethod("getAttribute", attrClass);
            Object inst = getAttribute.invoke(p, attr);
            if (inst == null) return 20.0;
            Object v = inst.getClass().getMethod("getValue").invoke(inst);
            if (v instanceof Number n) return n.doubleValue();
        } catch (Exception ignored) { }

        return 20.0;
    }

    /**
     * UUID do dono de um animal domado.
     *
     * getOwnerUniqueId() e uma adicao do Paper e nao existe na API do Spigot,
     * entao vai por reflexao. O fallback via getOwner() funciona, mas resolve um
     * OfflinePlayer e pode voltar null quando o dono nunca entrou nesta sessao
     * -- por isso o caminho direto vem primeiro.
     */
    public static java.util.UUID petOwnerId(org.bukkit.entity.Tameable t) {
        try {
            Object v = t.getClass().getMethod("getOwnerUniqueId").invoke(t);
            if (v instanceof java.util.UUID u) return u;
        } catch (Exception ignored) { }
        org.bukkit.entity.AnimalTamer owner = t.getOwner();
        return owner == null ? null : owner.getUniqueId();
    }

    /** O Paper expoe perfil de jogador; o Spigot puro nao. */
    public static boolean hasPlayerProfileApi() {
        try {
            Class.forName("com.destroystokyo.paper.profile.PlayerProfile");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Aplica uma textura de skin assinada no jogador.
     * Retorna false quando a plataforma nao expoe a API (Spigot puro).
     */
    public static boolean applySkin(Player p, String value, String signature) {
        if (!hasPlayerProfileApi()) return false;
        try {
            Object profile = p.getClass().getMethod("getPlayerProfile").invoke(p);
            Class<?> propClass = Class.forName("com.destroystokyo.paper.profile.ProfileProperty");
            Object prop = propClass.getConstructor(String.class, String.class, String.class)
                    .newInstance("textures", value, signature);

            try {
                profile.getClass().getMethod("removeProperty", String.class).invoke(profile, "textures");
            } catch (NoSuchMethodException ignored) { }

            profile.getClass().getMethod("setProperty", propClass).invoke(profile, prop);
            p.getClass().getMethod("setPlayerProfile",
                    Class.forName("com.destroystokyo.paper.profile.PlayerProfile")).invoke(p, profile);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
