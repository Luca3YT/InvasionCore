package work.maiotanet.invasioncore.ui;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BossBar とサイドバーの管理。
 *
 * <p>サイドバーは「エントリ文字列は一意でなければならない」というスコアボードの制約があるため、
 * 不可視のカラーコードをエントリに使い、実際の表示は Team の prefix で行う定石で実装している。</p>
 */
public final class HudService {

    /** サイドバー1行分の不可視エントリ。行数の上限でもある。 */
    private static final String[] SLOTS = {
            "§0", "§1", "§2", "§3", "§4", "§5", "§6",
            "§7", "§8", "§9", "§a", "§b", "§c", "§d", "§e"
    };

    private final Map<UUID, BossBar> nexusBars = new HashMap<>();
    private final Map<UUID, List<UUID>> barViewers = new HashMap<>();
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

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
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            boards.put(player.getUniqueId(), board);
        }

        Objective objective = board.getObjective("invasion");
        if (objective == null) {
            objective = board.registerNewObjective("invasion", Criteria.DUMMY, title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            objective.displayName(title);
        }

        int max = Math.min(lines.size(), SLOTS.length);
        for (int i = 0; i < max; i++) {
            String entry = SLOTS[i];
            Team team = board.getTeam("line" + i);
            if (team == null) {
                team = board.registerNewTeam("line" + i);
                team.addEntry(entry);
            }
            team.prefix(lines.get(i));
            // 上の行ほど高いスコアにする
            objective.getScore(entry).setScore(max - i);
        }
        // 余った行は消す
        for (int i = max; i < SLOTS.length; i++) {
            board.resetScores(SLOTS[i]);
        }

        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
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
