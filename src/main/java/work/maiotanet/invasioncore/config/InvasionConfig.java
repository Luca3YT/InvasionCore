package work.maiotanet.invasioncore.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * config.yml の読み出し窓口。
 * バランス値は全てここを通す。コード中に定数を直書きしないこと。
 */
public final class InvasionConfig {

    private final Plugin plugin;
    private FileConfiguration cfg;

    public InvasionConfig(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        this.cfg = plugin.getConfig();
    }

    // ---- ネクサス ----
    public double nexusBaseHealth() {
        return cfg.getDouble("nexus.base-health", 500.0);
    }

    public double nexusHealthPerLevel() {
        return cfg.getDouble("nexus.health-per-level", 120.0);
    }

    public int nexusDefenseRadius() {
        return cfg.getInt("nexus.defense-radius", 48);
    }

    /** 侵略Mobがブロックを壊してよい範囲。ここを超える破壊は行わない。 */
    public int nexusBreachRadius() {
        return cfg.getInt("nexus.breach-radius", 40);
    }

    public double nexusDamagePerHit() {
        return cfg.getDouble("nexus.damage-per-hit", 6.0);
    }

    public int nexusMaxLevel() {
        return cfg.getInt("nexus.max-level", 30);
    }

    /** ネクサスが殴られたときの警告を、同じネクサスについて何秒に1回まで出すか。 */
    public int nexusAttackWarningSeconds() {
        return Math.max(1, cfg.getInt("nexus.attack-warning-seconds", 10));
    }

    /** 稼働中ネクサスの周囲でバニラ敵Mobの自然湧きを止めるか。 */
    public boolean suppressVanillaSpawns() {
        return cfg.getBoolean("nexus.suppress-vanilla-spawns", true);
    }

    // ---- 侵略Mobの速度 ----
    /**
     * 全侵略Mobの移動速度に掛かる係数。
     * 種別ごとの相対関係を保ったまま、全体の速さだけを調整するためのノブ。
     */
    public double mobSpeedMultiplier() {
        return cfg.getDouble("mob.speed-multiplier", 0.85);
    }

    /**
     * 飛行型の移動速度（ブロック/tick）。
     * 経路探索を使わず速度を直接与えるため、属性ではなくこの値で決まる。
     * 参考: 歩行約0.215 / 疾走約0.28。
     */
    public double flyerSpeed() {
        return cfg.getDouble("mob.flyer-speed", 0.20);
    }

    // ---- 突破AI ----
    /** AI更新1回あたりの基礎ブロック破壊力。 */
    public double breachPowerBase() {
        return cfg.getDouble("breach.power-base", 2.5);
    }

    /** 攻撃力1あたりの追加破壊力。 */
    public double breachPowerPerDamage() {
        return cfg.getDouble("breach.power-per-damage", 1.5);
    }

    /** 何回のAI更新で距離が縮まらなければ閉塞と判断するか。 */
    public int breachStuckThreshold() {
        return cfg.getInt("breach.stuck-threshold", 3);
    }

    /**
     * 壁を殴る間隔（tick）。
     *
     * <p>これが無いとAI更新のたびに殴ることになり、破壊力が
     * performance.ai-buckets の設定値に左右されてしまう。
     * 性能設定を変えたら壁の強度まで変わる、という事故を防ぐために必要。</p>
     */
    public int breachSwingIntervalTicks() {
        return Math.max(1, cfg.getInt("breach.swing-interval-ticks", 20));
    }

    /** この距離までプレイヤーが近づくと、ネクサスより先にプレイヤーを狙う。 */
    public double playerAggroRange() {
        return cfg.getDouble("breach.player-aggro-range", 7.0);
    }

    /** 工兵が建てられる最大の高さ。 */
    public int engineerMaxBuildHeight() {
        return cfg.getInt("breach.engineer-max-build-height", 24);
    }

    // ---- 日サイクル / ブラッドムーン ----
    public int bloodMoonInterval() {
        return Math.max(1, cfg.getInt("cycle.blood-moon-interval", 7));
    }

    public boolean nightSkirmishEnabled() {
        return cfg.getBoolean("cycle.night-skirmish", true);
    }

