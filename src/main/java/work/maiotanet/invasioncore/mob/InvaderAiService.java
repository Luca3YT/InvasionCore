package work.maiotanet.invasioncore.mob;

import com.destroystokyo.paper.entity.Pathfinder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import work.maiotanet.invasioncore.util.Keys;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.defense.FortifyService;
import work.maiotanet.invasioncore.heat.HeatService;
import work.maiotanet.invasioncore.nexus.Nexus;
import work.maiotanet.invasioncore.nexus.NexusCombatSink;
import work.maiotanet.invasioncore.nexus.NexusManager;
import work.maiotanet.invasioncore.stage.GameStageService;
import work.maiotanet.invasioncore.world.BreachLog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 侵略Mobの独自AI。
 *
 * <p>バニラのMobは「近くのプレイヤーを追う」だけで、防衛対象という概念を持たない。
 * 本サービスは全ての侵略Mobを毎tickではなくバケット分割で巡回し、
 * ネクサスへの経路確保・障害物破壊・役割別の特殊行動を上書きする。</p>
 *
 * <p>負荷対策として、Mob は UUID のハッシュで N 個のバケットに分割され、
 * 1体あたりの AI 更新は N tick に1回になる。100体いても1tickあたり25体しか処理しない。</p>
 */
public final class InvaderAiService {

    private final InvasionConfig config;
    private final NexusManager nexuses;
    private final InvaderRegistry registry;
    private final FortifyService fortify;
    private final BreachLog breachLog;
    private final InvaderSpawner spawner;
    private final GameStageService stages;
    private final HeatService heat;
    private final BossAbilities bossAbilities;

    private NexusCombatSink combatSink;

    private long tickCounter;

    public InvaderAiService(InvasionConfig config, NexusManager nexuses, InvaderRegistry registry,
                            FortifyService fortify, BreachLog breachLog, InvaderSpawner spawner,
                            GameStageService stages, HeatService heat, BossAbilities bossAbilities) {
        this.config = config;
        this.nexuses = nexuses;
        this.registry = registry;
        this.fortify = fortify;
        this.breachLog = breachLog;
        this.spawner = spawner;
        this.stages = stages;
        this.heat = heat;
        this.bossAbilities = bossAbilities;
    }

    public void combatSink(NexusCombatSink combatSink) {
        this.combatSink = combatSink;
    }

    // ---------- メインループ ----------

    /** 毎tick呼ばれる。実処理は担当バケットのMobのみ。 */
    public void tick() {
        tickCounter++;

        if (tickCounter % 40 == 0) {
            registry.purgeInvalid();
        }

        int buckets = config.aiBuckets();
        int currentBucket = (int) (tickCounter % buckets);

        for (Invader invader : new ArrayList<>(registry.all())) {
            if (!invader.isValid()) {
                continue;
            }
            int bucket = Math.floorMod(invader.entity().getUniqueId().hashCode(), buckets);
            if (bucket != currentBucket) {
                continue;
            }
            try {
                tickInvader(invader);
            } catch (RuntimeException ex) {
                // 1体の異常で全体を止めない
                invader.entity().getServer().getLogger()
                        .warning("[InvasionCore] AI処理で例外: " + ex);
            }
        }
    }

