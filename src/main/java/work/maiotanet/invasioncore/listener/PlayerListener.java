package work.maiotanet.invasioncore.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.economy.ShardPickupSound;
import work.maiotanet.invasioncore.economy.ShardService;
import work.maiotanet.invasioncore.heat.HeatService;
import work.maiotanet.invasioncore.item.InvasionItems;
import work.maiotanet.invasioncore.nexus.Nexus;
import work.maiotanet.invasioncore.nexus.NexusManager;
import work.maiotanet.invasioncore.perk.PerkService;
import work.maiotanet.invasioncore.ui.HudService;
import work.maiotanet.invasioncore.util.Text;


/**
 * プレイヤー側の横断的な処理。
 * ヒートの発生源、Shard を拾った音、HUD の後始末を扱う（死んだときの墓は GraveListener）。
 */
public final class PlayerListener implements Listener {

    private final InvasionConfig config;
    private final NexusManager nexuses;
    private final HeatService heat;
    private final PerkService perks;
    private final HudService hud;
    private final ShardPickupSound shardSound;
    private final ShardService shards;

    public PlayerListener(InvasionConfig config, NexusManager nexuses, HeatService heat,
                          PerkService perks, HudService hud,
                          ShardPickupSound shardSound, ShardService shards) {
        this.shardSound = shardSound;
        this.shards = shards;
        this.config = config;
        this.nexuses = nexuses;
        this.heat = heat;
        this.perks = perks;
        this.hud = hud;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // 名前を最新化しておく（ランキング表示で使う）
        perks.profile(event.getPlayer());
        // サーバーに入った時点で参加者として扱う（/invasion join と同じ）
        for (Nexus nexus : nexuses.all()) {
            nexus.members().add(event.getPlayer().getUniqueId());
        }
    }

    /** Shard を拾ったら、拾えた個数ぶん効果音を鳴らす。開始前などで拾えなかったときは鳴らさない。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickupShard(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !InvasionItems.is(event.getItem().getItemStack(), InvasionItems.SHARD)) {
            return;
        }
        // インベントリが一杯に近いと一部しか拾えないので、拾えた分だけ数える
        int picked = event.getItem().getItemStack().getAmount() - event.getRemaining();
        shardSound.queue(player, picked);
        shards.notePickup(player, picked);
    }

    /**
     * 地面に落ちた Shard を光らせ、壁越しや草むらの中でも見つけやすくする。
     * 侵略者が倒れた場所は乱戦の最中なので、光っていないと拾い損ねやすい。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShardSpawn(ItemSpawnEvent event) {
        if (InvasionItems.is(event.getEntity().getItemStack(), InvasionItems.SHARD)) {
            event.getEntity().setGlowing(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hud.clearSidebar(event.getPlayer());
        Text.forgetActionBar(event.getPlayer());
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

    /**
     * 稼働中ネクサスの防衛範囲内にいるか。
     * 距離を問わない nearestActive だと、同じワールドのどこで採掘しても熱が溜まってしまう。
     */
    private boolean isNearActiveNexus(Player player) {
        if (!config.heatEnabled()) {
            return false;
        }
        Nexus nexus = nexuses.nearest(player.getLocation());
        return nexus != null && nexus.isActive();
    }
}
