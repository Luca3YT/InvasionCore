package work.maiotanet.invasioncore.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import work.maiotanet.invasioncore.InvasionCore;
import work.maiotanet.invasioncore.economy.ShardService;
import work.maiotanet.invasioncore.item.InvasionItems;
import work.maiotanet.invasioncore.mob.BossType;
import work.maiotanet.invasioncore.mob.InvaderRegistry;
import work.maiotanet.invasioncore.mob.InvaderSpawner;
import work.maiotanet.invasioncore.mob.InvaderType;
import work.maiotanet.invasioncore.mob.MutationTier;
import work.maiotanet.invasioncore.nexus.Nexus;
import work.maiotanet.invasioncore.nexus.NexusManager;
import work.maiotanet.invasioncore.perk.PerkService;
import work.maiotanet.invasioncore.stage.GameStageService;
import work.maiotanet.invasioncore.ui.NexusGui;
import work.maiotanet.invasioncore.util.Text;
import work.maiotanet.invasioncore.wave.WaveEngine;
import work.maiotanet.invasioncore.wave.WaveModifier;
import work.maiotanet.invasioncore.world.BreachLog;
import work.maiotanet.invasioncore.world.WorldEventService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /invasion コマンド。
 * Paper の BasicCommand を使い、旧来の plugin.yml へのコマンド宣言は使わない。
 */
public final class InvasionCommands implements BasicCommand {

    private static final String ADMIN_PERMISSION = "invasioncore.admin";

    private static final List<String> ROOT = List.of(
            "status", "join", "leave", "stop", "perks", "top", "give", "admin", "help");
    private static final List<String> ITEMS = List.of(
            "nexus", "hammer", "shard", "kit",
            "turret_arrow", "turret_flame", "turret_frost", "turret_heal",
            "spike", "mine", "flash", "medkit", "barrier");
    private static final List<String> ADMIN = List.of(
            "forcewave", "setlevel", "setday", "restore", "despawn", "reload", "info",
            "list", "create", "activate", "spawn", "boss", "points", "airdrop");

    private final InvasionCore plugin;
    private final NexusManager nexuses;
    private final GameStageService stages;
    private final ShardService shards;
    private final PerkService perks;
    private final NexusGui gui;
    private final WaveEngine waves;
    private final InvaderRegistry registry;
    private final InvaderSpawner spawner;
    private final WorldEventService worldEvents;
    private final BreachLog breachLog;

