package work.maiotanet.invasioncore.economy;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import work.maiotanet.invasioncore.item.InvasionItems;
import work.maiotanet.invasioncore.nexus.Nexus;
import work.maiotanet.invasioncore.web.ShardFeed;

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

    /** OBS オーバーレイへ増減を流す先。Web 機能が無効なら null。 */
    private ShardFeed feed;

    public ShardService() {
    }

    public void feed(ShardFeed feed) {
        this.feed = feed;
    }

    /**
     * 手持ちの増減をオーバーレイに流す。
     * 数えるのはゲームの操作（拾う・報酬・引き出し・支払い・預ける）だけ。
     * チェストへの出し入れや捨てた分は、呼び出し元が無いので流れない。
     */
    private void notify(Player player, int delta, int total, String reason) {
        if (feed != null && delta != 0) {
            feed.publish(player.getUniqueId(), player.getName(), total, delta, reason);
        }
    }

    /** 地面の Shard を拾ったとき。拾う直前に呼ばれるので、拾った分を足した数を所持数とする。 */
    public void notePickup(Player player, int picked) {
        notify(player, picked, count(player) + picked, "拾った");
    }

    /** チェストや補給物資から取り出したとき。取り出した後に呼ばれるので、今の所持数をそのまま使う。 */
    public void noteFound(Player player, int found) {
        notify(player, found, count(player), "見つけた");
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
     *
     * @param reason オーバーレイに出す理由（「強化した」など）。手持ちから払った分だけ流す
     */
    public boolean takeWithPool(Player player, Nexus nexus, int amount, String reason) {
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
        notify(player, -fromOwn, own - fromOwn, reason);
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
            notify(player, -amount, 0, "ネクサスに預けた");
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
        notify(player, -amount, count(player), "ネクサスに預けた");
        return amount;
    }

    /** ネクサス内から最大 amount 個を引き出して手持ちに入れる。引き出した数を返す。 */
    public int withdraw(Player player, Nexus nexus, int amount) {
        int n = Math.min(Math.max(0, amount), nexus.sharedShards());
        if (n > 0) {
            nexus.sharedShards(nexus.sharedShards() - n);
            give(player, n, "引き出した");
        }
        return n;
    }

    /** 与えて、オーバーレイにも流す（報酬・引き出しなど、ゲームの操作で増えたとき）。 */
    public void give(Player player, int amount, String reason) {
        give(player, amount);
        notify(player, amount, count(player), reason);
    }

    /** 与える。インベントリが満杯なら足元にドロップする。オーバーレイには流さない（管理者の配布など）。 */
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
