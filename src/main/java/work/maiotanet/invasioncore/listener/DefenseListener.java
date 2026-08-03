package work.maiotanet.invasioncore.listener;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.defense.BlockTier;
import work.maiotanet.invasioncore.defense.FortifyService;
import work.maiotanet.invasioncore.defense.TurretService;
import work.maiotanet.invasioncore.defense.TurretType;
import work.maiotanet.invasioncore.economy.ContributionTracker;
import work.maiotanet.invasioncore.economy.ShardService;
import work.maiotanet.invasioncore.item.InvasionItems;
import work.maiotanet.invasioncore.nexus.Nexus;
import work.maiotanet.invasioncore.nexus.NexusManager;
import work.maiotanet.invasioncore.perk.PerkService;
import work.maiotanet.invasioncore.util.Text;

/**
 * 防衛設備の操作。強化ハンマー・補修キット・タレットの設置と装填を扱う。
 */
public final class DefenseListener implements Listener {

    private final InvasionConfig config;
    private final NexusManager nexuses;
    private final FortifyService fortify;
    private final TurretService turrets;
    private final ShardService shards;
    private final PerkService perks;
    private final ContributionTracker contributions;

    public DefenseListener(InvasionConfig config, NexusManager nexuses, FortifyService fortify,
                           TurretService turrets, ShardService shards, PerkService perks,
                           ContributionTracker contributions) {
        this.config = config;
        this.nexuses = nexuses;
        this.fortify = fortify;
        this.turrets = turrets;
        this.shards = shards;
        this.perks = perks;
        this.contributions = contributions;
    }

