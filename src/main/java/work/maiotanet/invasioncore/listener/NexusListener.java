package work.maiotanet.invasioncore.listener;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.item.InvasionItems;
import work.maiotanet.invasioncore.nexus.Nexus;
import work.maiotanet.invasioncore.nexus.NexusManager;
import work.maiotanet.invasioncore.ui.InvasionHolder;
import work.maiotanet.invasioncore.ui.NexusGui;
import work.maiotanet.invasioncore.util.Text;
import work.maiotanet.invasioncore.wave.WaveEngine;

import java.util.Iterator;

/**
 * ネクサスの設置・破壊・操作と、GUI クリックの受け口。
 */
public final class NexusListener implements Listener {

    private final InvasionConfig config;
    private final NexusManager nexuses;
    private final NexusGui gui;
    private final WaveEngine waves;

    public NexusListener(InvasionConfig config, NexusManager nexuses, NexusGui gui, WaveEngine waves) {
        this.config = config;
        this.nexuses = nexuses;
        this.gui = gui;
        this.waves = waves;
    }

    // ---------- 設置 ----------

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!InvasionItems.is(item, InvasionItems.NEXUS_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();

        // 近すぎるネクサス同士は湧きと防衛範囲が干渉するので禁止する
        double minGap = config.nexusDefenseRadius() * 2.0;
        for (Nexus other : nexuses.all()) {
            Location c = other.center();
            if (c == null || !c.getWorld().equals(block.getWorld())) {
                continue;
            }
            if (c.distance(block.getLocation().add(0.5, 0.5, 0.5)) < minGap) {
                event.setCancelled(true);
                player.sendMessage(Text.error("他のネクサスに近すぎる（最低 "
                        + (int) minGap + " ブロック離すこと）"));
                return;
            }
        }

        nexuses.create(block.getLocation(), player.getUniqueId(), player.getName());
        player.sendMessage(Text.good("ネクサスを設置した。右クリックで操作できる。"));
        player.playSound(block.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.8f);
        nexuses.save();
    }

    // ---------- 破壊 ----------

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Nexus nexus = nexuses.at(event.getBlock().getLocation());
        if (nexus == null) {
            return;
        }
        Player player = event.getPlayer();
        if (nexus.isActive() && !player.isOp()) {
            event.setCancelled(true);
            player.sendMessage(Text.error("稼働中のネクサスは破壊できない。先に /invasion stop で停止すること。"));
            return;
        }
        waves.deactivate(nexus, false);
        nexuses.remove(nexus);
        nexuses.save();

        event.setDropItems(false);
        if (player.getGameMode() != GameMode.CREATIVE) {
            event.getBlock().getWorld().dropItemNaturally(
                    event.getBlock().getLocation().add(0.5, 0.5, 0.5), InvasionItems.nexusBlock());
        }
        player.sendMessage(Text.warn("ネクサスを撤去した。"));
    }

    /** 爆発でネクサスが吹き飛ぶと理不尽なので、必ず対象から外す。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        stripNexusBlocks(event.blockList().iterator());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        stripNexusBlocks(event.blockList().iterator());
    }

    private void stripNexusBlocks(Iterator<Block> it) {
        while (it.hasNext()) {
            if (nexuses.at(it.next().getLocation()) != null) {
                it.remove();
            }
        }
    }

    // ---------- 操作 ----------

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null) {
            return;
        }
        Nexus nexus = nexuses.at(event.getClickedBlock().getLocation());
        if (nexus == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        nexus.members().add(player.getUniqueId());
        gui.openMain(player, nexus);
    }

    // ---------- GUI ----------

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof InvasionHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        Nexus nexus = holder.nexusId() == null ? null : nexuses.byId(holder.nexusId());
        if (nexus == null && holder.kind() != InvasionHolder.Kind.PERK) {
            player.closeInventory();
            player.sendMessage(Text.error("このネクサスはもう存在しない。"));
            return;
        }
        switch (holder.kind()) {
            case MAIN -> gui.handleMain(player, nexus, event.getSlot());
            case UPGRADE -> gui.handleUpgrade(player, nexus, event.getSlot());
            case PERK -> gui.handlePerk(player, nexus, event.getSlot());
        }
    }
}
