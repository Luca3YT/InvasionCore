package work.maiotanet.invasioncore.mob;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.defense.FortifyService;
import work.maiotanet.invasioncore.nexus.Nexus;
import work.maiotanet.invasioncore.stage.GameStageService;
import work.maiotanet.invasioncore.world.BreachLog;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ボスの固有スキル。
 *
 * <p>ボスごとに「防衛側に何を要求するか」を変えている。
 * 鉄槌は壁の厚み、腐敗の女王は継続火力、炎心は屋根、虚空の使者は隊列維持を問う。
 * 同じ構成の拠点であらゆるボスに勝てないようにするのが狙い。</p>
 */
public final class BossAbilities {

    private final InvasionConfig config;
    private final InvaderRegistry registry;
    private final InvaderSpawner spawner;
    private final GameStageService stages;
    private final FortifyService fortify;
    private final BreachLog breachLog;

    public BossAbilities(InvasionConfig config, InvaderRegistry registry, InvaderSpawner spawner,
                         GameStageService stages, FortifyService fortify, BreachLog breachLog) {
        this.config = config;
        this.registry = registry;
        this.spawner = spawner;
        this.stages = stages;
        this.fortify = fortify;
        this.breachLog = breachLog;
    }

    /**
     * スキルを1回発動する。
     *
     * @return 次に発動可能になるまでの tick
     */
    public int execute(Invader invader, Nexus nexus) {
        BossType boss = invader.bossType();
        if (boss == null) {
            boss = BossType.WARLORD;
        }
        int stage = stages.computeCurrent(nexus);

        switch (boss) {
            case WARLORD -> warlord(invader, nexus, stage);
            case SIEGE_TITAN -> siegeTitan(invader, nexus);
            case BLIGHT_QUEEN -> blightQueen(invader, nexus, stage);
            case EMBERHEART -> emberheart(invader, nexus);
            case VOID_HERALD -> voidHerald(invader, nexus);
        }
        return boss.abilityCooldown();
    }

    // ---------- 侵略主 ----------

