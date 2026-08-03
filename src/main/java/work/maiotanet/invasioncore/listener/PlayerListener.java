package work.maiotanet.invasioncore.listener;

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
        if (nexuses.nearestActive(player.getLocation()) == null) {
            // 侵攻に無関係な場所での死亡は通常どおりに任せる
            return;
        }
        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        if (worldEvents.placeBackpack(player, drops)) {
            event.getDrops().clear();
        }
    }
}
