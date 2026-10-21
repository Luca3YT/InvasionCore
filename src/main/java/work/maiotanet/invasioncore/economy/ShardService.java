package work.maiotanet.invasioncore.economy;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import work.maiotanet.invasioncore.item.InvasionItems;
import work.maiotanet.invasioncore.nexus.Nexus;

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

    // ---------- ネクサスの共有 Shard ----------

    /** 手持ちとネクサス内の Shard の合計。支払いに使える上限。 */
    public int available(Player player, Nexus nexus) {
        return count(player) + (nexus == null ? 0 : nexus.sharedShards());
    }

    /**
     * 手持ちから先に払い、足りない分だけネクサス内の共有 Shard から払う。
     * 合わせても足りなければ何も消費せず false。
     */
    public boolean takeWithPool(Player player, Nexus nexus, int amount) {
        if (amount <= 0) {
            return true;
        }
        int own = count(player);
        int pool = nexus == null ? 0 : nexus.sharedShards();
        if (own + pool < amount) {
            return false;
        }
        int fromOwn = Math.min(own, amount);
        take(player, fromOwn);
        if (amount > fromOwn) {
            nexus.sharedShards(pool - (amount - fromOwn));
        }
        return true;
    }

    /** 手持ちの Shard を全部ネクサスに預ける。預けた数を返す。 */
    public int depositAll(Player player, Nexus nexus) {
        int amount = count(player);
        if (amount > 0 && take(player, amount)) {
            nexus.sharedShards(nexus.sharedShards() + amount);
            return amount;
        }
        return 0;
    }

    /** メインハンドに持っている Shard の束だけを預ける。預けた数を返す。 */
    public int depositHeld(Player player, Nexus nexus) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!InvasionItems.is(held, InvasionItems.SHARD)) {
            return 0;
        }
        int amount = held.getAmount();
        player.getInventory().setItemInMainHand(null);
        nexus.sharedShards(nexus.sharedShards() + amount);
        return amount;
    }

    /** ネクサス内から最大 amount 個を引き出して手持ちに入れる。引き出した数を返す。 */
    public int withdraw(Player player, Nexus nexus, int amount) {
        int n = Math.min(Math.max(0, amount), nexus.sharedShards());
        if (n > 0) {
            nexus.sharedShards(nexus.sharedShards() - n);
            give(player, n);
        }
        return n;
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
