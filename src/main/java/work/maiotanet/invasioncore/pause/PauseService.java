package work.maiotanet.invasioncore.pause;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import work.maiotanet.invasioncore.mob.Invader;
import work.maiotanet.invasioncore.mob.InvaderRegistry;
import work.maiotanet.invasioncore.util.Text;
import work.maiotanet.invasioncore.world.WorldEventService;

import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * ゲームの一時停止（/invasion admin pause・resume）。
 *
 * <p>配信で説明をしたいときや、トラブルの対応中にゲームを止めておくため。
 * 止めている間は、時間（昼夜と Day）・侵略者・ワールドイベント・タレットと罠が動かない。
 * 参加者の止め方は 3 通りから選べる（運営は対象外）。どの止め方でも参加者はダメージを受けない。</p>
 *
 * <p>止めている間の時間はワールドの昼夜を止めて実現するので、サーバーを止めるときは必ず元に戻す。
 * 侵略者やタレットの処理そのものは、呼び出し側（InvasionCore の定期処理）が {@link #isPaused()} を見て飛ばす。</p>
 */
public final class PauseService {

    /** 参加者の止め方。 */
    public enum Mode {
        /** その場から動けない。 */
        FREEZE("freeze", "その場から動けない"),
        /** 歩けるが、壊す・置く・攻撃する・アイテムを使うはできない。 */
        WALK("walk", "歩けるが、壊す・置く・攻撃はできない"),
        /** 自由に動ける（ダメージだけ受けない）。 */
        FREE("free", "自由に動ける");

        private final String id;
        private final String description;

        Mode(String id, String description) {
            this.id = id;
            this.description = description;
        }

        public String id() {
            return id;
        }

        public String description() {
            return description;
        }

        public static Mode parse(String text) {
            for (Mode mode : values()) {
                if (mode.id.equals(text.toLowerCase(Locale.ROOT))) {
                    return mode;
                }
            }
            return null;
        }
    }

    /** 再開までのカウントダウン（秒）。 */
    private static final int COUNTDOWN_SECONDS = 3;

    private final Plugin plugin;
    private final InvaderRegistry registry;
    private final WorldEventService worldEvents;

    private boolean paused;
    private Mode mode = Mode.WALK;
    private long pausedAtMillis;
    /** 止める前の昼夜の進み方（ワールドごと）。再開時に戻す。 */
    private final Map<String, Boolean> daylightBefore = new HashMap<>();
    private final BossBar bar = BossBar.bossBar(Text.of("一時停止中", Text.WARN), 1.0f,
            BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
    private BukkitTask countdown;

    public PauseService(Plugin plugin, InvaderRegistry registry, WorldEventService worldEvents) {
        this.plugin = plugin;
        this.registry = registry;
        this.worldEvents = worldEvents;
    }

    /** 止めている間（再開のカウントダウン中も含む）。 */
    public boolean isPaused() {
        return paused;
    }

    public Mode mode() {
        return mode;
    }

    /** この人が止め方の制限を受けるか。運営は自由に動ける。 */
    public boolean isRestricted(Player player) {
        return paused && !player.hasPermission("invasioncore.admin");
    }

    // ---------- 止める・再開 ----------

    public boolean pause(Mode mode) {
        if (paused) {
            if (countdown != null) {
                // 再開のカウントダウン中にもう一度止めたら、カウントダウンをやめて止めたままにする
                countdown.cancel();
                countdown = null;
            }
            this.mode = mode;
            announce("一時停止", "参加者は" + mode.description());
            return true;
        }
        paused = true;
        this.mode = mode;
        pausedAtMillis = System.currentTimeMillis();
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) {
                continue;
            }
            daylightBefore.put(world.getName(), !Boolean.FALSE.equals(world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE)));
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        }
        freezeInvaders();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(bar);
        }
        announce("一時停止", "運営が再開するまで待ってね（参加者は" + mode.description() + "）");
        return true;
    }

    /** 3・2・1 と数えてから再開する。止めていなければ false。 */
    public boolean resume() {
        if (!paused || countdown != null) {
            return false;
        }
        countdown = new BukkitRunnable() {
            private int left = COUNTDOWN_SECONDS;

            @Override
            public void run() {
                if (left > 0) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.showTitle(Title.title(Text.of(String.valueOf(left), Text.WARN), Text.of("まもなく再開", Text.MUTED),
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.2f);
                    }
                    left--;
                    return;
                }
                cancel();
                countdown = null;
                finishResume();
            }
        }.runTaskTimer(plugin, 0L, 20L);
        return true;
    }

    private void finishResume() {
        paused = false;
        long pausedFor = System.currentTimeMillis() - pausedAtMillis;
        restoreDaylight();
        unfreezeInvaders();
        // 止めていた間に補給物資やホードの時刻が過ぎて、再開した瞬間にまとめて来ないようにずらす
        worldEvents.shiftTimers(pausedFor);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(bar);
            p.showTitle(Title.title(Text.of("再開！", Text.GOOD), Component.empty(),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(400))));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.6f);
        }
    }

    private void restoreDaylight() {
        for (Map.Entry<String, Boolean> e : daylightBefore.entrySet()) {
            World world = Bukkit.getWorld(e.getKey());
            if (world != null) {
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, e.getValue());
            }
        }
        daylightBefore.clear();
    }

    private void announce(String title, String subtitle) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(Title.title(Text.of(title, Text.WARN), Text.of(subtitle, Text.MUTED),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
        }
    }

    // ---------- 侵略者 ----------

    /** 1 秒ごと。止めている間に出てきた侵略者（管理コマンドなど）も止める。後から入った人にバーを出す。 */
    public void tick() {
        if (!paused) {
            return;
        }
        freezeInvaders();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showBossBar(bar);
        }
    }

    private void freezeInvaders() {
        for (Invader invader : registry.all()) {
            if (!invader.isValid()) {
                continue;
            }
            Mob mob = invader.entity();
            mob.setAI(false);
            mob.setInvulnerable(true);
        }
    }

    private void unfreezeInvaders() {
        for (Invader invader : registry.all()) {
            if (!invader.isValid()) {
                continue;
            }
            Mob mob = invader.entity();
            mob.setAI(true);
            mob.setInvulnerable(false);
        }
    }

    /** サーバー停止時。止めたままだと昼夜が止まったワールドが残るので、黙って元に戻す。 */
    public void shutdown() {
        if (countdown != null) {
            countdown.cancel();
            countdown = null;
        }
        if (paused) {
            paused = false;
            restoreDaylight();
            unfreezeInvaders();
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.hideBossBar(bar);
        }
    }
}
