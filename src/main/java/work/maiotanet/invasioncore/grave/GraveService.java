package work.maiotanet.invasioncore.grave;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rotatable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.util.Sounds;
import work.maiotanet.invasioncore.util.Text;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 死んだときの墓。死んだ場所にプレイヤーの頭を置き、所持品をその中にしまっておく。
 *
 * <p>そのまま落とすと、侵略者が群がる場所やネザーの溶岩際で死んだときに取り戻せない。
 * 一方で「誰でも拾える」と死体漁りが起きるので、最初の一定時間は本人だけが回収できる。
 * その間に他の人がクリックしたら本人に許可を聞き、許可されたら回収できる
 * （本人が拠点に戻れないとき、仲間に代わりに取ってきてもらうため）。</p>
 *
 * <p>中身は墓のブロックではなくこのサービスが持ち、graves.yml に保存する。
 * 回収されるまで消えないので、再起動を跨いでも残す必要がある。
 * 頭の上の名札（TextDisplay）は保存しない表示用で、チャンクが読み込まれるたびに作り直す。</p>
 */
public final class GraveService {

    /** 同じ人のクリックを受け付ける間隔（ミリ秒）。左右のクリックや連打で二重に処理しないため。 */
    private static final long CLICK_INTERVAL_MILLIS = 300L;

    /** 頭の向き。yaw を 22.5 度ずつに分けた順（南から右回り）。 */
    private static final BlockFace[] ROTATIONS = {
            BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
            BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST,
            BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST,
            BlockFace.EAST, BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST
    };

    /** 墓1つ分。 */
    private static final class Grave {
        final UUID id;
        final UUID owner;
        final String ownerName;
        final String world;
        final int x;
        final int y;
        final int z;
        final long createdAtMillis;
        final List<ItemStack> items;
        /** 墓を置く前のブロック（空気や水など）。回収したら戻す。 */
        final String original;
        /** 本人が回収を許可した人。保存しない（再起動したら頼み直してもらう）。 */
        final Set<UUID> approved = new HashSet<>();
        TextDisplay label;

        Grave(UUID id, UUID owner, String ownerName, String world, int x, int y, int z,
              long createdAtMillis, List<ItemStack> items, String original) {
            this.id = id;
            this.owner = owner;
            this.ownerName = ownerName;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.createdAtMillis = createdAtMillis;
            this.items = items;
            this.original = original;
        }

