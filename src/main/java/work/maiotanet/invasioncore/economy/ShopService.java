package work.maiotanet.invasioncore.economy;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
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
 */
public final class ShopService {

    /** 1 品。押すと amount 個を price Shard で買う。 */
    public record Entry(ItemStack item, int price) {
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
