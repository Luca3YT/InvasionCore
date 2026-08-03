package work.maiotanet.invasioncore.wave;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import work.maiotanet.invasioncore.nexus.Nexus;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ブラッドムーンごとの変調。
 *
 * <p>同じ GameStage でも毎回同じ戦いにならないようにする仕掛け。
 * 侵略MODは回を重ねると「数が増えるだけ」になりがちなので、
 * 質的な揺らぎをここで与えている。</p>
 */
public enum WaveModifier {

    /** 変調なし。 */
    NONE("平常", 1.00, false, 0),

    /** 大群：数が増える代わりに1体あたりは軽い。 */
    SWARM("大群 — 敵の数が大幅に増える", 1.55, false, 0),

    /** 狂乱の夜：全個体が凶暴化する。 */
    FRENZY("狂乱の夜 — 全ての侵略者が凶暴化する", 0.85, true, 20),

    /** 暗夜：視界が効かなくなる。 */
    DARK_NIGHT("暗夜 — 視界が著しく低下する", 1.00, false, 10),

    /** 増殖：撃破した個体が分裂する。 */
    SPLIT("増殖 — 撃破した侵略者が分裂する", 0.75, false, 35);

    private final String description;
    private final double countScale;
    private final boolean forceFeral;
    private final int minStage;

    WaveModifier(String description, double countScale, boolean forceFeral, int minStage) {
        this.description = description;
        this.countScale = countScale;
        this.forceFeral = forceFeral;
        this.minStage = minStage;
    }

    public String description() {
        return description;
    }

    // ---------- 文字列IDでの参照（Nexus には name() を保持する） ----------

    public static WaveModifier parse(String id) {
        if (id == null) {
            return NONE;
        }
        try {
            return valueOf(id);
        } catch (IllegalArgumentException ex) {
            return NONE;
        }
    }

    public static String describe(String id) {
        return parse(id).description();
    }

    public static double countScale(String id) {
        return parse(id).countScale;
    }

    public static boolean forceFeral(String id) {
        return parse(id).forceFeral;
    }

    public static boolean isSplit(String id) {
        return parse(id) == SPLIT;
    }

    /**
     * GameStage に応じて変調を抽選する。
     * 序盤は変調なしの比率を高くして、まず基本ルールを覚えられるようにする。
     */
    public static String roll(int stage) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (rng.nextDouble() < 0.35) {
            return NONE.name();
        }
        WaveModifier[] all = values();
        for (int attempt = 0; attempt < 12; attempt++) {
            WaveModifier candidate = all[rng.nextInt(all.length)];
            if (candidate != NONE && candidate.minStage <= stage) {
                return candidate.name();
            }
        }
        return NONE.name();
    }

    /** 変調による持続効果。ブラッドムーン中、毎秒呼ばれる。 */
    public static void applyAmbient(String id, Nexus nexus, List<Player> participants) {
        if (parse(id) != DARK_NIGHT) {
            return;
        }
        for (Player p : participants) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, true, false, false));
        }
    }
}
