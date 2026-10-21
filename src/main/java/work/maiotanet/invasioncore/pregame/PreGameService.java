package work.maiotanet.invasioncore.pregame;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.nexus.Nexus;
import work.maiotanet.invasioncore.nexus.NexusManager;
import work.maiotanet.invasioncore.util.Sounds;
import work.maiotanet.invasioncore.util.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ゲーム開始（ネクサスの起動）前の待機状態。
 *
 * <p>配信では、視聴者が集まるまでの間にスポーン周辺を荒らされたり、遠くへ行って戻れなくなったりしやすい。
 * 開始までは行動を絞り、死なないようにしておき、アクションバーで人数とルールの案内を出し続ける。
 * 開始の判定はネクサスの状態だけで決める（無いか未起動なら開始前）。</p>
 */
public final class PreGameService {

    /** 移動制限・禁止事項の対象外にする権限。配信者が準備できるようにするため。 */
    public static final String EXEMPT_PERMISSION = "invasioncore.admin";

    /** 開始前に配る効果のレベル（254 = Lv.255）。即座に全回復し、空腹にもならない。 */
    private static final int EFFECT_AMPLIFIER = 254;

    /** 効果の持続時間（tick）。毎秒かけ直すので、開始後は自然に切れる。 */
    private static final int EFFECT_TICKS = 60;

    /** 同じ人に注意を出す間隔（ミリ秒）。押し続けたときにチャットが流れないようにする。 */
    private static final long NOTICE_INTERVAL_MILLIS = 5000L;

    private final InvasionConfig config;
    private final NexusManager nexuses;
    private final Map<UUID, Long> lastNotice = new HashMap<>();

    /** 前回の tick で開始前だったか。null は起動直後でまだ判定していない。 */
    private Boolean wasPreGame;

    public PreGameService(InvasionConfig config, NexusManager nexuses) {
        this.config = config;
        this.nexuses = nexuses;
    }

    // ---------- 判定 ----------

    /** ネクサスが無いか、まだ起動していなければ開始前。崩壊しても開始前には戻らない。 */
    public boolean isPreGame() {
        Nexus nexus = nexuses.all().stream().findFirst().orElse(null);
        return nexus == null || nexus.phase() == Nexus.Phase.DORMANT;
    }

    /** 開始前の制限を受けるか。管理者は準備のために自由に動ける。 */
    public boolean isRestricted(Player player) {
        return isPreGame() && !player.hasPermission(EXEMPT_PERMISSION);
    }

    /**
     * 開始前に止めた理由を伝える（チャットと失敗音）。同じ人には5秒に1回まで。
     * 移動・破壊などの制限だけでなく、ハンマーや緊急バリアでブロックを変えようとしたときにも使う。
     */
    public void notice(Player player, String message) {
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(player.getUniqueId());
        if (last != null && now - last < NOTICE_INTERVAL_MILLIS) {
            return;
        }
        lastNotice.put(player.getUniqueId(), now);
        player.sendMessage(Text.warn(message));
        Sounds.fail(player);
    }

    /** 移動制限の中心。メインワールドのスポーン地点。 */
    public Location center() {
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    public int radius() {
        return config.pregameRadius();
    }

    /** スポーン地点からの水平距離。別のワールドなら -1。 */
    public double distanceFromCenter(Location loc) {
        Location c = center();
        World w = loc.getWorld();
        if (w == null || !w.equals(c.getWorld())) {
            return -1;
        }
        double dx = loc.getX() - c.getX();
        double dz = loc.getZ() - c.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ---------- 1秒ごと ----------

    public void tick() {
        boolean pre = isPreGame();
        if (wasPreGame != null && wasPreGame && !pre) {
            onGameStart();
        }
        wasPreGame = pre;
        if (!pre) {
            return;
        }

        int online = Bukkit.getOnlinePlayers().size();
        Component bar = Text.of("参加者 " + online + " 人", Text.GOOD)
                .append(Text.of("  ｜  ", Text.MUTED))
                .append(Text.of("ルール説明は配信の概要欄か固定コメントを見てね", Text.WARN));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendActionBar(bar);
            // 開始前は拾うことができないので、死んで持ち物を落とすと取り戻せない。死なないようにしておく。
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                    EFFECT_TICKS, EFFECT_AMPLIFIER, true, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION,
                    EFFECT_TICKS, EFFECT_AMPLIFIER, true, false, true));
        }
    }

    /** ネクサスが起動した瞬間。開始前に配った効果を外して、制限の解除を知らせる。 */
    private void onGameStart() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeOwnEffect(player, PotionEffectType.REGENERATION);
            removeOwnEffect(player, PotionEffectType.SATURATION);
            player.sendMessage(Text.good("ゲーム開始！ 移動とブロックの制限を解除した。"));
        }
    }

    /** ポーションなどで本人が得た効果まで消さないよう、こちらが配ったレベルのものだけ外す。 */
    private void removeOwnEffect(Player player, PotionEffectType type) {
        PotionEffect effect = player.getPotionEffect(type);
        if (effect != null && effect.getAmplifier() == EFFECT_AMPLIFIER) {
            player.removePotionEffect(type);
        }
    }
}
