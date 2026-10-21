package work.maiotanet.invasioncore.ui;

import net.kyori.adventure.text.Component;
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

    public NexusGui(InvasionConfig config, NexusManager nexuses, GameStageService stages,
                    ShardService shards, PerkService perks, WaveEngine waves) {
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
                boolean wasBroken = nexus.phase() == Nexus.Phase.BROKEN;
                if (waves.activate(nexus, player)) {
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
                    player.closeInventory();
                } else {
                    Sounds.fail(player);
                    if (!wasBroken) {
                        // 崩壊時は activate 側が Shard 不足の理由を通知済み
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
