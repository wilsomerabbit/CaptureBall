package dev.captureball;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * CaptureBall
 *
 * - Balls are only created by "/captureball give" (admin). No crafting, no shop.
 * - An EMPTY ball: right-click a mob while holding it to capture it (single use).
 * - A FILLED ball stores the mob's data inside the item (PersistentDataContainer),
 *   NOT as a spawn egg, so it can never be put into a spawner.
 *   Right-click a block while holding it to release the mob (single use).
 * - Items are identified by a hidden tag, so a plain item of the same material does nothing.
 *
 * Protection rules (capture AND release):
 * - WorldGuard: inside ANY region, only OPs may use balls.
 * - Residence: inside a residence, the player must be the owner or have the "build" flag.
 */
public final class CaptureBall extends JavaPlugin implements Listener, TabExecutor {

    private static final String TYPE_EMPTY = "empty";
    private static final String TYPE_FILLED = "filled";

    /** Built-in fallbacks so new messages work even if config.yml has no such keys yet. */
    private static final Map<String, String> DEFAULT_MESSAGES = Map.of(
            "region-denied", "<red>這個保護區內不能捕捉生物",
            "residence-denied", "<red>你在這塊領地沒有 build 權限，無法使用捕捉球");

    /** Built-in lore used when config.yml has no such list. */
    private static final List<String> DEFAULT_EMPTY_LORE = List.of(
            "<gray>狀態：<white>空的",
            "",
            "<yellow>對生物按右鍵 <gray>收服牠",
            "<dark_gray>單次使用");
    private static final List<String> DEFAULT_FILLED_LORE = List.of(
            "<gray>狀態：<green>已收服 <aqua><mob>",
            "",
            "<yellow>對方塊按右鍵 <gray>放出牠",
            "<dark_gray>單次使用");

    private final MiniMessage mm = MiniMessage.miniMessage();

    private NamespacedKey keyType;
    private NamespacedKey keyData;

