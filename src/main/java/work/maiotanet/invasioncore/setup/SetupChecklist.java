package work.maiotanet.invasioncore.setup;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.nexus.Nexus;
import work.maiotanet.invasioncore.nexus.NexusManager;
import work.maiotanet.invasioncore.stage.GameStageService;
import work.maiotanet.invasioncore.util.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * ゲーム開始前の確認項目（/invasion setup）。
 *
 * <p>難易度がピースフル、時間が止まっている、ネザーに設置した、といった設定ミスは、
 * 起動しても「何も起きない」という形でしか表に出ず、配信中に原因を探す羽目になる。
 * 開始前に一覧で見せ、直すコマンドもクリック一つで入力欄に入れられるようにしておく。</p>
 */
public final class SetupChecklist {

    /** 確認結果の重さ。開始できないものだけを FAIL にする。 */
    public enum Status {
        OK("✔", Text.GOOD),
        WARN("⚠", Text.WARN),
        FAIL("✘", NamedTextColor.RED);

        private final String mark;
        private final TextColor color;

        Status(String mark, TextColor color) {
            this.mark = mark;
            this.color = color;
        }
    }

    /**
     * 1項目分の結果。
     *
     * @param hint       直し方の説明。不要なら null
     * @param fixCommand クリックで入力欄に入れるコマンド。コマンドで直せないものは null
     */
    public record Check(Status status, String message, String hint, String fixCommand) {

        static Check ok(String message) {
            return new Check(Status.OK, message, null, null);
        }
    }

    private final InvasionConfig config;
    private final NexusManager nexuses;
    private final GameStageService stages;

    /** Web 機能の状態。config で有効でも、ポートが使えないなどで動いていないことがある。 */
    private boolean webEnabled;
    private work.maiotanet.invasioncore.web.WebServer web;

    public SetupChecklist(InvasionConfig config, NexusManager nexuses, GameStageService stages) {
        this.config = config;
        this.nexuses = nexuses;
        this.stages = stages;
    }

    public void web(boolean enabled, work.maiotanet.invasioncore.web.WebServer web) {
        this.webEnabled = enabled;
        this.web = web;
    }

    // ---------- 確認 ----------

    /**
     * 確認を実行する。
     *
     * @param origin 実行したプレイヤーの位置。コンソールなら null（登録簿の先頭のネクサスを見る）
     */
    public List<Check> run(Location origin) {
        Nexus nexus = origin != null
                ? nexuses.nearest(origin)
                : nexuses.primary();
        World world = nexus != null && nexus.world() != null
                ? nexus.world()
                : origin != null ? origin.getWorld() : Bukkit.getWorlds().get(0);

        List<Check> checks = new ArrayList<>();
        checks.add(checkNexus(nexus, world, origin != null));
        checks.add(checkDifficulty(world));
        checks.add(checkDaylightCycle(world));
        checks.add(checkMobGriefing(world));
        checks.add(checkTntExplodes(world));
        checks.add(checkKeepInventory(world));
        checks.add(checkPvp(world));
        checks.add(checkSpawnProtection(nexus, origin, world));
        checks.add(checkSimulationDistance(nexus, world));
        if (nexus != null) {
            checks.add(checkParticipants(nexus));
        }
        if (webEnabled) {
            checks.add(checkWeb());
        }
        return checks;
    }

    private Check checkNexus(Nexus nexus, World world, boolean byPlayer) {
        if (nexus == null) {
            return byPlayer
                    ? new Check(Status.FAIL,
                    "近く（" + config.nexusDefenseRadius() + " ブロック以内）にネクサスがありません。",
                    "ネクサスを受け取って、拠点の中心に置いてください。",
                    "/invasion give nexus")
                    : new Check(Status.FAIL, "ネクサスが1基もありません。",
                    "/invasion admin create <ワールド> <x> <y> <z> で設置してください。", null);
        }
        if (world.isFixedTime()) {
            // ネザーやエンドは昼夜が無いので、日数も血の月も進まない
            return new Check(Status.FAIL,
                    "ネクサスが昼夜の無いワールド（" + world.getName() + "）にあります。日数が進みません。",
                    "オーバーワールドに置き直してください。", null);
        }
        String where = "（" + nexus.worldName() + " " + coords(nexus) + "）";
        return switch (nexus.phase()) {
            case BROKEN -> new Check(Status.FAIL, "ネクサスが崩壊しています" + where + "。",
                    "管理者コマンドなら Shard なしで復旧できます。", "/invasion admin activate");
            case DORMANT -> Check.ok("ネクサスを設置済みです" + where + "。まだ起動していません。");
            default -> Check.ok("ネクサスは稼働中です" + where + "。Day " + nexus.day()
                    + "・" + nexus.phase().label());
        };
    }

