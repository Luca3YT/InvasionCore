package work.maiotanet.invasioncore.defense;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import work.maiotanet.invasioncore.item.InvasionItems;
import work.maiotanet.invasioncore.mob.Invader;
import work.maiotanet.invasioncore.mob.InvaderRegistry;
import work.maiotanet.invasioncore.perk.PerkService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 設置型トラップ。
 *
 * <p>タレットが「常時働く自動火力」なのに対し、トラップは「経路を設計して初めて効く仕掛け」。
 * ノックバックの効かない重装体やボスに対して、プレイヤー側が地形で対抗するための手段になる。</p>
 */
public final class TrapService {

    /** トラップの種類。 */
    public enum TrapType {
        /** 繰り返し作動。上を通る侵略者に継続ダメージと鈍足。 */
        SPIKE("スパイクトラップ", InvasionItems.TRAP_SPIKE, Material.POINTED_DRIPSTONE,
                1.6, 8.0, 20, false),
        /** 使い切り。踏まれると範囲を吹き飛ばす。 */
        MINE("爆裂地雷", InvasionItems.TRAP_MINE, Material.HEAVY_WEIGHTED_PRESSURE_PLATE,
                2.6, 55.0, 0, true);

        private final String displayName;
        private final String itemId;
        private final Material block;
        private final double triggerRadius;
        private final double power;
        private final int cooldownTicks;
        private final boolean consumedOnTrigger;

        TrapType(String displayName, String itemId, Material block, double triggerRadius,
                 double power, int cooldownTicks, boolean consumedOnTrigger) {
            this.displayName = displayName;
            this.itemId = itemId;
            this.block = block;
            this.triggerRadius = triggerRadius;
            this.power = power;
            this.cooldownTicks = cooldownTicks;
            this.consumedOnTrigger = consumedOnTrigger;
        }

        public String displayName() {
            return displayName;
        }

        public String itemId() {
            return itemId;
        }

        public Material block() {
            return block;
        }

        public static TrapType byItemId(String itemId) {
            for (TrapType t : values()) {
                if (t.itemId.equals(itemId)) {
                    return t;
                }
            }
            return null;
        }
    }

    private static final class Trap {
        private final TrapType type;
        private final UUID owner;
        private long readyAtTick;

        Trap(TrapType type, UUID owner) {
            this.type = type;
            this.owner = owner;
        }
    }

    private final Plugin plugin;
    private final InvaderRegistry registry;
    private final PerkService perks;
    private final File file;
    private final Map<String, Trap> traps = new HashMap<>();

    public TrapService(Plugin plugin, InvaderRegistry registry, PerkService perks) {
        this.plugin = plugin;
        this.registry = registry;
        this.perks = perks;
        this.file = new File(plugin.getDataFolder(), "traps.yml");
    }

    // ---------- 設置・撤去 ----------