    private void tickInvader(Invader invader) {
        Mob mob = invader.entity();
        Nexus nexus = nexuses.byId(invader.nexusId());
        if (nexus == null || !nexus.isActive()) {
            // ネクサスが消えた／侵攻が終わった残党は撤収させる
            mob.remove();
            registry.unregister(mob.getUniqueId());
            return;
        }
        Location target = nexus.center();
        if (target == null || target.getWorld() == null
                || !target.getWorld().equals(mob.getWorld())) {
            return;
        }

        InvaderType type = invader.type();
        long now = mob.getWorld().getFullTime();

        // --- 役割別の常時処理 ---
        if (type == InvaderType.SHIELDER) {
            applySupportAura(invader);
        }
        if (type == InvaderType.NECROMANCER) {
            tickNecromancer(invader, now);
        }
        if (type.isStealth()) {
            tickStealth(invader);
        }
        if (type == InvaderType.SCREAMER) {
            tickScreamer(invader, nexus, now);
            return;
        }

        double distSq = mob.getLocation().distanceSquared(target);

        // --- ボススキルは位置に関係なく回す ---
        // ネクサス到達後に止めてしまうと、防衛線を抜かれた瞬間からボスがただの殴り屋になる。
        if (type.isBoss() && now >= invader.abilityReadyTick()) {
            invader.abilityReadyTick(now + bossAbilities.execute(invader, nexus));
        }

        // --- ネクサスに到達していれば殴る ---
        if (distSq <= 9.0) {
            attackNexus(invader, nexus, now);
            return;
        }

        // --- 目の前のプレイヤーは無視できない ---
        Player near = nearestPlayer(mob, config.playerAggroRange());
        if (near != null) {
            mob.setTarget(near);
        } else if (mob.getTarget() != null) {
            mob.setTarget(null);
        }

        if (type.isRanged() && now >= invader.abilityReadyTick()) {
            tryShoot(invader, now);
        }
        if (type.isArtillery()) {
            // 砲撃型は懐に入られると何もできない。射程内へ下がることを最優先にする。
            if (distSq < 100.0) {
                retreatFrom(mob, target);
                return;
            }
            if (now >= invader.abilityReadyTick()) {
                lobBomb(invader, nexus, target, now);
            }
        }
        // --- 飛行型は経路探索を使わず直行する ---
        if (type.isFlying() || (invader.bossType() != null && invader.bossType().isAirborne())) {
            flyToward(mob, target);
            return;
        }

        // --- 通常移動 ---
        Pathfinder pathfinder = mob.getPathfinder();
        boolean pathOk = pathfinder.moveTo(target, 1.0);

        double dist = Math.sqrt(distSq);
        if (dist < invader.bestDistance() - 0.5) {
            invader.bestDistance(dist);
            invader.stuckTicks(0);
            invader.breachTarget(null);
        } else {
            invader.incrementStuck();
        }

        if (!pathOk || invader.stuckTicks() >= config.breachStuckThreshold()) {
            handleObstruction(invader, nexus, target, now);
        }
    }

    // ---------- 障害物への対処 ----------

    private void handleObstruction(Invader invader, Nexus nexus, Location target, long now) {
        InvaderType type = invader.type();

        if (type.isBomber()) {
            // 爆導体は壁を殴らず、壁際で起爆する
            igniteBomber(invader, target);
            return;
        }
        if (type.canBuild() && target.getY() > invader.entity().getLocation().getY() + 1.5) {
            if (buildUpward(invader, nexus)) {
                return;
            }
        }
        if (type.canBurrow()) {
            if (burrowToward(invader, nexus, target)) {
                return;
            }
        }
        if (type.canBreach()) {
            breachToward(invader, nexus, target);
        }
    }

    /**
     * ネクサス方向の障害ブロックを殴る。
     * 破壊範囲はネクサス中心からの半径で制限し、拠点外の建築物を巻き込まない。
     */
    private void breachToward(Invader invader, Nexus nexus, Location target) {
        Block block = invader.breachTarget();
        if (block == null || block.getType().isAir() || !isBreachable(nexus, block)) {
            block = findObstructingBlock(invader, target, nexus);
            invader.breachTarget(block);
        }
        if (block == null) {
            return;
        }

        double power = breachPower(invader);
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        invader.entity().swingMainHand();
        block.getWorld().playSound(center, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.6f, 0.9f);

        if (fortify.damage(block, power)) {
            breachLog.record(nexus.id(), block);
            invader.breachTarget(null);
            invader.stuckTicks(0);
            invader.bestDistance(Double.MAX_VALUE);
        }
    }