    private final Set<String> blacklist = new HashSet<>();
    private final Set<String> disabledWorlds = new HashSet<>();

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onEnable() {
        saveDefaultConfig();
        keyType = new NamespacedKey(this, "ball_type");
        keyData = new NamespacedKey(this, "ball_data");
        loadSettings();

        getServer().getPluginManager().registerEvents(this, this);

        var cmd = getCommand("captureball");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    private void loadSettings() {
        reloadConfig();
        blacklist.clear();
        for (String s : getConfig().getStringList("blacklist")) {
            blacklist.add(s.toUpperCase(Locale.ROOT));
        }
        disabledWorlds.clear();
        disabledWorlds.addAll(getConfig().getStringList("disabled-worlds"));
    }

    // ------------------------------------------------------------------ items

    private Component msg(String key) {
        return mm.deserialize(getConfig().getString("messages." + key,
                DEFAULT_MESSAGES.getOrDefault(key, key)));
    }

    private Component noItalic(Component c) {
        return c.decoration(TextDecoration.ITALIC, false);
    }

    private List<String> loreLines(String path, List<String> defaults) {
        return getConfig().contains(path) ? getConfig().getStringList(path) : defaults;
    }

    private List<Component> lore(List<String> lines, TagResolver resolver) {
        List<Component> out = new ArrayList<>();
        for (String line : lines) {
            out.add(noItalic(mm.deserialize(line, resolver)));
        }
        return out;
    }

    private void applyModel(ItemMeta meta, String model) {
        if (model == null || model.isBlank()) return;
        NamespacedKey k = NamespacedKey.fromString(model.trim());
        if (k != null) {
            meta.setItemModel(k);
        }
    }

    /** Base material of the ball. Should be an item with no right-click use of its own. */
    private Material ballMaterial() {
        Material m = Material.matchMaterial(getConfig().getString("item.material", "FIREWORK_STAR"));
        return (m != null && m.isItem()) ? m : Material.FIREWORK_STAR;
    }

    private ItemStack createEmpty(int amount) {
        ItemStack item = new ItemStack(ballMaterial(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(mm.deserialize(getConfig().getString("empty.name", "<gold><bold>捕捉球</bold>"))));
        meta.lore(lore(loreLines("empty.lore", DEFAULT_EMPTY_LORE), TagResolver.empty()));
        applyModel(meta, getConfig().getString("empty.item-model", ""));
        meta.getPersistentDataContainer().set(keyType, PersistentDataType.STRING, TYPE_EMPTY);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFilled(Entity mob, String data) {
        Component mobName = mob.customName() != null
                ? mob.customName()
                : Component.translatable(mob.getType().translationKey());
        TagResolver r = Placeholder.component("mob", mobName);

        ItemStack item = new ItemStack(ballMaterial(), 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(mm.deserialize(
                getConfig().getString("filled.name", "<green><bold>捕捉球</bold> <dark_gray>(<aqua><mob><dark_gray>)"), r)));
        meta.lore(lore(loreLines("filled.lore", DEFAULT_FILLED_LORE), r));
        applyModel(meta, getConfig().getString("filled.item-model", ""));
        meta.setMaxStackSize(1); // filled balls never stack
        var pdc = meta.getPersistentDataContainer();
        pdc.set(keyType, PersistentDataType.STRING, TYPE_FILLED);
        pdc.set(keyData, PersistentDataType.STRING, data);
        item.setItemMeta(meta);
        return item;
    }

    private String typeOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(keyType, PersistentDataType.STRING);
    }

    private void give(Player p, ItemStack item) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(item);
        for (ItemStack rest : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    /** Removes one item from the player's main hand. */
    private void consumeOne(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getAmount() <= 1) {
            p.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(hand.getAmount() - 1);
            p.getInventory().setItemInMainHand(hand);
        }
    }

    // ------------------------------------------------------------------ events

    /** Common gate for both capture and release. @return false (and tells the player) if not allowed. */
    private boolean passesBasicChecks(Player p) {
        if (disabledWorlds.contains(p.getWorld().getName())) {
            p.sendActionBar(msg("world-disabled"));
            return false;
        }
        if (!p.hasPermission("captureball.use")) {
            p.sendActionBar(msg("no-permission"));
            return false;
        }
        return true;
    }

    /** Right-click a mob with an EMPTY ball -> capture. */
    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return; // the event fires once per hand
        Player p = e.getPlayer();
        ItemStack item = p.getInventory().getItemInMainHand();
        String type = typeOf(item);
        if (type == null) return; // not one of our balls

        // Only living mobs are balls' business. Item frames, armor stands, paintings, boats,
        // minecarts etc. keep their normal behaviour (e.g. placing the ball in an item frame).
        Entity hit = e.getRightClicked();
        if (!(hit instanceof Mob)) return;

        e.setCancelled(true); // never let a ball trigger vanilla/other plugin interactions
        if (!TYPE_EMPTY.equals(type)) return;

        if (!hit.isValid()) return;
        if (!passesBasicChecks(p)) return;

        String problem = checkCapturable(hit, p);
        if (problem != null) {
            p.sendActionBar(msg(problem)); // ball is NOT consumed
            return;
        }

        double chance = getConfig().getDouble("capture-chance", 1.0);
        if (chance < 1.0 && ThreadLocalRandom.current().nextDouble() >= chance) {
            consumeOne(p);
            p.sendActionBar(msg("failed-chance"));
            return;
        }

        EntitySnapshot snap = hit.createSnapshot();
        if (snap == null) {
            p.sendActionBar(msg("not-capturable"));
            return;
        }
        String data = snap.getAsString();
        if (data.length() > getConfig().getInt("max-data-length", 30000)) {
            p.sendActionBar(msg("data-too-large"));
            return;
        }

        ItemStack filled = createFilled(hit, data);
        Location loc = hit.getLocation();
        hit.remove();
        consumeOne(p);
        give(p, filled);

        p.sendActionBar(msg("captured"));
        World w = loc.getWorld();
        w.spawnParticle(Particle.POOF, loc.clone().add(0, 0.5, 0), 15, 0.3, 0.3, 0.3, 0.02);
        w.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.4f);
    }