    private Check checkDifficulty(World world) {
        Difficulty difficulty = world.getDifficulty();
        if (difficulty == Difficulty.PEACEFUL) {
            return new Check(Status.FAIL, "難易度がピースフルになっています。",
                    "ピースフルでは侵略者が出た瞬間に消えてしまいます。", "/difficulty normal");
        }
        return Check.ok("難易度は" + difficultyName(difficulty) + "です。");
    }

    private Check checkDaylightCycle(World world) {
        GameRule<Boolean> rule = GameRule.DO_DAYLIGHT_CYCLE;
        if (isFalse(world, rule)) {
            return new Check(Status.FAIL, "時間が止まっています（" + ruleName(rule) + " が false）。",
                    "日数が進まず、血の月が来ません。", gamerule(rule, true));
        }
        return Check.ok("時間が進む設定です。");
    }

    private Check checkMobGriefing(World world) {
        GameRule<Boolean> rule = GameRule.MOB_GRIEFING;
        if (isFalse(world, rule)) {
            return new Check(Status.WARN, "Mob がブロックを壊せない設定です（" + ruleName(rule) + " が false）。",
                    "爆導体が自爆しても壁が壊れず、防衛が簡単になりすぎます。", gamerule(rule, true));
        }
        return Check.ok("Mob がブロックを壊せる設定です。");
    }

    private Check checkTntExplodes(World world) {
        GameRule<Boolean> rule = GameRule.TNT_EXPLODES;
        if (isFalse(world, rule)) {
            return new Check(Status.WARN, "TNT が爆発しない設定です（" + ruleName(rule) + " が false）。",
                    "投擲手の爆弾が不発になります。", gamerule(rule, true));
        }
        return Check.ok("TNT が爆発する設定です。");
    }

    private Check checkKeepInventory(World world) {
        GameRule<Boolean> rule = GameRule.KEEP_INVENTORY;
        if (Boolean.TRUE.equals(world.getGameRuleValue(rule))) {
            return new Check(Status.WARN, "死んでも持ち物が消えない設定です（" + ruleName(rule) + " が true）。",
                    "死亡時バックパックが働かず、死んでも失うものが無くなります。", gamerule(rule, false));
        }
        return Check.ok("死亡時バックパックが使える設定です。");
    }

    private Check checkPvp(World world) {
        GameRule<Boolean> rule = GameRule.PVP;
        if (Boolean.TRUE.equals(world.getGameRuleValue(rule))) {
            return new Check(Status.WARN, "PvP が有効になっています（" + ruleName(rule) + " が true）。",
                    "参加者同士で攻撃できてしまいます。", gamerule(rule, false));
        }
        return Check.ok("PvP は無効です。");
    }

    private Check checkSpawnProtection(Nexus nexus, Location origin, World world) {
        int radius = Bukkit.getSpawnRadius();
        if (radius <= 0) {
            return Check.ok("スポーン保護はありません。");
        }
        Location target = nexus != null ? nexus.center() : origin;
        Location spawn = world.getSpawnLocation();
        if (target != null && target.getWorld() != null && target.getWorld().equals(spawn.getWorld())
                && Math.max(Math.abs(target.getBlockX() - spawn.getBlockX()),
                Math.abs(target.getBlockZ() - spawn.getBlockZ())) <= radius) {
            return new Check(Status.WARN, "スポーン保護（半径 " + radius + "）の中にネクサスがあります。",
                    "OP 以外は周りに建築できません。server.properties の spawn-protection を 0 にして"
                            + "再起動するか、ネクサスを保護の外に置いてください。", null);
        }
        return Check.ok("ネクサスはスポーン保護の外にあります。");
    }

    /**
     * 侵略者はネクサスから離れたリング上に湧く。プレイヤーがネクサスにいるとき、
     * そこまでがシミュレーション距離に入っていないと、湧いた侵略者が止まったままになる。
     */
    private Check checkSimulationDistance(Nexus nexus, World world) {
        int spawnRadius = nexus != null ? nexuses.spawnRadiusMax(nexus) : config.spawnRadiusMax();
        int required = (int) Math.ceil(spawnRadius / 16.0) + 1;
        int actual = world.getSimulationDistance();
        if (actual < required) {
            return new Check(Status.WARN, "シミュレーション距離が " + actual + " チャンクです（"
                    + required + " 以上が必要）。",
                    "遠くに湧いた侵略者が動かないことがあります。server.properties の simulation-distance を "
                            + required + " 以上にして再起動してください。", null);
        }
        return Check.ok("シミュレーション距離は " + actual + " チャンクです。");
    }

