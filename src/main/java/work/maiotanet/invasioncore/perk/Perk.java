package work.maiotanet.invasioncore.perk;

import org.bukkit.Material;

/**
 * 7DTD のパーク相当。ブラッドムーンを踏破するとポイントが入り、恒久的に強化される。
 *
 * <p>「戦闘が上手い人だけが強くなる」設計にすると支援役が報われないので、
 * 建築・修理・タレット寄りのパークにも同等の価値を持たせている。</p>
 */
public enum Perk {

    DEMOLITION("解体屋", Material.IRON_SWORD, 5,
            "侵略者への与ダメージ +8%/Lv"),
    HEAVY_ARMOR("重装", Material.IRON_CHESTPLATE, 5,
            "侵略者からの被ダメージ -6%/Lv"),
    ARCHITECT("建築士", Material.BRICKS, 4,
            "強化・修理コスト -8%/Lv"),
    SCAVENGER("漁り屋", Material.AMETHYST_SHARD, 5,
            "Shard 獲得量 +10%/Lv"),
    MEDIC("衛生兵", Material.GOLDEN_APPLE, 3,
            "補修キットの効果 +25%/Lv"),
    GUNNER("砲手", Material.CROSSBOW, 4,
            "タレットの威力 +8%/Lv");

    private final String displayName;
    private final Material icon;
    private final int maxLevel;
    private final String description;

    Perk(String displayName, Material icon, int maxLevel, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.maxLevel = maxLevel;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public Material icon() {
        return icon;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public String description() {
        return description;
    }

    /** 次のレベルに必要なポイント。上限なら -1。 */
    public int costFor(int currentLevel) {
        return currentLevel >= maxLevel ? -1 : currentLevel + 1;
    }
}
