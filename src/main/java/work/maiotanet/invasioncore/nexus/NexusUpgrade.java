package work.maiotanet.invasioncore.nexus;

import org.bukkit.Material;

/**
 * ネクサスのアップグレードツリー。
 * Shard を消費して段階強化する。効果の適用側は各サービスが本 enum を参照する。
 */
public enum NexusUpgrade {

    MAX_HEALTH("装甲強化", Material.NETHERITE_INGOT, 5, 40, 1.6,
            "ネクサス最大HP +15%/Lv"),
    REGENERATION("自己修復", Material.GLISTERING_MELON_SLICE, 5, 55, 1.7,
            "準備フェーズ中に毎秒HP回復"),
    SPAWN_DISTANCE("結界拡張", Material.END_CRYSTAL, 4, 60, 1.8,
            "敵の湧き位置を遠ざける +4ブロック/Lv"),
    TURRET_RANGE("照準演算", Material.SPYGLASS, 4, 50, 1.7,
            "タレット射程 +2ブロック/Lv"),
    SHARD_YIELD("残滓精製", Material.AMETHYST_CLUSTER, 5, 45, 1.65,
            "Shard 獲得量 +12%/Lv"),
    REPAIR_EFFICIENCY("整備術式", Material.ANVIL, 4, 50, 1.7,
            "修理・強化コスト -10%/Lv"),
    TURRET_SLOTS("砲台増設", Material.DISPENSER, 13, 30, 1.2,
            "タレットを置ける数を増やす");

    /** 砲台増設の段数。config.yml の turret-limit から決まるので、設定を読み直すたびに入れ直す。 */
    private static volatile int turretSlotLevels = 13;

    public static void turretSlotLevels(int levels) {
        turretSlotLevels = Math.max(0, levels);
    }

    private final String displayName;
    private final Material icon;
    private final int maxLevel;
    private final int baseCost;
    private final double costGrowth;
    private final String description;

    NexusUpgrade(String displayName, Material icon, int maxLevel, int baseCost, double costGrowth, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.maxLevel = maxLevel;
        this.baseCost = baseCost;
        this.costGrowth = costGrowth;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public int maxLevel() {
        return this == TURRET_SLOTS ? turretSlotLevels : maxLevel;
    }

    public String description() {
        return description;
    }

    /** 現在レベルから次のレベルへ上げるコスト。maxLevel 到達時は -1。 */
    public int costFor(int currentLevel) {
        if (currentLevel >= maxLevel()) {
            return -1;
        }
        return (int) Math.round(baseCost * Math.pow(costGrowth, currentLevel));
    }
}
