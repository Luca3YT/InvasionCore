package work.maiotanet.invasioncore.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 貢献度の集計。
 *
 * <p>撃破数だけで報酬を割ると、壁を直す人・タレットを建てる人・囮になる人が損をする。
 * 防衛ゲームで最も嫌われる形なので、支援行動にも明確な重みを与えている。</p>
 */
public final class ContributionTracker {

    /** 貢献の種類と重み。 */
    public enum Kind {
        /** 侵略者へ与えたダメージ。1ダメージあたり。 */
        DAMAGE(1.0),
        /** 侵略者の撃破。1体あたり。 */
        KILL(12.0),
        /** ブロック修理。回復HP 10 あたり。 */
        REPAIR(1.4),
        /** ブロック強化。1ブロックあたり。 */
        FORTIFY(8.0),
        /** タレット設置。1基あたり。 */
        TURRET(25.0),
        /** 被ダメージ（前衛を張った分）。1ダメージあたり。 */
        TANKED(2.2);

        private final double weight;

        Kind(double weight) {
            this.weight = weight;
        }

        public double weight() {
            return weight;
        }
    }

    /** ネクサスID -> プレイヤーID -> 種別 -> 生値 */
    private final Map<UUID, Map<UUID, Map<Kind, Double>>> data = new HashMap<>();

    public void record(UUID nexusId, UUID playerId, Kind kind, double amount) {
        if (nexusId == null || playerId == null || amount <= 0) {
            return;
        }
        data.computeIfAbsent(nexusId, k -> new HashMap<>())
                .computeIfAbsent(playerId, k -> new HashMap<>())
                .merge(kind, amount, Double::sum);
    }

    /** 重み付け後の合計スコア。 */
    public double score(UUID nexusId, UUID playerId) {
        Map<UUID, Map<Kind, Double>> perNexus = data.get(nexusId);
        if (perNexus == null) {
            return 0.0;
        }
        Map<Kind, Double> perPlayer = perNexus.get(playerId);
        if (perPlayer == null) {
            return 0.0;
        }
        double total = 0.0;
        for (Map.Entry<Kind, Double> e : perPlayer.entrySet()) {
            total += e.getKey().weight() * e.getValue();
        }
        return total;
    }

    /** 種別ごとの重み付け後スコア。管理画面の内訳表示に使う。記録が無い種別は含まない。 */
    public Map<Kind, Double> breakdown(UUID nexusId, UUID playerId) {
        Map<Kind, Double> out = new java.util.EnumMap<>(Kind.class);
        Map<UUID, Map<Kind, Double>> perNexus = data.get(nexusId);
        Map<Kind, Double> perPlayer = perNexus == null ? null : perNexus.get(playerId);
        if (perPlayer != null) {
            for (Map.Entry<Kind, Double> e : perPlayer.entrySet()) {
                out.put(e.getKey(), e.getKey().weight() * e.getValue());
            }
        }
        return out;
    }

    /** スコア降順のランキング。 */
    public List<Map.Entry<UUID, Double>> ranking(UUID nexusId) {
        Map<UUID, Map<Kind, Double>> perNexus = data.get(nexusId);
        if (perNexus == null) {
            return List.of();
        }
        List<Map.Entry<UUID, Double>> out = new ArrayList<>();
        for (UUID player : perNexus.keySet()) {
            out.add(Map.entry(player, score(nexusId, player)));
        }
        out.sort(Comparator.<Map.Entry<UUID, Double>>comparingDouble(Map.Entry::getValue).reversed());
        return out;
    }

    /**
     * 報酬総量を貢献度で按分する。
     *
     * <p>完全比例だと差がつきすぎて後発プレイヤーが参加しなくなるため、
     * 一定割合を頭割りにしてから残りを比例配分する。</p>
     *
     * @param flatShare 頭割りにする割合（0.0〜1.0）
     */
    public Map<UUID, Integer> distribute(UUID nexusId, List<UUID> participants, int total, double flatShare) {
        Map<UUID, Integer> out = new LinkedHashMap<>();
        if (participants.isEmpty() || total <= 0) {
            return out;
        }
        int flatPool = (int) Math.round(total * Math.max(0.0, Math.min(1.0, flatShare)));
        int weightedPool = total - flatPool;

        int flatEach = flatPool / participants.size();
        double sum = 0.0;
        for (UUID p : participants) {
            sum += score(nexusId, p);
        }

        int handed = 0;
        for (UUID p : participants) {
            int amount = flatEach;
            if (sum > 0) {
                amount += (int) Math.floor(weightedPool * (score(nexusId, p) / sum));
            }
            out.put(p, amount);
            handed += amount;
        }
        // 端数は参加者のうち最上位に寄せる。
        // 貢献度ランキングの1位が既に範囲外へ出ている（死亡中を含む）と、その人に割り当てた分は
        // 誰にも配られずに消えるので、必ず参加者の中から選ぶ。
        int remainder = total - handed;
        if (remainder > 0) {
            UUID top = participants.get(0);
            for (Map.Entry<UUID, Double> e : ranking(nexusId)) {
                if (participants.contains(e.getKey())) {
                    top = e.getKey();
                    break;
                }
            }
            out.merge(top, remainder, Integer::sum);
        }
        return out;
    }

    public void reset(UUID nexusId) {
        data.remove(nexusId);
    }

    public void clear() {
        data.clear();
    }
}
