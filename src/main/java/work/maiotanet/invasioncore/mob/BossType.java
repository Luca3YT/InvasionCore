package work.maiotanet.invasioncore.mob;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ボスのロースター。
 *
 * <p>ボスが1種類だと5波ごとの山場が「同じ処理の繰り返し」になり、
 * 周回するほど作業感が出る。役割の異なる5体を用意し、
 * GameStage で解禁しながら抽選することで、毎回の血の月に別種の対応を要求する。</p>
 */
public enum BossType {

    /** 増援召喚・咆哮・突進。物量で押しつぶす基本形。 */
    WARLORD("侵略主", EntityType.RAVAGER, 6.0, 0.95, 1.8, 0, 60,
            TextColor.color(0xFF6B6B),
            "増援を呼び、咆哮でプレイヤーを吹き飛ばし、ネクサスへ突進する"),

    /** 超高HP・壁粉砕・叩きつけ。防衛線そのものを破壊しに来る。 */
    SIEGE_TITAN("鉄槌", EntityType.IRON_GOLEM, 9.0, 0.62, 2.6, 40, 140,
            TextColor.color(0xB0BEC5),
            "極めて硬く、周囲の壁をまとめて粉砕する。速度でのみ勝てる"),

    /** 毒霧・産卵・自己回復。持久戦を強いる。 */
    BLIGHT_QUEEN("腐敗の女王", EntityType.SPIDER, 7.5, 1.05, 1.5, 55, 110,
            TextColor.color(0x9CCC65),
            "毒霧を撒き、小蜘蛛を産み、傷つくほど自己回復する"),

    /** 火炎弾の雨・炎上地帯・飛行。地上の防御を上から溶かす。 */
    EMBERHEART("炎心", EntityType.BLAZE, 5.5, 1.00, 1.9, 70, 95,
            TextColor.color(0xFF8A50),
            "上空から火炎弾を降らせ、着弾点を炎上地帯に変える"),

    /** 瞬間移動・引き寄せ・虚空弾。位置取りを崩しに来る。 */
    VOID_HERALD("虚空の使者", EntityType.ENDERMAN, 6.0, 1.15, 2.2, 90, 130,
            TextColor.color(0xB388FF),
            "瞬間移動でタレットの裏を取り、プレイヤーを引き寄せる");

    private final String displayName;
    private final EntityType entityType;
    private final double healthMultiplier;
    private final double speedMultiplier;
    private final double damageMultiplier;
    private final int minStage;
    private final int shardValue;
    private final TextColor color;
    private final String description;

    BossType(String displayName, EntityType entityType, double healthMultiplier, double speedMultiplier,
             double damageMultiplier, int minStage, int shardValue, TextColor color, String description) {
        this.displayName = displayName;
        this.entityType = entityType;
        this.healthMultiplier = healthMultiplier;
        this.speedMultiplier = speedMultiplier;
        this.damageMultiplier = damageMultiplier;
        this.minStage = minStage;
        this.shardValue = shardValue;
        this.color = color;
        this.description = description;
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

    public int minStage() {
        return minStage;
    }

    public int shardValue() {
        return shardValue;
    }

    public TextColor color() {
        return color;
    }

    public String description() {
        return description;
    }

    /** スキルの発動間隔（tick）。硬いボスほど間隔を空けて手数を抑える。 */
    public int abilityCooldown() {
        return switch (this) {
            case SIEGE_TITAN -> 240;
            case VOID_HERALD -> 160;
            case EMBERHEART -> 140;
            default -> 200;
        };
    }

    /** 空を飛ぶボスか。地上の経路探索を使わない。 */
    public boolean isAirborne() {
        return this == EMBERHEART;
    }

    /**
     * GameStage で解禁済みのボスから抽選する。
     * 同じ血の月で同じボスが続かないよう、直前のボスは除外する。
     */
    public static BossType roll(int gameStage, BossType exclude) {
        List<BossType> pool = new ArrayList<>();
        for (BossType boss : values()) {
            if (boss.minStage <= gameStage && boss != exclude) {
                pool.add(boss);
            }
        }
        if (pool.isEmpty()) {
            return WARLORD;
        }
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    public static BossType parse(String name) {
        if (name == null) {
            return null;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
