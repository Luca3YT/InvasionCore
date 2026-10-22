package work.maiotanet.invasioncore.listener;

import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import work.maiotanet.invasioncore.BuildInfo;
import work.maiotanet.invasioncore.util.Text;

import java.time.Duration;

/**
 * 運営（OP）がサーバーに入ったとき、入っているプラグインの版を画面に出す。
 * テストサーバーと本番、develop と main の jar を入れ替えていると、どれが動いているか分からなくなるため。
 */
public final class VersionNoticeListener implements Listener {

    private final BuildInfo build;

    public VersionNoticeListener(BuildInfo build) {
        this.build = build;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.isOp()) {
            return;
        }
        player.showTitle(Title.title(
                Text.of("InvasionCore", Text.ACCENT),
                Text.of(build.shortVersion(), Text.INFO),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))));
        player.sendMessage(Text.info("InvasionCore " + build.fullVersion()));
    }
}
