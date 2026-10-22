package work.maiotanet.invasioncore.world;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.economy.RewardService;
import work.maiotanet.invasioncore.economy.ShardLootService;
import work.maiotanet.invasioncore.heat.HeatService;
import work.maiotanet.invasioncore.item.InvasionItems;
import work.maiotanet.invasioncore.mob.InvaderSpawner;
import work.maiotanet.invasioncore.mob.InvaderType;
import work.maiotanet.invasioncore.mob.MutationTier;
import work.maiotanet.invasioncore.nexus.Nexus;
import work.maiotanet.invasioncore.nexus.NexusManager;
import work.maiotanet.invasioncore.stage.GameStageService;
import work.maiotanet.invasioncore.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 準備フェーズを退屈にしないためのワールドイベント群。
 *
 * <p>徘徊ホード・空中投下・叫喚体の湧きを扱う。
 * どれも「拠点に籠って待つだけ」を防ぎ、外へ出る動機を作るための仕掛け。
 * 夜間襲撃が始まる日（cycle.skirmish-start-day）より前は猶予期間として、
 * 叫喚体を湧かせず、徘徊ホードも弱い個体の少数だけにする。</p>
 */
public final class WorldEventService {

    /**
     * 着地した補給物資。樽そのものは光らせられないので、同じ見た目の表示用ブロックを重ねて光らせ、
     * 上に名札を出す。どちらも保存しない表示用で、チャンクが読み込まれ直したら作り直す。
     */
    private static final class AirDrop {
        final Location location;
        final BlockData original;
        final long placedAtMillis;
        final long expireAtMillis;
        BlockDisplay glow;
        TextDisplay label;

        AirDrop(Location location, BlockData original, long placedAtMillis, long expireAtMillis) {
            this.location = location;
            this.original = original;
            this.placedAtMillis = placedAtMillis;
            this.expireAtMillis = expireAtMillis;
        }
    }

    /**
     * 設置直後に「空だから撤去」と判定されないための猶予（ミリ秒）。
     * チャンクの読み込み待ちなどで一時的に中身が読めないことがある。
     */
    private static final long PLACEMENT_GRACE_MILLIS = 3000L;

    /** 補給物資が置かれてから消えるまで（ミリ秒）。 */
    private static final long AIR_DROP_LIFETIME_MILLIS = 15 * 60 * 1000L;

    /** 落ちてくる演出の高さ（ブロック）と時間（tick）。 */
    private static final int FALL_HEIGHT = 40;
    private static final int FALL_TICKS = 60;

    /** 遠くからも見える光の柱の高さ（ブロック）。 */
    private static final int BEAM_HEIGHT = 48;

    private static final Color AIR_DROP_COLOR = Color.fromRGB(0xFFD166);

    private final Plugin plugin;
    private final InvasionConfig config;
    private final NexusManager nexuses;
    private final InvaderSpawner spawner;
    private final GameStageService stages;
    private final HeatService heat;
    private final RewardService rewards;
    private final ShardLootService shardLoot;

    private final Map<UUID, Long> nextHorde = new HashMap<>();
    private final Map<UUID, Long> nextAirDrop = new HashMap<>();
    private final List<AirDrop> airDrops = new ArrayList<>();

    public WorldEventService(Plugin plugin, InvasionConfig config, NexusManager nexuses,
                             InvaderSpawner spawner, GameStageService stages,
                             HeatService heat, RewardService rewards, ShardLootService shardLoot) {
        this.plugin = plugin;
        this.config = config;
        this.nexuses = nexuses;
        this.spawner = spawner;
        this.stages = stages;
        this.heat = heat;
        this.rewards = rewards;
        this.shardLoot = shardLoot;
    }

    // ---------- 1秒ごと ----------

    public void tick() {
        heat.decayTick();
        long now = System.currentTimeMillis();

        for (Nexus nexus : new ArrayList<>(nexuses.all())) {
            if (!nexus.isActive()) {
                continue;
            }
            checkScreamer(nexus);

            if (nexus.phase() != Nexus.Phase.PREPARATION) {
                // 血の月の最中に徘徊ホードや空中投下を重ねると処理が破綻する
                continue;
            }
            checkWanderingHorde(nexus, now);
            checkAirDrop(nexus, now);
        }
        tickAirDrops(now);
    }