        Location location() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z);
        }

        String coords() {
            return x + ", " + y + ", " + z;
        }
    }

    /** 他の人からの「回収してよいか」の確認。 */
    private static final class Request {
        final UUID graveId;
        final UUID requester;
        final String requesterName;
        final long expiresAtMillis;
        boolean answered;

        Request(UUID graveId, UUID requester, String requesterName, long expiresAtMillis) {
            this.graveId = graveId;
            this.requester = requester;
            this.requesterName = requesterName;
            this.expiresAtMillis = expiresAtMillis;
        }
    }

    private final Plugin plugin;
    private final InvasionConfig config;
    private final File file;

    private final Map<String, Grave> byLocation = new LinkedHashMap<>();
    /** 確認待ち。キーは「墓ID:頼んだ人」。 */
    private final Map<String, Request> requests = new HashMap<>();
    /** 断られた人が、また頼めるようになる時刻。本人に何度も確認が飛ばないようにする。 */
    private final Map<String, Long> rejectedUntil = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private int ticks;

    public GraveService(Plugin plugin, InvasionConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.file = new File(plugin.getDataFolder(), "graves.yml");
    }

    private static String key(Block block) {
        return block.getWorld().getName() + ';' + block.getX() + ';' + block.getY() + ';' + block.getZ();
    }

    private static String key(Grave grave) {
        return grave.world + ';' + grave.x + ';' + grave.y + ';' + grave.z;
    }

    public boolean isGrave(Block block) {
        return block != null && byLocation.containsKey(key(block));
    }

    // ---------- 作る ----------

    /**
     * 死んだ場所に墓を作り、所持品をしまう。
     *
     * @return 作れたら墓の場所。作れなければ null で、呼び出し元は通常どおりアイテムを落とす。
     */
    public Location create(Player player, List<ItemStack> drops) {
        if (!config.graveEnabled()) {
            return null;
        }
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : drops) {
            if (stack != null && !stack.getType().isAir()) {
                items.add(stack.clone());
            }
        }
        if (items.isEmpty()) {
            return null;
        }
        Block block = findSpot(player.getLocation());
        if (block == null) {
            return null;
        }
        String original = block.getBlockData().getAsString();
        Grave grave = new Grave(UUID.randomUUID(), player.getUniqueId(), player.getName(),
                block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                System.currentTimeMillis(), items, original);
        if (!placeHead(block, grave, player.getLocation().getYaw())) {
            block.setBlockData(parse(original), false);
            return null;
        }
        byLocation.put(key(grave), grave);
        save();
        return block.getLocation();
    }

    /**
     * 墓を置ける場所を探す。まず死んだ場所の真上方向、次に周り3ブロック以内。
     * 空気を優先し、無ければ水・溶岩・草などの上書きできるブロックを使う。
     * 奈落や高度上限の外で死んだときは、ワールドの高さの範囲に収める。
     */
    private Block findSpot(Location origin) {
        World world = origin.getWorld();
        if (world == null) {
            return null;
        }
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;
        int ox = origin.getBlockX();
        int oy = Math.max(minY, Math.min(maxY, origin.getBlockY()));
        int oz = origin.getBlockZ();

        for (int pass = 0; pass < 2; pass++) {
            boolean airOnly = pass == 0;
            for (int dy = 0; dy <= 3; dy++) {
                Block b = candidate(world, ox, oy + dy, oz, minY, maxY, airOnly);
                if (b != null) {
                    return b;
                }
            }
            for (int r = 1; r <= 3; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.abs(dx) != r && Math.abs(dz) != r) {
                            continue;
                        }
                        for (int dy = -1; dy <= 3; dy++) {
                            Block b = candidate(world, ox + dx, oy + dy, oz + dz, minY, maxY, airOnly);
                            if (b != null) {
                                return b;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private Block candidate(World world, int x, int y, int z, int minY, int maxY, boolean airOnly) {
        if (y < minY || y > maxY) {
            return null;
        }
        Block b = world.getBlockAt(x, y, z);
        if (byLocation.containsKey(key(b))) {
            return null;
        }
        if (airOnly) {
            return b.getType().isAir() ? b : null;
        }
        return b.getType().isAir() || b.isLiquid() || b.isReplaceable() ? b : null;
    }

    /** プレイヤーの頭を置く。向きは死んだときに向いていた方向。 */
    private boolean placeHead(Block block, Grave grave, float yaw) {
        BlockData data = Material.PLAYER_HEAD.createBlockData();
        if (data instanceof Rotatable rotatable) {
            int index = Math.floorMod(Math.round(yaw / 22.5f), ROTATIONS.length);
            rotatable.setRotation(ROTATIONS[index]);
        }
        block.setBlockData(data, false);
        // getState() はスナップショットなので、実体の頭に持ち主を書き込むため getState(false) を使う
        if (!(block.getState(false) instanceof Skull skull)) {
            return false;
        }
        Player online = Bukkit.getPlayer(grave.owner);
        skull.setPlayerProfile(online != null
                ? online.getPlayerProfile()
                : Bukkit.createProfile(grave.owner, grave.ownerName));
        skull.update(true, false);
        return true;
    }

    // ---------- クリック ----------

    /**
     * 墓をクリックしたとき。
     *
     * @return 墓だったら true（呼び出し元はイベントを取り消す）
     */
    public boolean click(Player player, Block block) {
        Grave grave = byLocation.get(key(block));
        if (grave == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long last = lastClick.get(player.getUniqueId());
        if (last != null && now - last < CLICK_INTERVAL_MILLIS) {
            return true;
        }
        lastClick.put(player.getUniqueId(), now);

        if (player.getUniqueId().equals(grave.owner)
                || ownerOnlyRemainingMillis(grave, now) <= 0
                || grave.approved.contains(player.getUniqueId())) {
            collect(player, grave);
        } else {
            ask(player, grave, now);
        }
        return true;
    }

    private long ownerOnlyRemainingMillis(Grave grave, long now) {
        return grave.createdAtMillis + config.graveOwnerOnlySeconds() * 1000L - now;
    }

    /** 本人だけの期間に他の人がクリックした。本人に許可を聞く。 */
    private void ask(Player player, Grave grave, long now) {
        String reqKey = grave.id + ":" + player.getUniqueId();
        long remain = ownerOnlyRemainingMillis(grave, now);

        Request pending = requests.get(reqKey);
        if (pending != null && !pending.answered && now < pending.expiresAtMillis) {
            Text.actionBarWarn(player, grave.ownerName + " の返事を待っている（あと "
                    + seconds(pending.expiresAtMillis - now) + " 秒）");
            Sounds.fail(player);
            return;
        }
        Long until = rejectedUntil.get(reqKey);
        if (until != null && now < until) {
            Text.actionBarWarn(player, grave.ownerName + " に断られた。あと " + minutes(remain)
                    + " でだれでも回収できるようになる。");
            Sounds.fail(player);
            return;
        }
        Player owner = Bukkit.getPlayer(grave.owner);
        if (owner == null) {
            Text.actionBarWarn(player, "持ち主の " + grave.ownerName + " がいないので、あと "
                    + minutes(remain) + " は回収できない。");
            Sounds.fail(player);
            return;
        }

        int timeout = config.graveApprovalTimeoutSeconds();
        Request request = new Request(grave.id, player.getUniqueId(), player.getName(), now + timeout * 1000L);
        requests.put(reqKey, request);

        owner.sendMessage(Text.warn(player.getName() + " があなたの墓（" + grave.coords()
                + "）を回収したいと言っている。"));
        owner.sendMessage(Text.prefixed(Component.text()
                .append(button("[許可する]", Text.GOOD, "クリックで、" + player.getName() + " に回収を許可する",
                        request, true, timeout))
                .append(Text.of("  ", Text.MUTED))
                .append(button("[断る]", Text.ACCENT, "クリックで断る", request, false, timeout))
                .append(Text.of("  （" + timeout + " 秒以内）", Text.MUTED))
                .build()));
        owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f);

        player.sendMessage(Text.info("持ち主の " + grave.ownerName + " に、回収してよいか聞いた。"
                + "返事を待ってね（" + timeout + " 秒）。"));
        Sounds.click(player);
    }

    private Component button(String label, TextColor color, String hover,
                             Request request, boolean allow, int timeoutSeconds) {
        return Text.of(label, color)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Text.of(hover, Text.MUTED)))
                .clickEvent(ClickEvent.callback(audience -> answer(request, allow, audience),
                        options -> options.uses(1).lifetime(Duration.ofSeconds(timeoutSeconds))));
    }

    /** 本人がボタンを押した。 */
    private void answer(Request request, boolean allow, Audience audience) {
        Runnable task = () -> {
            if (!(audience instanceof Player owner)) {
                return;
            }
            Grave grave = byId(request.graveId);
            if (grave == null) {
                owner.sendMessage(Text.warn("その墓はもう回収されている。"));
                return;
            }
            if (!owner.getUniqueId().equals(grave.owner)) {
                return;
            }
            String reqKey = request.graveId + ":" + request.requester;
            if (request.answered || System.currentTimeMillis() >= request.expiresAtMillis) {
                owner.sendMessage(Text.warn("この確認はもう締め切られている。"));
                return;
            }
            request.answered = true;
            requests.remove(reqKey);
            Player requester = Bukkit.getPlayer(request.requester);
            if (allow) {
                grave.approved.add(request.requester);
                owner.sendMessage(Text.good(request.requesterName + " に回収を許可した。"));
                Sounds.success(owner);
                if (requester != null) {
                    requester.sendMessage(Text.good(grave.ownerName + " が許可してくれた。墓をクリックして回収してね。"));
                    Sounds.success(requester);
                }
            } else {
                rejectedUntil.put(reqKey, System.currentTimeMillis()
                        + config.graveApprovalTimeoutSeconds() * 1000L);
                owner.sendMessage(Text.info(request.requesterName + " の回収を断った。"));
                Sounds.click(owner);
                if (requester != null) {
                    requester.sendMessage(Text.warn(grave.ownerName + " に断られた。"));
                    Sounds.fail(requester);
                }
            }
        };
        // ボタンの処理はメインスレッドで動くはずだが、Bukkit API を触るので念のため寄せる
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private Grave byId(UUID id) {
        for (Grave grave : byLocation.values()) {
            if (grave.id.equals(id)) {
                return grave;
            }
        }
        return null;
    }

    // ---------- 回収 ----------

    /**
     * 中身を渡して墓を片付ける。
     * 先に記録とブロックを消してから渡す。逆だと、同じ tick の二重クリックで中身が増える。
     */
    private void collect(Player player, Grave grave) {
        remove(grave);
        List<ItemStack> leftovers = new ArrayList<>();
        PlayerInventory inv = player.getInventory();
        for (ItemStack stack : grave.items) {
            EquipmentSlot slot = stack.getType().getEquipmentSlot();
            boolean wearable = slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST
                    || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET || slot == EquipmentSlot.OFF_HAND;
            ItemStack current = wearable ? inv.getItem(slot) : null;
            if (wearable && (current == null || current.getType().isAir())) {
                // 防具は空いていればそのまま着せる。死ぬ前の格好にすぐ戻れるように
                inv.setItem(slot, stack);
                continue;
            }
            leftovers.addAll(inv.addItem(stack).values());
        }
        for (ItemStack stack : leftovers) {
            player.getWorld().dropItemNaturally(player.getLocation(), stack);
        }
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.0f, 1.0f);

        boolean own = player.getUniqueId().equals(grave.owner);
        String note = leftovers.isEmpty() ? "" : " 入り切らなかった分は足元に落とした。";
        player.sendMessage(Text.good((own ? "墓から所持品を取り戻した。" : grave.ownerName + " の墓を回収した。") + note));
        if (!own) {
            Player owner = Bukkit.getPlayer(grave.owner);
            if (owner != null) {
                owner.sendMessage(Text.warn(player.getName() + " があなたの墓（" + grave.coords() + "）を回収した。"));
            }
        }
    }

    /** 記録・確認待ち・名札・ブロックを消す。 */
    private void remove(Grave grave) {
        byLocation.remove(key(grave));
        requests.values().removeIf(r -> r.graveId.equals(grave.id));
        rejectedUntil.keySet().removeIf(k -> k.startsWith(grave.id + ":"));
        removeLabel(grave);
        Location loc = grave.location();
        if (loc != null) {
            loc.getBlock().setBlockData(parse(grave.original), false);
        }
        save();
    }

    private static BlockData parse(String data) {
        try {
            return Bukkit.createBlockData(data);
        } catch (IllegalArgumentException ex) {
            return Material.AIR.createBlockData();
        }
    }

    // ---------- 1秒ごと ----------

    public void tick() {
        long now = System.currentTimeMillis();
        expireRequests(now);
        rejectedUntil.values().removeIf(until -> until <= now);
        lastClick.values().removeIf(at -> now - at > 60_000L);
        boolean particles = ++ticks % 3 == 0;

        for (Grave grave : new ArrayList<>(byLocation.values())) {
            World world = Bukkit.getWorld(grave.world);
            if (world == null || !world.isChunkLoaded(grave.x >> 4, grave.z >> 4)) {
                // チャンクが読まれていなければ、名札は一緒に消えているので記録だけ捨てる
                grave.label = null;
                continue;
            }
            Block block = world.getBlockAt(grave.x, grave.y, grave.z);
            if (!keepHead(block, grave)) {
                continue;
            }
            updateLabel(grave, block, now);
            if (particles) {
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, block.getLocation().add(0.5, 1.4, 0.5),
                        3, 0.15, 0.3, 0.15, 0.01);
            }
        }
    }

    /** 返事が無いまま締め切った確認を片付けて、頼んだ人に伝える。 */
    private void expireRequests(long now) {
        Iterator<Map.Entry<String, Request>> it = requests.entrySet().iterator();
        while (it.hasNext()) {
            Request request = it.next().getValue();
            if (request.answered || now < request.expiresAtMillis) {
                continue;
            }
            it.remove();
            Player requester = Bukkit.getPlayer(request.requester);
            Grave grave = byId(request.graveId);
            if (requester != null && grave != null) {
                requester.sendMessage(Text.warn(grave.ownerName + " から返事がなかった。"));
            }
        }
    }

    /**
     * 頭が残っているか確かめる。/fill などで消されたら置き直し、
     * 別のブロックで埋められていたら中身をその場に落として墓を片付ける（中身を消さないため）。
     *
     * @return 墓がまだあれば true
     */
    private boolean keepHead(Block block, Grave grave) {
        Material type = block.getType();
        if (type == Material.PLAYER_HEAD || type == Material.PLAYER_WALL_HEAD) {
            return true;
        }
        if ((type.isAir() || block.isLiquid() || block.isReplaceable()) && placeHead(block, grave, 0f)) {
            return true;
        }
        plugin.getLogger().warning("墓（" + grave.ownerName + " / " + grave.world + " " + grave.coords()
                + "）が別のブロックで埋められていたので、中身をその場に落としました。");
        Location at = block.getLocation().add(0.5, 1.0, 0.5);
        for (ItemStack stack : grave.items) {
            block.getWorld().dropItemNaturally(at, stack);
        }
        byLocation.remove(key(grave));
        removeLabel(grave);
        save();
        return false;
    }

    /** 頭の上に、誰の墓か・誰が回収できるかを出す。 */
    private void updateLabel(Grave grave, Block block, long now) {
        long remain = ownerOnlyRemainingMillis(grave, now);
        Component text = Component.text()
                .append(Text.of(grave.ownerName + " の墓", Text.WARN))
                .append(Component.newline())
                .append(remain > 0
                        ? Text.of("本人だけが回収できる あと " + clock(remain), Text.MUTED)
                        : Text.of("だれでも回収できる", Text.GOOD))
                .append(Component.newline())
                .append(Text.of("クリックで回収", Text.MUTED))
                .build();
        if (grave.label == null || !grave.label.isValid()) {
            grave.label = block.getWorld().spawn(block.getLocation().add(0.5, 0.9, 0.5), TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setBillboard(Display.Billboard.CENTER);
                d.setViewRange(0.5f);
                d.text(text);
            });
            return;
        }
        grave.label.text(text);
    }

    private void removeLabel(Grave grave) {
        if (grave.label != null && grave.label.isValid()) {
            grave.label.remove();
        }
        grave.label = null;
    }

    private static long seconds(long millis) {
        return Math.max(1, (millis + 999) / 1000);
    }

    private static String minutes(long millis) {
        long s = seconds(millis);
        return s >= 60 ? ((s + 59) / 60) + " 分" : s + " 秒";
    }

    private static String clock(long millis) {
        long s = seconds(millis);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    // ---------- 永続化 ----------

    public void load() {
        byLocation.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("graves");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) {
                continue;
            }
            try {
                List<ItemStack> items = new ArrayList<>();
                for (ItemStack stack : ItemStack.deserializeItemsFromBytes(
                        Base64.getDecoder().decode(sec.getString("items", "")))) {
                    if (stack != null && !stack.getType().isAir()) {
                        items.add(stack);
                    }
                }
                Grave grave = new Grave(UUID.fromString(id), UUID.fromString(sec.getString("owner", "")),
                        sec.getString("owner-name", "?"), sec.getString("world", ""),
                        sec.getInt("x"), sec.getInt("y"), sec.getInt("z"),
                        sec.getLong("created"), items, sec.getString("original", "minecraft:air"));
                byLocation.put(key(grave), grave);
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING, "壊れた墓の記録をスキップしました: " + id, ex);
            }
        }
        if (!byLocation.isEmpty()) {
            plugin.getLogger().info("墓を " + byLocation.size() + " 個読み込みました。");
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("graves");
        for (Grave grave : byLocation.values()) {
            ConfigurationSection sec = root.createSection(grave.id.toString());
            sec.set("owner", grave.owner.toString());
            sec.set("owner-name", grave.ownerName);
            sec.set("world", grave.world);
            sec.set("x", grave.x);
            sec.set("y", grave.y);
            sec.set("z", grave.z);
            sec.set("created", grave.createdAtMillis);
            sec.set("original", grave.original);
            sec.set("items", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(grave.items)));
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("データフォルダを作成できませんでした: " + parent);
            }
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "graves.yml の保存に失敗しました", ex);
        }
    }

    /** サーバー停止時。名札を消して保存する（墓そのものは残す）。 */
    public void shutdown() {
        for (Grave grave : byLocation.values()) {
            removeLabel(grave);
        }
        save();
    }
}
