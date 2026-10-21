package work.maiotanet.invasioncore.perk;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * プレイヤーの恒久進行（パーク・累計戦績）。
 */
public final class PerkService {

    /** プレイヤー1人分の進行データ。 */
    public static final class Profile {
        private final Map<Perk, Integer> perks = new EnumMap<>(Perk.class);
        private int points;
        private int totalKills;
        private int bloodMoonsSurvived;
        private int bestWave;
        private String lastKnownName = "";

        public int level(Perk perk) {
            return perks.getOrDefault(perk, 0);
        }

        public int points() {
            return points;
        }

        public int totalKills() {
            return totalKills;
        }

        public int bloodMoonsSurvived() {
            return bloodMoonsSurvived;
        }

        public int bestWave() {
            return bestWave;
        }

        public String lastKnownName() {
            return lastKnownName;
        }
    }

    private final Plugin plugin;
    private final File file;
    private final Map<UUID, Profile> profiles = new HashMap<>();

    public PerkService(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
    }

    public Profile profile(UUID id) {
        return profiles.computeIfAbsent(id, k -> new Profile());
    }

    public Profile profile(Player player) {
        Profile p = profile(player.getUniqueId());
        p.lastKnownName = player.getName();
        return p;
    }

    // ---------- 効果の適用 ----------

    /**
     * 侵略者への与ダメージ倍率。
     *
     * @param healthRatio 現在HPの割合。半分以下なら「猛者」が乗る。
     */
    public double outgoingDamageMultiplier(UUID id, double healthRatio) {
        double multiplier = 1.0 + 0.08 * profile(id).level(Perk.DEMOLITION);
        if (healthRatio <= 0.5) {
            multiplier += 0.15 * profile(id).level(Perk.BERSERKER);
        }
        return multiplier;
    }

    /** 侵略者からの被ダメージ倍率。 */
    public double incomingDamageMultiplier(UUID id) {
        return Math.max(0.4, 1.0 - 0.06 * profile(id).level(Perk.HEAVY_ARMOR));
    }

    /** 強化・修理コスト倍率。 */
    public double costMultiplier(UUID id) {
        return Math.max(0.4, 1.0 - 0.08 * profile(id).level(Perk.ARCHITECT));
    }

    /** Shard 獲得倍率。 */
    public double shardMultiplier(UUID id) {
        return 1.0 + 0.10 * profile(id).level(Perk.SCAVENGER);
    }

    /** 補修キットの効果倍率。 */
    public double repairKitMultiplier(UUID id) {
        return 1.0 + 0.25 * profile(id).level(Perk.MEDIC);
    }

    /** タレット・トラップ威力倍率。 */
    public double turretMultiplier(UUID id) {
        return 1.0 + 0.08 * profile(id).level(Perk.GUNNER);
    }

    /** 強化ブロックの最大耐久倍率。強化した本人のパークが乗る。 */
    public double fortifyHealthMultiplier(UUID id) {
        return 1.0 + 0.12 * profile(id).level(Perk.FORTIFIER);
    }

    /** タレットの弾薬を消費しない確率。 */
    public double ammoSaveChance(UUID id) {
        return Math.min(0.6, 0.12 * profile(id).level(Perk.AMMO_SMITH));
    }

    /** 「遊撃」による移動速度の上乗せ段階。0 なら効果なし。 */
    public int skirmisherLevel(UUID id) {
        return profile(id).level(Perk.SKIRMISHER);
    }

    // ---------- 進行 ----------

    public void addPoints(UUID id, int amount) {
        profile(id).points += Math.max(0, amount);
    }

    public boolean spend(UUID id, Perk perk) {
        Profile p = profile(id);
        int current = p.level(perk);
        int cost = perk.costFor(current);
        if (cost < 0 || p.points < cost) {
            return false;
        }
        p.points -= cost;
        p.perks.put(perk, current + 1);
        return true;
    }

    public void recordKill(UUID id) {
        profile(id).totalKills++;
    }

    public void recordVictory(UUID id, int waves) {
        Profile p = profile(id);
        p.bloodMoonsSurvived++;
        p.bestWave = Math.max(p.bestWave, waves);
        // 踏破1回で1点、5波ごとに追加1点
        p.points += 1 + waves / 5;
    }

    /** 累計戦績のランキング。 */
    public List<Map.Entry<UUID, Profile>> ranking(Comparator<Profile> comparator, int limit) {
        List<Map.Entry<UUID, Profile>> out = new ArrayList<>(profiles.entrySet());
        out.sort((a, b) -> comparator.compare(b.getValue(), a.getValue()));
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    // ---------- 永続化 ----------

    public void load() {
        profiles.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            Profile p = new Profile();
            p.points = sec.getInt("points", 0);
            p.totalKills = sec.getInt("kills", 0);
            p.bloodMoonsSurvived = sec.getInt("blood-moons", 0);
            p.bestWave = sec.getInt("best-wave", 0);
            p.lastKnownName = sec.getString("name", "");
            ConfigurationSection perkSec = sec.getConfigurationSection("perks");
            if (perkSec != null) {
                for (String pk : perkSec.getKeys(false)) {
                    try {
                        p.perks.put(Perk.valueOf(pk), perkSec.getInt(pk));
                    } catch (IllegalArgumentException ignored) {
                        // 削除されたパークは捨てる
                    }
                }
            }
            profiles.put(id, p);
        }
        plugin.getLogger().info("プレイヤー進行データを " + profiles.size() + " 件読み込みました。");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("players");
        for (Map.Entry<UUID, Profile> e : profiles.entrySet()) {
            Profile p = e.getValue();
            ConfigurationSection sec = root.createSection(e.getKey().toString());
            sec.set("points", p.points);
            sec.set("kills", p.totalKills);
            sec.set("blood-moons", p.bloodMoonsSurvived);
            sec.set("best-wave", p.bestWave);
            sec.set("name", p.lastKnownName);
            ConfigurationSection perkSec = sec.createSection("perks");
            for (Map.Entry<Perk, Integer> pe : p.perks.entrySet()) {
                perkSec.set(pe.getKey().name(), pe.getValue());
            }
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("データフォルダを作成できませんでした: " + parent);
            }
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "players.yml の保存に失敗しました", ex);
        }
    }
}
