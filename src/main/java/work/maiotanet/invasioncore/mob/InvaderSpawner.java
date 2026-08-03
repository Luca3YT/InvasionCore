package work.maiotanet.invasioncore.mob;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.nexus.Nexus;
import work.maiotanet.invasioncore.nexus.NexusManager;
import work.maiotanet.invasioncore.util.Keys;
import work.maiotanet.invasioncore.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 侵略Mobの生成。
 * 湧き位置の選定と、種別・変異ティア・GameStage に応じた能力付与を担う。
 */
public final class InvaderSpawner {

    private final InvasionConfig config;
    private final NexusManager nexuses;
    private final InvaderRegistry registry;

    public InvaderSpawner(InvasionConfig config, NexusManager nexuses, InvaderRegistry registry) {
        this.config = config;
        this.nexuses = nexuses;
        this.registry = registry;
    }

    /**
     * ネクサス周辺に1体生成する。湧き場所が見つからない場合は null。
     */
    public Invader spawn(Nexus nexus, InvaderType type, MutationTier tier, int gameStage, int wave) {
        Location origin = nexus.center();
        if (origin == null || origin.getWorld() == null) {
            return null;
        }
        Location spot = findSpawnLocation(nexus, type);
        if (spot == null) {
            return null;
        }
        return spawnAt(nexus, type, tier, gameStage, wave, spot);
    }

    /** 座標を指定して生成する。徘徊ホードや叫喚体の呼び出しで使う。 */
    public Invader spawnAt(Nexus nexus, InvaderType type, MutationTier tier, int gameStage, int wave, Location spot) {
        World world = spot.getWorld();
        if (world == null) {
            return null;
        }
        Entity spawned = world.spawnEntity(spot, type.entityType());
        if (!(spawned instanceof Mob mob)) {
            spawned.remove();
            return null;
        }

        applyStats(mob, type, tier, gameStage);
        applyAppearance(mob, type, tier);
        applyBehaviourTweaks(mob, type);
        // Phantom の setSize など、後段の設定が最大HPを変えることがある。
        // 現在HPが最大を超えたまま残ると setHealth 系で例外になるので最後に揃える。
        AttributeInstance maxHealth = mob.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            mob.setHealth(Math.min(mob.getHealth(), maxHealth.getValue()));
        }

        int shard = (int) Math.round(type.shardValue() * tier.shardMultiplier());
        shard = nexuses.shardBonus(nexus, shard);

        var pdc = mob.getPersistentDataContainer();
        pdc.set(Keys.INVADER_NEXUS, PersistentDataType.STRING, nexus.id().toString());
        pdc.set(Keys.INVADER_TYPE, PersistentDataType.STRING, type.name());
        pdc.set(Keys.INVADER_TIER, PersistentDataType.STRING, tier.name());
        pdc.set(Keys.INVADER_WAVE, PersistentDataType.INTEGER, wave);
        pdc.set(Keys.INVADER_SHARD, PersistentDataType.INTEGER, shard);

