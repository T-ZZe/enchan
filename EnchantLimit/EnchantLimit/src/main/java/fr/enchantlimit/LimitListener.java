package fr.enchantlimit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LimitListener implements Listener {

    private static final List<EquipmentSlot> SLOTS =
            List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private final EnchantLimitPlugin plugin;
    private final LimitManager limits;

    public LimitListener(EnchantLimitPlugin plugin, LimitManager limits) {
        this.plugin = plugin;
        this.limits = limits;
    }

    // ------------------------------------------------------------ niveaux

    @EventHandler
    public void onEnchant(EnchantItemEvent e) {
        Iterator<Map.Entry<Enchantment, Integer>> it = e.getEnchantsToAdd().entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Integer lim = limits.getLimit(e.getItem().getType(), entry.getKey());
            if (lim != null && entry.getValue() > lim) {
                if (lim <= 0) it.remove();
                else entry.setValue(lim);
            }
        }
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        ItemStack result = e.getResult();
        if (result != null && limits.clamp(result)) e.setResult(result);
    }

    /** Amelioration diamant -> netherite : on redescend aux limites de la version netherite. */
    @EventHandler
    public void onSmithing(PrepareSmithingEvent e) {
        ItemStack result = e.getResult();
        if (result != null && limits.clamp(result)) e.setResult(result);
    }

    // ------------------------------------------------------------ pieces portees

    /** Derniere armure vue par joueur, pour savoir quelle piece vient d'etre equipee. */
    private final Map<UUID, Material[]> lastArmor = new HashMap<>();

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastArmor.remove(e.getPlayer().getUniqueId());
    }

    /** Retire les pieces en trop (en priorite celle qui vient d'etre equipee). */
    public void enforce(Player p) {
        PlayerInventory inv = p.getInventory();

        Material[] last = lastArmor.computeIfAbsent(p.getUniqueId(), k -> new Material[4]);
        EquipmentSlot preferred = null;
        for (int i = 0; i < SLOTS.size(); i++) {
            ItemStack it = inv.getItem(SLOTS.get(i));
            Material now = it == null ? Material.AIR : it.getType();
            if (now != Material.AIR && now != last[i]) preferred = SLOTS.get(i);
        }
        final EquipmentSlot pref = preferred;
        for (Tier tier : Tier.ARMOR) {
            int limit = limits.getPieceLimit(tier);
            if (limit >= LimitManager.NO_PIECE_LIMIT) continue;

            List<EquipmentSlot> worn = new ArrayList<>();
            for (EquipmentSlot s : SLOTS) {
                ItemStack it = inv.getItem(s);
                if (it != null && !it.getType().isAir()
                        && Tier.isArmor(it.getType()) && Tier.of(it.getType()) == tier) {
                    worn.add(s);
                }
            }
            int excess = worn.size() - limit;
            if (excess <= 0) continue;

            worn.sort(Comparator.comparingInt(s -> s == pref ? 0 : 1 + (3 - SLOTS.indexOf(s))));
            for (int i = 0; i < excess; i++) {
                EquipmentSlot s = worn.get(i);
                ItemStack it = inv.getItem(s);
                inv.setItem(s, null);
                inv.addItem(it).values().forEach(
                        left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
            }
            p.sendMessage(Component.text("Tu ne peux porter que " + limit + " pièce(s) en "
                    + tier.label + " à la fois.", NamedTextColor.RED));
        }

        for (int i = 0; i < SLOTS.size(); i++) {
            ItemStack it = inv.getItem(SLOTS.get(i));
            last[i] = it == null ? Material.AIR : it.getType();
        }
    }
}
