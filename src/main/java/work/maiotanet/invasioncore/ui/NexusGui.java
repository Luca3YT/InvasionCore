package work.maiotanet.invasioncore.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.economy.ShardService;
import work.maiotanet.invasioncore.economy.ShopService;
import work.maiotanet.invasioncore.gadget.RecallService;
import work.maiotanet.invasioncore.nexus.Nexus;
import work.maiotanet.invasioncore.nexus.NexusManager;
import work.maiotanet.invasioncore.nexus.NexusUpgrade;
import work.maiotanet.invasioncore.perk.Perk;
import work.maiotanet.invasioncore.perk.PerkService;
import work.maiotanet.invasioncore.stage.GameStageService;
import work.maiotanet.invasioncore.util.Sounds;
import work.maiotanet.invasioncore.util.Text;
import work.maiotanet.invasioncore.wave.WaveEngine;
import work.maiotanet.invasioncore.wave.WaveModifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ネクサスとパークの GUI。
 */
public final class NexusGui {

    /** メインメニューのスロット割り当て。 */
    private static final int SLOT_STATUS = 4;
    private static final int SLOT_ACTIVATE = 11;
    private static final int SLOT_REWARDS = 13;
    private static final int SLOT_UPGRADE = 15;
    private static final int SLOT_PERK = 22;
    private static final int SLOT_DEPOSIT = 20;
    private static final int SLOT_POOL = 24;
    private static final int SLOT_RECALL = 18;
    private static final int SLOT_SHOP = 26;

    /** SHOP の画面の品の欄（上 5 段）と、戻るボタンの位置。 */
    private static final int SHOP_ITEM_SLOTS = 45;
    private static final int SHOP_BACK_SLOT = 49;

    /**
     * 侵攻の起動・再建ができる権限。参加者が勝手に押すと、配信者の準備が終わる前にゲームが始まってしまう。
     */
    private static final String ACTIVATE_PERMISSION = "invasioncore.admin";

    /** ネクサス内の Shard を引き出せる権限。共有の財産を独り占めされないよう管理者だけにする。 */
    private static final String WITHDRAW_PERMISSION = "invasioncore.admin";

    /** 引き出し画面のボタン位置と個数。-1 は「全部」。 */
    private static final int[] WITHDRAW_SLOTS = {11, 12, 14, 15};
    private static final int[] WITHDRAW_AMOUNTS = {1, 10, 64, -1};
    private static final int SHARD_BACK_SLOT = 26;

