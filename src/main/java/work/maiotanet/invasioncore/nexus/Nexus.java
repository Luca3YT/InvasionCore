package work.maiotanet.invasioncore.nexus;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ネクサス1基の状態。
 *
 * <p>侵略MODの「ネクサス」と 7DTD の「拠点＋7日周期」を統合した中心オブジェクト。
 * 日数・GameStage・ウェーブ進行・アップグレード・報酬バッファを全てここが保持する。</p>
 */
public final class Nexus {

    /** ネクサスの進行フェーズ。 */
    public enum Phase {
        /** 未起動。設置しただけの状態。 */
        DORMANT("待機"),
        /** 準備フェーズ（ブラッドムーンでない日）。探索・建築・強化を行う。 */
        PREPARATION("準備"),
        /** ブラッドムーン。ウェーブ戦闘中。 */
        BLOOD_MOON("血の月"),
        /** 敗北。HPが0になった状態。再起動には修復が必要。 */
        BROKEN("崩壊");

        private final String label;

        Phase(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final UUID id;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;

    private UUID ownerId;
    private String ownerName;

    private int level = 1;
    private double health;
    private Phase phase = Phase.DORMANT;

    /** 起動してから経過した日数（1始まり）。7の倍数の夜がブラッドムーン。 */
    private int day = 1;

    /** 最後に日跨ぎ処理をしたワールド日。重複処理防止用。 */
    private long lastProcessedWorldDay = -1L;

    /** そのブラッドムーンで到達した最大ウェーブ（統計用）。 */
    private int bestWave = 0;

    private final Map<NexusUpgrade, Integer> upgrades = new EnumMap<>(NexusUpgrade.class);
    private final Set<UUID> members = new HashSet<>();
    private final List<ItemStack> rewardBuffer = new ArrayList<>();

    /**
     * 開始時の帰還の羅針盤を受け取った人。ゲーム（このネクサス）ごとに1人1個だけ配るため保存する。
     * 入り直すたびに配ると、抜けて入るだけで何個でも手に入ってしまう。
     */
    private final Set<UUID> recallGiven = new HashSet<>();

    /**
     * ネクサスに預けられた共有 Shard。
     * 参加者の支払いで、手持ちが足りない分に充てられる。引き出せるのは管理者だけ。
     */
    private int sharedShards = 0;

    // --- 実行時のみの状態（永続化しない） ---
    private transient int currentWave = 0;
    private transient int totalWaves = 0;
    private transient long nextWaveTick = 0L;
    private transient String activeModifier = null;

    public Nexus(UUID id, String worldName, int x, int y, int z) {
        this.id = id;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.health = 500.0;
    }

    // ---------- 基本情報 ----------

    public UUID id() {
        return id;
    }

    public String worldName() {
        return worldName;
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    public Location location() {
        World w = world();
        return w == null ? null : new Location(w, x, y, z);
    }

    /** ブロック中心座標。距離計算やパーティクルはこちらを使う。 */
    public Location center() {
        World w = world();
        return w == null ? null : new Location(w, x + 0.5, y + 0.5, z + 0.5);
    }

    public boolean isAt(Location loc) {
        return loc != null
                && loc.getWorld() != null
                && loc.getWorld().getName().equals(worldName)
                && loc.getBlockX() == x
                && loc.getBlockY() == y
                && loc.getBlockZ() == z;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public void ownerId(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public String ownerName() {
        return ownerName == null ? "unknown" : ownerName;
    }

    public void ownerName(String ownerName) {
        this.ownerName = ownerName;
    }

    // ---------- 進行状態 ----------

    public int level() {
        return level;
    }

    public void level(int level) {
        this.level = Math.max(1, level);
    }

    public double health() {
        return health;
    }

    public void health(double health) {
        this.health = Math.max(0.0, health);
    }

    public Phase phase() {
        return phase;
    }

    public void phase(Phase phase) {
        this.phase = phase;
    }

    public int day() {
        return day;
    }

    public void day(int day) {
        this.day = Math.max(1, day);
    }

    public long lastProcessedWorldDay() {
        return lastProcessedWorldDay;
    }

    public void lastProcessedWorldDay(long v) {
        this.lastProcessedWorldDay = v;
    }

    public int bestWave() {
        return bestWave;
    }

    public void bestWave(int bestWave) {
        this.bestWave = Math.max(this.bestWave, bestWave);
    }

    public boolean isActive() {
        return phase == Phase.PREPARATION || phase == Phase.BLOOD_MOON;
    }

    /** 次の夜がブラッドムーンかどうか。 */
    public boolean isBloodMoonDay(int interval) {
        return day % interval == 0;
    }

    /** ブラッドムーンまでの残り日数。当日なら 0。 */
    public int daysUntilBloodMoon(int interval) {
        int mod = day % interval;
        return mod == 0 ? 0 : interval - mod;
    }

    // ---------- アップグレード ----------

    public int upgradeLevel(NexusUpgrade upgrade) {
        return upgrades.getOrDefault(upgrade, 0);
    }

    public void upgradeLevel(NexusUpgrade upgrade, int value) {
        upgrades.put(upgrade, Math.max(0, Math.min(upgrade.maxLevel(), value)));
    }

    public Map<NexusUpgrade, Integer> upgrades() {
        return upgrades;
    }

    // ---------- メンバー ----------

    public Set<UUID> members() {
        return members;
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public Set<UUID> recallGiven() {
        return recallGiven;
    }

    // ---------- 報酬バッファ ----------

    public List<ItemStack> rewardBuffer() {
        return rewardBuffer;
    }

    // ---------- 共有 Shard ----------

    public int sharedShards() {
        return sharedShards;
    }

    public void sharedShards(int sharedShards) {
        this.sharedShards = Math.max(0, sharedShards);
    }

    // ---------- 実行時状態 ----------

    public int currentWave() {
        return currentWave;
    }

    public void currentWave(int currentWave) {
        this.currentWave = currentWave;
    }

    public int totalWaves() {
        return totalWaves;
    }

    public void totalWaves(int totalWaves) {
        this.totalWaves = totalWaves;
    }

    public long nextWaveTick() {
        return nextWaveTick;
    }

    public void nextWaveTick(long nextWaveTick) {
        this.nextWaveTick = nextWaveTick;
    }

    public String activeModifier() {
        return activeModifier;
    }

    public void activeModifier(String activeModifier) {
        this.activeModifier = activeModifier;
    }

    // ---------- 永続化 ----------

    public void save(ConfigurationSection sec) {
        sec.set("world", worldName);
        sec.set("x", x);
        sec.set("y", y);
        sec.set("z", z);
        sec.set("owner-id", ownerId == null ? null : ownerId.toString());
        sec.set("owner-name", ownerName);
        sec.set("level", level);
        sec.set("health", health);
        sec.set("phase", phase.name());
        sec.set("day", day);
        sec.set("last-world-day", lastProcessedWorldDay);
        sec.set("best-wave", bestWave);

        ConfigurationSection up = sec.createSection("upgrades");
        for (Map.Entry<NexusUpgrade, Integer> e : upgrades.entrySet()) {
            up.set(e.getKey().name(), e.getValue());
        }

        List<String> memberList = new ArrayList<>(members.size());
        for (UUID uuid : members) {
            memberList.add(uuid.toString());
        }
        sec.set("members", memberList);
        List<String> recallList = new ArrayList<>(recallGiven.size());
        for (UUID uuid : recallGiven) {
            recallList.add(uuid.toString());
        }
        sec.set("recall-given", recallList);
        sec.set("rewards", new ArrayList<>(rewardBuffer));
        sec.set("shared-shards", sharedShards);
    }

    @SuppressWarnings("unchecked")
    public static Nexus load(UUID id, ConfigurationSection sec) {
        String world = sec.getString("world");
        if (world == null) {
            return null;
        }
        Nexus nexus = new Nexus(id, world, sec.getInt("x"), sec.getInt("y"), sec.getInt("z"));
        String owner = sec.getString("owner-id");
        if (owner != null) {
            try {
                nexus.ownerId = UUID.fromString(owner);
            } catch (IllegalArgumentException ignored) {
                // 壊れたUUIDは無視して所有者なし扱いにする
            }
        }
        nexus.ownerName = sec.getString("owner-name");
        nexus.level = Math.max(1, sec.getInt("level", 1));
        nexus.health = sec.getDouble("health", 500.0);
        nexus.day = Math.max(1, sec.getInt("day", 1));
        nexus.lastProcessedWorldDay = sec.getLong("last-world-day", -1L);
        nexus.bestWave = sec.getInt("best-wave", 0);
        nexus.sharedShards = Math.max(0, sec.getInt("shared-shards", 0));
        try {
            nexus.phase = Phase.valueOf(sec.getString("phase", Phase.DORMANT.name()));
        } catch (IllegalArgumentException ex) {
            nexus.phase = Phase.DORMANT;
        }
        // ウェーブの進行状況は永続化していないので、血の月を途中から再開することはできない。
        // BLOOD_MOON のまま読み込むと「全ウェーブ消化済み・残敵0」と判定されて即勝利になり、
        // 再起動するだけで Lv・報酬・パークポイントが手に入ってしまう。準備フェーズに戻しておけば、
        // 夜のうちに再起動した場合は日没処理が未済扱いになり、血の月が最初からやり直しになる。
        if (nexus.phase == Phase.BLOOD_MOON) {
            nexus.phase = Phase.PREPARATION;
        }

        ConfigurationSection up = sec.getConfigurationSection("upgrades");
        if (up != null) {
            for (String k : up.getKeys(false)) {
                try {
                    nexus.upgrades.put(NexusUpgrade.valueOf(k), up.getInt(k));
                } catch (IllegalArgumentException ignored) {
                    // 削除された古いアップグレード種別は捨てる
                }
            }
        }

        for (String s : sec.getStringList("members")) {
            try {
                nexus.members.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                // 同上
            }
        }

        for (String s : sec.getStringList("recall-given")) {
            try {
                nexus.recallGiven.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                // 同上
            }
        }

        List<?> rewards = sec.getList("rewards");
        if (rewards != null) {
            for (Object o : rewards) {
                if (o instanceof ItemStack stack) {
                    nexus.rewardBuffer.add(stack);
                }
            }
        }
        return nexus;
    }
}
