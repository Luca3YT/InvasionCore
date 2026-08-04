package work.maiotanet.invasioncore.mob;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * 侵略Mobの種別定義。
 *
 * <p>Forge の MOD と違い新規エンティティは追加できないため、バニラのエンティティに
 * 属性・装備・独自AIを載せて役割を表現する。見た目はリソースパックなしでも判別できるよう、
 * 頭防具と手持ちアイテムで差をつけている。</p>
 *
 * <p><b>倍率はベースエンティティの既定値に対する比</b>である点に注意。
 * 例えばゾンビ(20HP)の 1.0 とアイアンゴーレム(100HP)の 1.4 では実HPが7倍違う。
 * 「重装体は硬い」といった役割はこのベース差も含めて設計している。</p>
 */
public enum InvaderType {

    // ---- 基本層（Stage 0〜15）----

    /** 基本歩兵。数で押す。 */
    GRUNT("侵略者", EntityType.ZOMBIE, 1.00, 0.90, 1.00, 2, 0, 100,
            Material.LEATHER_HELMET, null),

    /** 俊足。夜とブラッドムーンで真価を発揮する。 */
    RUNNER("疾走者", EntityType.HUSK, 0.80, 1.15, 0.90, 3, 8, 68,
            Material.CHAINMAIL_HELMET, null),

    /** 遠距離攻撃。タレットとプレイヤーを優先的に狙う。 */
    RANGED("射手", EntityType.SKELETON, 0.75, 1.00, 1.00, 4, 12, 46,
            null, Material.BOW),

    /** 壁を登る。高い壁で囲うだけの防御を無効化する。 */
    CRAWLER("壁蟲", EntityType.SPIDER, 0.75, 1.00, 0.85, 4, 14, 42,
            null, null),

    // ---- 突破層（Stage 16〜30）----

    /** 地面を掘って地下から侵入する。埋設防御を無効化する。 */
    BURROWER("掘削者", EntityType.ZOMBIE, 1.40, 0.80, 1.10, 5, 18, 26,
            Material.IRON_HELMET, Material.IRON_SHOVEL),

    /** 足場を組んで高所・堀を突破する。侵略MODの Pig Engineer 相当。 */
    ENGINEER("工兵", EntityType.ZOMBIE_VILLAGER, 1.20, 0.85, 0.70, 6, 22, 20,
            Material.GOLDEN_HELMET, Material.SCAFFOLDING),

    /** 自爆。壁とタレットを吹き飛ばす。 */
    BOMBER("爆導体", EntityType.CREEPER, 0.85, 1.10, 1.00, 7, 26, 24,
            null, null),

    /** 質量爆弾を放物線で撃ち込む砲撃型。遠距離から拠点を削る。 */
    LOBBER("投擲手", EntityType.WITHER_SKELETON, 0.90, 0.80, 1.00, 9, 28, 16,
            null, Material.TNT),

    /** 超重装。足は遅いがノックバックが効かず、ネクサスへの一撃が重い。 */
    BRUTE("重装体", EntityType.IRON_GOLEM, 1.40, 0.62, 1.60, 14, 30, 13,
            null, null),

    // ---- 上位層（Stage 31〜）----

    /** 飛行。地上防御を完全に無視してネクサスへ直行する。 */
    FLYER("飛翔体", EntityType.PHANTOM, 0.90, 1.20, 1.00, 6, 32, 22,
            null, null),

    /** 接近するまで不可視。前衛を素通りして後衛を刈りに来る。 */
    STALKER("潜伏体", EntityType.ZOMBIE, 0.70, 1.18, 1.45, 10, 34, 18,
            null, Material.IRON_SWORD),

    /** 周囲の味方にダメージ軽減を配る支援型。放置すると硬直する。 */
    SHIELDER("守護者", EntityType.PIGLIN_BRUTE, 1.60, 0.80, 0.80, 9, 38, 14,
            Material.DIAMOND_HELMET, Material.SHIELD),

    /** 味方を回復し、撃破された個体を蘇らせる。最優先で潰すべき相手。 */
    NECROMANCER("蘇生者", EntityType.WITCH, 1.30, 0.85, 0.50, 18, 45, 10,
            null, null),

    // ---- 特殊湧き（通常抽選には出ない）----

    /** ヒート超過で出現。叫ぶと追加ホードを呼ぶ。 */
    SCREAMER("叫喚体", EntityType.EVOKER, 1.10, 0.95, 0.60, 10, 0, 0,
            null, null),

    /** ボス。実体は {@link BossType} が決める。 */
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

    /** ウェーブ抽選の基礎重み。0 は通常抽選に出さない（特殊湧きのみ）。 */
    public int weight() {
        return weight;
    }

    /**
     * この GameStage を超えると出現率が落ちていく。0 は減衰なし。
     *
     * <p>7DTD で後半になると通常ゾンビが上位個体に置き換わるのと同じ挙動。
     * これが無いと、Stage 200 でも侵略者(Grunt)が最多という間の抜けた構成になる。</p>
     */
    public int falloffStage() {
        return switch (this) {
            case GRUNT -> 55;
            case RUNNER -> 85;
            case RANGED -> 95;
            case CRAWLER -> 115;
            default -> 0;
        };
    }

    /**
     * GameStage を考慮した実効重み。
     * 未解禁なら 0、減衰対象なら最低でも基礎重みの 20% は残す（完全に消すと構成が単調になる）。
     */
    public double effectiveWeight(int gameStage) {
        if (weight <= 0 || minStage > gameStage) {
            return 0.0;
        }
        int falloff = falloffStage();
        if (falloff <= 0 || gameStage <= falloff) {
            return weight;
        }
        double decayed = weight * (1.0 - (gameStage - falloff) * 0.008);
        return Math.max(weight * 0.2, decayed);
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
        return this != FLYER && this != SCREAMER && this != CRAWLER && this != LOBBER;
    }

    /** 地面を掘り進むか。 */
    public boolean canBurrow() {
        return this == BURROWER;
    }

    /** 足場を建てるか。 */
    public boolean canBuild() {
        return this == ENGINEER;
    }

    /** 壁を登るか（バニラのクモの登坂を利用する）。 */
    public boolean canClimb() {
        return this == CRAWLER;
    }

    public boolean isRanged() {
        return this == RANGED;
    }

    /** 質量爆弾を投げる砲撃型か。 */
    public boolean isArtillery() {
        return this == LOBBER;
    }

    public boolean isFlying() {
        return this == FLYER;
    }

    public boolean isBomber() {
        return this == BOMBER;
    }

    /** 接近するまで姿を消すか。 */
    public boolean isStealth() {
        return this == STALKER;
    }

    /** 味方支援型か（軽減付与・回復・蘇生）。 */
    public boolean isSupport() {
        return this == SHIELDER || this == NECROMANCER;
    }

    /** ノックバックをほぼ受けないか。 */
    public boolean isHeavy() {
        return this == BRUTE || this == BOSS;
    }

    public boolean isBoss() {
        return this == BOSS;
    }
}
