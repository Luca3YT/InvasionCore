package work.maiotanet.invasioncore.listener;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import work.maiotanet.invasioncore.grave.GraveService;
import work.maiotanet.invasioncore.util.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * 墓の作成・回収と、墓を壊させないための保護。
 *
 * <p>墓の頭は中身を持たない飾りなので、壊れると所持品の在りかが分からなくなる。
 * 素手での破壊・爆発・ピストン・エンダーマンなど、ブロックが消える経路を全部止める
 * （侵略者による破壊は FortifyService 側で止めている）。</p>
 */
public final class GraveListener implements Listener {

    private final GraveService graves;

    public GraveListener(GraveService graves) {
        this.graves = graves;
    }

    // ---------- 作成 ----------

    /** 死んだら所持品を墓にしまう。どこで死んでも作る。 */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory() || event.getDrops().isEmpty()) {
            return;
        }
        Player player = event.getEntity();
        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        Location at = graves.create(player, drops);
        if (at == null) {
            // 置ける場所が無かったときは、普通のマイクラと同じくその場に落とす
            return;
        }
        event.getDrops().clear();
        player.sendMessage(Text.warn("所持品は死んだ場所の墓に残った（" + at.getBlockX() + ", "
                + at.getBlockY() + ", " + at.getBlockZ() + "）。墓をクリックすると取り戻せる。"));
    }

    // ---------- 回収 ----------

    /** 右クリックでも左クリックでも回収する。他のリスナー（ハンマーなど）より先に取る。 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (graves.click(event.getPlayer(), event.getClickedBlock())) {
            event.setCancelled(true);
        }
    }

    // ---------- 保護 ----------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (graves.isGrave(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(graves::isGrave);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(graves::isGrave);
    }

    /** 頭はピストンで押すと壊れるので、墓を巻き込むピストンは動かさない。 */
    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (anyGrave(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (anyGrave(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    /** エンダーマンが持ち去る、ウィザーが壊す、など。 */
    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (graves.isGrave(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    private boolean anyGrave(List<Block> blocks) {
        for (Block block : blocks) {
            if (graves.isGrave(block)) {
                return true;
            }
        }
        return false;
    }
}
