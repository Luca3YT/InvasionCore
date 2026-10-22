package work.maiotanet.invasioncore.world;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.economy.RewardService;
import work.maiotanet.invasioncore.heat.HeatService;
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

    /** 一時的に設置した容器（空中投下）の記録。 */
    private record TempContainer(Location location, BlockData original, long placedAtMillis,
                                 long expireAtMillis) {
    }

    /**
     * 設置直後に「空だから撤去」と判定されないための猶予（ミリ秒）。
     * チャンクの読み込み待ちなどで一時的に中身が読めないことがある。
     */
    private static final long PLACEMENT_GRACE_MILLIS = 3000L;

    private final Plugin plugin;
    private final InvasionConfig config;
    private final NexusManager nexuses;
    private final InvaderSpawner spawner;
    private final GameStageService stages;
    private final HeatService heat;
    private final RewardService rewards;

    private final Map<UUID, Long> nextHorde = new HashMap<>();
    private final Map<UUID, Long> nextAirDrop = new HashMap<>();
    private final List<TempContainer> temporary = new ArrayList<>();

    public WorldEventService(Plugin plugin, InvasionConfig config, NexusManager nexuses,
                             InvaderSpawner spawner, GameStageService stages,
                             HeatService heat, RewardService rewards) {
        this.plugin = plugin;
        this.config = config;
        this.nexuses = nexuses;
        this.spawner = spawner;
        this.stages = stages;
        this.heat = heat;
        this.rewards = rewards;
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
        expireContainers(now);
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
     * 空中投下を即座に行う。管理コマンドから呼ぶ。
     *
     * @return 投下地点。失敗したら null
     */
    public Location forceAirDrop(Nexus nexus) {
        Location spot = findDropSite(nexus);
        if (spot == null) {
            return null;
        }
        int stage = stages.computeCurrent(nexus);
        List<ItemStack> overflow = placeContainer(spot, Material.BARREL, rewards.airDropLoot(stage),
                15 * 60 * 1000L);
        if (overflow == null) {
            return null;
        }
        World world = spot.getWorld();
        for (ItemStack stack : overflow) {
            world.dropItemNaturally(spot.clone().add(0.5, 1.0, 0.5), stack);
        }
        world.playSound(spot, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 2.0f, 0.8f);
        nextAirDrop.put(nexus.id(), System.currentTimeMillis()
                + config.airDropIntervalSeconds() * 1000L);
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

        List<Player> participants = stages.participants(nexus);
        if (participants.isEmpty()) {
            return;
        }
        Location spot = forceAirDrop(nexus);
        if (spot == null) {
            return;
        }
        for (Player p : participants) {
            p.sendMessage(Text.info("補給物資が投下された（"
                    + spot.getBlockX() + ", " + spot.getBlockY() + ", " + spot.getBlockZ() + "）"));
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.2f);
        }
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
            double dist = 40 + rng.nextDouble() * 40;
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

    // ---------- 一時容器 ----------

    /**
     * 一時的な容器を設置して中身を詰める。
     *
     * <p>{@code getState()} はスナップショットなので、書き戻しに失敗すると
     * 「空の樽だけが残る」状態になりうる。
     * 格納できたかを必ず実体から読み直して確認し、失敗時は設置を取り消す。</p>
     *
     * @return 格納に成功したら、容量不足で入り切らなかったアイテム（空のこともある）。失敗したら null
     */
    private List<ItemStack> placeContainer(Location loc, Material material, List<ItemStack> contents,
                                           long lifetimeMillis) {
        Block block = loc.getBlock();
        BlockData original = block.getBlockData().clone();
        block.setType(material, false);

        // getState() はスナップショットを返し、インベントリへの追加が実体へ反映されない。
        // getState(false) で実体のタイルステートを取り、直接書き込む。
        BlockState state = block.getState(false);
        if (!(state instanceof Container container)) {
            block.setBlockData(original, false);
            return null;
        }
        int expected = 0;
        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack stack : contents) {
            if (stack != null && !stack.getType().isAir()) {
                // addItem は渡したスタックの個数を書き換えることがある。失敗時に呼び出し元が
                // 元のドロップを使えるよう、複製を渡す。
                overflow.addAll(container.getInventory().addItem(stack.clone()).values());
                expected++;
            }
        }

        if (expected > 0 && !hasContents(block)) {
            plugin.getLogger().warning("一時容器に中身を格納できませんでした: " + loc);
            block.setBlockData(original, false);
            return null;
        }
        long now = System.currentTimeMillis();
        temporary.add(new TempContainer(loc.clone(), original, now, now + lifetimeMillis));
        return overflow;
    }

    /** 設置後の実体を読み直して、中身が入っているかを確認する。 */
    private boolean hasContents(Block block) {
        return block.getState(false) instanceof Container container
                && !container.getInventory().isEmpty();
    }

    /** 期限切れの容器を片付ける。中身が残っていればその場にぶちまける。 */
    private void expireContainers(long now) {
        Iterator<TempContainer> it = temporary.iterator();
        while (it.hasNext()) {
            TempContainer tc = it.next();
            World world = tc.location().getWorld();
            if (world == null) {
                it.remove();
                continue;
            }
            Block block = tc.location().getBlock();
            BlockState state = block.getState(false);

            if (!(state instanceof Container container)) {
                // 既に別のブロックに変わっている＝プレイヤーが壊した
                it.remove();
                continue;
            }

            if (container.getInventory().isEmpty()) {
                if (now - tc.placedAtMillis() < PLACEMENT_GRACE_MILLIS) {
                    // 設置直後はまだ中身が読めていない可能性があるので触らない
                    continue;
                }
                // 中身が空になったら即座に元の地形へ戻す
                block.setBlockData(tc.original(), false);
                it.remove();
                continue;
            }
            if (now < tc.expireAtMillis()) {
                // 目印のパーティクル
                if ((now / 1000L) % 3 == 0) {
                    world.spawnParticle(Particle.END_ROD,
                            tc.location().clone().add(0.5, 1.2, 0.5), 3, 0.15, 0.4, 0.15, 0.01);
                }
                continue;
            }
            // 期限切れ：中身をドロップして元のブロックに戻す
            for (ItemStack stack : container.getInventory().getContents()) {
                if (stack != null && !stack.getType().isAir()) {
                    world.dropItemNaturally(tc.location().clone().add(0.5, 1.0, 0.5), stack);
                }
            }
            container.getInventory().clear();
            container.update();
            block.setBlockData(tc.original(), false);
            it.remove();
        }
    }

    private long randomDelay(int minSeconds, int maxSeconds) {
        int min = Math.max(30, minSeconds);
        int max = Math.max(min + 30, maxSeconds);
        return ThreadLocalRandom.current().nextInt(min, max) * 1000L;
    }

    /** サーバー停止時に一時容器を全て片付ける。 */
    public void shutdown() {
        expireContainers(Long.MAX_VALUE);
        temporary.clear();
    }
}
