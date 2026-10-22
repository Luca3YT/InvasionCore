package work.maiotanet.invasioncore.gadget;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.economy.ShardService;
import work.maiotanet.invasioncore.item.InvasionItems;
import work.maiotanet.invasioncore.nexus.Nexus;
import work.maiotanet.invasioncore.nexus.NexusManager;
import work.maiotanet.invasioncore.util.Sounds;
import work.maiotanet.invasioncore.util.Text;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 帰還の羅針盤。詠唱が終わるとネクサスのそばへ戻る。
 *
 * <p>遠出した参加者が、血の月や攻撃の警告に間に合うよう戻れるようにする。
 * 戦闘からの即脱出に使われないよう詠唱を挟み、その間に動くかダメージを受けたら中断する。
 * 羅針盤は詠唱が終わったときに 1 個減らす（中断したら減らない）。</p>
 *
 * <p>ゲーム開始時にいた全員と、開始後に初めて入ってきた人に 1 個ずつ配る。
 * 誰に配ったかはネクサスに保存し、入り直しても増えないようにする。</p>
 */
public final class RecallService implements Listener {

    /** 詠唱中に動いてよい距離（ブロック）。向きを変えるだけで中断しないよう、少しだけ許す。 */
    private static final double MOVE_TOLERANCE = 0.3;

    /** 詠唱中の人。値は詠唱を始めた場所と終わる時刻。 */
    private record Cast(Location start, long endsAtMillis) {
    }

    private final InvasionConfig config;
    private final NexusManager nexuses;
    private final ShardService shards;
    private final Map<UUID, Cast> casting = new HashMap<>();

    public RecallService(InvasionConfig config, NexusManager nexuses, ShardService shards) {
        this.config = config;
        this.nexuses = nexuses;
        this.shards = shards;
    }

    // ---------- 使う ----------

    /**
     * 右クリックで詠唱を始める。
     * 空中への右クリックは最初から取り消し扱いで届くので、ignoreCancelled にはしない。
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || !InvasionItems.is(event.getItem(), InvasionItems.RECALL)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (casting.containsKey(player.getUniqueId())) {
            return;
        }
        if (nexuses.standingSpot(nexuses.primary()) == null) {
            Text.actionBarWarn(player, "ネクサスがまだ置かれていない。");
            Sounds.fail(player);
            return;
        }
        long now = System.currentTimeMillis();
        casting.put(player.getUniqueId(),
                new Cast(player.getLocation().clone(), now + config.recallCastSeconds() * 1000L));
        player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.4f, 1.6f);
        showProgress(player, config.recallCastSeconds() * 1000L);
    }

    /** ダメージを受けたら中断する。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && casting.containsKey(player.getUniqueId())) {
            cancel(player, "ダメージを受けたので、帰還を中断した。");
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        casting.remove(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        casting.remove(event.getPlayer().getUniqueId());
    }

    /** 2 tick ごと。動いたら中断し、時間が来たら戻す。 */
    public void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Cast>> it = casting.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Cast> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            Cast cast = entry.getValue();
            if (player == null) {
                it.remove();
                continue;
            }
            Location loc = player.getLocation();
            if (!loc.getWorld().equals(cast.start().getWorld())
                    || loc.distanceSquared(cast.start()) > MOVE_TOLERANCE * MOVE_TOLERANCE) {
                it.remove();
                Text.actionBarWarn(player, "動いたので、帰還を中断した。");
                Sounds.fail(player);
                continue;
            }
            long remain = cast.endsAtMillis() - now;
            if (remain > 0) {
                showProgress(player, remain);
                player.getWorld().spawnParticle(Particle.PORTAL, loc.clone().add(0, 1.0, 0),
                        6, 0.4, 0.6, 0.4, 0.3);
                continue;
            }
            it.remove();
            finish(player);
        }
    }

    private void showProgress(Player player, long remainMillis) {
        long total = config.recallCastSeconds() * 1000L;
        double ratio = 1.0 - (double) remainMillis / total;
        long seconds = (remainMillis + 999) / 1000;
        Text.actionBar(player, Text.of("帰還中 ", Text.INFO)
                .append(Text.of(Text.bar(ratio, 10), Text.GOOD))
                .append(Text.of(" あと " + seconds + " 秒（動くと中断）", Text.MUTED)));
    }

    private void finish(Player player) {
        Location spot = nexuses.standingSpot(nexuses.primary());
        if (spot == null) {
            Text.actionBarWarn(player, "ネクサスが無くなったので、帰還できなかった。");
            Sounds.fail(player);
            return;
        }
        // 詠唱中に羅針盤を手放していたら戻さない（落として仲間に渡す、などで二重に使えないように）
        if (!consumeOne(player)) {
            Text.actionBarWarn(player, "帰還の羅針盤を持っていないので、帰還できなかった。");
            Sounds.fail(player);
            return;
        }
        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0, 1.0, 0),
                40, 0.4, 0.8, 0.4, 0.05);
        player.teleport(spot);
        player.playSound(spot, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, spot.clone().add(0, 1.0, 0),
                40, 0.4, 0.8, 0.4, 0.05);
        Text.actionBarGood(player, "ネクサスに帰還した。");
    }

    private void cancel(Player player, String message) {
        casting.remove(player.getUniqueId());
        Text.actionBarWarn(player, message);
        Sounds.fail(player);
    }

    private static boolean consumeOne(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (!InvasionItems.is(stack, InvasionItems.RECALL)) {
                continue;
            }
            if (stack.getAmount() <= 1) {
                inv.setItem(i, null);
            } else {
                stack.setAmount(stack.getAmount() - 1);
                inv.setItem(i, stack);
            }
            return true;
        }
        return false;
    }

    // ---------- 配る ----------

    /** ゲーム開始の瞬間。いまいる全員に配る。 */
    public void onGameStart() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            giveStarter(player);
        }
    }

    /** 開始後に入ってきた人には、まだ受け取っていなければ配る。 */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Nexus nexus = nexuses.primary();
        if (nexus != null && nexus.isActive()) {
            giveStarter(event.getPlayer());
        }
    }

    private void giveStarter(Player player) {
        Nexus nexus = nexuses.primary();
        if (nexus == null || !nexus.recallGiven().add(player.getUniqueId())) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(InvasionItems.recall(1));
        for (ItemStack stack : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), stack);
        }
        player.sendMessage(Text.good("帰還の羅針盤をもらった。右クリックして動かずに待つと、ネクサスのそばへ戻れる。"));
    }

    // ---------- ネクサスの画面で買う ----------

    /** 手持ちの Shard で 1 個買う。ネクサス内の Shard は使わない（みんなの強化・修理用のため）。 */
    public boolean buy(Player player) {
        int price = config.recallPrice();
        if (!shards.take(player, price)) {
            Text.actionBarError(player, "Shard が足りない（" + price + " 必要、所持 " + shards.count(player) + "）");
            Sounds.fail(player);
            return false;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(InvasionItems.recall(1));
        for (ItemStack stack : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), stack);
        }
        Text.actionBarGood(player, "帰還の羅針盤を買った。（-" + price + " Shard）");
        Sounds.success(player);
        return true;
    }

    public int price() {
        return config.recallPrice();
    }
}