        Invader invader = new Invader(mob, type, tier, nexus.id(), wave);
        registry.register(invader);
        return invader;
    }

    // ---------- 能力付与 ----------

    private void applyStats(Mob mob, InvaderType type, MutationTier tier, int gameStage) {
        // GameStage による地力の底上げ。線形だと後半で破綻するので緩やかな伸びにする。
        double stageHealth = 1.0 + gameStage * 0.012;
        double stageDamage = 1.0 + gameStage * 0.008;
        double stageArmor = Math.min(12.0, gameStage * 0.05);

        setAttribute(mob, Attribute.MAX_HEALTH,
                type.healthMultiplier() * tier.healthMultiplier() * stageHealth, true);
        setAttribute(mob, Attribute.ATTACK_DAMAGE,
                type.damageMultiplier() * tier.damageMultiplier() * stageDamage, true);
        setAttribute(mob, Attribute.MOVEMENT_SPEED,
                type.speedMultiplier() * tier.speedMultiplier(), true);

        AttributeInstance armor = mob.getAttribute(Attribute.ARMOR);
        if (armor != null) {
            armor.setBaseValue(Math.min(20.0, armor.getBaseValue() + stageArmor));
        }
        AttributeInstance follow = mob.getAttribute(Attribute.FOLLOW_RANGE);
        if (follow != null) {
            // ネクサスまで到達させるため索敵範囲を広く取る
            follow.setBaseValue(Math.max(follow.getBaseValue(), config.nexusDefenseRadius() + 24.0));
        }
        AttributeInstance kb = mob.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (kb != null && type.isBoss()) {
            kb.setBaseValue(0.85);
        }

        mob.setHealth(mob.getAttribute(Attribute.MAX_HEALTH) == null
                ? mob.getHealth()
                : mob.getAttribute(Attribute.MAX_HEALTH).getValue());

        if (tier.isRegenerating()) {
            mob.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                    PotionEffect.INFINITE_DURATION, 0, false, false, false));
        }
        // 昼間の自然発火で勝手に溶けないようにする（準備フェーズの襲撃で必要）
        mob.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,
                PotionEffect.INFINITE_DURATION, 0, false, false, false));
    }

    private void setAttribute(Mob mob, Attribute attribute, double multiplier, boolean fromDefault) {
        AttributeInstance inst = mob.getAttribute(attribute);
        if (inst == null) {
            return;
        }
        double base = fromDefault ? inst.getDefaultValue() : inst.getBaseValue();
        inst.setBaseValue(base * multiplier);
    }

    private void applyAppearance(Mob mob, InvaderType type, MutationTier tier) {
        String label = tier.prefix().isEmpty()
                ? type.displayName()
                : tier.prefix() + " " + type.displayName();
        mob.customName(Component.text(label, tier.color()).decoration(TextDecoration.ITALIC, false));
        mob.setCustomNameVisible(false);
        if (tier != MutationTier.NORMAL) {
            mob.setGlowing(true);
        }

        EntityEquipment eq = mob.getEquipment();
        if (eq == null) {
            return;
        }
        if (type.helmet() != null) {
            eq.setHelmet(new ItemStack(type.helmet()));
            eq.setHelmetDropChance(0f);
        }
        if (type.handItem() != null) {
            eq.setItemInMainHand(new ItemStack(type.handItem()));
            eq.setItemInMainHandDropChance(0f);
        }
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        eq.setItemInOffHandDropChance(0f);
    }

    private void applyBehaviourTweaks(Mob mob, InvaderType type) {
        mob.setRemoveWhenFarAway(false);
        mob.setPersistent(true);
        mob.setCanPickupItems(false);
        mob.setAware(true);

        if (mob instanceof Zombie zombie) {
            zombie.setShouldBurnInDay(false);
            zombie.setAdult();
        }
        if (mob instanceof Creeper creeper && type.isBomber()) {
            creeper.setMaxFuseTicks(35);
            creeper.setExplosionRadius(2);
        }
        if (mob instanceof Phantom phantom) {
            phantom.setSize(type.isBoss() ? 6 : 2);
        }
        mob.getPathfinder().setCanOpenDoors(true);
        mob.getPathfinder().setCanPassDoors(true);
    }

    // ---------- 湧き位置 ----------

    /**
     * ネクサスを中心としたリング状の範囲から、地面のある安全な湧き位置を探す。
     * プレイヤーの目の前に湧くと興醒めなので、近すぎる候補は捨てる。
     */
    public Location findSpawnLocation(Nexus nexus, InvaderType type) {
        Location center = nexus.center();
        World world = center == null ? null : center.getWorld();
        if (world == null) {
            return null;
        }
        int min = nexuses.spawnRadiusMin(nexus);
        int max = Math.max(min + 4, nexuses.spawnRadiusMax(nexus));
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist = min + rng.nextDouble() * (max - min);
            int bx = center.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int bz = center.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);

            if (type.isFlying()) {
                int y = center.getBlockY() + 12 + rng.nextInt(10);
                Location air = new Location(world, bx + 0.5, y, bz + 0.5);
                if (air.getBlock().getType().isAir()) {
                    return air;
                }
                continue;
            }

            Location ground = findGround(world, bx, bz, center.getBlockY());
            if (ground == null) {
                continue;
            }
            if (isTooCloseToPlayer(ground, 12.0)) {
                continue;
            }
            return ground;
        }
        return null;
    }

    /** ネクサスのY座標を基準に、上下±16 の範囲で立てる地面を探す。 */
    private Location findGround(World world, int x, int z, int referenceY) {
        int minY = Math.max(world.getMinHeight() + 1, referenceY - 16);
        int maxY = Math.min(world.getMaxHeight() - 3, referenceY + 16);
        List<Integer> candidates = new ArrayList<>();
        for (int y = maxY; y >= minY; y--) {
            Block floor = world.getBlockAt(x, y, z);
            Block feet = world.getBlockAt(x, y + 1, z);
            Block head = world.getBlockAt(x, y + 2, z);
            if (floor.getType().isSolid()
                    && isPassable(feet)
                    && isPassable(head)) {
                candidates.add(y + 1);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        // 最も参照Yに近い足場を選ぶ（地下拠点でも地表に湧きっぱなしにならないように）
        int best = candidates.get(0);
        for (int y : candidates) {
            if (Math.abs(y - referenceY) < Math.abs(best - referenceY)) {
                best = y;
            }
        }
        return new Location(world, x + 0.5, best, z + 0.5);
    }

    private boolean isPassable(Block block) {
        Material t = block.getType();
        return t.isAir() || !t.isSolid();
    }

    private boolean isTooCloseToPlayer(Location loc, double distance) {
        double sq = distance * distance;
        World world = loc.getWorld();
        if (world == null) {
            return false;
        }
        for (var p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(loc) < sq) {
                return true;
            }
        }
        return false;
    }
}
