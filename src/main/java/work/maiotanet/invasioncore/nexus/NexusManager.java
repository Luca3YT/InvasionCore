package work.maiotanet.invasioncore.nexus;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import work.maiotanet.invasioncore.config.InvasionConfig;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * ネクサスの登録簿と永続化。
 *
 * <p>件数が高々数十基なので YAML で保持する。SQLite を使うほどのデータ量ではなく、
 * ネイティブライブラリ同梱を避けられる分こちらのほうが配布事故が少ない。</p>
 */
public final class NexusManager {

    private final Plugin plugin;
    private final InvasionConfig config;
    private final Map<UUID, Nexus> byId = new LinkedHashMap<>();
    private final File file;

    public NexusManager(Plugin plugin, InvasionConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.file = new File(plugin.getDataFolder(), "nexuses.yml");
    }

    // ---------- 検索 ----------

    public Collection<Nexus> all() {
        return byId.values();
    }

    public Nexus byId(UUID id) {
        return byId.get(id);
    }

    /**
     * サーバーに1基だけのネクサス。無ければ null。
     * 参加はサーバー単位なので、距離やディメンションに関係なく対象を決めたい処理はこれを使う。
     */
    public Nexus primary() {
        return byId.values().stream().findFirst().orElse(null);
    }

    /** 指定座標にネクサスブロックがあればそれを返す。 */
    public Nexus at(Location loc) {
        for (Nexus nexus : byId.values()) {
            if (nexus.isAt(loc)) {
                return nexus;
            }
        }
        return null;
    }

    /** 防衛半径内で最も近いネクサス。範囲外なら null。 */
    public Nexus nearest(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        double radius = config.nexusDefenseRadius();
        double bestSq = radius * radius;
        Nexus best = null;
        for (Nexus nexus : byId.values()) {
            Location c = nexus.center();
            if (c == null || !c.getWorld().equals(loc.getWorld())) {
                continue;
            }
            double d = c.distanceSquared(loc);
            if (d <= bestSq) {
                bestSq = d;
                best = nexus;
            }
        }
        return best;
    }

    /** 半径を問わず最も近い稼働中ネクサス。ワールドイベントの基準点に使う。 */
    public Nexus nearestActive(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        double bestSq = Double.MAX_VALUE;
        Nexus best = null;
        for (Nexus nexus : byId.values()) {
            if (!nexus.isActive()) {
                continue;
            }
            Location c = nexus.center();
            if (c == null || !c.getWorld().equals(loc.getWorld())) {
                continue;
            }
            double d = c.distanceSquared(loc);
            if (d < bestSq) {
                bestSq = d;
                best = nexus;
            }
        }
        return best;
    }

    public List<Nexus> active() {
        List<Nexus> out = new ArrayList<>();
        for (Nexus nexus : byId.values()) {
            if (nexus.isActive()) {
                out.add(nexus);
            }
        }
        return out;
    }

    // ---------- 登録 ----------