    // ---------- 右クリック操作 ----------

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = event.getItem();
        Block block = event.getClickedBlock();

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null) {
            return;
        }

        // タレットへの弾薬装填
        TurretService.Turret turret = turrets.get(block);
        if (turret != null) {
            event.setCancelled(true);
            handleTurretInteract(player, block, turret, held);
            return;
        }

        if (InvasionItems.is(held, InvasionItems.HAMMER)) {
            event.setCancelled(true);
            handleHammer(player, block);
            return;
        }
        if (InvasionItems.is(held, InvasionItems.REPAIR_KIT)) {
            event.setCancelled(true);
            handleRepairKit(player, block, held);
        }
    }

    private void handleHammer(Player player, Block block) {
        Nexus nexus = nexuses.nearest(block.getLocation());
        if (nexus == null) {
            player.sendMessage(Text.error("ネクサスの防衛範囲内でのみ強化できる。"));
            return;
        }
        if (nexuses.at(block.getLocation()) != null) {
            player.sendMessage(Text.warn("ネクサス本体は強化できない。"));
            return;
        }

        if (player.isSneaking()) {
            showBlockInfo(player, block);
            return;
        }

        FortifyService.Fortified current = fortify.get(block);

        // 1) 未強化 → 木枠へ
        if (current == null || !current.isManaged()) {
            if (!isFortifiable(block)) {
                player.sendMessage(Text.error("このブロックは強化できない。"));
                return;
            }
            int cost = adjustedCost(player, nexus, BlockTier.first().upgradeCost());
            if (!shards.take(player, cost)) {
                sendInsufficient(player, cost);
                return;
            }
            fortify.fortify(block);
            contributions.record(nexus.id(), player.getUniqueId(), ContributionTracker.Kind.FORTIFY, 1);
            player.sendMessage(Text.good("木枠に強化した。（-" + cost + " Shard）"));
            return;
        }

        // 2) 傷んでいる → 修理
        if (current.ratio() < 1.0) {
            int cost = adjustedCost(player, nexus, fortify.repairCost(block, config.repairCostPerHp()));
            if (cost <= 0) {
                return;
            }
            if (!shards.take(player, cost)) {
                sendInsufficient(player, cost);
                return;
            }
            double healed = fortify.repair(block, current.maxHealth());
            contributions.record(nexus.id(), player.getUniqueId(),
                    ContributionTracker.Kind.REPAIR, healed / 10.0);
            player.sendMessage(Text.good(current.tier().displayName()
                    + " を修理した。（-" + cost + " Shard）"));
            return;
        }

        // 3) 無傷 → 次のティアへ
        BlockTier next = current.tier().next();
        if (next == null) {
            player.sendMessage(Text.warn("すでに最高ティア（" + current.tier().displayName() + "）だ。"));
            return;
        }
        int cost = adjustedCost(player, nexus, next.upgradeCost());
        if (!shards.take(player, cost)) {
            sendInsufficient(player, cost);
            return;
        }
        fortify.upgrade(block);
        contributions.record(nexus.id(), player.getUniqueId(), ContributionTracker.Kind.FORTIFY, 1);
        player.sendMessage(Text.good(next.displayName() + " に強化した。（-" + cost + " Shard）"));
    }

    private void handleRepairKit(Player player, Block block, ItemStack held) {
        FortifyService.Fortified f = fortify.get(block);
        if (f == null || !f.isManaged()) {
            player.sendMessage(Text.error("強化ブロックにのみ使える。"));
            return;
        }
        if (f.ratio() >= 1.0) {
            player.sendMessage(Text.warn("このブロックは無傷だ。"));
            return;
        }
        double amount = f.maxHealth() * perks.repairKitMultiplier(player.getUniqueId());
        double healed = fortify.repair(block, amount);

        Nexus nexus = nexuses.nearest(block.getLocation());
        if (nexus != null) {
            contributions.record(nexus.id(), player.getUniqueId(),
                    ContributionTracker.Kind.REPAIR, healed / 10.0);
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.setAmount(held.getAmount() - 1);
        }
        player.sendMessage(Text.good("補修キットで修復した。"));
    }

    private void handleTurretInteract(Player player, Block block,
                                      TurretService.Turret turret, ItemStack held) {
        TurretType type = turret.type();
        if (held != null && held.getType() == type.ammo()) {
            int loaded = turrets.loadAmmo(block, held.getAmount());
            if (loaded <= 0) {
                player.sendMessage(Text.warn("すでに満載だ。"));
                return;
            }
            if (player.getGameMode() != GameMode.CREATIVE) {
                held.setAmount(held.getAmount() - loaded);
            }
            player.sendMessage(Text.good(type.displayName() + " に "
                    + loaded + " 発 装填した。（" + turret.ammo() + " / " + type.maxAmmo() + "）"));
            return;
        }
        player.sendMessage(Text.info(type.displayName()
                + " — 残弾 " + turret.ammo() + " / " + type.maxAmmo()
                + "（弾薬: " + type.ammo().name() + "）"));
    }

    private void showBlockInfo(Player player, Block block) {
        FortifyService.Fortified f = fortify.get(block);
        if (f == null || !f.isManaged()) {
            double implicit = fortify.implicitHealth(block.getType());
            player.sendMessage(Text.info("未強化ブロック — 推定耐久 "
                    + (implicit <= 0 ? "破壊不能" : String.valueOf((int) implicit))));
            return;
        }
        player.sendMessage(Text.info(f.tier().displayName() + " — 耐久 "
                + (int) f.health() + " / " + (int) f.maxHealth() + "  "
                + Text.bar(f.ratio(), 12)));
    }

    // ---------- 設置・破壊 ----------

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!InvasionItems.isTurret(item)) {
            return;
        }
        TurretType type = TurretType.byItemId(InvasionItems.idOf(item));
        if (type == null) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();

        Nexus nexus = nexuses.nearest(block.getLocation());
        if (nexus == null) {
            event.setCancelled(true);
            player.sendMessage(Text.error("タレットはネクサスの防衛範囲内にのみ設置できる。"));
            return;
        }
        turrets.place(block, type, player);
        contributions.record(nexus.id(), player.getUniqueId(), ContributionTracker.Kind.TURRET, 1);
        player.sendMessage(Text.good(type.displayName() + " を設置した。"
                + type.ammo().name() + " を右クリックで装填すること。"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        TurretService.Turret turret = turrets.remove(block);
        if (turret != null) {
            event.setDropItems(false);
            if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5),
                        InvasionItems.turret(turret.type().itemId(), 1));
                if (turret.ammo() > 0) {
                    block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5),
                            new ItemStack(turret.type().ammo(), Math.min(64, turret.ammo())));
                }
            }
            event.getPlayer().playSound(block.getLocation(), Sound.BLOCK_ANVIL_DESTROY, 0.6f, 1.2f);
        }
        // 強化情報は必ず消す。残すと同じ座標の別ブロックが誤って強化扱いになる。
        fortify.forget(block);
    }

    // ---------- ヘルパ ----------

    private boolean isFortifiable(Block block) {
        Material type = block.getType();
        if (type.isAir() || !type.isSolid() || !type.isBlock()) {
            return false;
        }
        // 機能ブロックを壁材に変えてしまう事故を防ぐ
        return switch (type) {
            case CHEST, TRAPPED_CHEST, BARREL, FURNACE, BLAST_FURNACE, SMOKER, HOPPER,
                 DISPENSER, DROPPER, BEACON, ENCHANTING_TABLE, ANVIL, LODESTONE,
                 RESPAWN_ANCHOR, SPAWNER, END_PORTAL_FRAME, BEDROCK -> false;
            default -> true;
        };
    }

    private int adjustedCost(Player player, Nexus nexus, int base) {
        int discounted = nexuses.discounted(nexus, base);
        return Math.max(1, (int) Math.round(discounted * perks.costMultiplier(player.getUniqueId())));
    }

    private void sendInsufficient(Player player, int cost) {
        player.sendMessage(Text.error("Shard が足りない（必要 " + cost
                + " / 所持 " + shards.count(player) + "）"));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }
}
