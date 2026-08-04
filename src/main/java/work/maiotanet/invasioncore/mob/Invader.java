package work.maiotanet.invasioncore.mob;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * 生成済み侵略Mob 1体分の実行時状態。
 * エンティティ本体に加えて、独自AIが必要とする進捗情報を保持する。
 */
public final class Invader {

    private final Mob entity;
    private final InvaderType type;
    private final MutationTier tier;
    private final UUID nexusId;
    private final int wave;

    /** ボスの場合のみ設定される。通常個体は null。 */
    private BossType bossType;

    /** 潜伏体が既に姿を現したか。 */
    private boolean revealed;

    /** 現在攻撃中の突破対象ブロック。null なら通常移動中。 */
    private Block breachTarget;

    /** ネクサスへの最短距離の記録。これが縮まらないと閉塞と判断する。 */
    private double bestDistance = Double.MAX_VALUE;

    /** 距離が縮まらないまま経過したAI更新回数。 */
    private int stuckTicks;

    /** 工兵が設置した足場。侵攻終了時に撤去するため記録する。 */
    private final Deque<Location> placedBlocks = new ArrayDeque<>();

    /** 次に特殊行動（射撃・投擲・ボススキル・叫喚など）を行える時刻(tick)。 */
    private long abilityReadyTick;

    /**
     * 次にネクサスを殴れる時刻(tick)。
     *
     * <p>特殊行動と同じ変数を使うと、ネクサスに張り付いた瞬間から毎秒上書きされて
     * ボススキルが永久に発動しなくなる。攻撃とスキルで別々に持つ必要がある。</p>
     */
    private long attackReadyTick;

    public Invader(Mob entity, InvaderType type, MutationTier tier, UUID nexusId, int wave) {
        this.entity = entity;
        this.type = type;
        this.tier = tier;
        this.nexusId = nexusId;
        this.wave = wave;
    }

    public Mob entity() {
        return entity;
    }

    public InvaderType type() {
        return type;
    }

    public MutationTier tier() {
        return tier;
    }

    public UUID nexusId() {
        return nexusId;
    }

    public int wave() {
        return wave;
    }

    public BossType bossType() {
        return bossType;
    }

    public void bossType(BossType bossType) {
        this.bossType = bossType;
    }

    public boolean isRevealed() {
        return revealed;
    }

    public void revealed(boolean revealed) {
        this.revealed = revealed;
    }

    public boolean isValid() {
        return entity.isValid() && !entity.isDead();
    }

    public Block breachTarget() {
        return breachTarget;
    }

    public void breachTarget(Block breachTarget) {
        this.breachTarget = breachTarget;
    }

    public double bestDistance() {
        return bestDistance;
    }

    public void bestDistance(double bestDistance) {
        this.bestDistance = bestDistance;
    }

    public int stuckTicks() {
        return stuckTicks;
    }

    public void stuckTicks(int stuckTicks) {
        this.stuckTicks = stuckTicks;
    }

    public void incrementStuck() {
        this.stuckTicks++;
    }

    public Deque<Location> placedBlocks() {
        return placedBlocks;
    }

    public long abilityReadyTick() {
        return abilityReadyTick;
    }

    public void abilityReadyTick(long abilityReadyTick) {
        this.abilityReadyTick = abilityReadyTick;
    }

    public long attackReadyTick() {
        return attackReadyTick;
    }

    public void attackReadyTick(long attackReadyTick) {
        this.attackReadyTick = attackReadyTick;
    }
}