    public void place(Block block, TrapType type, Player owner) {
        traps.put(key(block), new Trap(type, owner == null ? null : owner.getUniqueId()));
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_METAL_PLACE, 0.8f, 1.4f);
    }

    public boolean isTrap(Block block) {
        return traps.containsKey(key(block));
    }

    public TrapType removeTrap(Block block) {
        Trap trap = traps.remove(key(block));
        return trap == null ? null : trap.type;
    }

    public int count() {
        return traps.size();
    }

    // ---------- 稼働 ----------

    /** 10 tick ごとに呼ばれる。 */
    public void tick() {
        if (traps.isEmpty()) {
            return;
        }
        List<Runnable> detonations = new ArrayList<>();
        Iterator<Map.Entry<String, Trap>> it = traps.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String, Trap> entry = it.next();
            Block block = fromKey(entry.getKey());
            Trap trap = entry.getValue();
            if (block == null
                    || !block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                // 未ロードのチャンクを getType() で同期ロードさせない
                continue;
            }
            if (block.getType() != trap.type.block()) {
                it.remove();
                continue;
            }
            long now = block.getWorld().getFullTime();
            if (now < trap.readyAtTick) {
                continue;
            }
            Location center = block.getLocation().add(0.5, 0.5, 0.5);
            List<Invader> caught = invadersWithin(center, trap.type.triggerRadius);
            if (caught.isEmpty()) {
                continue;
            }
            trap.readyAtTick = now + trap.type.cooldownTicks;
            double power = trap.type.power
                    * (trap.owner == null ? 1.0 : perks.turretMultiplier(trap.owner));

            if (trap.type.consumedOnTrigger) {
                it.remove();
                detonations.add(() -> detonate(block, center, caught, power));
            } else {
                detonations.add(() -> spike(center, caught, power));
            }
        }
        for (Runnable r : detonations) {
            r.run();
        }
    }

    private void spike(Location center, List<Invader> caught, double power) {
        World world = center.getWorld();
        for (Invader invader : caught) {
            invader.entity().damage(power);
            invader.entity().addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, 40, 1, false, true, false));
        }
        world.spawnParticle(Particle.CRIT, center, 10, 0.4, 0.2, 0.4, 0.1);
        world.playSound(center, Sound.ENTITY_PLAYER_HURT, 0.6f, 1.6f);
    }

    /**
     * 地雷の起爆。
     * バニラの爆発は使わない。ブロックを巻き込むと自分の拠点が崩れて本末転倒になる。
     */
    private void detonate(Block block, Location center, List<Invader> caught, double power) {
        World world = center.getWorld();
        block.setType(Material.AIR);

        for (Invader invader : caught) {
            double distance = invader.entity().getLocation().distance(center);
            double falloff = Math.max(0.35, 1.0 - distance / 5.0);
            invader.entity().damage(power * falloff);
            Vector push = invader.entity().getLocation().toVector()
                    .subtract(center.toVector());
            if (push.lengthSquared() > 1.0e-4) {
                invader.entity().setVelocity(push.normalize().multiply(0.7).setY(0.5));
            }
        }
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
        world.spawnParticle(Particle.LARGE_SMOKE, center, 30, 1.2, 0.6, 1.2, 0.05);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 1.1f);
    }

    private List<Invader> invadersWithin(Location center, double radius) {
        List<Invader> out = new ArrayList<>();
        double sq = radius * radius;
        for (Invader invader : registry.all()) {
            if (!invader.isValid() || !invader.entity().getWorld().equals(center.getWorld())) {
                continue;
            }
            if (invader.entity().getLocation().distanceSquared(center) <= sq) {
                out.add(invader);
            }
        }
        return out;
    }

    // ---------- 永続化 ----------

    private String key(Block block) {
        return block.getWorld().getName() + ';' + block.getX() + ';' + block.getY() + ';' + block.getZ();
    }

    private Block fromKey(String key) {
        String[] parts = key.split(";");
        if (parts.length != 4) {
            return null;
        }
        World w = plugin.getServer().getWorld(parts[0]);
        if (w == null) {
            return null;
        }
        try {
            return w.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public void load() {
        traps.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("traps");
        if (root == null) {
            return;
        }
        for (String k : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(k);
            if (sec == null) {
                continue;
            }
            try {
                TrapType type = TrapType.valueOf(sec.getString("type", TrapType.SPIKE.name()));
                String ownerRaw = sec.getString("owner");
                traps.put(k, new Trap(type, ownerRaw == null ? null : UUID.fromString(ownerRaw)));
            } catch (IllegalArgumentException ignored) {
                // 未知の種別・壊れたUUIDはスキップ
            }
        }
        plugin.getLogger().info("トラップを " + traps.size() + " 個読み込みました。");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("traps");
        for (Map.Entry<String, Trap> e : traps.entrySet()) {
            ConfigurationSection sec = root.createSection(e.getKey());
            sec.set("type", e.getValue().type.name());
            sec.set("owner", e.getValue().owner == null ? null : e.getValue().owner.toString());
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("データフォルダを作成できませんでした: " + parent);
            }
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "traps.yml の保存に失敗しました", ex);
        }
    }
}
