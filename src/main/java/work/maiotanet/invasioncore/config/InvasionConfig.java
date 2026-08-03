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

    /** 稼働中ネクサスの周囲でバニラ敵Mobの自然湧きを止めるか。 */
    public boolean suppressVanillaSpawns() {
        return cfg.getBoolean("nexus.suppress-vanilla-spawns", true);
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

    public boolean backpackEnabled() {
        return cfg.getBoolean("world-events.backpack.enabled", true);
    }

    public int backpackExpireSeconds() {
        return cfg.getInt("world-events.backpack.expire-seconds", 1800);
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

    public FileConfiguration raw() {
        return cfg;
    }
}
