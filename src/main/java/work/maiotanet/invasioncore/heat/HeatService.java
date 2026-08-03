package work.maiotanet.invasioncore.heat;

import org.bukkit.Location;
import org.bukkit.World;
import work.maiotanet.invasioncore.config.InvasionConfig;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 7DTD のヒートマップ。
 *
 * <p>拠点での活動（採掘・建築・戦闘・発砲）が「熱」として蓄積し、閾値を超えると叫喚体が湧く。
 * これがあると準備フェーズが安全な作業時間ではなくなり、
 * 「掘りすぎると呼ばれる」という7DTD特有の緊張感が生まれる。</p>
 */
public final class HeatService {

    /** 熱を集計する区画サイズ（ブロック）。チャンク2x2相当。 */
    private static final int CELL_SIZE = 32;

    /** 1区画の熱の上限。際限なく貯めさせない。 */
    private static final double CELL_CAP = 400.0;

    private final InvasionConfig config;

    private final Map<String, Double> heat = new HashMap<>();
    /** 区画ごとの叫喚体クールダウン終了時刻（epoch millis）。 */
    private final Map<String, Long> screamerCooldown = new HashMap<>();

    public HeatService(InvasionConfig config) {
        this.config = config;
    }

    private String cellKey(Location loc) {
        World w = loc.getWorld();
        String name = w == null ? "?" : w.getName();
        return name + ';' + Math.floorDiv(loc.getBlockX(), CELL_SIZE) + ';' + Math.floorDiv(loc.getBlockZ(), CELL_SIZE);
    }

    public void add(Location loc, double amount) {
        if (!config.heatEnabled() || loc == null || amount <= 0) {
            return;
        }
        String key = cellKey(loc);
        heat.merge(key, amount, (a, b) -> Math.min(CELL_CAP, a + b));
    }

    public double get(Location loc) {
        if (loc == null) {
            return 0.0;
        }
        return heat.getOrDefault(cellKey(loc), 0.0);
    }

    /** 閾値に対する比率。HUD 表示に使う。 */
    public double ratio(Location loc) {
        double threshold = config.heatScreamerThreshold();
        return threshold <= 0 ? 0.0 : Math.min(1.0, get(loc) / threshold);
    }

    /** 1秒ごとに呼ぶ。熱を自然減衰させる。 */
    public void decayTick() {
        if (!config.heatEnabled()) {
            return;
        }
        double decay = config.heatDecayPerSecond();
        Iterator<Map.Entry<String, Double>> it = heat.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Double> e = it.next();
            double v = e.getValue() - decay;
            if (v <= 0.0) {
                it.remove();
            } else {
                e.setValue(v);
            }
        }
    }

    /**
     * この地点で叫喚体を湧かせてよいか。
     * 閾値超過かつクールダウン明けのときだけ true を返し、同時にクールダウンを開始する。
     */
    public boolean consumeScreamerTrigger(Location loc) {
        if (!config.heatEnabled() || loc == null) {
            return false;
        }
        String key = cellKey(loc);
        double current = heat.getOrDefault(key, 0.0);
        if (current < config.heatScreamerThreshold()) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long until = screamerCooldown.get(key);
        if (until != null && now < until) {
            return false;
        }
        screamerCooldown.put(key, now + config.heatScreamerCooldownSeconds() * 1000L);
        // 呼び出したぶん熱を落とす。落とさないと延々と湧き続ける。
        heat.put(key, current * 0.45);
        return true;
    }

    public void clear() {
        heat.clear();
        screamerCooldown.clear();
    }
}