    private void warlord(Invader invader, Nexus nexus, int stage) {
        Mob boss = invader.entity();
        World world = boss.getWorld();

        switch (ThreadLocalRandom.current().nextInt(3)) {
            case 0 -> {
                world.playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.4f, 0.7f);
                for (int i = 0; i < 4; i++) {
                    Location spot = scatter(boss.getLocation(), 3.0);
                    spawner.spawnAt(nexus, InvaderType.GRUNT, MutationTier.roll(stage),
                            stage, invader.wave(), spot);
                }
            }
            case 1 -> {
                world.playSound(boss.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.6f, 1.0f);
                world.spawnParticle(Particle.SONIC_BOOM, boss.getLocation().add(0, 1, 0), 3);
                for (Player p : nearbyPlayers(boss, 10.0)) {
                    Vector push = away(boss.getLocation(), p.getLocation()).multiply(1.1).setY(0.45);
                    p.setVelocity(push);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
                }
            }
            default -> {
                // 鼓舞：周囲の味方を加速させてから自身も突進する
                world.playSound(boss.getLocation(), Sound.EVENT_RAID_HORN, 1.4f, 1.2f);
                for (Invader ally : registry.forNexus(invader.nexusId())) {
                    if (ally == invader || !ally.isValid()) {
                        continue;
                    }
                    if (ally.entity().getLocation().distanceSquared(boss.getLocation()) > 256.0) {
                        continue;
                    }
                    ally.entity().addPotionEffect(new PotionEffect(
                            PotionEffectType.SPEED, 200, 1, false, false, false));
                }
                dash(boss, nexus, 1.3);
            }
        }
    }

    // ---------- 鉄槌 ----------

    /**
     * 叩きつけ。着弾点を中心に周囲の壁をまとめて削る。
     * 一枚壁では止まらないので、厚みか高さで距離を稼ぐ設計が要求される。
     */
    private void siegeTitan(Invader invader, Nexus nexus) {
        Mob boss = invader.entity();
        World world = boss.getWorld();
        Location center = boss.getLocation();

        world.playSound(center, Sound.ENTITY_IRON_GOLEM_DAMAGE, 2.0f, 0.5f);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.6f);
        world.spawnParticle(Particle.EXPLOSION, center, 6, 1.5, 0.5, 1.5, 0.0);

        double power = 900.0 * invader.tier().damageMultiplier();
        int radius = 3;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block block = center.getBlock().getRelative(dx, dy, dz);
                    if (!isBreakable(nexus, block)) {
                        continue;
                    }
                    if (fortify.damage(block, power)) {
                        breachLog.record(nexus.id(), block);
                    }
                }
            }
        }
        for (Player p : nearbyPlayers(boss, 6.0)) {
            p.damage(8.0, boss);
            p.setVelocity(away(center, p.getLocation()).multiply(0.8).setY(0.6));
        }
    }

    // ---------- 腐敗の女王 ----------

    private void blightQueen(Invader invader, Nexus nexus, int stage) {
        Mob boss = invader.entity();
        World world = boss.getWorld();

        if (ThreadLocalRandom.current().nextBoolean()) {
            // 毒霧
            world.playSound(boss.getLocation(), Sound.ENTITY_SPIDER_HURT, 1.6f, 0.6f);
            world.spawnParticle(Particle.SNEEZE, boss.getLocation().add(0, 1, 0),
                    60, 4.0, 1.5, 4.0, 0.02);
            for (Player p : nearbyPlayers(boss, 9.0)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 120, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 120, 0));
            }
        } else {
            // 産卵
            world.playSound(boss.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1.6f, 1.4f);
            for (int i = 0; i < 5; i++) {
                spawner.spawnAt(nexus, InvaderType.CRAWLER, MutationTier.NORMAL,
                        Math.max(1, stage / 2), invader.wave(), scatter(boss.getLocation(), 3.5));
            }
        }
        // 自己回復。DPSが足りないと押し切れない相手にする。
        // 現在HP基準だと瀕死時にほとんど回復せず脅威にならないので、最大HP基準にする。
        AttributeInstance max = boss.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            boss.setHealth(Math.min(max.getValue(), boss.getHealth() + max.getValue() * 0.05));
        }
        world.spawnParticle(Particle.HEART, boss.getLocation().add(0, 1.5, 0), 4, 0.5, 0.5, 0.5, 0.0);
    }

    // ---------- 炎心 ----------

    /** 上空から火炎弾を降らせる。屋根がないと拠点内部が焼かれる。 */
    private void emberheart(Invader invader, Nexus nexus) {
        Mob boss = invader.entity();
        World world = boss.getWorld();
        world.playSound(boss.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 2.0f, 0.7f);

        List<Player> targets = nearbyPlayers(boss, 26.0);
        Location aim = targets.isEmpty()
                ? nexus.center()
                : targets.get(ThreadLocalRandom.current().nextInt(targets.size())).getLocation();
        if (aim == null) {
            return;
        }
        for (int i = 0; i < 6; i++) {
            Location spot = scatter(aim, 4.0);
            Vector dir = spot.toVector().subtract(boss.getEyeLocation().toVector());
            if (dir.lengthSquared() < 1.0e-4) {
                continue;
            }
            SmallFireball ball = boss.launchProjectile(SmallFireball.class, dir.normalize().multiply(1.2));
            ball.setIsIncendiary(true);
            ball.setYield(0f);
        }
    }

    // ---------- 虚空の使者 ----------

    /** タレットの裏へ跳び、プレイヤーを引き寄せて隊列を崩す。 */
    private void voidHerald(Invader invader, Nexus nexus) {
        Mob boss = invader.entity();
        World world = boss.getWorld();
        Location center = nexus.center();
        if (center == null) {
            return;
        }

        if (ThreadLocalRandom.current().nextBoolean()) {
            // ネクサス寄りへ瞬間移動
            Location target = center.clone().add(
                    ThreadLocalRandom.current().nextDouble(-4, 4), 0,
                    ThreadLocalRandom.current().nextDouble(-4, 4));
            target.setY(world.getHighestBlockYAt(target) + 1);
            world.spawnParticle(Particle.PORTAL, boss.getLocation(), 40, 0.6, 1.0, 0.6, 0.4);
            world.playSound(boss.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.6f, 0.8f);
            boss.teleport(target);
            world.spawnParticle(Particle.PORTAL, target, 40, 0.6, 1.0, 0.6, 0.4);
        } else {
            // 引き寄せ
            world.playSound(boss.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 1.8f, 0.7f);
            for (Player p : nearbyPlayers(boss, 16.0)) {
                Vector pull = boss.getLocation().toVector()
                        .subtract(p.getLocation().toVector());
                if (pull.lengthSquared() < 1.0e-4) {
                    continue;
                }
                p.setVelocity(pull.normalize().multiply(1.4).setY(0.35));
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0, true, false, false));
            }
            world.spawnParticle(Particle.REVERSE_PORTAL, boss.getLocation().add(0, 1, 0),
                    50, 2.0, 1.0, 2.0, 0.1);
        }
    }

    // ---------- 共通部品 ----------

    private void dash(Mob boss, Nexus nexus, double power) {
        Location center = nexus.center();
        if (center == null) {
            return;
        }
        Vector dash = center.toVector().subtract(boss.getLocation().toVector());
        if (dash.lengthSquared() < 1.0e-4) {
            return;
        }
        boss.setVelocity(dash.normalize().multiply(power).setY(0.25));
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_CELEBRATE, 1.2f, 0.8f);
    }

    private boolean isBreakable(Nexus nexus, Block block) {
        if (block.getType().isAir() || nexus.isAt(block.getLocation())) {
            return false;
        }
        if (fortify.implicitHealth(block.getType()) <= 0 && !fortify.isFortified(block)) {
            return false;
        }
        Location center = nexus.center();
        if (center == null || !center.getWorld().equals(block.getWorld())) {
            return false;
        }
        double r = config.nexusBreachRadius();
        return center.distanceSquared(block.getLocation()) <= r * r;
    }

    private List<Player> nearbyPlayers(Mob boss, double range) {
        List<Player> out = new ArrayList<>();
        double sq = range * range;
        for (Player p : boss.getWorld().getPlayers()) {
            if (p.isDead() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR
                    || p.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                continue;
            }
            if (p.getLocation().distanceSquared(boss.getLocation()) <= sq) {
                out.add(p);
            }
        }
        return out;
    }

    private Location scatter(Location origin, double radius) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        return origin.clone().add(
                rng.nextDouble(-radius, radius), 0, rng.nextDouble(-radius, radius));
    }

    private Vector away(Location from, Location to) {
        Vector v = to.toVector().subtract(from.toVector());
        return v.lengthSquared() < 1.0e-4 ? new Vector(0, 1, 0) : v.normalize();
    }
}
