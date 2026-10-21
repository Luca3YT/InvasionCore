package work.maiotanet.invasioncore.ui;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * BossBar とサイドバーの管理。
 *
 * <p>サイドバーは行ごとに一意なエントリ名（line0〜）を使い、表示する文字は Score#customName で差し替える。
 * 古い「不可視のカラーコード＋Team の prefix」の方式は、このバージョンの API では表示が崩れるため使わない。</p>
 */
public final class HudService {

    /**
     * サイドバー1行分のエントリ名。行数の上限でもある。
     *
     * <p>エントリ文字列そのものは画面に出ない。{@link Score#customName(Component)} で
     * 表示内容を差し替えるため、一意でありさえすればよい。</p>
     */
    private static final String[] SLOTS = {
            "line0", "line1", "line2", "line3", "line4", "line5", "line6", "line7",
            "line8", "line9", "line10", "line11", "line12", "line13", "line14"
    };

    private final Map<UUID, BossBar> nexusBars = new HashMap<>();
    private final Map<UUID, List<UUID>> barViewers = new HashMap<>();
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Set<UUID> sidebarFailureLogged = new HashSet<>();

    // ---------- BossBar ----------

    public BossBar bar(UUID nexusId) {
        return nexusBars.computeIfAbsent(nexusId, k -> BossBar.bossBar(
                Component.empty(), 1.0f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10));
    }

    public void updateBar(UUID nexusId, Component name, float progress, BossBar.Color color) {
        BossBar bar = bar(nexusId);
        bar.name(name);
        bar.progress(Math.max(0.0f, Math.min(1.0f, progress)));
        bar.color(color);
    }

    /** 表示対象を差し替える。抜けた人からは自動で消える。 */
    public void showBarTo(UUID nexusId, List<Player> viewers) {
        BossBar bar = bar(nexusId);
        List<UUID> previous = barViewers.getOrDefault(nexusId, List.of());
        List<UUID> current = new ArrayList<>(viewers.size());

        for (Player p : viewers) {
            current.add(p.getUniqueId());
            p.showBossBar(bar);
        }
        for (UUID old : previous) {
            if (!current.contains(old)) {
                Player p = Bukkit.getPlayer(old);
                if (p != null) {
                    p.hideBossBar(bar);
                }
            }
        }
        barViewers.put(nexusId, current);
    }

    public void hideBar(UUID nexusId) {
        BossBar bar = nexusBars.remove(nexusId);
        List<UUID> viewers = barViewers.remove(nexusId);
        if (bar == null || viewers == null) {
            return;
        }
        for (UUID id : viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.hideBossBar(bar);
            }
        }
    }

    // ---------- サイドバー ----------

    public void updateSidebar(Player player, Component title, List<Component> lines) {
        try {
            Scoreboard board = boards.get(player.getUniqueId());
            if (board == null) {
                board = Bukkit.getScoreboardManager().getNewScoreboard();
                boards.put(player.getUniqueId(), board);
            }

            Objective objective = board.getObjective("invasion");
            if (objective == null) {
                objective = board.registerNewObjective("invasion", Criteria.DUMMY, title);
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
                // 行ごとの数値（右端に出る赤い数字）は表示しない
                objective.numberFormat(NumberFormat.blank());
            } else {
                objective.displayName(title);
            }

            int max = Math.min(lines.size(), SLOTS.length);
            for (int i = 0; i < max; i++) {
                Score score = objective.getScore(SLOTS[i]);
                score.customName(lines.get(i));
                // 上の行ほど高いスコアにする
                score.setScore(max - i);
            }
            // 余った行は消す
            for (int i = max; i < SLOTS.length; i++) {
                board.resetScores(SLOTS[i]);
            }

            if (player.getScoreboard() != board) {
                player.setScoreboard(board);
            }
        } catch (RuntimeException ex) {
            // 毎秒呼ばれるので、同じ例外でログを埋めないよう1回だけ出す。
            // 例外を握り潰したままだと「サイドバーが出ない」原因が追えなくなる。
            if (sidebarFailureLogged.add(player.getUniqueId())) {
                Bukkit.getLogger().log(Level.WARNING,
                        "[InvasionCore] サイドバーの更新に失敗しました: " + player.getName(), ex);
            }
            boards.remove(player.getUniqueId());
        }
    }

    public void clearSidebar(Player player) {
        boards.remove(player.getUniqueId());
        if (Bukkit.getScoreboardManager() != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public void shutdown() {
        for (UUID nexusId : new ArrayList<>(nexusBars.keySet())) {
            hideBar(nexusId);
        }
        for (UUID playerId : new ArrayList<>(boards.keySet())) {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null) {
                clearSidebar(p);
            }
        }
        boards.clear();
    }
}
