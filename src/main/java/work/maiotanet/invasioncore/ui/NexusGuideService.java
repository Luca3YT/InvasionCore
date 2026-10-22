package work.maiotanet.invasioncore.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.nexus.Nexus;
import work.maiotanet.invasioncore.nexus.NexusManager;
import work.maiotanet.invasioncore.pregame.PreGameService;
import work.maiotanet.invasioncore.util.Text;

import java.util.UUID;

/**
 * ネクサスの場所と防衛範囲を分かりやすくする表示。
 *
 * <p>配信の参加者は地形に慣れていないので、遠出すると拠点に戻れなくなりやすい。
 * ネクサスを光らせて壁越しにも見えるようにし、防衛範囲の外にいる間はアクションバーに方向と距離を出す。
 * 防衛範囲の境目は目に見えないので、近づいたときだけ境目にパーティクルを描く
 * （常に円全体を描くと画面がうるさく、負荷も人数に比例して増える）。</p>
 */
public final class NexusGuideService {

    /** 通知がアクションバーに出た直後は、読み終わるまで方向表示で上書きしない（ミリ秒）。 */
    private static final long NOTICE_GRACE_MILLIS = 2500L;

    /** プレイヤーの向きから見た方向。0 が正面で、45度ずつ右回り。 */
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    /** 境目に描く弧の、プレイヤーの左右それぞれの長さ（ブロック）。 */
    private static final int ARC_HALF_LENGTH = 14;

    private static final Color GUIDE_COLOR = Color.fromRGB(0xC77DFF);
    private static final Particle.DustOptions BOUNDARY_DUST = new Particle.DustOptions(GUIDE_COLOR, 1.3f);

    private final InvasionConfig config;
    private final NexusManager nexuses;
    private final PreGameService pregame;

    /** 光らせるための表示用エンティティ。保存しない（チャンクが読み込まれ直したら作り直す）。 */
    private BlockDisplay glow;
    private UUID glowNexus;
    private int ticks;

    public NexusGuideService(InvasionConfig config, NexusManager nexuses, PreGameService pregame) {
        this.config = config;
        this.nexuses = nexuses;
        this.pregame = pregame;
    }

    /** 5 tick ごと。矢印は向きを変えたらすぐ追従してほしいので、1秒ごとより細かく回す。 */
    public void tick() {
        Nexus nexus = nexuses.primary();
        updateGlow(nexus);
        if (nexus == null) {
            return;
        }
        Location center = nexus.center();
        if (center == null || center.getWorld() == null) {
            return;
        }
        // 境目のパーティクルは 10 tick ごとで十分見える
        boolean drawBoundary = (++ticks % 2 == 0) && config.guideBoundaryWithin() > 0;
        // 開始前はアクションバーに参加人数とルールの案内を出しているので、矢印で上書きしない
        boolean compass = config.guideCompass() && !pregame.isPreGame();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (compass) {
                showCompass(player, center);
            }
            if (drawBoundary) {
                drawBoundary(player, center);
            }
        }
    }

    // ---------- 方向表示 ----------

    private void showCompass(Player player, Location center) {
        if (Text.actionBarRecentlyUsed(player, NOTICE_GRACE_MILLIS)) {
            return;
        }
        Location loc = player.getLocation();
        if (!center.getWorld().equals(loc.getWorld())) {
            // 別のディメンションでは方向に意味が無いので、どこにあるかだけ伝える
            player.sendActionBar(Text.of("ネクサスは" + worldLabel(center.getWorld())
                    + "（" + center.getBlockX() + ", " + center.getBlockZ() + "）", Text.INFO));
            return;
        }
        double dx = center.getX() - loc.getX();
        double dz = center.getZ() - loc.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= config.nexusDefenseRadius()) {
            return;
        }
        // Minecraft の yaw は南が 0 で、右回りに増える（西が 90）
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = targetYaw - loc.getYaw();
        int index = (int) Math.floorMod(Math.round(relative / 45.0), 8L);

        Component bar = Text.of("ネクサス ", Text.INFO)
                .append(Text.of(ARROWS[index], Text.WARN).decorate(TextDecoration.BOLD))
                .append(Text.of(" " + (int) Math.round(distance) + "m", Text.MUTED));
        player.sendActionBar(bar);
    }

    private static String worldLabel(World world) {
        return switch (world.getEnvironment()) {
            case NORMAL -> "オーバーワールド";
            case NETHER -> "ネザー";
            case THE_END -> "エンド";
            default -> world.getName();
        };
    }

    // ---------- 境目 ----------

    /** プレイヤーが境目に近ければ、プレイヤーの正面側の弧だけを本人にだけ見えるように描く。 */
    private void drawBoundary(Player player, Location center) {
        Location loc = player.getLocation();
        if (!center.getWorld().equals(loc.getWorld())) {
            return;
        }
        double radius = config.nexusDefenseRadius();
        double dx = loc.getX() - center.getX();
        double dz = loc.getZ() - center.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (Math.abs(distance - radius) > config.guideBoundaryWithin()) {
            return;
        }
        double base = Math.atan2(dz, dx);
        double step = 1.0 / radius;
        for (int i = -ARC_HALF_LENGTH; i <= ARC_HALF_LENGTH; i++) {
            double angle = base + i * step;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            // 足元から頭の上まで3段。地面の高さは場所でばらばらなので、プレイヤーの高さに合わせる
            for (int layer = 0; layer < 3; layer++) {
                player.spawnParticle(Particle.DUST, x, loc.getY() + 0.2 + layer, z,
                        1, 0.0, 0.0, 0.0, 0.0, BOUNDARY_DUST);
            }
        }
    }

    // ---------- 発光 ----------

    /**
     * ネクサスに重ねた表示用ブロックを光らせる。
     * ブロックそのものは光らせられないので、少しだけ大きい同じ見た目のブロック表示を重ねて輪郭を出す。
     */
    private void updateGlow(Nexus nexus) {
        Location loc = nexus == null || !config.guideNexusGlow() ? null : nexus.location();
        if (loc == null || loc.getWorld() == null) {
            removeGlow();
            return;
        }
        if (glow != null && glow.isValid() && nexus.id().equals(glowNexus)) {
            return;
        }
        removeGlow();
        World world = loc.getWorld();
        if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            // 誰も近くにいなければ見えないので、読み込まれてから作る
            return;
        }
        glow = world.spawn(loc, BlockDisplay.class, display -> {
            display.setBlock(Material.LODESTONE.createBlockData());
            display.setPersistent(false);
            display.setGlowing(true);
            display.setGlowColorOverride(GUIDE_COLOR);
            // 本体とぴったり同じ大きさだと面が重なってちらつくので、わずかに大きくして外側へずらす
            display.setTransformation(new Transformation(
                    new Vector3f(-0.01f, -0.01f, -0.01f), new AxisAngle4f(),
                    new Vector3f(1.02f, 1.02f, 1.02f), new AxisAngle4f()));
            display.setViewRange(4.0f);
        });
        glowNexus = nexus.id();
    }

    private void removeGlow() {
        if (glow != null && glow.isValid()) {
            glow.remove();
        }
        glow = null;
        glowNexus = null;
    }

    public void shutdown() {
        removeGlow();
    }
}
