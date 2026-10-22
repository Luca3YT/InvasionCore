package work.maiotanet.invasioncore.defense;

import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.Plugin;
import work.maiotanet.invasioncore.nexus.NexusManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

/**
 * 拠点の中でプレイヤーが置いたブロックの記録。
 *
 * <p>爆導体の自爆で壊してよいのは「壁」だけにし、元からある地形は壊さないため。
 * 強化していない壁も壁として扱いたいので、強化の記録（FortifyService）とは別に、置いた場所を覚えておく。
 * 範囲はネクサスの防衛範囲の中だけ（爆発で壊せるのは侵略者が壊してよい範囲の中だけなので、それより外は要らない）。
 * この記録を入れる前に置かれたブロックは、地形と同じ扱いになる。</p>
 */
public final class PlacedBlockTracker implements Listener {

    private final Plugin plugin;
    private final NexusManager nexuses;
    private final File file;
    private final Set<String> placed = new HashSet<>();

    public PlacedBlockTracker(Plugin plugin, NexusManager nexuses) {
        this.plugin = plugin;
        this.nexuses = nexuses;
        this.file = new File(plugin.getDataFolder(), "placed.yml");
    }

    private static String key(Block block) {
        return block.getWorld().getName() + ';' + block.getX() + ';' + block.getY() + ';' + block.getZ();
    }

    /** プレイヤーが置いたブロックか。既に壊れて空気になっていれば記録も消す。 */
    public boolean isPlaced(Block block) {
        String k = key(block);
        if (!placed.contains(k)) {
            return false;
        }
        if (block.getType().isAir() || block.isLiquid()) {
            placed.remove(k);
            return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (nexuses.nearest(block.getLocation()) != null) {
            placed.add(key(block));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        placed.remove(key(event.getBlock()));
    }

    /** ネクサスを撤去して最初からやり直すときなど、記録を全部捨てる。 */
    public void clear() {
        placed.clear();
    }

    public void load() {
        placed.clear();
        if (!file.exists()) {
            return;
        }
        placed.addAll(YamlConfiguration.loadConfiguration(file).getStringList("placed"));
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("placed", new ArrayList<>(placed));
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("データフォルダを作成できませんでした: " + parent);
            }
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "placed.yml の保存に失敗しました", ex);
        }
    }
}