    public Nexus create(Location loc, UUID owner, String ownerName) {
        Nexus nexus = new Nexus(UUID.randomUUID(), loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        nexus.ownerId(owner);
        nexus.ownerName(ownerName);
        nexus.health(maxHealth(nexus));
        if (owner != null) {
            nexus.members().add(owner);
        }
        byId.put(nexus.id(), nexus);
        return nexus;
    }

    /**
     * いまオンラインの全員をメンバーにする。
     * サーバーに入った人は全員が参加者として扱われるため、後から置かれたネクサスにも揃えておく。
     */
    public void joinAllOnline(Nexus nexus) {
        for (var player : plugin.getServer().getOnlinePlayers()) {
            nexus.members().add(player.getUniqueId());
        }
    }

    public void remove(Nexus nexus) {
        byId.remove(nexus.id());
    }

    // ---------- 置き方 ----------

    /**
     * ネクサスの真下が空中（固体ブロックでない）か。
     *
     * <p>侵略者はネクサスの 3 ブロック以内まで近づかないと殴れないので、宙に浮かせると
     * 序盤の侵略者は一切届かず、夜明けまで待つだけで勝ててしまう。起動の条件と、起動後の監視に使う。</p>
     *
     * @param loadChunk チャンクが読まれていなければ読んでまで確かめるか。起動の可否は必ず確かめたいので true、
     *                  毎秒の監視はチャンクを読ませ続けないよう false（読まれていなければ浮いていない扱い）
     */
    public boolean isFloating(Nexus nexus, boolean loadChunk) {
        Location loc = nexus == null ? null : nexus.location();
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        if (!loadChunk && !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            return false;
        }
        return !loc.getBlock().getRelative(BlockFace.DOWN).getType().isSolid();
    }

    // ---------- 移動先 ----------

    /**
     * ネクサスのそばで立てる場所。開始前に参加者を集めるときと、/invasion tp nexus で使う。
     * 全員が同じ1点に重ならないよう、少し散らす。見つからなければネクサスの上に立たせる。
     */
    public Location standingSpot(Nexus nexus) {
        Location base = nexus == null ? null : nexus.location();
        if (base == null || base.getWorld() == null) {
            return null;
        }
        World world = base.getWorld();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist = 2.0 + rng.nextDouble() * 3.0;
            int x = base.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = base.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            // 屋根の上や谷底に飛ばさないよう、ネクサスと高さが近い地面だけを使う
            for (int y = base.getBlockY() + 3; y >= base.getBlockY() - 3; y--) {
                Block ground = world.getBlockAt(x, y, z);
                if (ground.getType().isSolid() && ground.getType() != Material.LAVA
                        && ground.getRelative(BlockFace.UP).isPassable()
                        && !ground.getRelative(BlockFace.UP).isLiquid()
                        && ground.getRelative(BlockFace.UP, 2).isPassable()) {
                    return faceNexus(new Location(world, x + 0.5, y + 1.0, z + 0.5), base);
                }
            }
        }
        return faceNexus(base.clone().add(0.5, 1.0, 0.5), base);
    }

    /** 着いたときにネクサスの方を向かせる。 */
    private static Location faceNexus(Location spot, Location nexus) {
        double dx = nexus.getX() + 0.5 - spot.getX();
        double dz = nexus.getZ() + 0.5 - spot.getZ();
        if (dx * dx + dz * dz > 0.01) {
            spot.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        }
        return spot;
    }

    // ---------- 派生値 ----------

    public double maxHealth(Nexus nexus) {
        double base = config.nexusBaseHealth() + config.nexusHealthPerLevel() * (nexus.level() - 1);
        double bonus = 1.0 + 0.15 * nexus.upgradeLevel(NexusUpgrade.MAX_HEALTH);
        return base * bonus;
    }

    public double healthRatio(Nexus nexus) {
        double max = maxHealth(nexus);
        return max <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, nexus.health() / max));
    }

    /** 湧き半径。結界拡張アップグレードで押し広げる。 */
    public int spawnRadiusMin(Nexus nexus) {
        return config.spawnRadiusMin() + 4 * nexus.upgradeLevel(NexusUpgrade.SPAWN_DISTANCE);
    }

    public int spawnRadiusMax(Nexus nexus) {
        return config.spawnRadiusMax() + 4 * nexus.upgradeLevel(NexusUpgrade.SPAWN_DISTANCE);
    }

    /** 整備術式による割引を適用したコスト。 */
    public int discounted(Nexus nexus, int cost) {
        if (nexus == null) {
            return cost;
        }
        double rate = 1.0 - 0.10 * nexus.upgradeLevel(NexusUpgrade.REPAIR_EFFICIENCY);
        return Math.max(1, (int) Math.round(cost * Math.max(0.4, rate)));
    }

    /** 残滓精製による Shard 増加を適用した量。 */
    public int shardBonus(Nexus nexus, int amount) {
        if (nexus == null) {
            return amount;
        }
        double rate = 1.0 + 0.12 * nexus.upgradeLevel(NexusUpgrade.SHARD_YIELD);
        return Math.max(1, (int) Math.round(amount * rate));
    }

    // ---------- 永続化 ----------

    public void load() {
        byId.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("nexuses");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            try {
                UUID id = UUID.fromString(key);
                Nexus nexus = Nexus.load(id, sec);
                if (nexus != null) {
                    byId.put(id, nexus);
                }
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("不正なネクサスIDをスキップしました: " + key);
            }
        }
        plugin.getLogger().info("ネクサスを " + byId.size() + " 基読み込みました。");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("nexuses");
        for (Nexus nexus : byId.values()) {
            nexus.save(root.createSection(nexus.id().toString()));
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("データフォルダを作成できませんでした: " + parent);
            }
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "nexuses.yml の保存に失敗しました", ex);
        }
    }
}
