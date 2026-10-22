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
import work.maiotanet.invasioncore.defense.TrapService;
import work.maiotanet.invasioncore.defense.TurretService;
import work.maiotanet.invasioncore.defense.TurretType;
import work.maiotanet.invasioncore.economy.ContributionTracker;
import work.maiotanet.invasioncore.economy.ShardService;
import work.maiotanet.invasioncore.item.InvasionItems;
import work.maiotanet.invasioncore.nexus.Nexus;
import work.maiotanet.invasioncore.nexus.NexusManager;
import work.maiotanet.invasioncore.nexus.NexusUpgrade;
import work.maiotanet.invasioncore.perk.PerkService;
import work.maiotanet.invasioncore.pregame.PreGameService;
import work.maiotanet.invasioncore.util.Sounds;
import work.maiotanet.invasioncore.util.Text;

/**
 * 防衛設備の操作。強化ハンマー・補修キット・タレットの設置と装填を扱う。
 */
public final class DefenseListener implements Listener {

    private final InvasionConfig config;
    private final NexusManager nexuses;
    private final FortifyService fortify;
    private final TurretService turrets;
    private final TrapService traps;
    private final ShardService shards;
    private final PerkService perks;
    private final ContributionTracker contributions;
    private final PreGameService pregame;

    public DefenseListener(InvasionConfig config, NexusManager nexuses, FortifyService fortify,
                           TurretService turrets, TrapService traps, ShardService shards,
                           PerkService perks, ContributionTracker contributions, PreGameService pregame) {
        this.pregame = pregame;
        this.config = config;
        this.nexuses = nexuses;
        this.fortify = fortify;
        this.turrets = turrets;
        this.traps = traps;
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

        boolean hammer = InvasionItems.is(held, InvasionItems.HAMMER);
        if ((hammer || InvasionItems.is(held, InvasionItems.REPAIR_KIT)) && pregame.isRestricted(player)) {
            // 強化・修理はブロックを置き換えるので、開始前の「壊す・置く」の禁止と揃える
            event.setCancelled(true);
            pregame.notice(player, "ゲーム開始まではブロックを強化・修理できません。");
            return;
        }
        if (hammer) {
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
            Text.actionBarError(player, "ネクサスの防衛範囲内でのみ強化できる。");
            Sounds.fail(player);
            return;
        }
        if (nexuses.at(block.getLocation()) != null) {
            Text.actionBarWarn(player, "ネクサス本体は強化できない。");
            Sounds.fail(player);
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
                Text.actionBarError(player, "このブロックは強化できない。");
                Sounds.fail(player);
                return;
            }
            int cost = adjustedCost(player, nexus, BlockTier.first().upgradeCost());
            if (!shards.takeWithPool(player, nexus, cost, "強化した")) {
                sendInsufficient(player, nexus, cost);
                return;
            }
            fortify.fortify(block, perks.fortifyHealthMultiplier(player.getUniqueId()));
            contributions.record(nexus.id(), player.getUniqueId(), ContributionTracker.Kind.FORTIFY, 1);
            Text.actionBarGood(player, "木枠に強化した。（-" + cost + " Shard）");
            return;
        }

        // 2) 傷んでいる → 修理
        if (current.ratio() < 1.0) {
            int cost = adjustedCost(player, nexus, fortify.repairCost(block, config.repairCostPerHp()));
            if (cost <= 0) {
                return;
            }
            if (!shards.takeWithPool(player, nexus, cost, "修理した")) {
                sendInsufficient(player, nexus, cost);
                return;
            }
            double healed = fortify.repair(block, current.maxHealth());
            contributions.record(nexus.id(), player.getUniqueId(),
                    ContributionTracker.Kind.REPAIR, healed / 10.0);
            Text.actionBarGood(player, current.tier().displayName()
                    + " を修理した。（-" + cost + " Shard）");
            return;
        }