    /** パーク画面（45スロット）の配置。2行に分けて最大14種まで並べられる。 */
    private static final int[] PERK_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
    };
    private static final int PERK_BACK_SLOT = 40;

    private final InvasionConfig config;
    private final NexusManager nexuses;
    private final GameStageService stages;
    private final ShardService shards;
    private final PerkService perks;
    private final WaveEngine waves;
    private final RecallService recall;
    private final ShopService shop;

    public NexusGui(InvasionConfig config, NexusManager nexuses, GameStageService stages,
                    ShardService shards, PerkService perks, WaveEngine waves, RecallService recall,
                    ShopService shop) {
        this.recall = recall;
        this.shop = shop;
        this.config = config;
        this.nexuses = nexuses;
        this.stages = stages;
        this.shards = shards;
        this.perks = perks;
        this.waves = waves;
    }

    // ---------- 表示 ----------

    public void openMain(Player player, Nexus nexus) {
        InvasionHolder holder = new InvasionHolder(InvasionHolder.Kind.MAIN, nexus.id());
        Inventory inv = Bukkit.createInventory(holder, 27, Text.of("ネクサス", Text.ACCENT));
        holder.inventory(inv);

        int stage = stages.computeCurrent(nexus);
        int until = nexus.daysUntilBloodMoon(config.bloodMoonInterval());

        List<String> statusLore = new ArrayList<>();
        statusLore.add("フェーズ: " + nexus.phase().label());
        statusLore.add("Day " + nexus.day() + (until == 0 ? "（今夜は血の月）" : " / 血の月まで " + until + " 日"));
        statusLore.add("レベル: " + nexus.level());
        statusLore.add("GameStage: " + stage);
        statusLore.add("HP: " + (int) nexus.health() + " / " + (int) nexuses.maxHealth(nexus));
        statusLore.add(Text.bar(nexuses.healthRatio(nexus), 20));
        if (nexus.phase() == Nexus.Phase.BLOOD_MOON) {
            statusLore.add("Wave " + nexus.currentWave() + " / " + nexus.totalWaves());
            if (nexus.activeModifier() != null) {
                statusLore.add("変調: " + WaveModifier.describe(nexus.activeModifier()));
            }
        }
        inv.setItem(SLOT_STATUS, icon(Material.LODESTONE,
                Text.of("状態", Text.INFO), statusLore));

        boolean broken = nexus.phase() == Nexus.Phase.BROKEN;
        boolean canActivate = nexus.phase() == Nexus.Phase.DORMANT || broken;
        List<String> activateLore;
        if (!canActivate) {
            activateLore = List.of("既に稼働している。", "停止は /invasion stop から。");
        } else if (broken) {
            activateLore = List.of("崩壊したネクサスを再建する。",
                    "必要 Shard: " + waves.rebuildCost(nexus),
                    "所持 Shard: " + shards.count(player),
                    "ネクサス内の Shard: " + nexus.sharedShards());
        } else {
            activateLore = List.of("クリックでネクサスを起動する。", "起動すると日数が進み始める。");
        }
        if (canActivate && !player.hasPermission(ACTIVATE_PERMISSION)) {
            activateLore = List.of("起動できるのは運営だけ。", "運営の合図を待ってね。");
        }
        inv.setItem(SLOT_ACTIVATE, icon(
                canActivate ? Material.LIME_DYE : Material.GRAY_DYE,
                Text.of(canActivate ? (broken ? "ネクサスを再建する" : "侵攻を起動する") : "稼働中",
                        canActivate ? Text.GOOD : Text.MUTED),
                activateLore));

        inv.setItem(SLOT_REWARDS, icon(Material.CHEST,
                Text.of("報酬を受け取る", Text.WARN),
                List.of("保管中: " + nexus.rewardBuffer().size() + " スタック",
                        "クリックで全て受け取る。")));

        inv.setItem(SLOT_UPGRADE, icon(Material.ANVIL,
                Text.of("アップグレード", Text.INFO),
                List.of("Shard を消費してネクサスを強化する。",
                        "所持 Shard: " + shards.count(player),
                        "ネクサス内の Shard: " + nexus.sharedShards())));

        inv.setItem(SLOT_DEPOSIT, icon(Material.AMETHYST_SHARD,
                Text.of("Shard をネクサスに預ける", Text.GOOD),
                List.of("クリックで手持ちの Shard を全部預ける。",
                        "Shard を持ってネクサスを右クリックしても預けられる。",
                        "預けた Shard は、みんなの強化・修理で",
                        "手持ちが足りない分に使われる。",
                        "所持 Shard: " + shards.count(player))));

        List<String> poolLore = new ArrayList<>();
        poolLore.add("ネクサス内の Shard: " + nexus.sharedShards());
        poolLore.add("手持ちが足りないときに自動で使われる。");
        if (player.hasPermission(WITHDRAW_PERMISSION)) {
            poolLore.add("クリックで引き出す（管理者のみ）");
        }
        inv.setItem(SLOT_POOL, icon(Material.AMETHYST_CLUSTER,
                Text.of("ネクサス内の Shard", Text.INFO), poolLore));

        inv.setItem(SLOT_RECALL, icon(Material.RECOVERY_COMPASS,
                Text.of("帰還の羅針盤を買う（" + recall.price() + " Shard）", Text.INFO),
                List.of("右クリックして動かずに待つと、",
                        "どこからでもネクサスのそばへ戻れる。使うとなくなる。",
                        "手持ちの Shard で払う（ネクサス内の Shard は使わない）。",
                        "所持 Shard: " + shards.count(player))));

        inv.setItem(SLOT_SHOP, icon(Material.EMERALD,
                Text.of("SHOP", Text.GOOD),
                List.of("Shard で道具・弾薬・素材を買う。",
                        "手持ちが足りない分はネクサス内の Shard から払う。",
                        "所持 Shard: " + shards.count(player),
                        "ネクサス内の Shard: " + nexus.sharedShards())));

        inv.setItem(SLOT_PERK, icon(Material.EXPERIENCE_BOTTLE,
                Text.of("パーク", Text.GOOD),
                List.of("残りポイント: " + perks.profile(player).points())));

        fill(inv);
        player.openInventory(inv);
    }

    public void openUpgrades(Player player, Nexus nexus) {
        InvasionHolder holder = new InvasionHolder(InvasionHolder.Kind.UPGRADE, nexus.id());
        Inventory inv = Bukkit.createInventory(holder, 27, Text.of("ネクサス強化", Text.INFO));
        holder.inventory(inv);

        NexusUpgrade[] all = NexusUpgrade.values();
        for (int i = 0; i < all.length; i++) {
            NexusUpgrade up = all[i];
            int level = nexus.upgradeLevel(up);
            int cost = up.costFor(level);
            // 表示と実際の請求額がずれないよう、購入時と同じ計算（パーク割引込み）を使う
            cost = cost < 0 ? -1 : upgradeCost(player, nexus, cost);

            List<String> lore = new ArrayList<>();
            lore.add(up.description());
            lore.add("Lv " + level + " / " + up.maxLevel());
            lore.add(Text.bar((double) level / up.maxLevel(), 10));
            lore.add(cost < 0 ? "上限に達している。" : "必要 Shard: " + cost);
            lore.add("所持 Shard: " + shards.count(player));
            lore.add("ネクサス内の Shard: " + nexus.sharedShards());
            inv.setItem(10 + i + (i >= 3 ? 1 : 0), icon(up.icon(),
                    Text.of(up.displayName(), Text.WARN), lore));
        }
        inv.setItem(26, icon(Material.ARROW, Text.of("戻る", Text.MUTED), List.of()));
        fill(inv);
        player.openInventory(inv);
    }

    public void openPerks(Player player, Nexus nexus) {
        InvasionHolder holder = new InvasionHolder(InvasionHolder.Kind.PERK,
                nexus == null ? null : nexus.id());
        Inventory inv = Bukkit.createInventory(holder, 45, Text.of("パーク", Text.GOOD));
        holder.inventory(inv);

        PerkService.Profile profile = perks.profile(player);
        Perk[] all = Perk.values();
        for (int i = 0; i < all.length && i < PERK_SLOTS.length; i++) {
            Perk perk = all[i];
            int level = profile.level(perk);
            int cost = perk.costFor(level);

            List<String> lore = new ArrayList<>();
            lore.add(perk.description());
            lore.add("Lv " + level + " / " + perk.maxLevel());
            lore.add(Text.bar((double) level / perk.maxLevel(), 10));
            lore.add(cost < 0 ? "上限に達している。" : "必要ポイント: " + cost);
            lore.add("残りポイント: " + profile.points());
            inv.setItem(PERK_SLOTS[i], icon(perk.icon(),
                    Text.of(perk.displayName(), Text.GOOD), lore));
        }

        inv.setItem(4, icon(Material.PAPER, Text.of("累計戦績", Text.INFO),
                List.of("撃破数: " + profile.totalKills(),
                        "血の月踏破: " + profile.bloodMoonsSurvived() + " 回",
                        "最高到達ウェーブ: " + profile.bestWave(),
                        "残りポイント: " + profile.points())));
        if (nexus != null) {
            inv.setItem(PERK_BACK_SLOT, icon(Material.ARROW, Text.of("戻る", Text.MUTED), List.of()));
        }
        fill(inv);
        player.openInventory(inv);
    }

    // ---------- クリック処理 ----------

    /** メインメニューのクリック。処理したら true。 */
    public boolean handleMain(Player player, Nexus nexus, int slot) {
        switch (slot) {
            case SLOT_ACTIVATE -> {
                if (!nexus.isActive() && !player.hasPermission(ACTIVATE_PERMISSION)) {
                    Sounds.fail(player);
                    player.sendMessage(Text.warn("ネクサスを起動できるのは運営だけ。運営の合図を待ってね。"));
                    return true;
                }
                boolean wasBroken = nexus.phase() == Nexus.Phase.BROKEN;
                if (waves.activate(nexus, player)) {
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
                    player.closeInventory();
                } else {
                    Sounds.fail(player);
                    if (nexus.isActive()) {
                        // 起動できなかった理由（Shard 不足・浮いている）は activate 側が通知済み
                        player.sendMessage(Text.warn("すでに稼働している。"));
                    }
                }
                return true;
            }
            case SLOT_REWARDS -> {
                collectRewards(player, nexus);
                openMain(player, nexus);
                return true;
            }
            case SLOT_UPGRADE -> {
                Sounds.click(player);
                openUpgrades(player, nexus);
                return true;
            }
            case SLOT_PERK -> {
                Sounds.click(player);
                openPerks(player, nexus);
                return true;
            }
            case SLOT_SHOP -> {
                Sounds.click(player);
                openShop(player, nexus);
                return true;
            }
            case SLOT_RECALL -> {
                if (recall.buy(player)) {
                    openMain(player, nexus);
                }
                return true;
            }
            case SLOT_DEPOSIT -> {
                deposit(player, nexus, shards.depositAll(player, nexus));
                openMain(player, nexus);
                return true;
            }
            case SLOT_POOL -> {
                if (player.hasPermission(WITHDRAW_PERMISSION)) {
                    Sounds.click(player);
                    openShard(player, nexus);
                }
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    public boolean handleUpgrade(Player player, Nexus nexus, int slot) {
        if (slot == 26) {
            Sounds.click(player);
            openMain(player, nexus);
            return true;
        }
        NexusUpgrade[] all = NexusUpgrade.values();
        for (int i = 0; i < all.length; i++) {
            int expected = 10 + i + (i >= 3 ? 1 : 0);
            if (expected != slot) {
                continue;
            }
            NexusUpgrade up = all[i];
            int level = nexus.upgradeLevel(up);
            int cost = up.costFor(level);
            if (cost < 0) {
                player.sendMessage(Text.warn(up.displayName() + " は上限に達している。"));
                Sounds.fail(player);
                return true;
            }
            int actual = upgradeCost(player, nexus, cost);
            if (!shards.takeWithPool(player, nexus, actual, "アップグレード")) {
                player.sendMessage(Text.error("Shard が足りない（必要 " + actual
                        + " / 所持 " + shards.count(player) + " / ネクサス内 " + nexus.sharedShards() + "）"));
                Sounds.fail(player);
                return true;
            }
            nexus.upgradeLevel(up, level + 1);
            if (up == NexusUpgrade.MAX_HEALTH) {
                // 最大HPが増えた分は即座に反映させる
                nexus.health(Math.min(nexuses.maxHealth(nexus), nexus.health() + 0.15 * nexuses.maxHealth(nexus)));
            }
            player.sendMessage(Text.good(up.displayName() + " を Lv." + (level + 1) + " に強化した。"));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
            openUpgrades(player, nexus);
            return true;
        }
        return true;
    }

    public boolean handlePerk(Player player, Nexus nexus, int slot) {
        if (slot == PERK_BACK_SLOT && nexus != null) {
            Sounds.click(player);
            openMain(player, nexus);
            return true;
        }
        Perk[] all = Perk.values();
        for (int i = 0; i < all.length && i < PERK_SLOTS.length; i++) {
            if (PERK_SLOTS[i] != slot) {
                continue;
            }
            Perk perk = all[i];
            if (perks.spend(player.getUniqueId(), perk)) {
                player.sendMessage(Text.good(perk.displayName() + " を Lv."
                        + perks.profile(player).level(perk) + " に上げた。"));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.4f);
            } else {
                player.sendMessage(Text.error("ポイントが足りないか、上限に達している。"));
                Sounds.fail(player);
            }
            openPerks(player, nexus);
            return true;
        }
        return true;
    }

    /** アップグレードの実請求額。整備術式の割引と、本人の「建築士」パークの両方を掛ける。 */
    private int upgradeCost(Player player, Nexus nexus, int base) {
        return Math.max(1, (int) Math.round(nexuses.discounted(nexus, base)
                * perks.costMultiplier(player.getUniqueId())));
    }

    // ---------- ネクサス内の Shard ----------

    /** 預けた結果を知らせる。右クリックで預けたときも同じ表示にする。 */
    public void deposit(Player player, Nexus nexus, int amount) {
        if (amount <= 0) {
            Text.actionBarWarn(player, "預けられる Shard を持っていない。");
            Sounds.fail(player);
            return;
        }
        nexuses.save();
        Text.actionBarGood(player, "Shard を " + amount + " 個ネクサスに預けた。（ネクサス内 "
                + nexus.sharedShards() + "）");
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.2f);
    }

    /** 管理者用の引き出し画面。 */
    public void openShard(Player player, Nexus nexus) {
        InvasionHolder holder = new InvasionHolder(InvasionHolder.Kind.SHARD, nexus.id());
        Inventory inv = Bukkit.createInventory(holder, 27, Text.of("ネクサス内の Shard", Text.INFO));
        holder.inventory(inv);

        inv.setItem(SLOT_STATUS, icon(Material.AMETHYST_CLUSTER,
                Text.of("ネクサス内の Shard: " + nexus.sharedShards(), Text.INFO),
                List.of("所持 Shard: " + shards.count(player))));
        for (int i = 0; i < WITHDRAW_SLOTS.length; i++) {
            int amount = WITHDRAW_AMOUNTS[i];
            String label = amount < 0 ? "全部引き出す" : amount + " 個引き出す";
            inv.setItem(WITHDRAW_SLOTS[i], icon(Material.AMETHYST_SHARD,
                    Text.of(label, Text.GOOD), List.of("ネクサス内の Shard を手持ちに移す。")));
        }
        inv.setItem(SHARD_BACK_SLOT, icon(Material.ARROW, Text.of("戻る", Text.MUTED), List.of()));
        fill(inv);
        player.openInventory(inv);
    }

    public boolean handleShard(Player player, Nexus nexus, int slot) {
        if (slot == SHARD_BACK_SLOT) {
            Sounds.click(player);
            openMain(player, nexus);
            return true;
        }
        if (!player.hasPermission(WITHDRAW_PERMISSION)) {
            player.closeInventory();
            return true;
        }
        for (int i = 0; i < WITHDRAW_SLOTS.length; i++) {
            if (WITHDRAW_SLOTS[i] != slot) {
                continue;
            }
            int requested = WITHDRAW_AMOUNTS[i] < 0 ? nexus.sharedShards() : WITHDRAW_AMOUNTS[i];
            int taken = shards.withdraw(player, nexus, requested);
            if (taken > 0) {
                nexuses.save();
                Text.actionBarGood(player, "Shard を " + taken + " 個引き出した。（ネクサス内 "
                        + nexus.sharedShards() + "）");
                Sounds.success(player);
            } else {
                Text.actionBarWarn(player, "ネクサス内に Shard が無い。");
                Sounds.fail(player);
            }
            openShard(player, nexus);
            return true;
        }
        return true;
    }

    // ---------- SHOP ----------

    public void openShop(Player player, Nexus nexus) {
        InvasionHolder holder = new InvasionHolder(InvasionHolder.Kind.SHOP, nexus.id());
        Inventory inv = Bukkit.createInventory(holder, 54, Text.of("SHOP", Text.GOOD));
        holder.inventory(inv);

        List<ShopService.Entry> entries = shop.entries();
        int available = shards.available(player, nexus);
        for (int i = 0; i < entries.size() && i < SHOP_ITEM_SLOTS; i++) {
            ShopService.Entry entry = entries.get(i);
            ItemStack shown = entry.item().clone();
            ItemMeta meta = shown.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
                lore.add(Component.empty());
                lore.add(Text.of("値段: " + priceLabel(entry),
                        available >= entry.buyPrice(1) ? Text.GOOD : NamedTextColor.RED));
                lore.add(Text.of("クリックで売買の画面へ（売ることもできる）", Text.MUTED));
                meta.lore(lore);
                shown.setItemMeta(meta);
            }
            inv.setItem(i, shown);
        }
        inv.setItem(SHOP_BACK_SLOT, icon(Material.ARROW, Text.of("戻る", Text.MUTED),
                List.of("所持 Shard: " + shards.count(player),
                        "ネクサス内の Shard: " + nexus.sharedShards())));
        fill(inv);
        player.openInventory(inv);
    }

    public boolean handleShop(Player player, Nexus nexus, int slot) {
        if (slot == SHOP_BACK_SLOT) {
            Sounds.click(player);
            openMain(player, nexus);
            return true;
        }
        List<ShopService.Entry> entries = shop.entries();
        if (slot < 0 || slot >= entries.size() || slot >= SHOP_ITEM_SLOTS) {
            return true;
        }
        Sounds.click(player);
        openShopTrade(player, nexus, slot, 1);
        return true;
    }

    /** 「8 個で 6 Shard」「1 個 8 Shard」。 */
    private static String priceLabel(ShopService.Entry entry) {
        return entry.lot() > 1
                ? entry.lot() + " 個で " + entry.price() + " Shard"
                : "1 個 " + entry.price() + " Shard";
    }

    /** 売買の画面の個数のボタン（欄 → 増やす数）。 */
    private static final int[] TRADE_STEP_SLOTS = {9, 10, 11, 15, 16, 17};
    private static final int[] TRADE_STEPS = {-64, -10, -1, 1, 10, 64};
    private static final int TRADE_ITEM_SLOT = 4;
    private static final int TRADE_COUNT_SLOT = 13;
    private static final int TRADE_BACK_SLOT = 18;
    private static final int TRADE_BUY_SLOT = 21;
    private static final int TRADE_SELL_SLOT = 23;
    /** 一度に売り買いできる個数の上限。 */
    private static final int TRADE_MAX = 64;

    private static final int CONFIRM_ITEM_SLOT = 4;
    private static final int CONFIRM_YES_SLOT = 11;
    private static final int CONFIRM_NO_SLOT = 15;

    /** SHOP の品。設定を読み直して並びが変わっていたら null。 */
    private ShopService.Entry shopEntry(int index) {
        List<ShopService.Entry> entries = shop.entries();
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    /** 1 品の売買の画面。上に品、真ん中で個数を選び、下で買う・売るを選ぶ。 */
    public void openShopTrade(Player player, Nexus nexus, int index, int count) {
        ShopService.Entry entry = shopEntry(index);
        if (entry == null) {
            openShop(player, nexus);
            return;
        }
        count = Math.max(1, Math.min(TRADE_MAX, count));
        InvasionHolder holder = new InvasionHolder(InvasionHolder.Kind.SHOP_TRADE, nexus.id()).shop(index, count, false);
        Inventory inv = Bukkit.createInventory(holder, 27, Text.of("SHOP — 個数を選ぶ", Text.GOOD));
        holder.inventory(inv);

        int owned = ShopService.owned(player, entry);
        inv.setItem(TRADE_ITEM_SLOT, shownItem(entry, count, List.of(
                Text.of("値段: " + priceLabel(entry), Text.INFO),
                Text.of("持っている数: " + owned + " 個", Text.MUTED))));
        for (int i = 0; i < TRADE_STEP_SLOTS.length; i++) {
            int step = TRADE_STEPS[i];
            inv.setItem(TRADE_STEP_SLOTS[i], icon(step < 0 ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
                    Text.of((step < 0 ? "−" : "＋") + Math.abs(step), step < 0 ? NamedTextColor.RED : Text.GOOD),
                    List.of("個数を " + Math.abs(step) + (step < 0 ? " 減らす" : " 増やす") + "（1〜" + TRADE_MAX + " 個）")));
        }
        inv.setItem(TRADE_COUNT_SLOT, icon(Material.NAME_TAG, Text.of("個数: " + count + " 個", Text.WARN),
                List.of("左右のボタンで変える")));

        int buyTotal = entry.buyPrice(count);
        int available = shards.available(player, nexus);
        boolean canBuy = available >= buyTotal;
        inv.setItem(TRADE_BUY_SLOT, icon(Material.EMERALD, Text.of(count + " 個買う", canBuy ? Text.GOOD : NamedTextColor.RED),
                List.of("合計: " + buyTotal + " Shard",
                        "使える Shard: " + available + "（手持ち " + shards.count(player) + "＋ネクサス内 " + nexus.sharedShards() + "）",
                        canBuy ? "手持ちから払い、足りない分はネクサス内から払う" : "Shard が足りない")));

        int sellTotal = entry.sellPrice(count, shop.sellRatio());
        String sellNote;
        if (owned < count) {
            sellNote = "持っている数が足りない（" + owned + " 個）";
        } else if (sellTotal <= 0) {
            sellNote = "この数では値段が付かない（もっとまとめて売る）";
        } else {
            sellNote = "受け取った Shard は手持ちに入る";
        }
        boolean canSell = owned >= count && sellTotal > 0;
        inv.setItem(TRADE_SELL_SLOT, icon(Material.GOLD_INGOT, Text.of(count + " 個売る", canSell ? Text.WARN : NamedTextColor.RED),
                List.of("受け取り: " + sellTotal + " Shard", "持っている数: " + owned + " 個", sellNote)));
        inv.setItem(TRADE_BACK_SLOT, icon(Material.ARROW, Text.of("戻る", Text.MUTED), List.of("SHOP の一覧へ")));
        fill(inv);
        player.openInventory(inv);
    }

    public boolean handleShopTrade(Player player, Nexus nexus, InvasionHolder holder, int slot) {
        int index = holder.shopIndex();
        int count = holder.shopCount();
        for (int i = 0; i < TRADE_STEP_SLOTS.length; i++) {
            if (TRADE_STEP_SLOTS[i] == slot) {
                Sounds.click(player);
                openShopTrade(player, nexus, index, count + TRADE_STEPS[i]);
                return true;
            }
        }
        if (slot == TRADE_BACK_SLOT) {
            Sounds.click(player);
            openShop(player, nexus);
            return true;
        }
        ShopService.Entry entry = shopEntry(index);
        if (entry == null) {
            openShop(player, nexus);
            return true;
        }
        if (slot == TRADE_BUY_SLOT) {
            int total = entry.buyPrice(count);
            if (shards.available(player, nexus) < total) {
                Text.actionBarError(player, "Shard が足りない（" + total + " 必要、使えるのは "
                        + shards.available(player, nexus) + "）");
                Sounds.fail(player);
                return true;
            }
            Sounds.click(player);
            openShopConfirm(player, nexus, index, count, false);
        } else if (slot == TRADE_SELL_SLOT) {
            int owned = ShopService.owned(player, entry);
            if (owned < count) {
                Text.actionBarError(player, "持っている数が足りない（" + count + " 個必要、持っているのは " + owned + " 個）");
                Sounds.fail(player);
                return true;
            }
            if (entry.sellPrice(count, shop.sellRatio()) <= 0) {
                Text.actionBarWarn(player, "この数では値段が付かない。もっとまとめて売ってね。");
                Sounds.fail(player);
                return true;
            }
            Sounds.click(player);
            openShopConfirm(player, nexus, index, count, true);
        }
        return true;
    }

    /** 買う・売るの確認の画面。間違えて押したときに引き返せるよう、もう一度だけ聞く。 */
    private void openShopConfirm(Player player, Nexus nexus, int index, int count, boolean sell) {
        ShopService.Entry entry = shopEntry(index);
        if (entry == null) {
            openShop(player, nexus);
            return;
        }
        InvasionHolder holder = new InvasionHolder(InvasionHolder.Kind.SHOP_CONFIRM, nexus.id()).shop(index, count, sell);
        Inventory inv = Bukkit.createInventory(holder, 27, Text.of(sell ? "SHOP — 売っていい？" : "SHOP — 買っていい？", Text.GOOD));
        holder.inventory(inv);
        String deal = sell
                ? count + " 個売って " + entry.sellPrice(count, shop.sellRatio()) + " Shard 受け取る"
                : count + " 個を " + entry.buyPrice(count) + " Shard で買う";
        inv.setItem(CONFIRM_ITEM_SLOT, shownItem(entry, count, List.of(Text.of(deal, Text.WARN))));
        inv.setItem(CONFIRM_YES_SLOT, icon(Material.LIME_CONCRETE, Text.of(sell ? "売る" : "買う", Text.GOOD), List.of(deal)));
        inv.setItem(CONFIRM_NO_SLOT, icon(Material.RED_CONCRETE, Text.of("やめる", NamedTextColor.RED),
                List.of("個数を選ぶ画面に戻る")));
        fill(inv);
        player.openInventory(inv);
    }

    public boolean handleShopConfirm(Player player, Nexus nexus, InvasionHolder holder, int slot) {
        int index = holder.shopIndex();
        int count = holder.shopCount();
        if (slot == CONFIRM_NO_SLOT) {
            Sounds.click(player);
            openShopTrade(player, nexus, index, count);
            return true;
        }
        if (slot != CONFIRM_YES_SLOT) {
            return true;
        }
        ShopService.Entry entry = shopEntry(index);
        if (entry == null) {
            openShop(player, nexus);
            return true;
        }
        // 確認の画面を開いている間に持ち物や Shard が変わっているかもしれないので、もう一度確かめる
        if (holder.shopSell()) {
            int total = entry.sellPrice(count, shop.sellRatio());
            if (total <= 0 || !ShopService.removeOwned(player, entry, count)) {
                Text.actionBarError(player, "持っている数が足りない。");
                Sounds.fail(player);
                openShopTrade(player, nexus, index, count);
                return true;
            }
            shards.give(player, total, "SHOP で売った");
            Text.actionBarGood(player, count + " 個売った。（+" + total + " Shard）");
        } else {
            int total = entry.buyPrice(count);
            if (!shards.takeWithPool(player, nexus, total, "SHOP で買った")) {
                Text.actionBarError(player, "Shard が足りない（" + total + " 必要、使えるのは "
                        + shards.available(player, nexus) + "）");
                Sounds.fail(player);
                openShopTrade(player, nexus, index, count);
                return true;
            }
            ShopService.give(player, entry, count);
            nexuses.save();
            Text.actionBarGood(player, count + " 個買った。（-" + total + " Shard）");
        }
        Sounds.success(player);
        // 続けて同じ品を売り買いしやすいよう、個数を選ぶ画面に戻す
        openShopTrade(player, nexus, index, count);
        return true;
    }

    /** 画面に出す品。個数ぶん重ねて見せ（最大 64）、説明を足す。 */
    private static ItemStack shownItem(ShopService.Entry entry, int count, List<Component> extra) {
        ItemStack shown = entry.item().clone();
        shown.setAmount(Math.max(1, Math.min(count, shown.getMaxStackSize())));
        ItemMeta meta = shown.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.empty());
            lore.addAll(extra);
            meta.lore(lore);
            shown.setItemMeta(meta);
        }
        return shown;
    }

    private void collectRewards(Player player, Nexus nexus) {
        if (nexus.rewardBuffer().isEmpty()) {
            player.sendMessage(Text.warn("受け取れる報酬はない。"));
            Sounds.fail(player);
            return;
        }
        int handed = 0;
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack stack : nexus.rewardBuffer()) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            if (leftover.isEmpty()) {
                handed++;
            } else {
                remaining.addAll(leftover.values());
            }
        }
        nexus.rewardBuffer().clear();
        nexus.rewardBuffer().addAll(remaining);

        player.sendMessage(Text.good(handed + " スタックを受け取った。"
                + (remaining.isEmpty() ? "" : " インベントリが満杯のため " + remaining.size() + " スタックが残っている。")));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
    }

    // ---------- 部品 ----------

    private ItemStack icon(Material material, Component name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            List<Component> lines = new ArrayList<>(lore.size());
            for (String s : lore) {
                lines.add(Text.of(s, Text.MUTED));
            }
            meta.lore(lines);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void fill(Inventory inv) {
        ItemStack filler = icon(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of());
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }
    }
}
