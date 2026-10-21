package work.maiotanet.invasioncore.defense;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.mob.Invader;
import work.maiotanet.invasioncore.mob.InvaderRegistry;
import work.maiotanet.invasioncore.nexus.Nexus;
import work.maiotanet.invasioncore.nexus.NexusManager;
import work.maiotanet.invasioncore.nexus.NexusUpgrade;
import work.maiotanet.invasioncore.perk.PerkService;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * 設置型タレットの管理。
 *
 * <p>侵略MODには無かったタワーディフェンス要素。
 * プレイヤーが常時張り付かなくても拠点が機能するようにしつつ、
 * 弾薬という上限を設けることで「置きっぱなしで勝てる」にはしていない。</p>
 */
public final class TurretService {

    /** 設置済みタレット1基分。 */
    public static final class Turret {
        private final TurretType type;
        private final UUID owner;
        private int ammo;
        private long readyAtTick;

        Turret(TurretType type, UUID owner, int ammo) {
            this.type = type;
            this.owner = owner;
            this.ammo = ammo;
        }

        public TurretType type() {
            return type;
        }

        public UUID owner() {
            return owner;
        }

        public int ammo() {
            return ammo;
        }
    }

    private final Plugin plugin;
    private final InvasionConfig config;
    private final NexusManager nexuses;
    private final InvaderRegistry registry;
    private final FortifyService fortify;
    private final PerkService perks;
    private final File file;

    private final Map<String, Turret> turrets = new HashMap<>();

    public TurretService(Plugin plugin, InvasionConfig config, NexusManager nexuses,
                         InvaderRegistry registry, FortifyService fortify, PerkService perks) {
        this.plugin = plugin;
        this.config = config;
        this.nexuses = nexuses;
        this.registry = registry;
        this.fortify = fortify;
        this.perks = perks;
        this.file = new File(plugin.getDataFolder(), "turrets.yml");
    }

    // ---------- 設置・撤去 ----------

