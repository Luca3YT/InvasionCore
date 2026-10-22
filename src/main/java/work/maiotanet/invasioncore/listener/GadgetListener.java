package work.maiotanet.invasioncore.listener;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import work.maiotanet.invasioncore.defense.BlockTier;
import work.maiotanet.invasioncore.defense.FortifyService;
import work.maiotanet.invasioncore.item.InvasionItems;
import work.maiotanet.invasioncore.mob.Invader;
import work.maiotanet.invasioncore.mob.InvaderRegistry;
import work.maiotanet.invasioncore.nexus.NexusManager;
import work.maiotanet.invasioncore.perk.PerkService;
import work.maiotanet.invasioncore.pregame.PreGameService;
import work.maiotanet.invasioncore.util.Keys;
import work.maiotanet.invasioncore.util.Sounds;
import work.maiotanet.invasioncore.util.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 消耗ガジェット（閃光弾・救急キット・緊急バリア）。
 *
 * <p>敵側にノックバック無効の重装体や広範囲スキルのボスを追加した以上、
 * プレイヤー側にも「押し切られた瞬間を覆す手札」が要る。
 * どれも消耗品にしてあるので、常用はできず持ち込み判断が問われる。</p>
 */
public final class GadgetListener implements Listener {

    /** 緊急バリアの寿命（tick）。 */
    private static final int BARRIER_LIFETIME_TICKS = 45 * 20;

    private final Plugin plugin;
    private final NexusManager nexuses;
    private final InvaderRegistry registry;
    private final PerkService perks;
    private final FortifyService fortify;
    private final PreGameService pregame;

    /**
     * 展開中の緊急バリアのブロック位置。
     *
     * <p>追跡しないと、展開した鉄ブロックを掘るだけで鉄ブロックが手に入る（素材の複製）うえ、
     * 45秒以内にサーバーを止めると消去タスクが走らずに壁が恒久的に残ってしまう。</p>
     */
    private final Set<Location> barrierBlocks = new HashSet<>();

    public GadgetListener(Plugin plugin, NexusManager nexuses, InvaderRegistry registry,
                          PerkService perks, FortifyService fortify, PreGameService pregame) {
        this.pregame = pregame;
        this.plugin = plugin;
        this.nexuses = nexuses;
        this.registry = registry;
        this.perks = perks;
        this.fortify = fortify;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack held = event.getItem();
        String id = InvasionItems.idOf(held);
        if (id == null) {
            return;
        }
        Action action = event.getAction();
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!rightClick) {
            return;
        }
        Player player = event.getPlayer();

