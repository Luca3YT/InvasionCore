package work.maiotanet.invasioncore.pregame;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * ゲーム開始（ネクサスの起動）前の待機状態。
 *
 * <p>配信では、視聴者が集まるまでの間にスポーン周辺を荒らされたり、遠くへ行って戻れなくなったりしやすい。
 * 開始までは行動を絞り、死なないようにしておき、アクションバーで人数とルールの案内を出し続ける。
 * 開始の判定はネクサスの状態だけで決める（無いか未起動なら開始前）。</p>
 *
 * <p>移動だけは、ネクサスが置かれるまで制限しない（配信者が拠点の場所を決める前に、皆で下見できるように）。
 * ネクサスが置かれたら運営以外をネクサスの近くへ集め、開始まで防衛範囲から出られないようにする。</p>
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

    /** 前回の tick で見えていたネクサス。新しく置かれた瞬間を見つけるために持つ。 */
    private UUID lastNexusId;

    public PreGameService(InvasionConfig config, NexusManager nexuses) {
        this.config = config;
        this.nexuses = nexuses;
    }

    // ---------- 判定 ----------

    /** ネクサスが無いか、まだ起動していなければ開始前。崩壊しても開始前には戻らない。 */
    public boolean isPreGame() {
        Nexus nexus = nexuses.primary();
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

    /** 移動制限がかかっているか。ネクサスが置かれてから起動するまでの間だけ。 */
    public boolean isMovementLimited() {
        Nexus nexus = nexuses.primary();
        return nexus != null && nexus.phase() == Nexus.Phase.DORMANT;
    }

    /** このプレイヤーが移動制限を受けるか。 */
    public boolean isMovementRestricted(Player player) {
        return isMovementLimited() && !player.hasPermission(EXEMPT_PERMISSION);
    }

    /** 移動制限の中心。置かれたネクサス。無ければ null。 */
    public Location center() {
        Nexus nexus = nexuses.primary();
        return nexus == null ? null : nexus.center();
    }

    /** 移動できる半径。防衛範囲と同じにして、境目のパーティクルで見えるようにする。 */
    public int radius() {
        return config.nexusDefenseRadius();
    }

    /** ネクサスからの水平距離。ネクサスが無いか、別のワールドなら -1。 */
    public double distanceFromCenter(Location loc) {
        Location c = center();
        World w = loc.getWorld();
        if (c == null || w == null || !w.equals(c.getWorld())) {
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
        Nexus nexus = nexuses.primary();
        UUID nexusId = nexus == null ? null : nexus.id();
        if (wasPreGame != null && pre && nexus != null && !nexus.id().equals(lastNexusId)) {
            // 起動直後（wasPreGame が null）は、前から置いてあったネクサスなので集めない
            gatherAll();
        }
        wasPreGame = pre;
        lastNexusId = nexusId;
        if (!pre) {
            return;
        }
        if (isMovementLimited()) {
            // 後から入ってきた人や、範囲の外でリスポーンした人を範囲の中へ戻す
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (needsGathering(player) && isOutside(player)) {
                    teleportToNexus(player, "ゲーム開始まで、ネクサスの近くで待っていてね。");
                }
            }
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

    // ---------- ネクサスへ集める ----------

    /** ネクサスが置かれた瞬間。運営以外の全員をネクサスの近くへ移す。 */
    private void gatherAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (needsGathering(player)) {
                teleportToNexus(player, "ネクサスが置かれた！ ゲーム開始まで、ネクサスの近くで待っていてね。");
            }
        }
    }

    /** 集める対象か。運営と観戦モードの人は動かさない。 */
    private boolean needsGathering(Player player) {
        return isMovementRestricted(player) && player.getGameMode() != GameMode.SPECTATOR;
    }

    /** 移動できる範囲の外（別のディメンションを含む）にいるか。境目ちょうどの人は動かさない。 */
    private boolean isOutside(Player player) {
        double distance = distanceFromCenter(player.getLocation());
        return distance < 0 || distance > radius() + 2;
    }

    private void teleportToNexus(Player player, String message) {
        Location spot = safeSpotNear(nexuses.primary());
        if (spot == null) {
            return;
        }
        player.teleport(spot);
        player.sendMessage(Text.info(message));
    }

    /**
     * ネクサスのそばで立てる場所。全員が同じ1点に重ならないよう、少し散らす。
     * 見つからなければネクサスの上に立たせる。
     */
    private Location safeSpotNear(Nexus nexus) {
        Location base = nexus == null ? null : nexus.location();
        if (base == null || base.getWorld() == null) {
            return null;
        }
        World world = base.getWorld();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist = 2.0 + rng.nextDouble() * 3.0;
            int x = base.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = base.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            // 屋根の上や谷底に飛ばさないよう、ネクサスと高さが近い地面だけを使う
            for (int y = base.getBlockY() + 3; y >= base.getBlockY() - 3; y--) {
                Block ground = world.getBlockAt(x, y, z);
                if (ground.getType().isSolid() && ground.getType() != Material.LAVA
                        && ground.getRelative(BlockFace.UP).isPassable()
                        && !ground.getRelative(BlockFace.UP).isLiquid()
                        && ground.getRelative(BlockFace.UP, 2).isPassable()) {
                    return faceNexus(new Location(world, x + 0.5, y + 1.0, z + 0.5), base);
                }
            }
        }
        return faceNexus(base.clone().add(0.5, 1.0, 0.5), base);
    }

    /** 着いたときにネクサスの方を向かせる。 */
    private static Location faceNexus(Location spot, Location nexus) {
        double dx = nexus.getX() + 0.5 - spot.getX();
        double dz = nexus.getZ() + 0.5 - spot.getZ();
        if (dx * dx + dz * dz > 0.01) {
            spot.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        }
        return spot;
    }

    // ---------- 開始 ----------

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
