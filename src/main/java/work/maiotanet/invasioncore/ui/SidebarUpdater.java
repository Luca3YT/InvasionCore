package work.maiotanet.invasioncore.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.economy.ShardService;
import work.maiotanet.invasioncore.heat.HeatService;
import work.maiotanet.invasioncore.mob.InvaderRegistry;
import work.maiotanet.invasioncore.nexus.Nexus;
import work.maiotanet.invasioncore.nexus.NexusManager;
import work.maiotanet.invasioncore.stage.GameStageService;
import work.maiotanet.invasioncore.util.Text;

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
                        buildLines(nexus, player, stage));
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

    private List<Component> buildLines(Nexus nexus, Player player, int stage) {
        List<Component> lines = new ArrayList<>();
        int until = nexus.daysUntilBloodMoon(config.bloodMoonInterval());

        lines.add(Text.of("Day " + nexus.day(), Text.INFO));
        if (nexus.phase() == Nexus.Phase.BLOOD_MOON) {
            lines.add(Text.of("血の月 — 防衛中", Text.ACCENT));
            lines.add(Text.of("Wave " + nexus.currentWave() + " / " + nexus.totalWaves(), Text.WARN));
            lines.add(Text.of("残敵 " + registry.aliveCount(nexus.id()), Text.WARN));
        } else {
            lines.add(Text.of(until == 0 ? "今夜は血の月" : "血の月まで " + until + " 日",
                    until <= 1 ? Text.ACCENT : Text.WARN));
        }

        lines.add(BLANK);
        lines.add(Text.of("ネクサス Lv." + nexus.level(), Text.INFO));
        lines.add(Text.of(Text.bar(nexuses.healthRatio(nexus), 12)
                + " " + (int) nexus.health(), Text.MUTED));
        lines.add(Text.of("GameStage " + stage, Text.MUTED));

        lines.add(BLANK);
        lines.add(Text.of("Shard " + shards.count(player), Text.GOOD));
        if (config.heatEnabled()) {
            double ratio = heat.ratio(player.getLocation());
            lines.add(Text.of("ヒート " + Text.bar(ratio, 8),
                    ratio > 0.75 ? Text.ACCENT : Text.MUTED));
        }
        return lines;
    }
}
