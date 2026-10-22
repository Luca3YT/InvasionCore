package work.maiotanet.invasioncore.ui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import work.maiotanet.invasioncore.item.InvasionItems;
import work.maiotanet.invasioncore.util.Sounds;
import work.maiotanet.invasioncore.util.Text;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

/**
 * /invasion item — このプラグインのアイテムを、クリエイティブのように何度でも取り出せる画面。
 *
 * <p>テストプレイで道具を集める手間を省くため。普段は運営だけが使え、
 * /invasion admin item on で誰でも使えるようにできる（off で運営だけに戻す）。
 * 誰でも使える状態は再起動しても残るよう item-box.yml に保存する。</p>
 */
public final class ItemBox {

    /** 運営だけが使えるようにするための権限。 */
    public static final String ADMIN_PERMISSION = "invasioncore.admin";

    /** 画面に並べる品（/invasion give と同じ名前）。 */
    private static final List<String> ITEMS = List.of(
            "nexus", "hammer", "shard", "kit",
            "turret_arrow", "turret_flame", "turret_frost", "turret_heal",
            "spike", "mine", "flash", "medkit", "barrier", "recall");

    private final Plugin plugin;
    private final File file;
    private boolean openToAll;

    public ItemBox(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "item-box.yml");
        this.openToAll = file.exists() && YamlConfiguration.loadConfiguration(file).getBoolean("open-to-all", false);
    }

    public boolean canUse(Player player) {
        return openToAll || player.hasPermission(ADMIN_PERMISSION);
    }

    public boolean isOpenToAll() {
        return openToAll;
    }

    public void openToAll(boolean value) {
        this.openToAll = value;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("open-to-all", value);
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("データフォルダを作成できませんでした: " + parent);
            }
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "item-box.yml の保存に失敗しました", ex);
        }
    }

    public void open(Player player) {
        InvasionHolder holder = new InvasionHolder(InvasionHolder.Kind.ITEMS, null);
        Inventory inv = Bukkit.createInventory(holder, 27, Text.of("InvasionCore のアイテム", Text.INFO));
        holder.inventory(inv);
        for (int i = 0; i < ITEMS.size(); i++) {
            inv.setItem(i, InvasionItems.create(ITEMS.get(i), 1));
        }
        player.openInventory(inv);
    }

    /** クリックで 1 個、Shift+クリックで 1 スタック。画面の品は減らない。 */
    public void handle(Player player, int slot, boolean shift) {
        if (slot < 0 || slot >= ITEMS.size()) {
            return;
        }
        if (!canUse(player)) {
            player.closeInventory();
            return;
        }
        ItemStack item = InvasionItems.create(ITEMS.get(slot), 1);
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        item.setAmount(shift ? item.getMaxStackSize() : 1);
        for (ItemStack left : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
        Sounds.click(player);
    }
}
