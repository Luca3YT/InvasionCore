package work.maiotanet.invasioncore.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.economy.ShardService;
import work.maiotanet.invasioncore.heat.HeatService;
import work.maiotanet.invasioncore.mob.InvaderRegistry;
import work.maiotanet.invasioncore.nexus.Nexus;
import work.maiotanet.invasioncore.nexus.NexusManager;
import work.maiotanet.invasioncore.stage.GameStageService;
import work.maiotanet.invasioncore.util.Text;
import work.maiotanet.invasioncore.wave.WaveEngine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * サイドバーの内容生成。
 * 「今どのフェーズで、あと何日で、何を優先すべきか」が一目で分かることだけを狙う。
 */
public final class SidebarUpdater {

    private final InvasionConfig config;
    private final NexusManager nexuses;
    private final GameStageService stages;
    private final InvaderRegistry registry;
    private final ShardService shards;
    private final HeatService heat;
    private final HudService hud;

    public SidebarUpdater(InvasionConfig config, NexusManager nexuses, GameStageService stages,
                          InvaderRegistry registry, ShardService shards, HeatService heat, HudService hud) {
        this.config = config;
        this.nexuses = nexuses;
        this.stages = stages;
        this.registry = registry;
        this.shards = shards;
        this.heat = heat;
        this.hud = hud;
    }

    /**
     * 区切り用の空行。
     * 完全に空の Component はクライアントがエントリ名にフォールバックすることがあるため、
     * 半角スペースを1つ入れて「中身のある空行」にしておく。
     */
    private static final Component BLANK = Component.text(" ");

    private static final long TICKS_PER_DAY = 24000L;

    /** 前回のtickでサイドバーを出していたプレイヤー。範囲外へ出た人を消すために持つ。 */
    private final Set<UUID> shown = new HashSet<>();

    public void tick() {
        Set<UUID> current = new HashSet<>();

        for (Nexus nexus : nexuses.active()) {
            List<Player> participants = stages.participants(nexus);
            if (participants.isEmpty()) {
                continue;
            }
            int stage = stages.compute(nexus, participants);
            for (Player player : participants) {
                // 複数ネクサスの範囲が重なった場合は最初の1つだけを表示する
                if (!current.add(player.getUniqueId())) {
                    continue;
                }
                hud.updateSidebar(player, Text.of("INVASION", Text.ACCENT),
                        buildLines(nexus, player, stage, participants));
            }
        }

        for (UUID id : shown) {
            if (current.contains(id)) {
                continue;
            }
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                hud.clearSidebar(player);
            }
        }
        shown.clear();
        shown.addAll(current);
    }

    /**
     * 日没と次の Day までの残り時間を、現実の時間で出す。
     *
     * <p>ゲーム内の時刻だと、準備にあとどれだけ使えるかが分かりにくい。
     * 襲撃は日没に来るので、日中は日没までと次の Day まで、夜は夜明けまでを出す。
     * 残り 1 分を切ったら秒で数え、赤で目立たせる。20 tick = 1 秒として計算するので、サーバーが重いと少しずれる。</p>
     */
    private void addClock(List<Component> lines, Nexus nexus) {
        World world = nexus.world();
        if (world == null) {
            return;
        }
        if (Boolean.FALSE.equals(world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE))) {
            lines.add(Text.of("時間が止まっている", Text.MUTED));
            return;
        }
        long time = world.getTime() % TICKS_PER_DAY;
        if (time < WaveEngine.DUSK) {
            lines.add(countdown("日没まで ", WaveEngine.DUSK - time));
            lines.add(countdown("次の Day まで ", TICKS_PER_DAY - time));
        } else if (time < WaveEngine.DAWN) {
            lines.add(countdown("夜明けまで ", WaveEngine.DAWN - time));
        } else {
            lines.add(countdown("次の Day まで ", TICKS_PER_DAY - time));
        }
    }

    private static Component countdown(String label, long ticks) {
        long seconds = (ticks + 19) / 20;
        if (seconds <= 60) {
            return Text.of(label + seconds + "秒", Text.ACCENT).decorate(TextDecoration.BOLD);
        }
        return Text.of(label + ((seconds + 59) / 60) + "分", Text.MUTED);
    }

    /** 防衛範囲の中にいる参加者の数。 */
    private int atBase(Nexus nexus, List<Player> participants) {
        Location center = nexus.center();
        if (center == null || center.getWorld() == null) {
            return 0;
        }
        double radiusSq = config.nexusDefenseRadius() * (double) config.nexusDefenseRadius();
        int count = 0;
        for (Player p : participants) {
            if (center.getWorld().equals(p.getWorld()) && p.getLocation().distanceSquared(center) <= radiusSq) {
                count++;
            }
        }
        return count;
    }

    private List<Component> buildLines(Nexus nexus, Player player, int stage, List<Player> participants) {
        List<Component> lines = new ArrayList<>();
        int until = nexus.daysUntilBloodMoon(config.bloodMoonInterval());

        lines.add(Text.of("Day " + nexus.day(), Text.INFO));
        if (nexus.phase() == Nexus.Phase.BLOOD_MOON) {
            lines.add(Text.of("血の月 — 防衛中", Text.ACCENT));
            lines.add(Text.of("Wave " + nexus.currentWave() + " / " + nexus.totalWaves(), Text.WARN));
        } else {
            lines.add(Text.of(until == 0 ? "今夜は血の月" : "血の月まで " + until + " 日",
                    until <= 1 ? Text.ACCENT : Text.WARN));
        }
        addClock(lines, nexus);

        lines.add(BLANK);
        int invaders = registry.aliveCount(nexus.id());
        lines.add(Text.of("襲撃中の侵略者 " + invaders + " 体", invaders > 0 ? Text.ACCENT : Text.MUTED));
        lines.add(Text.of("拠点にいる人 " + atBase(nexus, participants) + " / " + participants.size() + " 人",
                Text.INFO));

        lines.add(BLANK);
        lines.add(Text.of("ネクサス Lv." + nexus.level(), Text.INFO));
        lines.add(Text.of(Text.bar(nexuses.healthRatio(nexus), 12)
                + " " + (int) nexus.health(), Text.MUTED));
        lines.add(Text.of("GameStage " + stage, Text.MUTED));

        lines.add(BLANK);
        lines.add(Text.of("Shard " + shards.count(player), Text.GOOD));
        lines.add(Text.of("ネクサス内の Shard " + nexus.sharedShards(), Text.INFO));
        if (config.heatEnabled()) {
            double ratio = heat.ratio(player.getLocation());
            lines.add(Text.of("ヒート " + Text.bar(ratio, 8),
                    ratio > 0.75 ? Text.ACCENT : Text.MUTED));
        }
        return lines;
    }
}