    /**
     * 夜間襲撃が始まる日。これより前の夜は襲撃が来ない。
     * 拠点もShardも無い初日から削られると、立ち上がりが理不尽になる。
     * 叫喚体もこの日までは湧かせず、徘徊ホードは弱い個体の少数だけにする（猶予期間）。
     */
    public int skirmishStartDay() {
        return cfg.getInt("cycle.skirmish-start-day", 3);
    }

    public double nightSkirmishScale() {
        return cfg.getDouble("cycle.night-skirmish-scale", 0.22);
    }

    // ---- ウェーブ ----
    public int waveBaseCount() {
        return cfg.getInt("wave.base-count", 5);
    }

    public int waveCountPerFiveStage() {
        return cfg.getInt("wave.count-per-five-stage", 1);
    }

    public int waveMaxCount() {
        return cfg.getInt("wave.max-count", 12);
    }

    public int waveIntervalTicks() {
        return cfg.getInt("wave.interval-ticks", 900);
    }

    public int waveBaseMobs() {
        return cfg.getInt("wave.base-mobs", 6);
    }

    public double waveMobsPerStage() {
        return cfg.getDouble("wave.mobs-per-stage", 0.35);
    }

    public int waveMaxAlive() {
        return cfg.getInt("wave.max-alive", 90);
    }

    public int spawnRadiusMin() {
        return cfg.getInt("wave.spawn-radius-min", 26);
    }

    public int spawnRadiusMax() {
        return cfg.getInt("wave.spawn-radius-max", 42);
    }

    // ---- GameStage ----
    public double stagePerNexusLevel() {
        return cfg.getDouble("gamestage.per-nexus-level", 8.0);
    }

    public double stagePerDay() {
        return cfg.getDouble("gamestage.per-day", 1.5);
    }

    public double stagePerPlayerLevel() {
        return cfg.getDouble("gamestage.per-player-level", 1.2);
    }

    public double stagePartyBonus() {
        return cfg.getDouble("gamestage.party-bonus", 0.20);
    }

    // ---- ヒート ----
    public boolean heatEnabled() {
        return cfg.getBoolean("heat.enabled", true);
    }

    public double heatPerBlockBreak() {
        return cfg.getDouble("heat.per-block-break", 0.6);
    }

    public double heatPerBlockPlace() {
        return cfg.getDouble("heat.per-block-place", 0.3);
    }

    public double heatPerKill() {
        return cfg.getDouble("heat.per-kill", 1.5);
    }

    public double heatPerShot() {
        return cfg.getDouble("heat.per-shot", 0.8);
    }

    public double heatDecayPerSecond() {
        return cfg.getDouble("heat.decay-per-second", 0.45);
    }

    public double heatScreamerThreshold() {
        return cfg.getDouble("heat.screamer-threshold", 120.0);
    }

    public int heatScreamerCooldownSeconds() {
        return cfg.getInt("heat.screamer-cooldown-seconds", 180);
    }

    // ---- 経済 ----
    public int shardBaseDrop() {
        return cfg.getInt("economy.shard-base-drop", 2);
    }

    public int repairCostPerHp() {
        return cfg.getInt("economy.repair-cost-per-hp", 20);
    }

    /** 侵略者ではない普通の敵をプレイヤーが倒したときに落とす Shard の数。0 で落とさない。 */
    public int vanillaMobShard() {
        return Math.max(0, cfg.getInt("economy.vanilla-mob-shard", 1));
    }

    /** モンスタースポナーから出た敵にも Shard を落とさせるか。トラップタワーでの稼ぎを防ぐため既定は false。 */
    public boolean vanillaMobShardFromSpawner() {
        return cfg.getBoolean("economy.vanilla-mob-shard-from-spawner", false);
    }

    // ---- ワールドイベント ----
    public boolean wanderingHordeEnabled() {
        return cfg.getBoolean("world-events.wandering-horde.enabled", true);
    }

    public int wanderingHordeMinSeconds() {
        return cfg.getInt("world-events.wandering-horde.min-seconds", 600);
    }