    /** Right-click a block with a FILLED ball -> release. */
    @EventHandler(ignoreCancelled = true)
    public void onInteractBlock(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = e.getItem();
        String type = typeOf(item);
        if (type == null) return;

        Player p = e.getPlayer();
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;

        // Let chests, doors, buttons etc. work normally unless the player is sneaking.
        if (clicked.getType().isInteractable() && !p.isSneaking()) return;

        e.setCancelled(true);
        if (!TYPE_FILLED.equals(type)) return;
        if (!passesBasicChecks(p)) return;

        String data = item.getItemMeta().getPersistentDataContainer().get(keyData, PersistentDataType.STRING);
        if (data == null) return;

        BlockFace face = e.getBlockFace();
        Location loc = clicked.getRelative(face).getLocation().add(0.5, 0, 0.5);

        String problem = protectionProblem(p, loc);
        if (problem != null) {
            p.sendActionBar(msg(problem)); // ball is NOT consumed
            return;
        }

        World w = loc.getWorld();
        boolean ok = false;
        try {
            EntitySnapshot snap = Bukkit.getEntityFactory().createEntitySnapshot(data);
            Entity ent = snap.createEntity(w);
            ok = ent.spawnAt(loc, CreatureSpawnEvent.SpawnReason.CUSTOM);
        } catch (Exception ex) {
            getLogger().warning("Failed to release captured mob: " + ex.getMessage());
        }

        if (!ok) {
            p.sendActionBar(msg("release-failed")); // ball is NOT consumed
            return;
        }

        consumeOne(p);
        w.spawnParticle(Particle.POOF, loc.clone().add(0, 0.5, 0), 15, 0.3, 0.3, 0.3, 0.02);
        w.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.8f);
    }

    /** Old throwable (snowball) balls from earlier versions can no longer be thrown. */
    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (e.getEntity() instanceof ThrowableProjectile tp && typeOf(tp.getItem()) != null) {
            e.setCancelled(true);
        }
    }

    /** Block using balls as crafting ingredients. */
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        for (ItemStack s : e.getInventory().getMatrix()) {
            if (typeOf(s) != null) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ rules

    /** @return a message key describing why the target cannot be captured, or null if it can. */
    private String checkCapturable(Entity hit, Player p) {
        if (!(hit instanceof Mob)) return "not-capturable";
        if (blacklist.contains(hit.getType().name())) return "blacklisted";
        if (hit.isInsideVehicle() || !hit.getPassengers().isEmpty()) return "has-vehicle";
        if (hit instanceof Tameable t && t.isTamed() && t.getOwner() != null
                && !t.getOwner().getUniqueId().equals(p.getUniqueId())
                && !p.hasPermission("captureball.bypass")) {
            return "not-owner";
        }
        return protectionProblem(p, hit.getLocation());
    }

    /**
     * Land-protection rules, shared by capture and release.
     *
     * 1) WorldGuard: inside ANY region, only OPs may capture/release.
     * 2) Residence: inside a residence, the player must be the owner or have the "build" flag.
     *
     * @return a message key, or null if allowed.
     */
    private String protectionProblem(Player p, Location loc) {
        if (!p.isOp()
                && getConfig().getBoolean("protection.worldguard-block-non-op", true)
                && isInWorldGuardRegion(loc)) {
            return "region-denied";
        }

        boolean admin = p.isOp() || p.hasPermission("captureball.bypass");
        if (!admin
                && getConfig().getBoolean("protection.residence-require-build", true)
                && !residenceAllows(p, loc)) {
            return "residence-denied";
        }
        return null;
    }

    private boolean isInWorldGuardRegion(Location loc) {
        Plugin wg = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (wg == null || !wg.isEnabled()) return false;
        try {
            return WorldGuardHook.hasRegion(loc);
        } catch (Throwable t) {
            getLogger().warning("WorldGuard region check failed, denying: " + t);
            return true; // fail closed
        }
    }

    /** Isolated so WorldGuard classes are only loaded when WorldGuard is installed. */
    private static final class WorldGuardHook {
        static boolean hasRegion(Location loc) {
            var query = com.sk89q.worldguard.WorldGuard.getInstance()
                    .getPlatform().getRegionContainer().createQuery();
            return query.getApplicableRegions(
                    com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(loc)).size() > 0;
        }
    }

    /**
     * Residence check done through reflection, so the plugin needs no Residence dependency to build.
     * @return true if there is no residence here, or the player owns it / has the "build" flag there.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean residenceAllows(Player p, Location loc) {
        Plugin res = Bukkit.getPluginManager().getPlugin("Residence");
        if (res == null || !res.isEnabled()) return true;
        try {
            Object manager = call(res, "getResidenceManager");
            Object claim = call(manager, "getByLoc", loc);
            if (claim == null) return true; // not inside any residence

            if (Boolean.TRUE.equals(call(claim, "isOwner", p))) return true;

            Object perms = call(claim, "getPermissions");
            Class<?> flagsClass = Class.forName("com.bekvon.bukkit.residence.containers.Flags",
                    true, res.getClass().getClassLoader());
            Object build = Enum.valueOf((Class) flagsClass, "build");
            return Boolean.TRUE.equals(call(perms, "playerHas", p, build, Boolean.FALSE));
        } catch (Throwable t) {
            getLogger().warning("Residence check failed, denying: " + t);
            return false; // fail closed
        }
    }

    /** Calls a public method by name, matching on argument count and assignable types. */
    private static Object call(Object target, String name, Object... args) throws Exception {
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            Class<?>[] pt = m.getParameterTypes();
            boolean match = true;
            for (int i = 0; i < pt.length; i++) {
                Class<?> want = pt[i] == boolean.class ? Boolean.class : pt[i];
                if (args[i] == null || !want.isInstance(args[i])) {
                    match = false;
                    break;
                }
            }
            if (match) return m.invoke(target, args);
        }
        throw new NoSuchMethodException(target.getClass().getName() + "#" + name);
    }

    // ------------------------------------------------------------------ commands

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(msg("usage"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            loadSettings();
            sender.sendMessage(msg("reloaded"));
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (args.length < 2) {
                sender.sendMessage(msg("usage"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(msg("player-not-found"));
                return true;
            }
            int amount = 1;
            if (args.length >= 3) {
                try {
                    amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
                } catch (NumberFormatException ex) {
                    sender.sendMessage(msg("usage"));
                    return true;
                }
            }
            give(target, createEmpty(amount));
            sender.sendMessage(mm.deserialize(
                    getConfig().getString("messages.given", "given"),
                    Placeholder.unparsed("player", target.getName()),
                    Placeholder.unparsed("amount", String.valueOf(amount))));
            return true;
        }

        sender.sendMessage(msg("usage"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("give", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}