    // ---------- 叫喚体 ----------

    private void checkScreamer(Nexus nexus) {
        if (!config.heatEnabled() || isGracePeriod(nexus)) {
            return;
        }
        for (Player player : stages.participants(nexus)) {
            if (!heat.consumeScreamerTrigger(player.getLocation())) {
                continue;
            }
            int stage = stages.computeCurrent(nexus);
            Location spot = spawner.findSpawnLocation(nexus, InvaderType.SCREAMER);
            if (spot == null) {
                spot = player.getLocation().add(
                        ThreadLocalRandom.current().nextDouble(-16, 16), 0,
                        ThreadLocalRandom.current().nextDouble(-16, 16));
            }
            spawner.spawnAt(nexus, InvaderType.SCREAMER, MutationTier.NORMAL, stage, 0, spot);

            player.sendMessage(Text.warn("騒ぎすぎた。何かがこちらに気づいている。"));
            player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 1.2f, 0.5f);
            break;
        }
    }

    // ---------- 徘徊ホード ----------

    private void checkWanderingHorde(Nexus nexus, long now) {
        if (!config.wanderingHordeEnabled()) {
            return;
        }
        Long next = nextHorde.get(nexus.id());
        if (next == null) {
            nextHorde.put(nexus.id(), now + randomDelay(
                    config.wanderingHordeMinSeconds(), config.wanderingHordeMaxSeconds()));
            return;
        }
        if (now < next) {
            return;
        }
        nextHorde.put(nexus.id(), now + randomDelay(
                config.wanderingHordeMinSeconds(), config.wanderingHordeMaxSeconds()));

        List<Player> participants = stages.participants(nexus);
        if (participants.isEmpty()) {
            return;
        }
        int stage = stages.computeCurrent(nexus);
        boolean grace = isGracePeriod(nexus);
        int count = grace
                ? 3 + ThreadLocalRandom.current().nextInt(3)
                : 3 + ThreadLocalRandom.current().nextInt(4) + stage / 30;

        for (int i = 0; i < count; i++) {
            if (grace) {
                // 猶予期間は足の遅いグラントの通常個体だけ。拠点が無いうちから走者や変異体に削られないように
                spawner.spawn(nexus, InvaderType.GRUNT, MutationTier.NORMAL, stage, 0);
                continue;
            }
            InvaderType type = ThreadLocalRandom.current().nextDouble() < 0.7
                    ? InvaderType.GRUNT : InvaderType.RUNNER;
            spawner.spawn(nexus, type, MutationTier.roll(stage), stage, 0);
        }
        for (Player p : participants) {
            p.sendMessage(Text.warn("徘徊するホードが近づいている（" + count + " 体）"));
            p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 1.0f, 0.6f);
        }
    }

    /** 夜間襲撃が始まる日より前か。最初の数日は拠点作りに集中できるよう、襲撃を弱める。 */
    private boolean isGracePeriod(Nexus nexus) {
        return nexus.day() < config.skirmishStartDay();
    }

    // ---------- 空中投下 ----------

    /**
     * 空中投下を始める。自動の投下と、管理コマンドの手動投下の両方から呼ぶ。
     * どちらでも全員に座標を知らせ、上空から樽が落ちてくる演出のあとに着地させる。
     *
     * @return 投下地点。見つからなければ null
     */
    public Location forceAirDrop(Nexus nexus) {
        Location spot = findDropSite(nexus);
        if (spot == null) {
            return null;
        }
        nextAirDrop.put(nexus.id(), System.currentTimeMillis()
                + config.airDropIntervalSeconds() * 1000L);
        int stage = stages.computeCurrent(nexus);
        double distance = nexus.center().distance(spot);
        double multiplier = distanceMultiplier(distance);
        List<ItemStack> loot = scale(rewards.airDropLoot(stage), multiplier);

        String bonus = String.format("%.1f", multiplier);
        for (Player p : stages.participants(nexus)) {
            p.sendMessage(Text.info("補給物資が投下された！（"
                    + spot.getBlockX() + ", " + spot.getBlockY() + ", " + spot.getBlockZ() + "）"
                    + " ネクサスから " + (int) Math.round(distance) + "m・中身 " + bonus + " 倍。"
                    + "光の柱が目印。15 分で消える。"));
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.2f);
        }
        playFall(spot, loot);
        return spot;
    }

    private void checkAirDrop(Nexus nexus, long now) {
        if (!config.airDropEnabled()) {
            return;
        }
        Long next = nextAirDrop.get(nexus.id());
        if (next == null) {
            nextAirDrop.put(nexus.id(), now + config.airDropIntervalSeconds() * 1000L);
            return;
        }
        if (now < next) {
            return;
        }
        nextAirDrop.put(nexus.id(), now + config.airDropIntervalSeconds() * 1000L);
        if (stages.participants(nexus).isEmpty()) {
            return;
        }
        forceAirDrop(nexus);
    }

    /**
     * 上空から樽が煙を引いて落ちてくる演出。着地したら本物の樽を置いて中身を詰める。
     * 落ちてくる樽は表示用のブロックなので、途中で何かに当たったり拾われたりすることはない。
     */
    private void playFall(Location spot, List<ItemStack> loot) {
        World world = spot.getWorld();
        Location start = spot.clone().add(0, FALL_HEIGHT, 0);
        BlockDisplay falling = world.spawn(start, BlockDisplay.class, d -> {
            d.setBlock(Material.BARREL.createBlockData());
            d.setPersistent(false);
            d.setGlowing(true);
            d.setGlowColorOverride(AIR_DROP_COLOR);
            d.setViewRange(4.0f);
        });
        world.playSound(start, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 3.0f, 0.6f);

        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                tick++;
                if (!falling.isValid()) {
                    // チャンクが読み込まれていないなど。演出を飛ばして着地させる
                    cancel();
                    land(spot, loot);
                    return;
                }
                // 最初はゆっくり、だんだん速く落ちる
                double t = Math.min(1.0, (double) tick / FALL_TICKS);
                Location at = spot.clone().add(0, FALL_HEIGHT * (1.0 - t * t), 0);
                falling.teleport(at);
                world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, at.clone().add(0.5, 1.2, 0.5),
                        2, 0.15, 0.1, 0.15, 0.01, null, true);
                if (tick >= FALL_TICKS) {
                    cancel();
                    falling.remove();
                    land(spot, loot);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** 着地。樽を置いて中身を詰め、土煙と音を出す。 */
    private void land(Location spot, List<ItemStack> loot) {
        Block block = spot.getBlock();
        if (!block.getType().isAir() && !block.isReplaceable()) {
            // 落ちている間に何か置かれた。その上の空いている所に置く
            block = spot.getWorld().getHighestBlockAt(spot).getRelative(BlockFace.UP);
        }
        Location at = block.getLocation();
        World world = at.getWorld();
        List<ItemStack> overflow = placeAirDrop(at, loot);
        if (overflow == null) {
            // 置けなかったら中身はその場に落とす。消えるよりはよい
            for (ItemStack stack : loot) {
                world.dropItemNaturally(at.clone().add(0.5, 1.0, 0.5), stack);
            }
            return;
        }
        for (ItemStack stack : overflow) {
            world.dropItemNaturally(at.clone().add(0.5, 1.0, 0.5), stack);
        }
        Location center = at.clone().add(0.5, 0.5, 0.5);
        world.spawnParticle(Particle.EXPLOSION, center, 3, 0.6, 0.2, 0.6, 0.0);
        world.spawnParticle(Particle.CLOUD, center, 30, 1.2, 0.2, 1.2, 0.05);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.4f);
        world.playSound(center, Sound.BLOCK_BARREL_CLOSE, 1.5f, 0.8f);
    }

    /**
     * 遠くに落ちたほど中身を多くする倍率。取りに行く手間と危なさに見合うようにする。
     * 一番近い距離で near、一番遠い距離で far になり、その間は距離に比例する。
     */
    private double distanceMultiplier(double distance) {
        int min = config.airDropMinDistance();
        int max = config.airDropMaxDistance();
        double t = Math.max(0.0, Math.min(1.0, (distance - min) / (max - min)));
        return config.airDropNearMultiplier()
                + (config.airDropFarMultiplier() - config.airDropNearMultiplier()) * t;
    }

    /**
     * 中身の個数に倍率を掛ける。端数は確率で 1 個足すので、平均すると倍率どおりになる。
     * 0 個になった品は入れない。重ねられない品（エンチャントの本など）は数を増やす。
     */
    private static List<ItemStack> scale(List<ItemStack> loot, double multiplier) {
        List<ItemStack> out = new ArrayList<>();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (ItemStack stack : loot) {
            double exact = stack.getAmount() * multiplier;
            int total = (int) Math.floor(exact) + (rng.nextDouble() < exact - Math.floor(exact) ? 1 : 0);
            int max = Math.max(1, stack.getMaxStackSize());
            while (total > 0) {
                ItemStack part = stack.clone();
                part.setAmount(Math.min(max, total));
                out.add(part);
                total -= part.getAmount();
            }
        }
        return out;
    }

    /** いま置かれている補給物資の中心。方向の案内に使う。 */
    public List<Location> activeAirDrops() {
        List<Location> out = new ArrayList<>(airDrops.size());
        for (AirDrop drop : airDrops) {
            out.add(drop.location.clone().add(0.5, 0.5, 0.5));
        }
        return out;
    }

    private Location findDropSite(Nexus nexus) {
        Location center = nexus.center();
        if (center == null || center.getWorld() == null) {
            return null;
        }
        World world = center.getWorld();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            int min = config.airDropMinDistance();
            double dist = min + rng.nextDouble() * (config.airDropMaxDistance() - min);
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);
            int y = world.getHighestBlockYAt(x, z) + 1;
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isAir()) {
                return block.getLocation();
            }
        }
        return null;
    }

    // ---------- 補給物資の樽 ----------

    /**
     * 樽を置いて中身を詰める。
     *
     * <p>{@code getState()} はスナップショットなので、書き戻しに失敗すると
     * 「空の樽だけが残る」状態になりうる。
     * 格納できたかを必ず実体から読み直して確認し、失敗時は設置を取り消す。</p>
     *
     * @return 格納に成功したら、容量不足で入り切らなかったアイテム（空のこともある）。失敗したら null
     */
    private List<ItemStack> placeAirDrop(Location loc, List<ItemStack> contents) {
        Block block = loc.getBlock();
        BlockData original = block.getBlockData().clone();
        block.setType(Material.BARREL, false);

        // getState() はスナップショットを返し、インベントリへの追加が実体へ反映されない。
        // getState(false) で実体のタイルステートを取り、直接書き込む。
        BlockState state = block.getState(false);
        if (!(state instanceof Container container)) {
            block.setBlockData(original, false);
            return null;
        }
        int expected = 0;
        int shardCount = 0;
        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack stack : contents) {
            if (stack != null && !stack.getType().isAir()) {
                // addItem は渡したスタックの個数を書き換えることがある。失敗時に呼び出し元が
                // 元のドロップを使えるよう、複製を渡す。
                overflow.addAll(container.getInventory().addItem(stack.clone()).values());
                expected++;
                if (InvasionItems.is(stack, InvasionItems.SHARD)) {
                    shardCount += stack.getAmount();
                }
            }
        }

        if (expected > 0 && !hasContents(block)) {
            plugin.getLogger().warning("補給物資に中身を格納できませんでした: " + loc);
            block.setBlockData(original, false);
            return null;
        }
        // 取り出した Shard を「見つけた」として数えられるよう、入れた数を樽に記録する
        shardLoot.markLoot(block, shardCount);
        long now = System.currentTimeMillis();
        airDrops.add(new AirDrop(loc.clone(), original, now, now + AIR_DROP_LIFETIME_MILLIS));
        return overflow;
    }

    /** 設置後の実体を読み直して、中身が入っているかを確認する。 */
    private boolean hasContents(Block block) {
        return block.getState(false) instanceof Container container
                && !container.getInventory().isEmpty();
    }

    /**
     * 1 秒ごと。空になった・期限が来た補給物資を片付け、残っているものには光の柱・発光・名札を出す。
     * 期限が来たときに中身が残っていれば、その場にぶちまける。
     */
    private void tickAirDrops(long now) {
        Iterator<AirDrop> it = airDrops.iterator();
        while (it.hasNext()) {
            AirDrop drop = it.next();
            World world = drop.location.getWorld();
            if (world == null) {
                removeMarkers(drop);
                it.remove();
                continue;
            }
            if (!world.isChunkLoaded(drop.location.getBlockX() >> 4, drop.location.getBlockZ() >> 4)
                    && now != Long.MAX_VALUE) {
                // 誰も近くにいない。表示はチャンクごと消えているので記録だけ捨て、次に来たときに作り直す。
                // 期限切れの片付けも、読み込まれるまで待つ（読み込むと重い）
                drop.glow = null;
                drop.label = null;
                continue;
            }
            Block block = drop.location.getBlock();
            BlockState state = block.getState(false);

            if (!(state instanceof Container container)) {
                // 既に別のブロックに変わっている＝プレイヤーが壊した
                removeMarkers(drop);
                it.remove();
                continue;
            }

            if (container.getInventory().isEmpty()) {
                if (now - drop.placedAtMillis < PLACEMENT_GRACE_MILLIS) {
                    // 設置直後はまだ中身が読めていない可能性があるので触らない
                    continue;
                }
                // 中身が空になったら即座に元の地形へ戻す
                removeMarkers(drop);
                block.setBlockData(drop.original, false);
                it.remove();
                continue;
            }
            if (now < drop.expireAtMillis) {
                showMarkers(drop, now);
                continue;
            }
            // 期限切れ：中身をドロップして元のブロックに戻す
            for (ItemStack stack : container.getInventory().getContents()) {
                if (stack != null && !stack.getType().isAir()) {
                    world.dropItemNaturally(drop.location.clone().add(0.5, 1.0, 0.5), stack);
                }
            }
            container.getInventory().clear();
            container.update();
            removeMarkers(drop);
            block.setBlockData(drop.original, false);
            it.remove();
        }
    }

    /** 光の柱・発光・名札。光の柱は遠くからでも見えるよう、パーティクルの表示距離の制限を外す。 */
    private void showMarkers(AirDrop drop, long now) {
        World world = drop.location.getWorld();
        Location base = drop.location.clone().add(0.5, 1.0, 0.5);
        for (int y = 0; y < BEAM_HEIGHT; y += 2) {
            world.spawnParticle(Particle.END_ROD, base.clone().add(0, y, 0), 1, 0.05, 0.4, 0.05, 0.0, null, true);
        }
        if (drop.glow == null || !drop.glow.isValid()) {
            drop.glow = world.spawn(drop.location, BlockDisplay.class, d -> {
                d.setBlock(Material.BARREL.createBlockData());
                d.setPersistent(false);
                d.setGlowing(true);
                d.setGlowColorOverride(AIR_DROP_COLOR);
                // 本体とぴったり重なるとちらつくので、わずかに大きくして外側へずらす
                d.setTransformation(new Transformation(
                        new Vector3f(-0.01f, -0.01f, -0.01f), new AxisAngle4f(),
                        new Vector3f(1.02f, 1.02f, 1.02f), new AxisAngle4f()));
                d.setViewRange(4.0f);
            });
        }
        long remain = Math.max(0, drop.expireAtMillis - now);
        long seconds = (remain + 999) / 1000;
        Component text = Text.of("補給物資", Text.WARN)
                .append(Component.newline())
                .append(Text.of("あと " + (seconds / 60) + ":" + String.format("%02d", seconds % 60), Text.MUTED));
        if (drop.label == null || !drop.label.isValid()) {
            drop.label = world.spawn(drop.location.clone().add(0.5, 1.4, 0.5), TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setBillboard(Display.Billboard.CENTER);
                d.setViewRange(1.0f);
            });
        }
        drop.label.text(text);
    }

    private static void removeMarkers(AirDrop drop) {
        if (drop.glow != null && drop.glow.isValid()) {
            drop.glow.remove();
        }
        if (drop.label != null && drop.label.isValid()) {
            drop.label.remove();
        }
        drop.glow = null;
        drop.label = null;
    }

    private long randomDelay(int minSeconds, int maxSeconds) {
        int min = Math.max(30, minSeconds);
        int max = Math.max(min + 30, maxSeconds);
        return ThreadLocalRandom.current().nextInt(min, max) * 1000L;
    }

    /** サーバー停止時に補給物資を全て片付ける（中身はその場に落とす）。 */
    public void shutdown() {
        tickAirDrops(Long.MAX_VALUE);
        for (AirDrop drop : airDrops) {
            removeMarkers(drop);
        }
        airDrops.clear();
    }
}