    public int wanderingHordeMaxSeconds() {
        return cfg.getInt("world-events.wandering-horde.max-seconds", 1500);
    }

    public boolean airDropEnabled() {
        return cfg.getBoolean("world-events.air-drop.enabled", true);
    }

    public int airDropIntervalSeconds() {
        return cfg.getInt("world-events.air-drop.interval-seconds", 1200);
    }

    // ---- 墓 ----
    /** 死んだときに所持品を墓（プレイヤーの頭）にまとめるか。 */
    public boolean graveEnabled() {
        return cfg.getBoolean("grave.enabled", true);
    }

    /** 墓を本人しか回収できない時間（秒）。過ぎるとだれでも回収できる。 */
    public int graveOwnerOnlySeconds() {
        return Math.max(0, cfg.getInt("grave.owner-only-seconds", 600));
    }

    /** 他の人が回収したいと頼んだとき、本人の返事を待つ時間（秒）。 */
    public int graveApprovalTimeoutSeconds() {
        return Math.max(5, cfg.getInt("grave.approval-timeout-seconds", 60));
    }

    // ---- ネクサスの案内表示 ----
    /** ネクサスを光らせて、壁越しにも輪郭が見えるようにするか。 */
    public boolean guideNexusGlow() {
        return cfg.getBoolean("guide.nexus-glow", true);
    }

    /** 防衛範囲の外にいるとき、アクションバーにネクサスの方向と距離を出すか。 */
    public boolean guideCompass() {
        return cfg.getBoolean("guide.compass", true);
    }

    /** 防衛範囲の境目からこの距離（ブロック）以内にいると、境目にパーティクルを描く。0 で描かない。 */
    public int guideBoundaryWithin() {
        return Math.max(0, cfg.getInt("guide.boundary-particles-within", 12));
    }

    // ---- パフォーマンス ----
    public int aiBuckets() {
        return Math.max(1, cfg.getInt("performance.ai-buckets", 4));
    }

    public int turretTickInterval() {
        return Math.max(1, cfg.getInt("performance.turret-tick-interval", 10));
    }

    public boolean keepChunksLoaded() {
        return cfg.getBoolean("performance.keep-chunks-loaded", true);
    }

    // ---- ゲーム開始前 ----
    /** /rule で表示するルール説明の URL。 */
    public String rulesUrl() {
        return cfg.getString("rules.url", "https://luca3yt.github.io/InvasionCore/");
    }

    // ---- Web（管理画面・OBS オーバーレイ） ----
    public boolean webEnabled() {
        return cfg.getBoolean("web.enabled", true);
    }

    public int webPort() {
        return cfg.getInt("web.port", 8765);
    }

    public String webBind() {
        return cfg.getString("web.bind", "0.0.0.0");
    }

    /** 外から見たアドレス（末尾の / なし）。空なら LAN の IP で URL を作る。 */
    public String webPublicUrl() {
        String url = cfg.getString("web.public-url", "");
        url = url == null ? "" : url.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    /** OBS オーバーレイにもパスワードを要求するか。 */
    public boolean webOverlayToken() {
        return cfg.getBoolean("web.overlay-token", false);
    }

    public String overlayStyle() {
        String style = cfg.getString("web.overlay-style", "b");
        return style == null ? "b" : style.toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 管理画面から表示パターンを変えたとき。config.yml のこの項目だけを書き換える。
     *
     * <p>メモリ上の設定ごと保存すると、サーバー稼働中に config.yml を手で編集していた場合
     * その編集が古い内容で上書きされて消える。ファイルを読み直して1項目だけ変えて書き戻す。</p>
     */
    public void overlayStyle(String style) {
        java.io.File file = new java.io.File(plugin.getDataFolder(), "config.yml");
        org.bukkit.configuration.file.YamlConfiguration disk =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        disk.set("web.overlay-style", style);
        try {
            disk.save(file);
        } catch (java.io.IOException ex) {
            plugin.getLogger().warning("config.yml に表示パターンを保存できませんでした: " + ex.getMessage());
        }
        cfg.set("web.overlay-style", style);
    }

    public FileConfiguration raw() {
        return cfg;
    }
}