        switch (id) {
            case InvasionItems.FLASH_CHARGE -> {
                event.setCancelled(true);
                throwFlash(player, held);
            }
            case InvasionItems.MEDKIT -> {
                event.setCancelled(true);
                useMedkit(player, held);
            }
            case InvasionItems.BARRIER -> {
                if (event.getClickedBlock() != null) {
                    event.setCancelled(true);
                    if (pregame.isRestricted(player)) {
                        // バリアはブロックを置くので、開始前の「置く」の禁止と揃える
                        pregame.notice(player, "ゲーム開始まではブロックを置けません。");
                        return;
                    }
                    deployBarrier(player, held, event.getClickedBlock(), event.getBlockFace());
                }
            }
            default -> {
            }
        }
    }

    // ---------- 閃光弾 ----------

    private void throwFlash(Player player, ItemStack held) {
        Snowball ball = player.launchProjectile(Snowball.class,
                player.getEyeLocation().getDirection().multiply(1.6));
        ball.getPersistentDataContainer().set(
                Keys.ITEM_ID, PersistentDataType.STRING, InvasionItems.FLASH_CHARGE);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1.0f, 1.2f);
        consume(player, held);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        String id = projectile.getPersistentDataContainer()
                .get(Keys.ITEM_ID, PersistentDataType.STRING);
        if (!InvasionItems.FLASH_CHARGE.equals(id)) {
            return;
        }
        Location center = projectile.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.FLASH, center, 4);
        world.spawnParticle(Particle.END_ROD, center, 60, 2.0, 1.5, 2.0, 0.25);
        world.playSound(center, Sound.ITEM_FIRECHARGE_USE, 1.8f, 1.8f);

        for (Invader invader : invadersWithin(center, 7.0)) {
            invader.entity().addPotionEffect(new PotionEffect(
                    PotionEffectType.BLINDNESS, 120, 0, false, true, false));
            invader.entity().addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, 120, 2, false, true, false));
            // 目標を見失わせる。ボスにも効くのがこのガジェットの価値。
            invader.entity().setTarget(null);
        }
    }

    // ---------- 救急キット ----------

    private void useMedkit(Player player, ItemStack held) {
        double multiplier = perks.repairKitMultiplier(player.getUniqueId());
        int healed = 0;
        for (Player target : nearbyPlayers(player, 6.0)) {
            AttributeInstance max = target.getAttribute(Attribute.MAX_HEALTH);
            double cap = max == null ? 20.0 : max.getValue();
            double amount = (target == player ? 10.0 : 7.0) * multiplier;
            target.setHealth(Math.min(cap, target.getHealth() + amount));
            target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
            target.getWorld().spawnParticle(Particle.HEART,
                    target.getLocation().add(0, 1.6, 0), 6, 0.4, 0.3, 0.4, 0.0);
            healed++;
        }
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.7f, 1.6f);
        Text.actionBarGood(player, healed + " 人を回復した。");
        consume(player, held);
    }

    // ---------- 緊急バリア ----------

    /**
     * クリックした面に 3x3 の一時壁を張る。
     * 決壊した瞬間の穴を塞ぐための手段で、恒久防御にはならないよう45秒で消える。
     */
    private void deployBarrier(Player player, ItemStack held, Block clicked, BlockFace face) {
        if (nexuses.nearest(clicked.getLocation()) == null) {
            Text.actionBarError(player, "ネクサスの防衛範囲内でのみ展開できる。");
            Sounds.fail(player);
            return;
        }
        Block origin = clicked.getRelative(face);
        List<Block> placed = new ArrayList<>(9);

        // クリック面の法線に応じて、壁になる向きへ3x3を展開する
        boolean vertical = face == BlockFace.UP || face == BlockFace.DOWN;
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                Block target = vertical
                        ? origin.getRelative(a, 0, b)
                        : origin.getRelative(
                        face.getModX() != 0 ? 0 : a, b, face.getModZ() != 0 ? 0 : a);
                if (!target.getType().isAir() && !target.isLiquid()) {
                    continue;
                }
                target.setType(Material.IRON_BLOCK);
                // 耐久を登録しないと硬度由来の値しか持たず、説明どおりの「鋼鉄級」にならない
                fortify.register(target, BlockTier.STEEL,
                        perks.fortifyHealthMultiplier(player.getUniqueId()));
                placed.add(target);
                barrierBlocks.add(target.getLocation());
            }
        }
        if (placed.isEmpty()) {
            Text.actionBarWarn(player, "展開できる空間がない。");
            Sounds.fail(player);
            return;
        }
        World world = origin.getWorld();
        world.playSound(origin.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.6f);
        world.spawnParticle(Particle.END_ROD, origin.getLocation().add(0.5, 0.5, 0.5),
                24, 1.0, 1.0, 1.0, 0.05);
        Text.actionBarGood(player, "緊急バリアを展開した（45秒）。");
        consume(player, held);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Block block : placed) {
                if (removeBarrierBlock(block)) {
                    block.getWorld().spawnParticle(Particle.CLOUD,
                            block.getLocation().add(0.5, 0.5, 0.5), 6, 0.2, 0.2, 0.2, 0.01);
                }
            }
        }, BARRIER_LIFETIME_TICKS);
    }

    /**
     * バリアの1ブロックを消す。既に掘られた／置き換えられた位置は触らない。
     *
     * @return 鉄ブロックを実際に消したら true
     */
    private boolean removeBarrierBlock(Block block) {
        if (!barrierBlocks.remove(block.getLocation())) {
            return false;
        }
        // 消滅時に耐久記録も必ず消す。残すと同座標の別ブロックが鋼鉄扱いになる。
        fortify.forget(block);
        if (block.getType() != Material.IRON_BLOCK) {
            return false;
        }
        block.setType(Material.AIR);
        return true;
    }

    /** 一時的な壁なので、掘っても鉄ブロックは手に入らない。 */
    @EventHandler(ignoreCancelled = true)
    public void onBarrierBreak(BlockBreakEvent event) {
        if (barrierBlocks.remove(event.getBlock().getLocation())) {
            event.setDropItems(false);
        }
    }

    /** 侵略者に壊された跡へプレイヤーが置いた物は、もうバリアではない。 */
    @EventHandler(ignoreCancelled = true)
    public void onPlaceOverBarrier(BlockPlaceEvent event) {
        barrierBlocks.remove(event.getBlockPlaced().getLocation());
    }

    /** サーバー停止時に展開中のバリアを全て消す。消去タスクはスケジューラごと破棄されるため。 */
    public void shutdown() {
        for (Location loc : new ArrayList<>(barrierBlocks)) {
            if (loc.getWorld() != null) {
                removeBarrierBlock(loc.getBlock());
            }
        }
        barrierBlocks.clear();
    }

    // ---------- 共通 ----------

    private void consume(Player player, ItemStack held) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.setAmount(held.getAmount() - 1);
        }
    }

    private List<Invader> invadersWithin(Location center, double radius) {
        List<Invader> out = new ArrayList<>();
        double sq = radius * radius;
        for (Invader invader : registry.all()) {
            if (!invader.isValid() || !invader.entity().getWorld().equals(center.getWorld())) {
                continue;
            }
            if (invader.entity().getLocation().distanceSquared(center) <= sq) {
                out.add(invader);
            }
        }
        return out;
    }

    private List<Player> nearbyPlayers(Player origin, double radius) {
        List<Player> out = new ArrayList<>();
        double sq = radius * radius;
        for (Player p : origin.getWorld().getPlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (p.getLocation().distanceSquared(origin.getLocation()) <= sq) {
                out.add(p);
            }
        }
        return out;
    }
}
