package work.maiotanet.invasioncore.economy;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import work.maiotanet.invasioncore.item.InvasionItems;

import java.util.HashMap;
import java.util.Map;

/**
 * Shard（ネクサスの残滓）の授受。
 *
 * <p>インベントリ上の実アイテムを通貨として扱う。
 * 数値だけの残高にすると「稼いだ実感」が薄れるうえ、
 * 死亡時にドロップする緊張感も失われるため、あえて実体アイテムにしている。</p>
 */
public final class ShardService {

    public ShardService() {
    }

    public int count(Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (InvasionItems.is(stack, InvasionItems.SHARD)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    public boolean has(Player player, int amount) {
        return amount <= 0 || count(player) >= amount;
    }

    /**
     * 指定量を消費する。足りなければ何も消費せず false。
     */
    public boolean take(Player player, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (count(player) < amount) {
            return false;
        }
        int remaining = amount;
        Inventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (!InvasionItems.is(stack, InvasionItems.SHARD)) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            remaining -= take;
            if (stack.getAmount() - take <= 0) {
                inv.setItem(i, null);
            } else {
                stack.setAmount(stack.getAmount() - take);
                inv.setItem(i, stack);
            }
        }
        return true;
    }

    /** 与える。インベントリが満杯なら足元にドロップする。 */
    public void give(Player player, int amount) {
        if (amount <= 0) {
            return;
        }
        int remaining = amount;
        while (remaining > 0) {
            int chunk = Math.min(64, remaining);
            remaining -= chunk;
            Map<Integer, ItemStack> leftover =
                    player.getInventory().addItem(InvasionItems.shard(chunk));
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }

    /** 複数人へまとめて配る。 */
    public void giveAll(Map<Player, Integer> distribution) {
        for (Map.Entry<Player, Integer> e : new HashMap<>(distribution).entrySet()) {
            give(e.getKey(), e.getValue());
        }
    }
}
