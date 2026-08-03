package work.maiotanet.invasioncore.listener;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.defense.FortifyService;
import work.maiotanet.invasioncore.economy.ContributionTracker;
import work.maiotanet.invasioncore.economy.ShardService;
import work.maiotanet.invasioncore.heat.HeatService;
import work.maiotanet.invasioncore.item.InvasionItems;
import work.maiotanet.invasioncore.mob.Invader;
import work.maiotanet.invasioncore.mob.InvaderAiService;
import work.maiotanet.invasioncore.mob.InvaderRegistry;
import work.maiotanet.invasioncore.mob.InvaderSpawner;
import work.maiotanet.invasioncore.mob.InvaderType;
import work.maiotanet.invasioncore.mob.MutationTier;
import work.maiotanet.invasioncore.nexus.Nexus;
import work.maiotanet.invasioncore.nexus.NexusManager;
import work.maiotanet.invasioncore.perk.PerkService;
import work.maiotanet.invasioncore.stage.GameStageService;
import work.maiotanet.invasioncore.util.Keys;
import work.maiotanet.invasioncore.wave.WaveEngine;
import work.maiotanet.invasioncore.wave.WaveModifier;
import work.maiotanet.invasioncore.world.BreachLog;

import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 侵略Mobの被弾・撃破処理。
 * 貢献度の記録、Shard のドロップ、変調「増殖」の分裂をここで扱う。
 */
public final class InvaderListener implements Listener {

    private final InvasionConfig config;
    private final NexusManager nexuses;
    private final InvaderRegistry registry;
    private final InvaderSpawner spawner;
    private final InvaderAiService ai;
    private final FortifyService fortify;
    private final BreachLog breachLog;
    private final ShardService shards;
    private final PerkService perks;
    private final ContributionTracker contributions;
    private final GameStageService stages;
    private final HeatService heat;
    private final WaveEngine waves;

    public InvaderListener(InvasionConfig config, NexusManager nexuses, InvaderRegistry registry,
                           InvaderSpawner spawner, InvaderAiService ai, FortifyService fortify,
                           BreachLog breachLog, ShardService shards,
                           PerkService perks, ContributionTracker contributions,
                           GameStageService stages, HeatService heat, WaveEngine waves) {
        this.config = config;
        this.nexuses = nexuses;
        this.registry = registry;
        this.spawner = spawner;
        this.ai = ai;
        this.fortify = fortify;
        this.breachLog = breachLog;
        this.shards = shards;
        this.perks = perks;
        this.contributions = contributions;
        this.stages = stages;
        this.heat = heat;
        this.waves = waves;
    }

    // ---------- ダメージ ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = resolvePlayer(event.getDamager());
        Invader victim = registry.get(event.getEntity());

        // プレイヤー → 侵略者
        if (attacker != null && victim != null) {
            double multiplier = perks.outgoingDamageMultiplier(attacker.getUniqueId());
            event.setDamage(event.getDamage() * multiplier);
            contributions.record(victim.nexusId(), attacker.getUniqueId(),
                    ContributionTracker.Kind.DAMAGE, event.getFinalDamage());
            heat.add(attacker.getLocation(), config.heatPerShot() * 0.5);
            return;
        }

