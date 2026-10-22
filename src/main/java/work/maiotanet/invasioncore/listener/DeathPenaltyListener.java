package work.maiotanet.invasioncore.listener;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.nexus.NexusManager;
import work.maiotanet.invasioncore.util.Keys;
import work.maiotanet.invasioncore.util.Text;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 死んだときの待ち時間（デスペナルティ）。
 *
 * <p>すぐ生き返ると、血の月でも死ぬことが痛くなく、突っ込んでは生き返るだけになる。
 * 死んだら死んだ場所で一定時間観戦モードになり（戦況は見られる）、終わったらネクサスのそばで生き返る。
 * 待っている間に抜けたりサーバーが止まったりしても戻れるよう、復活する時刻は本人（PDC）に記録する。</p>
 */
public final class DeathPenaltyListener implements Listener {

    private final Plugin plugin;
    private final InvasionConfig config;
    private final NexusManager nexuses;

    /** 死んだ場所。生き返る瞬間（リスポーン）にそこへ出す。 */
    private final Map<UUID, Location> deathPlaces = new HashMap<>();

    public DeathPenaltyListener(Plugin plugin, InvasionConfig config, NexusManager nexuses) {
        this.plugin = plugin;
        this.config = config;
        this.nexuses = nexuses;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        int seconds = config.deathSpectateSeconds();
        if (seconds <= 0 || (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE)) {
            return;
        }
        Location place = player.getLocation().clone();
        World world = place.getWorld();
        if (world != null && place.getY() < world.getMinHeight()) {
            // 奈落で死んだときは、観戦の場所だけワールドの底より上にする
            place.setY(world.getMinHeight() + 1);
        }
        deathPlaces.put(player.getUniqueId(), place);
        player.getPersistentDataContainer().set(Keys.DEATH_SPECTATE_UNTIL, PersistentDataType.LONG,
                System.currentTimeMillis() + seconds * 1000L);
    }

    /** リスポーンの画面を押したら、死んだ場所で観戦モードにする。 */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location place = deathPlaces.remove(player.getUniqueId());
        if (place == null || !waiting(player)) {
            return;
        }
        event.setRespawnLocation(place);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && waiting(player)) {
                player.setGameMode(GameMode.SPECTATOR);
            }
        });
    }

    /** 待っている途中で抜けた人が戻ってきたとき。 */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.getPersistentDataContainer().has(Keys.DEATH_SPECTATE_UNTIL, PersistentDataType.LONG)) {
            return;
        }
        if (player.isDead()) {
            // 死んだ画面のまま抜けた。リスポーンを押したときに続きをする
            return;
        }
        if (waiting(player)) {
            player.setGameMode(GameMode.SPECTATOR);
        } else {
            revive(player);
        }
    }

    /** 1 秒ごと。残り時間を出し、時間が来た人を生き返らせる。 */
    private void tick() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Long until = player.getPersistentDataContainer().get(Keys.DEATH_SPECTATE_UNTIL, PersistentDataType.LONG);
            if (until == null || player.isDead()) {
                continue;
            }
            if (now >= until) {
                revive(player);
                continue;
            }
            long seconds = (until - now + 999) / 1000;
            player.showTitle(Title.title(
                    Text.of("死んでしまった", Text.ACCENT),
                    Text.of("復活まで " + seconds + " 秒", Text.WARN),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ZERO)));
        }
    }

    private static boolean waiting(Player player) {
        Long until = player.getPersistentDataContainer().get(Keys.DEATH_SPECTATE_UNTIL, PersistentDataType.LONG);
        return until != null && System.currentTimeMillis() < until;
    }

    /** ネクサスのそばで生き返らせる。ネクサスが無ければ、ベッドかワールドの初期地点。 */
    private void revive(Player player) {
        player.getPersistentDataContainer().remove(Keys.DEATH_SPECTATE_UNTIL);
        Location spot = nexuses.standingSpot(nexuses.primary());
        if (spot == null) {
            spot = player.getRespawnLocation() != null
                    ? player.getRespawnLocation()
                    : Bukkit.getWorlds().get(0).getSpawnLocation();
        }
        player.teleport(spot);
        player.setGameMode(GameMode.SURVIVAL);
        player.clearTitle();
        player.sendMessage(Text.good("ネクサスのそばで生き返った。"));
    }
}
