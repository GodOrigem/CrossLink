package br.origem.crosslink;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Snapshot do que um grupo compartilha. Serializa direto em YAML porque
 * ItemStack implementa ConfigurationSerializable -- nao precisa de base64
 * nem de NBT na mao, e sobrevive a upgrade de versao do servidor.
 */
public final class SharedState {

    public ItemStack[] inventory = new ItemStack[0];   // 41 slots: 36 mochila + 4 armadura + 1 offhand
    public ItemStack[] enderChest = new ItemStack[0];  // 27 slots

    public int level;
    public float exp;
    public int totalExperience;

    public double health = -1;      // -1 = nunca capturado
    public int foodLevel = -1;
    public float saturation;

    /** Identidade barata pra detectar "quem mexeu" sem comparar tudo slot a slot. */
    public int fingerprint() {
        return Objects.hash(
                Arrays.deepHashCode(inventory),
                Arrays.deepHashCode(enderChest),
                level, exp, totalExperience,
                health, foodLevel, saturation);
    }

    public void save(ConfigurationSection s) {
        s.set("inventory", Arrays.asList(inventory));
        s.set("ender-chest", Arrays.asList(enderChest));
        s.set("level", level);
        s.set("exp", exp);
        s.set("total-experience", totalExperience);
        s.set("health", health);
        s.set("food-level", foodLevel);
        s.set("saturation", saturation);
    }

    @SuppressWarnings("unchecked")
    public static SharedState load(ConfigurationSection s) {
        SharedState st = new SharedState();
        if (s == null) return st;
        List<ItemStack> inv = (List<ItemStack>) s.getList("inventory");
        List<ItemStack> ec = (List<ItemStack>) s.getList("ender-chest");
        if (inv != null) st.inventory = inv.toArray(new ItemStack[0]);
        if (ec != null) st.enderChest = ec.toArray(new ItemStack[0]);
        st.level = s.getInt("level");
        st.exp = (float) s.getDouble("exp");
        st.totalExperience = s.getInt("total-experience");
        st.health = s.getDouble("health", -1);
        st.foodLevel = s.getInt("food-level", -1);
        st.saturation = (float) s.getDouble("saturation");
        return st;
    }
}