    public Turret place(Block block, TurretType type, Player owner) {
        Turret turret = new Turret(type, owner == null ? null : owner.getUniqueId(), 0);
        turrets.put(key(block), turret);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.8f, 1.2f);
        return turret;
    }

    public Turret get(Block block) {
        return turrets.get(key(block));
    }

    public Turret remove(Block block) {
        return turrets.remove(key(block));
    }

    public int count() {
        return turrets.size();
    }

    /** 弾薬を装填する。装填できた数を返す。 */
    public int loadAmmo(Block block, int amount) {
        Turret turret = get(block);
        if (turret == null) {
            return 0;
        }
        int space = turret.type.maxAmmo() - turret.ammo;
        int loaded = Math.min(space, amount);
        turret.ammo += loaded;
        if (loaded > 0) {
            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.6f, 1.6f);
        }
        return loaded;
    }

    // ---------- 稼働 ----------

    /** config.turretTickInterval() tick ごとに呼ばれる。 */
    public void tick() {
        if (turrets.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<String, Turret>> it = turrets.entrySet().iterator();
        List<Runnable> actions = new ArrayList<>();

        while (it.hasNext()) {
            Map.Entry<String, Turret> entry = it.next();
            Block block = fromKey(entry.getKey());
            Turret turret = entry.getValue();

            if (block == null
                    || !block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
                // ワールド／チャンク未ロード。次回に持ち越す。
                // getType() で確かめるとチャンクを同期ロードしてしまい、誰もいない拠点のタレットが
                // 10tickごとにチャンクを読み込み続ける。
                continue;
            }
            if (block.getType() != turret.type.block()) {
                // 壊された／置き換えられた
                it.remove();
                continue;
            }
            long now = block.getWorld().getFullTime();
            if (now < turret.readyAtTick) {
                continue;
            }
            if (turret.ammo <= 0) {
                continue;
            }
            Nexus nexus = nexuses.nearest(block.getLocation());
            double range = turret.type.baseRange()
                    + (nexus == null ? 0 : 2.0 * nexus.upgradeLevel(NexusUpgrade.TURRET_RANGE));

            actions.add(() -> operate(block, turret, range, now));
        }
        for (Runnable r : actions) {
            r.run();
        }
    }

    private void operate(Block block, Turret turret, double range, long now) {
        Location muzzle = block.getLocation().add(0.5, 1.0, 0.5);

        switch (turret.type) {
            case HEAL -> {
                if (healAround(block, muzzle, range)) {
                    consumeAmmo(turret);
                    turret.readyAtTick = now + turret.type.cooldownTicks();
                }
            }
            default -> {
                Invader target = nearestInvader(muzzle, range);
                if (target == null) {
                    return;
                }
                consumeAmmo(turret);
                turret.readyAtTick = now + turret.type.cooldownTicks();
                // 設置者の「砲手」パークが威力に乗る
                double power = turret.type.power()
                        * (turret.owner == null ? 1.0 : perks.turretMultiplier(turret.owner));
                fire(turret.type, muzzle, target, power);
            }
        }
    }

    /** 弾薬を1発消費する。設置者の「弾薬工」パークで消費しないことがある。 */
    private void consumeAmmo(Turret turret) {
        if (turret.owner != null
                && ThreadLocalRandom.current().nextDouble() < perks.ammoSaveChance(turret.owner)) {
            return;
        }
        turret.ammo--;
    }

    private void fire(TurretType type, Location muzzle, Invader target, double power) {
        World world = muzzle.getWorld();
        Location targetLoc = target.entity().getEyeLocation();

        switch (type) {
            case ARROW -> {
                drawBeam(world, muzzle, targetLoc, Particle.CRIT);
                target.entity().damage(power);
                world.playSound(muzzle, Sound.ENTITY_ARROW_SHOOT, 0.7f, 1.4f);
            }
            case FLAME -> {
                drawBeam(world, muzzle, targetLoc, Particle.FLAME);
                target.entity().setFireTicks(Math.max(target.entity().getFireTicks(), 80));
                target.entity().damage(power);
                world.playSound(muzzle, Sound.ITEM_FIRECHARGE_USE, 0.7f, 1.2f);
            }
            case FROST -> {
                drawBeam(world, muzzle, targetLoc, Particle.SNOWFLAKE);
                target.entity().addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS, 80, 2, false, true, false));
                world.playSound(muzzle, Sound.BLOCK_GLASS_BREAK, 0.6f, 1.8f);
            }
            default -> {
            }
        }
    }

    /** 修復タレット：周囲のプレイヤーを回復し、傷んだ強化ブロックを直す。 */
    private boolean healAround(Block block, Location muzzle, double range) {
        boolean acted = false;
        World world = muzzle.getWorld();
        double rangeSq = range * range;

        for (Player p : world.getPlayers()) {
            if (p.isDead() || p.getLocation().distanceSquared(muzzle) > rangeSq) {
                continue;
            }
            // 無傷の人しかいないのに作動すると、近くに立っているだけで弾薬が尽きる
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            if (max != null && p.getHealth() >= max.getValue()) {
                continue;
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, false, true));
            acted = true;
        }

        // 周辺の強化ブロックを少しずつ直す
        int r = (int) Math.min(6, range);
        int repaired = 0;
        for (int dx = -r; dx <= r && repaired < 6; dx++) {
            for (int dy = -2; dy <= 2 && repaired < 6; dy++) {
                for (int dz = -r; dz <= r && repaired < 6; dz++) {
                    Block b = block.getRelative(dx, dy, dz);
                    FortifyService.Fortified f = fortify.get(b);
                    if (f == null || !f.isManaged() || f.ratio() >= 1.0) {
                        continue;
                    }
                    fortify.repair(b, f.maxHealth() * 0.04);
                    repaired++;
                    acted = true;
                }
            }
        }
        if (acted) {
            world.spawnParticle(Particle.HAPPY_VILLAGER, muzzle, 8, 1.2, 0.6, 1.2, 0.0);
        }
        return acted;
    }

    private void drawBeam(World world, Location from, Location to, Particle particle) {
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length < 0.01) {
            return;
        }
        Vector step = delta.normalize().multiply(0.6);
        Location cursor = from.clone();
        int steps = (int) Math.min(40, length / 0.6);
        for (int i = 0; i < steps; i++) {
            cursor.add(step);
            if (particle == Particle.CRIT) {
                world.spawnParticle(particle, cursor, 1, 0, 0, 0, 0);
            } else {
                world.spawnParticle(particle, cursor, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    private Invader nearestInvader(Location from, double range) {
        double bestSq = range * range;
        Invader best = null;
        for (Invader invader : registry.all()) {
            if (!invader.isValid()) {
                continue;
            }
            if (!invader.entity().getWorld().equals(from.getWorld())) {
                continue;
            }
            double d = invader.entity().getLocation().distanceSquared(from);
            if (d < bestSq) {
                bestSq = d;
                best = invader;
            }
        }
        return best;
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
        turrets.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("turrets");
        if (root == null) {
            return;
        }
        for (String k : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(k);
            if (sec == null) {
                continue;
            }
            try {
                TurretType type = TurretType.valueOf(sec.getString("type", TurretType.ARROW.name()));
                String ownerRaw = sec.getString("owner");
                UUID owner = ownerRaw == null ? null : UUID.fromString(ownerRaw);
                turrets.put(k, new Turret(type, owner, sec.getInt("ammo", 0)));
            } catch (IllegalArgumentException ignored) {
                // 未知の種別・壊れたUUIDはスキップ
            }
        }
        plugin.getLogger().info("タレットを " + turrets.size() + " 基読み込みました。");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("turrets");
        for (Map.Entry<String, Turret> e : turrets.entrySet()) {
            ConfigurationSection sec = root.createSection(e.getKey());
            sec.set("type", e.getValue().type.name());
            sec.set("ammo", e.getValue().ammo);
            sec.set("owner", e.getValue().owner == null ? null : e.getValue().owner.toString());
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("データフォルダを作成できませんでした: " + parent);
            }
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "turrets.yml の保存に失敗しました", ex);
        }
    }
}
