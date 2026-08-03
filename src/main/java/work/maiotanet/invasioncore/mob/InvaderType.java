package work.maiotanet.invasioncore.mob;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * 侵略Mobの種別定義。
 *
 * <p>Forge の MOD と違い新規エンティティは追加できないため、バニラのエンティティに
 * 属性・装備・独自AIを載せて役割を表現する。見た目はリソースパックなしでも判別できるよう、
 * 頭防具と手持ちアイテムで差をつけている。</p>
 */
public enum InvaderType {

    /** 基本歩兵。数で押す。 */
    GRUNT("侵略者", EntityType.ZOMBIE, 1.00, 0.90, 1.00, 2, 0, 100,
            Material.LEATHER_HELMET, null),

    /** 俊足。夜とブラッドムーンで真価を発揮する。 */
    RUNNER("疾走者", EntityType.HUSK, 0.80, 1.45, 0.90, 3, 8, 65,
            Material.CHAINMAIL_HELMET, null),

    /** 地面を掘って地下から侵入する。埋設防御を無効化する。 */
    BURROWER("掘削者", EntityType.ZOMBIE, 1.40, 0.80, 1.10, 5, 18, 26,
            Material.IRON_HELMET, Material.IRON_SHOVEL),

    /** 足場を組んで高所・堀を突破する。侵略MODのPig Engineer相当。 */
    ENGINEER("工兵", EntityType.ZOMBIE_VILLAGER, 1.20, 0.85, 0.70, 6, 22, 20,
            Material.GOLDEN_HELMET, Material.SCAFFOLDING),

    /** 遠距離攻撃。タレットとプレイヤーを優先的に狙う。 */
    RANGED("射手", EntityType.SKELETON, 0.75, 1.00, 1.00, 4, 12, 48,
            null, Material.BOW),

    /** 飛行。地上防御を完全に無視してネクサスへ直行する。 */
    FLYER("飛翔体", EntityType.PHANTOM, 0.90, 1.20, 1.00, 6, 32, 22,
            null, null),

    /** 自爆。壁とタレットを吹き飛ばす。 */
    BOMBER("爆導体", EntityType.CREEPER, 0.85, 1.10, 1.00, 7, 26, 24,
            null, null),

    /** 周囲の味方にダメージ軽減を配る支援型。放置すると硬直する。 */
    SHIELDER("守護者", EntityType.PIGLIN_BRUTE, 1.60, 0.80, 0.80, 9, 38, 14,
            Material.DIAMOND_HELMET, Material.SHIELD),

    /** ヒート超過で出現。叫ぶと追加ホードを呼ぶ。通常ウェーブには出ない。 */
    SCREAMER("叫喚体", EntityType.EVOKER, 1.10, 0.95, 0.60, 10, 0, 0,
            null, null),

    /** 5波ごとのボス。専用スキルを持つ。 */
    BOSS("侵略主", EntityType.RAVAGER, 6.00, 0.90, 1.80, 45, 0, 0,
            null, null);

    private final String displayName;
    private final EntityType entityType;
    private final double healthMultiplier;
    private final double speedMultiplier;
    private final double damageMultiplier;
    private final int shardValue;
    private final int minStage;
    private final int weight;
    private final Material helmet;
    private final Material handItem;

    InvaderType(String displayName, EntityType entityType, double healthMultiplier, double speedMultiplier,
                double damageMultiplier, int shardValue, int minStage, int weight,
                Material helmet, Material handItem) {
        this.displayName = displayName;
        this.entityType = entityType;
        this.healthMultiplier = healthMultiplier;
        this.speedMultiplier = speedMultiplier;
        this.damageMultiplier = damageMultiplier;
        this.shardValue = shardValue;
        this.minStage = minStage;
        this.weight = weight;
        this.helmet = helmet;
        this.handItem = handItem;
    }

    public String displayName() {
        return displayName;
    }

    public EntityType entityType() {
        return entityType;
    }

    public double healthMultiplier() {
        return healthMultiplier;
    }

    public double speedMultiplier() {
        return speedMultiplier;
    }

    public double damageMultiplier() {
        return damageMultiplier;
    }

    public int shardValue() {
        return shardValue;
    }

    public int minStage() {
        return minStage;
    }

    /** ウェーブ抽選の重み。0 は通常抽選に出さない（特殊湧きのみ）。 */
    public int weight() {
        return weight;
    }

    public Material helmet() {
        return helmet;
    }

    public Material handItem() {
        return handItem;
    }

    // ---- 挙動フラグ ----

    /** 経路が塞がれたときにブロックを壊して進むか。 */
    public boolean canBreach() {
        return this != FLYER && this != SCREAMER;
    }

    /** 地面を掘り進むか。 */
    public boolean canBurrow() {
        return this == BURROWER;
    }

    /** 足場を建てるか。 */
    public boolean canBuild() {
        return this == ENGINEER;
    }

    public boolean isRanged() {
        return this == RANGED;
    }

    public boolean isFlying() {
        return this == FLYER;
    }

    public boolean isBomber() {
        return this == BOMBER;
    }

    public boolean isSupport() {
        return this == SHIELDER;
    }

    public boolean isBoss() {
        return this == BOSS;
    }
}
