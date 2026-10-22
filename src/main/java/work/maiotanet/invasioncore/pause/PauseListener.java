package work.maiotanet.invasioncore.pause;

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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataType;
import work.maiotanet.invasioncore.mob.InvaderRegistry;
import work.maiotanet.invasioncore.util.Keys;
import work.maiotanet.invasioncore.util.Text;

/**
 * 一時停止中の制限。
 *
 * <p>参加者はダメージを受けず、空腹にもならない。止め方（{@link PauseService.Mode}）に応じて、
 * 動く・壊す・置く・攻撃する・アイテムを使うを止める。運営は対象外。
 * 侵略者の爆発（爆導体の自爆・撃ち込まれた爆弾）も止める。</p>
 */
public final class PauseListener implements Listener {

    private final PauseService pause;
    private final InvaderRegistry registry;

    public PauseListener(PauseService pause, InvaderRegistry registry) {
        this.pause = pause;
        this.registry = registry;
    }

    // ---------- 参加者を守る ----------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (pause.isPaused() && event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (pause.isPaused() && event.getEntity() instanceof Player player && pause.isRestricted(player)
                && event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    /** 侵略者の爆発は止める（止めている間に壁や拠点が削れないように）。 */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPrime(ExplosionPrimeEvent event) {
        if (pause.isPaused() && isInvaderBlast(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (pause.isPaused() && isInvaderBlast(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private boolean isInvaderBlast(Entity entity) {
        return registry.get(entity) != null
                || entity.getPersistentDataContainer().has(Keys.PROJECTILE_NEXUS, PersistentDataType.STRING);
    }

    // ---------- 止め方ごとの制限 ----------

    /** freeze：その場から動けない（向きは変えられる）。 */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!pause.isRestricted(player) || pause.mode() != PauseService.Mode.FREEZE) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }
        Location back = from.clone();
        back.setYaw(to.getYaw());
        back.setPitch(to.getPitch());
        event.setTo(back);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        deny(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        deny(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        deny(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        deny(event, event.getPlayer());
    }

    /** アイテムを使う・ブロックを右クリックする（タレットへの装填やガジェットも含む）。 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (isLimited(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        deny(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        Player attacker = event.getDamager() instanceof Player p ? p
                : event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player s ? s
                : null;
        if (attacker != null) {
            deny(event, attacker);
        }
    }

    /** freeze と walk では、壊す・置く・攻撃する・使うを止める。free では止めない。 */
    private boolean isLimited(Player player) {
        return pause.isRestricted(player) && pause.mode() != PauseService.Mode.FREE;
    }

    private void deny(Cancellable event, Player player) {
        if (isLimited(player)) {
            event.setCancelled(true);
            Text.actionBarWarn(player, "一時停止中。運営が再開するまで待ってね。");
        }
    }
}