        // 3) 無傷 → 次のティアへ
        BlockTier next = current.tier().next();
        if (next == null) {
            Text.actionBarWarn(player, "すでに最高ティア（" + current.tier().displayName() + "）だ。");
            Sounds.fail(player);
            return;
        }
        int cost = adjustedCost(player, nexus, next.upgradeCost());
        if (!shards.takeWithPool(player, nexus, cost, "強化した")) {
            sendInsufficient(player, nexus, cost);
            return;
        }
        fortify.upgrade(block, perks.fortifyHealthMultiplier(player.getUniqueId()));
        contributions.record(nexus.id(), player.getUniqueId(), ContributionTracker.Kind.FORTIFY, 1);
        Text.actionBarGood(player, next.displayName() + " に強化した。（-" + cost + " Shard）");
    }

    private void handleRepairKit(Player player, Block block, ItemStack held) {
        FortifyService.Fortified f = fortify.get(block);
        if (f == null || !f.isManaged()) {
            Text.actionBarError(player, "強化ブロックにのみ使える。");
            Sounds.fail(player);
            return;
        }
        if (f.ratio() >= 1.0) {
            Text.actionBarWarn(player, "このブロックは無傷だ。");
            Sounds.fail(player);
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
        Text.actionBarGood(player, "補修キットで修復した。");
    }

    private void handleTurretInteract(Player player, Block block,
                                      TurretService.Turret turret, ItemStack held) {
        TurretType type = turret.type();
        if (held != null && held.getType() == type.ammo()) {
            int loaded = turrets.loadAmmo(block, held.getAmount());
            if (loaded <= 0) {
                Text.actionBarWarn(player, "すでに満載だ。");
                Sounds.fail(player);
                return;
            }
            if (player.getGameMode() != GameMode.CREATIVE) {
                held.setAmount(held.getAmount() - loaded);
            }
            Text.actionBarGood(player, type.displayName() + " に "
                    + loaded + " 発 装填した。（" + turret.ammo() + " / " + type.maxAmmo() + "）");
            return;
        }
        Sounds.click(player);
        Text.actionBarInfo(player, type.displayName()
                + " — 残弾 " + turret.ammo() + " / " + type.maxAmmo()
                + "（弾薬: " + type.ammo().name() + "）");
    }

    private void showBlockInfo(Player player, Block block) {
        Sounds.click(player);
        FortifyService.Fortified f = fortify.get(block);
        if (f == null || !f.isManaged()) {
            double implicit = fortify.implicitHealth(block.getType());
            Text.actionBarInfo(player, "未強化ブロック — 推定耐久 "
                    + (implicit <= 0 ? "破壊不能" : String.valueOf((int) implicit)));
            return;
        }
        Text.actionBarInfo(player, f.tier().displayName() + " — 耐久 "
                + (int) f.health() + " / " + (int) f.maxHealth() + "  "
                + Text.bar(f.ratio(), 12));
    }

    // ---------- 設置・破壊 ----------

    /**
     * 拠点全体に置けるタレットの数。並べるだけで血の月が終わる拠点にならないよう上限を設け、
     * ネクサスの「砲台増設」で広げられるようにする。
     */
    private int turretLimit(Nexus nexus) {
        return Math.min(config.turretLimitMax(), config.turretLimitBase()
                + config.turretLimitPerLevel() * nexus.upgradeLevel(NexusUpgrade.TURRET_SLOTS));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();

        if (InvasionItems.isTurret(item)) {
            TurretType type = TurretType.byItemId(InvasionItems.idOf(item));
            if (type == null) {
                return;
            }
            Nexus nexus = nexuses.nearest(block.getLocation());
            if (nexus == null) {
                event.setCancelled(true);
                Text.actionBarError(player, "タレットはネクサスの防衛範囲内にのみ設置できる。");
                Sounds.fail(player);
                return;
            }
            int limit = turretLimit(nexus);
            if (turrets.count() >= limit) {
                event.setCancelled(true);
                Text.actionBarError(player, "タレットはあと置けない（" + turrets.count() + " / " + limit
                        + "）。ネクサスの「砲台増設」で増やせる。");
                Sounds.fail(player);
                return;
            }
            turrets.place(block, type, player);
            contributions.record(nexus.id(), player.getUniqueId(), ContributionTracker.Kind.TURRET, 1);
            Text.actionBarGood(player, type.displayName() + " を設置した。"
                    + type.ammo().name() + " を右クリックで装填すること。");
            return;
        }

        if (InvasionItems.isTrap(item)) {
            TrapService.TrapType type = TrapService.TrapType.byItemId(InvasionItems.idOf(item));
            if (type == null) {
                return;
            }
            Nexus nexus = nexuses.nearest(block.getLocation());
            if (nexus == null) {
                event.setCancelled(true);
                Text.actionBarError(player, "トラップはネクサスの防衛範囲内にのみ設置できる。");
                Sounds.fail(player);
                return;
            }
            traps.place(block, type, player);
            contributions.record(nexus.id(), player.getUniqueId(), ContributionTracker.Kind.TURRET, 1);
            Text.actionBarGood(player, type.displayName() + " を設置した。");
        }
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

        TrapService.TrapType trapType = traps.removeTrap(block);
        if (trapType != null) {
            event.setDropItems(false);
            if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5),
                        InvasionItems.trap(trapType.itemId(), 1));
            }
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

    private void sendInsufficient(Player player, Nexus nexus, int cost) {
        Text.actionBarError(player, "Shard が足りない（必要 " + cost
                + " / 所持 " + shards.count(player)
                + " / ネクサス内 " + (nexus == null ? 0 : nexus.sharedShards()) + "）");
        Sounds.fail(player);
    }
}
