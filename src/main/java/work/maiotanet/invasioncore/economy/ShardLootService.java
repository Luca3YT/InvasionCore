package work.maiotanet.invasioncore.economy;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.item.InvasionItems;
import work.maiotanet.invasioncore.util.Keys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 構造物のチェストや補給物資の中の Shard。
 *
 * <p>構造物のチェストには、初めて開けたときに中身と一緒に Shard を入れる。探索に出る動機にするため。</p>
 *
 * <p>そこから取り出した Shard も、地面から拾ったときと同じく音を鳴らし、オーバーレイに流す。
 * ただし数えるのは、こちらが入れた分（容器に記録した数）まで。自分で入れた Shard を出し入れしても数えない。</p>
 */
public final class ShardLootService implements Listener {

    /** 開いている容器と、最後に数えたときの所持数。 */
    private static final class Session {
        final List<Supplier<PersistentDataContainer>> sources;
        int lastCount;

        Session(List<Supplier<PersistentDataContainer>> sources, int lastCount) {
            this.sources = sources;
            this.lastCount = lastCount;
        }
    }

    private final Plugin plugin;
    private final InvasionConfig config;
    private final ShardService shards;
    private final ShardPickupSound sound;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public ShardLootService(Plugin plugin, InvasionConfig config, ShardService shards, ShardPickupSound sound) {
        this.plugin = plugin;
        this.config = config;
        this.shards = shards;
        this.sound = sound;
    }

    // ---------- 構造物のチェスト ----------

    /** 構造物のチェストの中身が作られるとき（初めて開けたとき・ホッパーで吸われたとき・壊されたとき）。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        InventoryHolder holder = event.getInventoryHolder();
        if (holder == null || event.isPlugin()) {
            return;
        }
        int[] range = config.lootShardRange(dimension(event.getWorld()));
        if (range[1] <= 0) {
            return;
        }
        int amount = ThreadLocalRandom.current().nextInt(range[0], range[1] + 1);
        if (amount <= 0) {
            return;
        }
        int remaining = amount;
        while (remaining > 0) {
            int stack = Math.min(64, remaining);
            event.getLoot().add(InvasionItems.shard(stack));
            remaining -= stack;
        }
        List<Supplier<PersistentDataContainer>> sources = sources(holder);
        if (!sources.isEmpty()) {
            add(sources.get(0), amount);
        }
    }

    private static String dimension(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> "nether";
            case THE_END -> "end";
            default -> "overworld";
        };
    }

    /** 補給物資など、プラグインが中身を詰めた容器に「これだけは見つけた扱い」と記録する。 */
    public void markLoot(Block block, int amount) {
        if (amount > 0) {
            add(blockSource(block), amount);
        }
    }

    private static void add(Supplier<PersistentDataContainer> source, int amount) {
        PersistentDataContainer pdc = source.get();
        if (pdc == null) {
            return;
        }
        int current = pdc.getOrDefault(Keys.LOOT_SHARDS, PersistentDataType.INTEGER, 0);
        pdc.set(Keys.LOOT_SHARDS, PersistentDataType.INTEGER, current + amount);
    }

    // ---------- 取り出し ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        List<Supplier<PersistentDataContainer>> sources = sources(event.getInventory().getHolder());
        if (remaining(sources) <= 0) {
            return;
        }
        sessions.put(player.getUniqueId(), new Session(sources, shards.count(player)));
    }

    /** 中身が動くのはイベントの後なので、次の tick に所持数の増え方を見る。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && sessions.containsKey(player.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> check(player));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && sessions.containsKey(player.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> check(player));
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player && sessions.containsKey(player.getUniqueId())) {
            check(player);
            sessions.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private void check(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        int now = shards.count(player);
        int delta = now - session.lastCount;
        session.lastCount = now;
        if (delta <= 0) {
            // 容器へ戻したときは記録を増やさない。出し入れを繰り返して何度も数えられないように
            return;
        }
        int credited = take(session.sources, delta);
        if (credited > 0) {
            sound.queue(player, credited);
            shards.noteFound(player, credited);
        }
    }

    /** 記録から最大 wanted 個を差し引き、実際に差し引けた数を返す。 */
    private static int take(List<Supplier<PersistentDataContainer>> sources, int wanted) {
        int taken = 0;
        for (Supplier<PersistentDataContainer> source : sources) {
            PersistentDataContainer pdc = source.get();
            if (pdc == null || taken >= wanted) {
                continue;
            }
            int left = pdc.getOrDefault(Keys.LOOT_SHARDS, PersistentDataType.INTEGER, 0);
            int n = Math.min(left, wanted - taken);
            if (n <= 0) {
                continue;
            }
            if (left - n <= 0) {
                pdc.remove(Keys.LOOT_SHARDS);
            } else {
                pdc.set(Keys.LOOT_SHARDS, PersistentDataType.INTEGER, left - n);
            }
            taken += n;
        }
        return taken;
    }

    private static int remaining(List<Supplier<PersistentDataContainer>> sources) {
        int total = 0;
        for (Supplier<PersistentDataContainer> source : sources) {
            PersistentDataContainer pdc = source.get();
            if (pdc != null) {
                total += pdc.getOrDefault(Keys.LOOT_SHARDS, PersistentDataType.INTEGER, 0);
            }
        }
        return total;
    }

    // ---------- 容器の記録場所 ----------

    /**
     * 容器の記録（PDC）の取り出し口。ラージチェストは左右それぞれに記録がある。
     * ブロックは毎回読み直す（getState(false) で実体を取り、書き込みがそのまま残るようにする）。
     */
    private static List<Supplier<PersistentDataContainer>> sources(InventoryHolder holder) {
        List<Supplier<PersistentDataContainer>> out = new ArrayList<>();
        if (holder instanceof DoubleChest chest) {
            out.addAll(sources(chest.getLeftSide()));
            out.addAll(sources(chest.getRightSide()));
        } else if (holder instanceof BlockInventoryHolder blockHolder) {
            out.add(blockSource(blockHolder.getBlock()));
        } else if (holder instanceof Entity entity && !(holder instanceof Player)) {
            // チェスト付きトロッコ（廃坑）など
            out.add(entity::getPersistentDataContainer);
        }
        return out;
    }

    private static Supplier<PersistentDataContainer> blockSource(Block block) {
        return () -> block.getState(false) instanceof TileState state ? state.getPersistentDataContainer() : null;
    }
}