    /** 掘削者は地形を直線的に掘り進む。堀や地下壁を無効化する。 */
    private boolean burrowToward(Invader invader, Nexus nexus, Location target) {
        Mob mob = invader.entity();
        Vector dir = target.toVector().subtract(mob.getLocation().toVector());
        if (dir.lengthSquared() < 1.0e-4) {
            return false;
        }
        dir.normalize();

        Location feet = mob.getLocation();
        // 足元・胴・そして進行方向の3点を対象にする
        List<Block> candidates = new ArrayList<>(3);
        candidates.add(feet.clone().add(dir.clone().multiply(1.0)).getBlock());
        candidates.add(feet.clone().add(0, 1, 0).add(dir.clone().multiply(1.0)).getBlock());
        if (target.getY() < feet.getY() - 1.5) {
            candidates.add(feet.clone().add(0, -1, 0).getBlock());
        }

        for (Block block : candidates) {
            if (block.getType().isAir() || !isBreachable(nexus, block)) {
                continue;
            }
            double power = breachPower(invader) * 1.8;
            mob.swingMainHand();
            block.getWorld().spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5),
                    8, 0.3, 0.3, 0.3, 0.0, block.getBlockData());
            if (fortify.damage(block, power)) {
                breachLog.record(nexus.id(), block);
                invader.stuckTicks(0);
            }
            return true;
        }
        return false;
    }

    /**
     * 工兵は足場を組んで高所へ登る。
     * 侵略MODの Pig Engineer 相当で、「高い塔に籠れば安全」を成立させないための役割。
     */
    private boolean buildUpward(Invader invader, Nexus nexus) {
        Mob mob = invader.entity();
        Location feet = mob.getLocation();
        Block at = feet.getBlock();

        if (invader.placedBlocks().size() >= config.engineerMaxBuildHeight()) {
            return false;
        }
        if (!at.getType().isAir() && at.getType() != Material.SCAFFOLDING) {
            return false;
        }
        if (!isWithinBreachRadius(nexus, at.getLocation())) {
            return false;
        }

        at.setType(Material.SCAFFOLDING);
        invader.placedBlocks().push(at.getLocation());
        // 置いた足場に自力で乗る
        mob.setVelocity(new Vector(0.0, 0.42, 0.0));
        mob.getWorld().playSound(feet, Sound.BLOCK_SCAFFOLDING_PLACE, 0.7f, 1.0f);
        invader.stuckTicks(0);
        return true;
    }

    /** 爆導体の起爆。壁とタレットを吹き飛ばす役割。 */
    private void igniteBomber(Invader invader, Location target) {
        Mob mob = invader.entity();
        if (mob instanceof Creeper creeper && !creeper.isIgnited()) {
            double dist = mob.getLocation().distance(target);
            // ネクサスに近いか、目の前が塞がっているときだけ起爆する
            if (dist <= 6.0 || invader.stuckTicks() >= config.breachStuckThreshold()) {
                creeper.ignite();
            }
        }
    }

    // ---------- 特殊行動 ----------

    private void attackNexus(Invader invader, Nexus nexus, long now) {
        if (now < invader.attackReadyTick()) {
            return;
        }
        invader.attackReadyTick(now + 20L);

        Mob mob = invader.entity();
        mob.swingMainHand();

        double damage = config.nexusDamagePerHit()
                * invader.type().damageMultiplier()
                * invader.tier().damageMultiplier();
        if (combatSink != null) {
            combatSink.damageNexus(nexus, damage);
        }

        Location center = nexus.center();
        if (center != null) {
            World w = center.getWorld();
            w.spawnParticle(Particle.CRIT, center, 10, 0.4, 0.4, 0.4, 0.15);
            w.playSound(center, Sound.ENTITY_IRON_GOLEM_DAMAGE, 0.8f, 1.3f);
        }
    }

    private void tryShoot(Invader invader, long now) {
        Mob mob = invader.entity();
        Player target = nearestPlayer(mob, 22.0);
        if (target == null) {
            return;
        }
        invader.abilityReadyTick(now + 50L);
        Vector dir = target.getEyeLocation().toVector()
                .subtract(mob.getEyeLocation().toVector()).normalize().multiply(1.9);
        Arrow arrow = mob.launchProjectile(Arrow.class, dir);
        arrow.setDamage(arrow.getDamage() * invader.tier().damageMultiplier());
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_SKELETON_SHOOT, 1.0f, 1.0f);
    }

    private void applySupportAura(Invader invader) {
        Mob shielder = invader.entity();
        for (Invader other : registry.forNexus(invader.nexusId())) {
            if (other == invader || !other.isValid()) {
                continue;
            }
            if (other.entity().getLocation().distanceSquared(shielder.getLocation()) > 64.0) {
                continue;
            }
            other.entity().addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                    60, 1, false, false, false));
        }
        shielder.getWorld().spawnParticle(Particle.ENCHANT,
                shielder.getLocation().add(0, 1.2, 0), 6, 0.6, 0.6, 0.6, 0.4);
    }

    /**
     * 質量爆弾の投擲。
     *
     * <p>着火済みTNTを放物線で撃ち込む。バニラの爆発に任せず PDC で発射元を刻み、
     * 着弾時は爆導体と同じくブロック強化ティアを通したダメージに変換する。
     * これにより「遠距離から壁を削られるが、上位ティアなら耐えられる」が成立する。</p>
     */
    private void lobBomb(Invader invader, Nexus nexus, Location target, long now) {
        Mob mob = invader.entity();
        double distance = mob.getLocation().distance(target);
        // 近すぎると自爆する。離れている時だけ撃つ。
        if (distance < 10.0 || distance > 45.0) {
            return;
        }
        invader.abilityReadyTick(now + 120L);

        Location muzzle = mob.getEyeLocation().add(0, 0.4, 0);
        TNTPrimed bomb = mob.getWorld().spawn(muzzle, TNTPrimed.class, tnt -> {
            tnt.setFuseTicks(60);
            tnt.setSource(mob);
            tnt.setIsIncendiary(false);
            tnt.getPersistentDataContainer().set(
                    Keys.PROJECTILE_NEXUS, PersistentDataType.STRING, nexus.id().toString());
            tnt.getPersistentDataContainer().set(
                    Keys.PROJECTILE_POWER, PersistentDataType.DOUBLE,
                    520.0 * invader.tier().damageMultiplier());
        });
        bomb.setVelocity(ballisticVelocity(muzzle, target));

        mob.swingMainHand();
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.6f);
        mob.getWorld().spawnParticle(Particle.SMOKE, muzzle, 12, 0.2, 0.2, 0.2, 0.02);
    }

    /**
     * 放物線の初速を求める。
     * 水平距離から所要時間を見積もり、重力で落ちる分を上方向に足す。
     */
    private Vector ballisticVelocity(Location from, Location to) {
        Vector delta = to.toVector().subtract(from.toVector());
        double dy = delta.getY();
        Vector flat = new Vector(delta.getX(), 0, delta.getZ());
        double horizontal = flat.length();
        if (horizontal < 0.01) {
            return new Vector(0, 0.6, 0);
        }
        // TNT の水平初速をこの程度に置くと、45ブロック先でも導火線内に着弾する
        double speed = Math.min(1.35, 0.5 + horizontal * 0.022);
        double time = horizontal / speed;
        double gravity = 0.04;
        double vy = (dy + 0.5 * gravity * time * time) / time;

        Vector v = flat.normalize().multiply(speed);
        v.setY(Math.max(0.15, Math.min(1.6, vy)));
        return v;
    }

    /** 潜伏体は接近するまで不可視。近づいたら姿を現し、以後は見える。 */
    private void tickStealth(Invader invader) {
        if (invader.isRevealed()) {
            return;
        }
        Mob mob = invader.entity();
        Player near = nearestPlayer(mob, 7.0);
        if (near == null) {
            return;
        }
        invader.revealed(true);
        mob.removePotionEffect(PotionEffectType.INVISIBILITY);
        mob.setGlowing(true);
        mob.getWorld().playSound(mob.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.2f, 1.4f);
        mob.getWorld().spawnParticle(Particle.LARGE_SMOKE,
                mob.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.02);
    }

    /**
     * 蘇生者は周囲の味方を回復し、瀕死の個体を全快させる。
     * 最優先で潰さないと前線が崩れなくなる。
     */
    private void tickNecromancer(Invader invader, long now) {
        if (now < invader.abilityReadyTick()) {
            return;
        }
        invader.abilityReadyTick(now + 100L);

        Mob healer = invader.entity();
        int healed = 0;
        for (Invader ally : registry.forNexus(invader.nexusId())) {
            if (ally == invader || !ally.isValid() || healed >= 5) {
                continue;
            }
            if (ally.entity().getLocation().distanceSquared(healer.getLocation()) > 144.0) {
                continue;
            }
            AttributeInstance max = ally.entity().getAttribute(Attribute.MAX_HEALTH);
            if (max == null || ally.entity().getHealth() >= max.getValue()) {
                continue;
            }
            double amount = max.getValue() * 0.25;
            ally.entity().setHealth(Math.min(max.getValue(), ally.entity().getHealth() + amount));
            drawLink(healer.getLocation().add(0, 1.2, 0),
                    ally.entity().getLocation().add(0, 1.0, 0));
            healed++;
        }
        if (healed > 0) {
            healer.getWorld().playSound(healer.getLocation(), Sound.ENTITY_WITCH_DRINK, 1.2f, 0.8f);
        }
    }

    private void drawLink(Location from, Location to) {
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length < 0.1 || length > 16.0) {
            return;
        }
        Vector step = delta.normalize().multiply(0.7);
        Location cursor = from.clone();
        for (int i = 0; i < (int) (length / 0.7); i++) {
            cursor.add(step);
            from.getWorld().spawnParticle(Particle.HEART, cursor, 1, 0, 0, 0, 0);
        }
    }

    /** 叫喚体：プレイヤーを見つけると叫び、追加ホードを呼ぶ。 */
    private void tickScreamer(Invader invader, Nexus nexus, long now) {
        Mob screamer = invader.entity();
        Player target = nearestPlayer(screamer, 20.0);
        if (target == null) {
            Location center = nexus.center();
            if (center != null) {
                screamer.getPathfinder().moveTo(center, 1.0);
            }
            return;
        }
        screamer.getPathfinder().moveTo(target, 1.0);

        if (now < invader.abilityReadyTick()) {
            return;
        }
        invader.abilityReadyTick(now + 300L);

        World world = screamer.getWorld();
        world.playSound(screamer.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 2.0f, 0.6f);
        world.playSound(screamer.getLocation(), Sound.ENTITY_GHAST_SCREAM, 2.0f, 0.5f);
        world.spawnParticle(Particle.SOUL, screamer.getLocation().add(0, 1.5, 0), 30, 1.0, 1.0, 1.0, 0.05);

        // 叫び自体がさらに熱を上げる。連鎖しやすくすることで「引き際」の判断を作る。
        heat.add(screamer.getLocation(), 25.0);

        int stage = stages.computeCurrent(nexus);
        int count = 3 + Math.min(6, stage / 25);
        for (int i = 0; i < count; i++) {
            Location spot = screamer.getLocation().add(
                    ThreadLocalRandom.current().nextDouble(-6, 6), 0,
                    ThreadLocalRandom.current().nextDouble(-6, 6));
            InvaderType type = ThreadLocalRandom.current().nextBoolean()
                    ? InvaderType.GRUNT : InvaderType.RUNNER;
            spawner.spawnAt(nexus, type, MutationTier.roll(stage), stage, invader.wave(), spot);
        }
    }

    // ---------- ヘルパ ----------

    /** 対象から離れる方向へ経路をとる。砲撃型が射程を取り直すために使う。 */
    private void retreatFrom(Mob mob, Location target) {
        Vector away = mob.getLocation().toVector().subtract(target.toVector());
        if (away.lengthSquared() < 1.0e-4) {
            return;
        }
        Location destination = mob.getLocation().clone()
                .add(away.normalize().multiply(14.0));
        mob.getPathfinder().moveTo(destination, 1.1);
    }

    /**
     * 飛行型をネクサスへ直進させる。
     *
     * <p>経路探索を使わず速度を直接与えるため、移動速度の属性ではなくここの係数で速さが決まる。
     * 0.42 だと毎秒8.4ブロックとなり、疾走するプレイヤー（約5.6）より明確に速くて逃げ場がない。</p>
     */
    private void flyToward(Mob mob, Location target) {
        Vector dir = target.toVector().subtract(mob.getLocation().toVector());
        if (dir.lengthSquared() < 1.0e-4) {
            return;
        }
        mob.setVelocity(dir.normalize().multiply(config.flyerSpeed()));
    }

    private double breachPower(Invader invader) {
        AttributeInstance atk = invader.entity().getAttribute(Attribute.ATTACK_DAMAGE);
        double damage = atk == null ? 3.0 : atk.getValue();
        return config.breachPowerBase() + damage * config.breachPowerPerDamage();
    }

    /**
     * Mob の視線方向（ネクサス側）で最初にぶつかる固体ブロックを返す。
     * 足元・胴の2段を見るので、1ブロックの段差でも段差の壁を殴れる。
     */
    private Block findObstructingBlock(Invader invader, Location target, Nexus nexus) {
        Mob mob = invader.entity();
        Vector dir = target.toVector().subtract(mob.getEyeLocation().toVector());
        if (dir.lengthSquared() < 1.0e-4) {
            return null;
        }
        dir.normalize();

        for (double offsetY : new double[]{0.0, -0.9}) {
            Location origin = mob.getEyeLocation().add(0, offsetY, 0);
            for (double d = 0.6; d <= 3.0; d += 0.4) {
                Block block = origin.clone().add(dir.clone().multiply(d)).getBlock();
                if (block.getType().isAir() || !block.getType().isSolid()) {
                    continue;
                }
                if (!isBreachable(nexus, block)) {
                    continue;
                }
                return block;
            }
        }
        return null;
    }

    /** 破壊してよいブロックか。ネクサス本体と範囲外は対象外。 */
    private boolean isBreachable(Nexus nexus, Block block) {
        if (block == null || block.getType().isAir()) {
            return false;
        }
        if (nexus.isAt(block.getLocation())) {
            return false;
        }
        if (fortify.implicitHealth(block.getType()) <= 0 && !fortify.isFortified(block)) {
            // 岩盤など破壊不能ブロック
            return false;
        }
        return isWithinBreachRadius(nexus, block.getLocation());
    }

    private boolean isWithinBreachRadius(Nexus nexus, Location loc) {
        Location center = nexus.center();
        if (center == null || center.getWorld() == null || !center.getWorld().equals(loc.getWorld())) {
            return false;
        }
        double r = config.nexusBreachRadius();
        return center.distanceSquared(loc) <= r * r;
    }

    private Player nearestPlayer(Mob mob, double range) {
        double bestSq = range * range;
        Player best = null;
        for (Player p : mob.getWorld().getPlayers()) {
            if (p.isDead() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR
                    || p.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                continue;
            }
            double d = p.getLocation().distanceSquared(mob.getLocation());
            if (d < bestSq) {
                bestSq = d;
                best = p;
            }
        }
        return best;
    }

    /** 工兵が建てた足場を撤去する。侵攻後にワールドを汚さないため必須。 */
    public void cleanupPlacedBlocks(java.util.UUID nexusId) {
        for (Invader invader : registry.forNexus(nexusId)) {
            removePlaced(invader);
        }
    }

    public void removePlaced(Invader invader) {
        while (!invader.placedBlocks().isEmpty()) {
            Location loc = invader.placedBlocks().pop();
            if (loc.getWorld() == null) {
                continue;
            }
            Block block = loc.getBlock();
            if (block.getType() == Material.SCAFFOLDING) {
                block.setType(Material.AIR);
            }
        }
    }
}