    public InvasionCommands(InvasionCore plugin, NexusManager nexuses, GameStageService stages,
                            ShardService shards, PerkService perks, NexusGui gui,
                            WaveEngine waves, InvaderRegistry registry, InvaderSpawner spawner,
                            WorldEventService worldEvents, BreachLog breachLog) {
        this.plugin = plugin;
        this.nexuses = nexuses;
        this.stages = stages;
        this.shards = shards;
        this.perks = perks;
        this.gui = gui;
        this.waves = waves;
        this.registry = registry;
        this.spawner = spawner;
        this.worldEvents = worldEvents;
        this.breachLog = breachLog;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length == 0) {
            sendHelp(sender);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> status(sender);
            case "join" -> join(sender);
            case "leave" -> leave(sender);
            case "stop" -> stop(sender);
            case "perks" -> openPerks(sender);
            case "top" -> top(sender);
            case "give" -> give(sender, args);
            case "admin" -> admin(sender, args);
            default -> sendHelp(sender);
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length <= 1) {
            return filter(ROOT, args.length == 0 ? "" : args[0]);
        }
        String head = args[0].toLowerCase(Locale.ROOT);
        if (head.equals("give") && args.length == 2) {
            return filter(ITEMS, args[1]);
        }
        if (head.equals("admin") && args.length == 2) {
            return filter(ADMIN, args[1]);
        }
        return List.of();
    }

    private Collection<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : options) {
            if (s.startsWith(p)) {
                out.add(s);
            }
        }
        return out;
    }

    // ---------- 一般 ----------

    private void status(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.info("稼働中のネクサス: " + nexuses.active().size()
                    + " / 侵略者: " + registry.totalAlive()));
            return;
        }
        Nexus nexus = nexuses.nearest(player.getLocation());
        if (nexus == null) {
            player.sendMessage(Text.warn("近くにネクサスがない。"));
            return;
        }
        int stage = stages.computeCurrent(nexus);
        player.sendMessage(Text.info("━━ ネクサス状態 ━━"));
        player.sendMessage(Text.prefixed(Text.of("所有者: " + nexus.ownerName(), Text.MUTED)));
        player.sendMessage(Text.prefixed(Text.of("フェーズ: " + nexus.phase().label()
                + " / Day " + nexus.day(), Text.MUTED)));
        player.sendMessage(Text.prefixed(Text.of("レベル: " + nexus.level()
                + " / GameStage: " + stage, Text.MUTED)));
        player.sendMessage(Text.prefixed(Text.of("HP: " + (int) nexus.health() + " / "
                + (int) nexuses.maxHealth(nexus) + "  " + Text.bar(nexuses.healthRatio(nexus), 16), Text.MUTED)));
        int until = nexus.daysUntilBloodMoon(plugin.config().bloodMoonInterval());
        player.sendMessage(Text.prefixed(Text.of(until == 0
                ? "今夜は血の月" : "血の月まで残り " + until + " 日", Text.WARN)));
        if (nexus.phase() == Nexus.Phase.BLOOD_MOON) {
            player.sendMessage(Text.prefixed(Text.of("Wave " + nexus.currentWave() + " / "
                    + nexus.totalWaves() + " / 残敵 " + registry.aliveCount(nexus.id()), Text.WARN)));
            if (nexus.activeModifier() != null) {
                player.sendMessage(Text.prefixed(Text.of("変調: "
                        + WaveModifier.describe(nexus.activeModifier()), Text.ACCENT)));
            }
        }
        player.sendMessage(Text.prefixed(Text.of("所持 Shard: " + shards.count(player), Text.INFO)));
    }

    private void join(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.error("プレイヤー専用のコマンドだ。"));
            return;
        }
        Nexus nexus = nexuses.nearest(player.getLocation());
        if (nexus == null) {
            player.sendMessage(Text.warn("近くにネクサスがない。"));
            return;
        }
        nexus.members().add(player.getUniqueId());
        player.sendMessage(Text.good("防衛に参加した。"));
    }

    private void leave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.error("プレイヤー専用のコマンドだ。"));
            return;
        }
        int removed = 0;
        for (Nexus nexus : nexuses.all()) {
            if (nexus.members().remove(player.getUniqueId())) {
                removed++;
            }
        }
        player.sendMessage(removed > 0
                ? Text.good("防衛から離脱した。")
                : Text.warn("参加しているネクサスがない。"));
    }

    private void stop(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.error("プレイヤー専用のコマンドだ。"));
            return;
        }
        Nexus nexus = nexuses.nearest(player.getLocation());
        if (nexus == null) {
            player.sendMessage(Text.warn("近くにネクサスがない。"));
            return;
        }
        boolean owner = player.getUniqueId().equals(nexus.ownerId());
        if (!owner && !player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage(Text.error("停止できるのは所有者か管理者だけだ。"));
            return;
        }
        waves.deactivate(nexus, false);
        nexuses.save();
        player.sendMessage(Text.good("侵攻を停止し、残党を撤収させた。"));
    }

    private void openPerks(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.error("プレイヤー専用のコマンドだ。"));
            return;
        }
        gui.openPerks(player, nexuses.nearest(player.getLocation()));
    }

    private void top(CommandSender sender) {
        List<Map.Entry<UUID, PerkService.Profile>> ranking = perks.ranking(
                (a, b) -> Integer.compare(a.bloodMoonsSurvived(), b.bloodMoonsSurvived()), 10);
        if (ranking.isEmpty()) {
            sender.sendMessage(Text.warn("まだ記録がない。"));
            return;
        }
        sender.sendMessage(Text.info("━━ 血の月 踏破ランキング ━━"));
        for (int i = 0; i < ranking.size(); i++) {
            PerkService.Profile p = ranking.get(i).getValue();
            String name = p.lastKnownName().isEmpty()
                    ? ranking.get(i).getKey().toString().substring(0, 8)
                    : p.lastKnownName();
            sender.sendMessage(Text.prefixed(Text.of("  " + (i + 1) + ". " + name
                    + " — 踏破 " + p.bloodMoonsSurvived() + " 回 / 最高 Wave " + p.bestWave()
                    + " / 撃破 " + p.totalKills(), Text.MUTED)));
        }
    }

    // ---------- 配布 ----------

    private void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(Text.error("権限がない。"));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Text.error("プレイヤー専用のコマンドだ。"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.warn("使い方: /invasion give <" + String.join("|", ITEMS) + "> [個数]"));
            return;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(2304, Integer.parseInt(args[2])));
            } catch (NumberFormatException ex) {
                sender.sendMessage(Text.error("個数が数値でない。"));
                return;
            }
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "nexus" -> player.getInventory().addItem(InvasionItems.nexusBlock());
            case "hammer" -> player.getInventory().addItem(InvasionItems.hammer());
            case "shard" -> shards.give(player, amount);
            case "kit" -> player.getInventory().addItem(InvasionItems.repairKit(amount));
            case "turret_arrow" -> player.getInventory().addItem(
                    InvasionItems.turret(InvasionItems.TURRET_ARROW, amount));
            case "turret_flame" -> player.getInventory().addItem(
                    InvasionItems.turret(InvasionItems.TURRET_FLAME, amount));
            case "turret_frost" -> player.getInventory().addItem(
                    InvasionItems.turret(InvasionItems.TURRET_FROST, amount));
            case "turret_heal" -> player.getInventory().addItem(
                    InvasionItems.turret(InvasionItems.TURRET_HEAL, amount));
            case "spike" -> player.getInventory().addItem(
                    InvasionItems.trap(InvasionItems.TRAP_SPIKE, amount));
            case "mine" -> player.getInventory().addItem(
                    InvasionItems.trap(InvasionItems.TRAP_MINE, amount));
            case "flash" -> player.getInventory().addItem(InvasionItems.flashCharge(amount));
            case "medkit" -> player.getInventory().addItem(InvasionItems.medkit(amount));
            case "barrier" -> player.getInventory().addItem(InvasionItems.barrier(amount));
            default -> {
                sender.sendMessage(Text.error("不明なアイテム: " + args[1]));
                return;
            }
        }
        sender.sendMessage(Text.good("配布した。"));
    }

    // ---------- 管理 ----------

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(Text.error("権限がない。"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.warn("使い方: /invasion admin <" + String.join("|", ADMIN) + ">"));
            return;
        }
        Player player = sender instanceof Player p ? p : null;
        // プレイヤーは足元のネクサス、コンソールは登録簿の先頭を対象にする。
        // 複数基ある環境では /invasion admin list で並び順を確認できる。
        Nexus nexus = player != null
                ? nexuses.nearest(player.getLocation())
                : nexuses.all().stream().findFirst().orElse(null);

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "list" -> listNexuses(sender);
            case "create" -> createNexus(sender, args);
            case "activate" -> {
                if (requireNexus(sender, nexus)) {
                    // 管理コマンドなので Shard コストは取らず、崩壊状態からも復旧させる
                    if (waves.forceActivate(nexus)) {
                        nexuses.save();
                        sender.sendMessage(Text.good("ネクサスを起動した（HP "
                                + (int) nexus.health() + "）。"));
                    } else {
                        sender.sendMessage(Text.warn("すでに稼働している。"));
                    }
                }
            }
            case "spawn" -> spawnInvaders(sender, nexus, args);
            case "boss" -> spawnBoss(sender, nexus, args);
            case "points" -> grantPoints(sender, player, args);
            case "airdrop" -> {
                if (requireNexus(sender, nexus)) {
                    Location spot = worldEvents.forceAirDrop(nexus);
                    if (spot == null) {
                        sender.sendMessage(Text.error("投下地点を確保できなかった。"));
                    } else {
                        sender.sendMessage(Text.good("補給物資を投下した: "
                                + spot.getBlockX() + " " + spot.getBlockY() + " " + spot.getBlockZ()));
                    }
                }
            }
            case "reload" -> {
                plugin.config().reload();
                sender.sendMessage(Text.good("config.yml を再読み込みした。"));
            }
            case "info" -> sender.sendMessage(Text.info("ネクサス " + nexuses.all().size()
                    + " 基 / 稼働 " + nexuses.active().size()
                    + " / 侵略者 " + registry.totalAlive()));
            case "despawn" -> {
                if (requireNexus(sender, nexus)) {
                    registry.despawnAll(nexus.id());
                    sender.sendMessage(Text.good("侵略者を全て撤去した。"));
                }
            }
            case "restore" -> {
                if (requireNexus(sender, nexus)) {
                    int restored = breachLog.restore(nexus.id());
                    sender.sendMessage(Text.good(restored + " ブロックを復元した。"));
                }
            }
            case "forcewave" -> {
                if (requireNexus(sender, nexus)) {
                    if (nexus.phase() != Nexus.Phase.BLOOD_MOON) {
                        sender.sendMessage(Text.error("ブラッドムーン中ではない。"));
                        return;
                    }
                    nexus.nextWaveTick(0L);
                    sender.sendMessage(Text.good("次のウェーブを即座に投入する。"));
                }
            }
            case "setlevel" -> {
                if (requireNexus(sender, nexus) && requireNumber(sender, args, 2)) {
                    nexus.level(Integer.parseInt(args[2]));
                    nexus.health(nexuses.maxHealth(nexus));
                    nexuses.save();
                    sender.sendMessage(Text.good("ネクサスを Lv." + nexus.level() + " に設定した。"));
                }
            }
            case "setday" -> {
                if (requireNexus(sender, nexus) && requireNumber(sender, args, 2)) {
                    nexus.day(Integer.parseInt(args[2]));
                    nexuses.save();
                    sender.sendMessage(Text.good("Day を " + nexus.day() + " に設定した。"));
                }
            }
            default -> sender.sendMessage(Text.error("不明なサブコマンド: " + args[1]));
        }
    }

    private void listNexuses(CommandSender sender) {
        if (nexuses.all().isEmpty()) {
            sender.sendMessage(Text.warn("ネクサスは1基も無い。"));
            return;
        }
        sender.sendMessage(Text.info("━━ ネクサス一覧 ━━"));
        int index = 0;
        for (Nexus n : nexuses.all()) {
            Location loc = n.location();
            String where = loc == null
                    ? n.worldName() + "（未ロード）"
                    : loc.getWorld().getName() + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
            sender.sendMessage(Text.prefixed(Text.of("  [" + index + "] " + where
                    + " / " + n.phase().label() + " / Lv." + n.level()
                    + " / Day " + n.day() + " / HP " + (int) n.health(), Text.MUTED)));
            index++;
        }
    }

    /** コンソールからネクサスを設置する。プレイヤー不在での初期設定に使う。 */
    private void createNexus(CommandSender sender, String[] args) {
        if (args.length < 6) {
            sender.sendMessage(Text.warn("使い方: /invasion admin create <world> <x> <y> <z>"));
            return;
        }
        World world = plugin.getServer().getWorld(args[2]);
        if (world == null) {
            sender.sendMessage(Text.error("ワールドが見つからない: " + args[2]));
            return;
        }
        int x;
        int y;
        int z;
        try {
            x = Integer.parseInt(args[3]);
            y = Integer.parseInt(args[4]);
            z = Integer.parseInt(args[5]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Text.error("座標が数値でない。"));
            return;
        }
        Location loc = new Location(world, x, y, z);
        if (nexuses.at(loc) != null) {
            sender.sendMessage(Text.error("その座標には既にネクサスがある。"));
            return;
        }
        loc.getBlock().setType(Material.LODESTONE);
        Nexus nexus = nexuses.create(loc, null, sender.getName());
        nexuses.save();
        sender.sendMessage(Text.good("ネクサスを設置した: " + world.getName()
                + " " + x + "," + y + "," + z + "（HP " + (int) nexus.health() + "）"));
    }

    /** 指定種別の侵略Mobを湧かせる。バランス確認とトラブルシュートに使う。 */
    private void spawnInvaders(CommandSender sender, Nexus nexus, String[] args) {
        if (!requireNexus(sender, nexus)) {
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.warn("使い方: /invasion admin spawn <type> [数]"));
            sender.sendMessage(Text.prefixed(Text.of(invaderTypeNames(), Text.MUTED)));
            return;
        }
        InvaderType type;
        try {
            type = InvaderType.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(Text.error("不明な種別: " + args[2]));
            sender.sendMessage(Text.prefixed(Text.of(invaderTypeNames(), Text.MUTED)));
            return;
        }
        int count = 1;
        if (args.length >= 4) {
            try {
                count = Math.max(1, Math.min(40, Integer.parseInt(args[3])));
            } catch (NumberFormatException ex) {
                sender.sendMessage(Text.error("数が不正だ: " + args[3]));
                return;
            }
        }
        int stage = stages.computeCurrent(nexus);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            if (spawner.spawn(nexus, type, MutationTier.roll(stage), stage, 0) != null) {
                spawned++;
            }
        }
        sender.sendMessage(Text.good(type.displayName() + " を " + spawned + " / " + count
                + " 体 湧かせた（GameStage " + stage + "）。"));
        if (spawned < count) {
            sender.sendMessage(Text.warn("湧き位置が確保できず一部失敗した。"));
        }
    }

    private void spawnBoss(CommandSender sender, Nexus nexus, String[] args) {
        if (!requireNexus(sender, nexus)) {
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.warn("使い方: /invasion admin boss <name>"));
            sender.sendMessage(Text.prefixed(Text.of(bossTypeNames(), Text.MUTED)));
            return;
        }
        BossType boss = BossType.parse(args[2].toUpperCase(Locale.ROOT));
        if (boss == null) {
            sender.sendMessage(Text.error("不明なボス: " + args[2]));
            sender.sendMessage(Text.prefixed(Text.of(bossTypeNames(), Text.MUTED)));
            return;
        }
        int stage = stages.computeCurrent(nexus);
        if (spawner.spawnBoss(nexus, boss, MutationTier.NORMAL, stage, 0) != null) {
            sender.sendMessage(Text.good(boss.displayName() + " を出現させた（GameStage " + stage + "）。"));
        } else {
            sender.sendMessage(Text.error("湧き位置が確保できなかった。"));
        }
    }

    /**
     * パークポイントを付与する。
     *
     * <p>通常はブラッドムーンの踏破でしか手に入らないため、
     * 検証時や、事故で進行データを失った際の補填手段が無いと運用できない。</p>
     */
    private void grantPoints(CommandSender sender, Player player, String[] args) {
        if (player == null) {
            sender.sendMessage(Text.error("プレイヤー専用のコマンドだ。"));
            return;
        }
        if (!requireNumber(sender, args, 2)) {
            sender.sendMessage(Text.warn("使い方: /invasion admin points <数>"));
            return;
        }
        int amount = Math.max(0, Math.min(999, Integer.parseInt(args[2])));
        perks.addPoints(player.getUniqueId(), amount);
        sender.sendMessage(Text.good("パークポイントを " + amount + " 付与した（所持 "
                + perks.profile(player).points() + "）。"));
    }

    private String invaderTypeNames() {
        StringBuilder sb = new StringBuilder("種別: ");
        for (InvaderType t : InvaderType.values()) {
            sb.append(t.name().toLowerCase(Locale.ROOT)).append(' ');
        }
        return sb.toString().trim();
    }

    private String bossTypeNames() {
        StringBuilder sb = new StringBuilder("ボス: ");
        for (BossType b : BossType.values()) {
            sb.append(b.name().toLowerCase(Locale.ROOT)).append(' ');
        }
        return sb.toString().trim();
    }

    private boolean requireNexus(CommandSender sender, Nexus nexus) {
        if (nexus == null) {
            sender.sendMessage(Text.error("近くにネクサスがない。"));
            return false;
        }
        return true;
    }

    private boolean requireNumber(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            sender.sendMessage(Text.error("数値を指定すること。"));
            return false;
        }
        try {
            Integer.parseInt(args[index]);
            return true;
        } catch (NumberFormatException ex) {
            sender.sendMessage(Text.error("数値が不正だ: " + args[index]));
            return false;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Text.info("━━ InvasionCore ━━"));
        sender.sendMessage(Text.prefixed(Text.of("/invasion status — ネクサスの状態", Text.MUTED)));
        sender.sendMessage(Text.prefixed(Text.of("/invasion join / leave — 防衛への参加・離脱", Text.MUTED)));
        sender.sendMessage(Text.prefixed(Text.of("/invasion perks — パーク画面", Text.MUTED)));
        sender.sendMessage(Text.prefixed(Text.of("/invasion top — ランキング", Text.MUTED)));
        sender.sendMessage(Text.prefixed(Text.of("/invasion stop — 侵攻の停止（所有者）", Text.MUTED)));
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(Text.prefixed(Text.of("/invasion give <item> [数] — アイテム配布", Text.MUTED)));
            sender.sendMessage(Text.prefixed(Text.of("/invasion admin <sub> — 管理コマンド", Text.MUTED)));
        }
    }
}
