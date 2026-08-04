package work.maiotanet.invasioncore.listener;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.heat.HeatService;
import work.maiotanet.invasioncore.nexus.Nexus;
import work.maiotanet.invasioncore.nexus.NexusManager;
import work.maiotanet.invasioncore.perk.PerkService;
import work.maiotanet.invasioncore.ui.HudService;
import work.maiotanet.invasioncore.world.WorldEventService;

import java.util.ArrayList;
import java.util.List;

/**
 * プレイヤー側の横断的な処理。
 * ヒートの発生源、死亡時バックパック、HUD の後始末を扱う。
 */
public final class PlayerListener implements Listener {

    private final InvasionConfig config;
    private final NexusManager nexuses;
    private final HeatService heat;
    private final PerkService perks;
    private final HudService hud;
    private final WorldEventService worldEvents;

    public PlayerListener(InvasionConfig config, NexusManager nexuses, HeatService heat,
                          PerkService perks, HudService hud, WorldEventService worldEvents) {
        this.config = config;
        this.nexuses = nexuses;
        this.heat = heat;
        this.perks = perks;
        this.hud = hud;
        this.worldEvents = worldEvents;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // 名前を最新化しておく（ランキング表示で使う）
        perks.profile(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hud.clearSidebar(event.getPlayer());
    }

    // ---------- ヒートの発生源 ----------

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isNearActiveNexus(event.getPlayer())) {
            heat.add(event.getBlock().getLocation(), config.heatPerBlockBreak());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isNearActiveNexus(event.getPlayer())) {
            heat.add(event.getBlock().getLocation(), config.heatPerBlockPlace());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player && isNearActiveNexus(player)) {
            heat.add(player.getLocation(), config.heatPerShot());
        }
    }

    private boolean isNearActiveNexus(Player player) {
        return config.heatEnabled() && nexuses.nearestActive(player.getLocation()) != null;
    }

    // ---------- 死亡 ----------

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!config.backpackEnabled() || event.getKeepInventory()) {
            return;
        }
        Player player = event.getEntity();
        if (!isInsideInvasionArea(player)) {
            // 侵攻に無関係な場所での死亡は通常どおりに任せる
            return;
        }
        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        if (worldEvents.placeBackpack(player, drops)) {
            event.getDrops().clear();
        }
    }

    /**
     * 侵攻の影響圏内で死んだかどうか。
     *
     * <p>判定を「稼働中ネクサスの有無」だけにすると、ネクサスが崩壊した瞬間に
     * バックパックが働かなくなる。落城直後こそ死にやすい場面なので、
     * 崩壊済みのネクサスも拠点として扱い、そのうえで距離で絞る。</p>
     */
    private boolean isInsideInvasionArea(Player player) {
        Location loc = player.getLocation();
        if (loc.getWorld() == null) {
            return false;
        }
        // 防衛範囲そのものより広く取る。拠点から少し離れた交戦も対象にしたい。
        double radius = config.nexusDefenseRadius() * 3.0;
        double radiusSq = radius * radius;

        for (Nexus nexus : nexuses.all()) {
            if (nexus.phase() == Nexus.Phase.DORMANT) {
                // 一度も起動していないネクサスは拠点として扱わない
                continue;
            }
            Location center = nexus.center();
            if (center == null || !center.getWorld().equals(loc.getWorld())) {
                continue;
            }
            if (center.distanceSquared(loc) <= radiusSq) {
                return true;
            }
        }
        return false;
    }
}