    /** 管理画面と OBS オーバーレイ。VPS ではポートの開放と public-url の設定が要る。 */
    private Check checkWeb() {
        if (web == null || !web.isRunning()) {
            return new Check(Status.WARN, "管理画面・OBS オーバーレイの Web サーバーが動いていません。",
                    "起動時のログを確認してください。ポートが使用中なら config.yml の web.port を変えて再起動します。", null);
        }
        if (!web.hasPublicUrl()) {
            return new Check(Status.WARN, "web.public-url が未設定です。表示される URL はこのマシンの LAN 用です。",
                    "外部のサーバー（VPS など）なら、config.yml に http://<サーバーのIPかドメイン>:<web.port> を書いて再起動してください。",
                    "/invasion admin web");
        }
        return Check.ok("管理画面・OBS オーバーレイを外から開ける設定です（/invasion admin web で URL を表示）。");
    }

    private Check checkParticipants(Nexus nexus) {
        int count = stages.participants(nexus).size();
        if (count == 0) {
            return new Check(Status.WARN, "参加者がいません（サーバーにいる人のうち、観戦モード以外）。",
                    "参加者はサーバーに入ると自動で数えられます。/invasion leave で抜けた人は /invasion join で戻れます。", null);
        }
        return Check.ok("参加者は " + count + " 人です。");
    }

    // ---------- 表示 ----------

    /** チャットに出す行を組み立てる。直せる項目は行全体をクリックできるようにする。 */
    public List<Component> render(List<Check> checks, Nexus nexus) {
        List<Component> lines = new ArrayList<>();
        lines.add(Text.info("━━ 開始前チェック ━━"));

        int fails = 0;
        int warns = 0;
        for (Check check : checks) {
            if (check.status() == Status.FAIL) {
                fails++;
            } else if (check.status() == Status.WARN) {
                warns++;
            }
            lines.add(line(check));
        }

        if (fails > 0) {
            lines.add(Text.prefixed(Text.of("開始できない項目が " + fails + " 件あります。"
                    + "赤い項目をクリックすると、直すコマンドが入力欄に入ります。", NamedTextColor.RED)));
        } else if (warns > 0) {
            lines.add(Text.prefixed(Text.of("開始はできますが、注意が " + warns + " 件あります。",
                    Text.WARN)));
        } else {
            lines.add(Text.prefixed(Text.of("すべてクリアしました。", Text.GOOD)));
        }

        Component actions = Text.prefixed(button("もう一度チェックする", "/invasion setup"));
        if (fails == 0 && nexus != null && nexus.phase() == Nexus.Phase.DORMANT) {
            lines.add(Text.prefixed(Text.of(
                    "ネクサスを右クリックして「侵攻を起動する」を押すと開始します。", Text.GOOD)));
            actions = actions.append(Text.plain("  ")).append(button("今すぐ起動する", "/invasion admin activate"));
        }
        lines.add(actions);
        return lines;
    }

    /**
     * 1項目の行。理由（hint）はチャットに行を足さず、カーソルを合わせたときにだけ出す。
     * 項目が多いとチャットが埋まり、肝心の ✘ / ⚠ の行が流れてしまうため。
     */
    private Component line(Check check) {
        Component line = Text.of("  [" + check.status().mark + "] " + check.message(), check.status().color);
        if (check.hint() != null) {
            line = line.hoverEvent(HoverEvent.showText(Text.of(check.hint(), Text.MUTED)));
        }
        if (check.fixCommand() != null) {
            line = line
                    .append(Text.of("  " + check.fixCommand(), Text.INFO).decorate(TextDecoration.UNDERLINED))
                    .clickEvent(ClickEvent.suggestCommand(check.fixCommand()));
        }
        return line;
    }

    private Component button(String label, String command) {
        return Text.of("[" + label + "]", Text.INFO)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.suggestCommand(command));
    }

    // ---------- 部品 ----------

    private static boolean isFalse(World world, GameRule<Boolean> rule) {
        return Boolean.FALSE.equals(world.getGameRuleValue(rule));
    }

    /**
     * ゲームルールの名前はバージョンで変わる（26.x で doDaylightCycle が advance_time になった）ので、
     * 決め打ちせず API が返す実際の名前を使う。名前空間 minecraft: は省いても通るので、短く見せる。
     */
    private static String ruleName(GameRule<?> rule) {
        String name = rule.getName();
        return name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
    }

    private static String gamerule(GameRule<Boolean> rule, boolean value) {
        return "/gamerule " + ruleName(rule) + " " + value;
    }

    private static String coords(Nexus nexus) {
        Location loc = nexus.location();
        return loc == null ? "未ロード" : loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    private static String difficultyName(Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> "ピースフル";
            case EASY -> "イージー";
            case NORMAL -> "ノーマル";
            case HARD -> "ハード";
        };
    }
}
