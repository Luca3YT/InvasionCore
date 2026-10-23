package work.maiotanet.invasioncore.economy;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.item.InvasionItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ネクサスの SHOP の品ぞろえ。
 *
 * <p>品物と値段は config.yml の shop.items に並べた順に出す。配信ごとに入れ替えたり値段を調整したりできるよう、
 * コードには持たない。独自アイテムは /invasion give と同じ名前（flash・turret_arrow など）、
 * それ以外は Minecraft のアイテム名（IRON_INGOT など）で書く。</p>
 *
 * <p>値段は設定の amount 個あたり。売買の画面では 1 個単位で数を選ぶので、合計は個数で割って出す。
 * 買うときは切り上げ、売るときは切り捨てにする。どちらも同じ向きに丸めると、
 * 少しずつ買ってまとめて売る（または逆）だけで Shard が増えてしまうため。</p>
 */
public final class ShopService {

    /** 1 品。item の個数（設定の amount）あたり price Shard。 */
    public record Entry(ItemStack item, int price) {

        /** 値段の基準になる個数（設定の amount）。 */
        public int lot() {
            return Math.max(1, item.getAmount());
        }

        /** count 個買うときの合計（切り上げ）。 */
        public int buyPrice(int count) {
            return (int) (((long) count * price + lot() - 1) / lot());
        }

        /** count 個売るときに受け取る Shard（切り捨て）。 */
        public int sellPrice(int count, double ratio) {
            return (int) Math.floor((double) count * price * ratio / lot() + 1e-9);
        }
    }

    private final Plugin plugin;
    private final InvasionConfig config;

    public ShopService(Plugin plugin, InvasionConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /** いまの設定の品ぞろえ。設定の書き間違いはログに出して、その品だけ飛ばす。 */
    public List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (Map<?, ?> raw : config.shopItems()) {
            String id = String.valueOf(raw.get("item"));
            int amount = toInt(raw.get("amount"), 1);
            int price = toInt(raw.get("price"), -1);
            ItemStack item = create(id, Math.max(1, amount));
            if (item == null || price < 0) {
                plugin.getLogger().warning("shop.items の品を読めませんでした（item: " + id + ", price: " + raw.get("price") + "）");
                continue;
            }
            out.add(new Entry(item, price));
        }
        return out;
    }

    /** 売るときの値段の割合。 */
    public double sellRatio() {
        return config.shopSellRatio();
    }

    /**
     * stack がこの品として売れるものか。
     * 独自アイテムは ID で見る（CustomModelData の付け直しなどで見た目の情報が違っても同じ品）。
     * バニラの品は、名前を変えた・エンチャントした・傷んだものを除くため、何も付いていない同じ品だけにする。
     */
    public static boolean matches(Entry entry, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        String id = InvasionItems.idOf(entry.item());
        if (id != null) {
            return id.equals(InvasionItems.idOf(stack));
        }
        return InvasionItems.idOf(stack) == null && stack.isSimilar(new ItemStack(entry.item().getType()));
    }

    /** 持ち物（防具とオフハンドを除く）の中の、この品の数。 */
    public static int owned(Player player, Entry entry) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (matches(entry, stack)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** 持ち物からこの品を count 個取り除く。足りなければ何もせず false。 */
    public static boolean removeOwned(Player player, Entry entry, int count) {
        if (owned(player, entry) < count) {
            return false;
        }
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        int left = count;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (!matches(entry, stack)) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            left -= take;
            if (take >= stack.getAmount()) {
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
        }
        inv.setStorageContents(contents);
        return true;
    }

    /** この品を count 個渡す。入り切らない分は足元に落とす。 */
    public static void give(Player player, Entry entry, int count) {
        int max = Math.max(1, entry.item().getMaxStackSize());
        int left = count;
        while (left > 0) {
            ItemStack stack = entry.item().clone();
            stack.setAmount(Math.min(max, left));
            left -= stack.getAmount();
            for (ItemStack drop : player.getInventory().addItem(stack).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }

    private static ItemStack create(String id, int amount) {
        ItemStack custom = InvasionItems.create(id, amount);
        if (custom != null) {
            return custom;
        }
        Material material = Material.matchMaterial(id.toUpperCase(Locale.ROOT));
        if (material == null || !material.isItem() || material.isAir()) {
            return null;
        }
        return new ItemStack(material, Math.min(amount, material.getMaxStackSize()));
    }

    private static int toInt(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
