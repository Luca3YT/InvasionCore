package work.maiotanet.invasioncore.listener;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.InventoryHolder;
import work.maiotanet.invasioncore.pregame.PreGameService;
import work.maiotanet.invasioncore.util.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ゲーム開始前の行動制限。
 *
 * <p>開始前はスポーン地点の周りから離れられず、ブロックの破壊・設置、アイテムを拾う・落とす、
 * 容器を開く、Mob や動物を攻撃する、をすべて止める。管理者は準備のために対象外。
 * 他のリスナーより先に取り消すため、優先度は LOW にしている（ネクサスの設置処理なども走らせない）。</p>
 */
public final class PreGameListener implements Listener {

    /** 同じ人に注意を出す間隔（ミリ秒）。押し続けたときにチャットが流れないようにする。 */
    private static final long NOTICE_INTERVAL_MILLIS = 5000L;

    private final PreGameService pregame;
    private final Map<UUID, Long> lastNotice = new HashMap<>();

    public PreGameListener(PreGameService pregame) {
        this.pregame = pregame;
    }

    // ---------- 移動 ----------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (!isLeaving(event.getFrom(), to) || !pregame.isRestricted(player)) {
            return;
        }
        // 向きだけは変えられるよう、位置だけ元に戻す
        Location back = event.getFrom().clone();
        back.setYaw(to.getYaw());
        back.setPitch(to.getPitch());
        event.setTo(back);
        notice(player, "ゲーム開始まで、スポーン地点から " + pregame.radius() + " ブロックより先には進めません。");
    }

    /** エンダーパールなどで範囲の外へ飛ぶのも止める。管理者の /tp などは止めない。 */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        String cause = event.getCause().name();
        if (!cause.equals("ENDER_PEARL") && !cause.equals("CHORUS_FRUIT") && !cause.equals("CONSUMABLE_EFFECT")) {
            return;
        }
        if (!isLeaving(event.getFrom(), event.getTo()) || !pregame.isRestricted(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        notice(event.getPlayer(), "ゲーム開始まで、スポーン地点から " + pregame.radius() + " ブロックより先には進めません。");
    }

    /**
     * 範囲の外へ出ようとしているか。
     * すでに外にいる人（範囲外でログインした人など）が戻ってくる動きは止めない。
     */
    private boolean isLeaving(Location from, Location to) {
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ())) {
            return false;
        }
        double toDist = pregame.distanceFromCenter(to);
        if (toDist < 0 || toDist <= pregame.radius()) {
            return false;
        }
        return toDist > pregame.distanceFromCenter(from);
    }

    // ---------- ブロック ----------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        deny(event, event.getPlayer(), "ゲーム開始まではブロックを壊せません。");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        deny(event, event.getPlayer(), "ゲーム開始まではブロックを置けません。");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        deny(event, event.getPlayer(), "ゲーム開始まではブロックを置けません。");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        deny(event, event.getPlayer(), "ゲーム開始まではブロックを壊せません。");
    }

    /** 額縁や絵画もブロックと同じく壊せないようにする。 */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player) {
            deny(event, player, "ゲーム開始まではブロックを壊せません。");
        }
    }

    // ---------- アイテム ----------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && pregame.isRestricted(player)) {
            // 拾おうとするたびに毎tick発生するので、注意は出さずに止めるだけにする
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        deny(event, event.getPlayer(), "ゲーム開始まではアイテムを落とせません。");
    }

    /** チェスト・樽などの容器と、チェスト付きトロッコやロバなどを開けないようにする。 */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        boolean container = holder instanceof BlockInventoryHolder
                || holder instanceof DoubleChest
                || (holder instanceof Entity entity && !entity.equals(player));
        if (container) {
            deny(event, player, "ゲーム開始まではチェストなどを開けません。");
        }
    }

    // ---------- 攻撃 ----------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            // プレイヤー同士はゲームルール（PvP）に任せる
            return;
        }
        Player attacker = event.getDamager() instanceof Player p ? p
                : event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player s ? s
                : null;
        if (attacker != null) {
            deny(event, attacker, "ゲーム開始までは Mob や動物を攻撃できません。");
        }
    }

    // ---------- 共通 ----------

    private void deny(Cancellable event, Player player, String message) {
        if (!pregame.isRestricted(player)) {
            return;
        }
        event.setCancelled(true);
        notice(player, message);
    }

    private void notice(Player player, String message) {
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(player.getUniqueId());
        if (last != null && now - last < NOTICE_INTERVAL_MILLIS) {
            return;
        }
        lastNotice.put(player.getUniqueId(), now);
        player.sendMessage(Text.warn(message));
    }
}