        // 侵略者 → プレイヤー
        Invader source = registry.get(event.getDamager());
        if (source == null && event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Entity shooter) {
            source = registry.get(shooter);
        }
        if (source != null && event.getEntity() instanceof Player defender) {
            double multiplier = perks.incomingDamageMultiplier(defender.getUniqueId());
            event.setDamage(event.getDamage() * multiplier);
            contributions.record(source.nexusId(), defender.getUniqueId(),
                    ContributionTracker.Kind.TANKED, event.getFinalDamage());
        }
    }

    /** 侵略Mobがネクサス以外に気を取られすぎないよう、ターゲットの寿命を制御する。 */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        Invader invader = registry.get(event.getEntity());
        if (invader == null) {
            return;
        }
        // 動物やビレジャーを追いかけ回すのは侵攻として不自然なので切る
        if (event.getTarget() != null && !(event.getTarget() instanceof Player)) {
            event.setCancelled(true);
        }
    }

    /**
     * 爆導体の爆発でブロックを直接消さず、強化ティアのHPを通したダメージに変換する。
     *
     * <p>素の爆発に任せると鋼鉄の壁も一撃で消え、ブロック強化という仕組み自体が無意味になる。
     * ここを通すことで「爆導体は低ティアの壁を崩すが、上位ティアには通らない」が成立し、
     * 壁を上げる動機が保たれる。破壊は BreachLog にも記録され、復元コマンドの対象になる。</p>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInvaderExplode(EntityExplodeEvent event) {
        Invader invader = registry.get(event.getEntity());
        if (invader == null) {
            return;
        }
        List<Block> affected = new ArrayList<>(event.blockList());
        // 直接破壊は全て取り消す。エンティティへのダメージはそのまま残す。
        event.blockList().clear();

        Nexus nexus = nexuses.byId(invader.nexusId());
        if (nexus == null) {
            return;
        }
        double power = 400.0 * invader.tier().damageMultiplier();
        Location center = nexus.center();
        double breachRadiusSq = config.nexusBreachRadius() * (double) config.nexusBreachRadius();

        for (Block block : affected) {
            if (nexus.isAt(block.getLocation())) {
                continue;
            }
            if (center == null || !center.getWorld().equals(block.getWorld())
                    || center.distanceSquared(block.getLocation()) > breachRadiusSq) {
                continue;
            }
            if (fortify.damage(block, power)) {
                breachLog.record(nexus.id(), block);
            }
        }
    }

    /**
     * 稼働中ネクサスの周囲では、バニラの自然湧きを止める。
     *
     * <p>止めないと、侵略者と無関係なゾンビが混在して「今どれを倒すべきか」が分からなくなる。
     * 湧き数の管理もウェーブ側に一本化できるので、負荷面でも有利。</p>
     */
    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!config.suppressVanillaSpawns()) {
            return;
        }
        SpawnReason reason = event.getSpawnReason();
        if (reason != SpawnReason.NATURAL && reason != SpawnReason.PATROL
                && reason != SpawnReason.VILLAGE_INVASION && reason != SpawnReason.REINFORCEMENTS) {
            return;
        }
        if (!(event.getEntity() instanceof Monster)) {
            return;
        }
        Nexus nexus = nexuses.nearest(event.getLocation());
        if (nexus != null && nexus.isActive()) {
            event.setCancelled(true);
        }
    }

    // ---------- 撃破 ----------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        Invader invader = registry.get(event.getEntity());
        if (invader == null) {
            return;
        }
        registry.unregister(event.getEntity().getUniqueId());
        ai.removePlaced(invader);

        // バニラ由来のドロップは侵攻の報酬体系と噛み合わないので消す
        event.getDrops().clear();
        event.setDroppedExp(Math.max(event.getDroppedExp(), 5));

        Nexus nexus = nexuses.byId(invader.nexusId());
        Location loc = event.getEntity().getLocation();

        int shardAmount = event.getEntity().getPersistentDataContainer()
                .getOrDefault(Keys.INVADER_SHARD, PersistentDataType.INTEGER, 1);

        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            shardAmount = (int) Math.round(shardAmount * perks.shardMultiplier(killer.getUniqueId()));
            perks.recordKill(killer.getUniqueId());
            if (nexus != null) {
                contributions.record(nexus.id(), killer.getUniqueId(), ContributionTracker.Kind.KILL, 1);
            }
            heat.add(loc, config.heatPerKill());
        }
        if (shardAmount > 0) {
            loc.getWorld().dropItemNaturally(loc, InvasionItems.shard(shardAmount));
        }

        if (nexus == null) {
            return;
        }

        // 変調「増殖」：撃破した個体が2体に分かれる
        if (WaveModifier.isSplit(nexus.activeModifier())
                && invader.type() != InvaderType.BOSS
                && invader.tier() != MutationTier.RADIATED) {
            int stage = stages.computeCurrent(nexus);
            for (int i = 0; i < 2; i++) {
                spawner.spawnAt(nexus, InvaderType.RUNNER, MutationTier.NORMAL,
                        Math.max(1, stage / 2), invader.wave(), loc);
            }
        }

        waves.onInvaderDeath(nexus);
    }

    private Player resolvePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
