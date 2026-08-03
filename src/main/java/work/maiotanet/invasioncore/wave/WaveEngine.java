package work.maiotanet.invasioncore.wave;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.economy.ContributionTracker;
import work.maiotanet.invasioncore.economy.RewardService;
import work.maiotanet.invasioncore.economy.ShardService;
import work.maiotanet.invasioncore.mob.BossType;
import work.maiotanet.invasioncore.mob.InvaderAiService;
import work.maiotanet.invasioncore.mob.InvaderRegistry;
import work.maiotanet.invasioncore.mob.InvaderSpawner;
import work.maiotanet.invasioncore.mob.InvaderType;
import work.maiotanet.invasioncore.mob.MutationTier;
import work.maiotanet.invasioncore.nexus.Nexus;
import work.maiotanet.invasioncore.nexus.NexusCombatSink;
import work.maiotanet.invasioncore.nexus.NexusManager;
import work.maiotanet.invasioncore.nexus.NexusUpgrade;
import work.maiotanet.invasioncore.perk.PerkService;
import work.maiotanet.invasioncore.stage.GameStageService;
import work.maiotanet.invasioncore.util.Text;
import work.maiotanet.invasioncore.world.BreachLog;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 侵攻の中核。7日周期とブラッドムーンのウェーブ戦を駆動する。
 *
 * <p>侵略MODの「毎晩襲撃」は緊張が持続せず単調になりやすい。
 * 7DTD の7日周期を骨格に据えることで、準備フェーズ（探索・建築・強化）と
 * 決戦フェーズ（ブラッドムーン）が分離し、拠点を作る動機が生まれる。</p>
 */
public final class WaveEngine implements NexusCombatSink {

    /** 夜の開始時刻（tick）。 */
    private static final long DUSK = 13000L;
    /** 夜の終了時刻（tick）。 */
    private static final long DAWN = 23500L;

    private final Plugin plugin;
    private final InvasionConfig config;
    private final NexusManager nexuses;
    private final InvaderRegistry registry;
    private final InvaderSpawner spawner;
    private final InvaderAiService ai;
    private final GameStageService stages;
    private final RewardService rewards;
    private final ContributionTracker contributions;
    private final ShardService shards;
    private final PerkService perks;
    private final HudServiceBridge hud;
    private final BreachLog breachLog;

    /** ネクサスごとに「この日の日没処理を済ませたか」を記録する。 */
    private final Map<UUID, Long> duskHandled = new HashMap<>();

    /** 血の月当日に、どこまで事前告知を出したか（0=未告知）。 */
    private final Map<UUID, Integer> warnStage = new HashMap<>();

    /** 直前に出したボス。同じ血の月で連続させないために覚えておく。 */
    private final Map<UUID, BossType> lastBoss = new HashMap<>();

    /** 事前告知を出す時刻（tick）と文言。日没 13000 に向けて段階的に煽る。 */
    private static final long[] WARN_TIMES = {6000L, 10000L, 12200L};
    private static final String[] WARN_TEXTS = {
            "血の月の日。日が高いうちに備えを終えろ。",
            "夕暮れが近い。壁と弾薬を最終確認しろ。",
            "まもなく日が沈む。血の月が来る。"
    };

    /**
     * HUD への依存を細く保つための小さな窓口。
     * WaveEngine が UI 実装の詳細を知る必要はない。
     */
    public interface HudServiceBridge {
        void updateBar(UUID nexusId, Component name, float progress, BossBar.Color color);

        void showBarTo(UUID nexusId, List<Player> viewers);

        void hideBar(UUID nexusId);
    }

    public WaveEngine(Plugin plugin, InvasionConfig config, NexusManager nexuses, InvaderRegistry registry,
                      InvaderSpawner spawner, InvaderAiService ai, GameStageService stages,
                      RewardService rewards, ContributionTracker contributions, ShardService shards,
                      PerkService perks, HudServiceBridge hud, BreachLog breachLog) {
        this.plugin = plugin;
        this.config = config;
        this.nexuses = nexuses;
        this.registry = registry;
        this.spawner = spawner;
        this.ai = ai;
        this.stages = stages;
        this.rewards = rewards;
        this.contributions = contributions;
        this.shards = shards;
        this.perks = perks;
        this.hud = hud;
        this.breachLog = breachLog;
    }

    // ---------- 起動・停止 ----------

    public boolean activate(Nexus nexus, Player by) {
        if (nexus.phase() != Nexus.Phase.DORMANT && nexus.phase() != Nexus.Phase.BROKEN) {
            return false;
        }
        // 崩壊からの再建には代償を課す。無料で再挑戦できると防衛の失敗が痛くなくなる。
        if (nexus.phase() == Nexus.Phase.BROKEN) {
            int cost = rebuildCost(nexus);
            if (by == null) {
                return false;
            }
            if (!shards.take(by, cost)) {
                by.sendMessage(Text.error("再建には " + cost + " Shard が必要だ（所持 "
                        + shards.count(by) + "）"));
                return false;
            }
            by.sendMessage(Text.good("ネクサスを再建した。（-" + cost + " Shard）"));
        }
        nexus.phase(Nexus.Phase.PREPARATION);
        nexus.health(nexuses.maxHealth(nexus));
        World world = nexus.world();
        if (world != null) {
            nexus.lastProcessedWorldDay(world.getFullTime() / 24000L);
        }
        if (by != null) {
            nexus.members().add(by.getUniqueId());
        }
        broadcast(nexus, Text.good("ネクサスが起動した。"));
        broadcast(nexus, Text.info("Day " + nexus.day() + " — 血の月まで残り "
                + nexus.daysUntilBloodMoon(config.bloodMoonInterval()) + " 日"));
        return true;
    }

    /** 崩壊したネクサスを再建するのに必要な Shard。 */
    public int rebuildCost(Nexus nexus) {
        return nexuses.discounted(nexus, 50 + nexus.level() * 20);
    }

    /** 侵攻を止めて残党を片付ける。 */
    public void deactivate(Nexus nexus, boolean broken) {
        ai.cleanupPlacedBlocks(nexus.id());
        registry.despawnAll(nexus.id());
        hud.hideBar(nexus.id());
        applyChunkTicket(nexus, false);
        duskHandled.remove(nexus.id());
        nexus.phase(broken ? Nexus.Phase.BROKEN : Nexus.Phase.DORMANT);
        nexus.currentWave(0);
        nexus.totalWaves(0);
        nexus.activeModifier(null);
    }

    // ---------- メインループ（1秒間隔） ----------

    public void tick() {
        for (Nexus nexus : new ArrayList<>(nexuses.all())) {
            if (!nexus.isActive()) {
                continue;
            }
            World world = nexus.world();
            if (world == null) {
                continue;
            }
            long fullTime = world.getFullTime();
            long worldDay = fullTime / 24000L;
            long timeOfDay = fullTime % 24000L;

            if (nexus.lastProcessedWorldDay() < 0) {
                nexus.lastProcessedWorldDay(worldDay);
            }
            if (worldDay > nexus.lastProcessedWorldDay()) {
                long delta = worldDay - nexus.lastProcessedWorldDay();
                nexus.lastProcessedWorldDay(worldDay);
                onDawn(nexus, (int) Math.min(delta, 30));
            }

            applyChunkTicket(nexus, true);

            if (nexus.phase() == Nexus.Phase.BLOOD_MOON) {
                tickBloodMoon(nexus, fullTime, timeOfDay);
            } else {
                tickPreparation(nexus, worldDay, timeOfDay);
            }
            updateHud(nexus);
        }
    }

    /**
     * 侵攻中はネクサス周辺のチャンクを保持する。
     * 解放されると侵略Mobが凍りつき、夜が明けても侵攻が終わらなくなる。
     */
    private void applyChunkTicket(Nexus nexus, boolean keep) {
        if (!config.keepChunksLoaded()) {
            return;
        }
        World world = nexus.world();
        Location center = nexus.center();
        if (world == null || center == null) {
            return;
        }
        int cx = center.getBlockX() >> 4;
        int cz = center.getBlockZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (keep) {
                    world.addPluginChunkTicket(cx + dx, cz + dz, plugin);
                } else {
                    world.removePluginChunkTicket(cx + dx, cz + dz, plugin);
                }
            }
        }
    }

    /** 夜明け。ブラッドムーンを生き延びていれば勝利確定。 */
    private void onDawn(Nexus nexus, int daysPassed) {
        if (nexus.phase() == Nexus.Phase.BLOOD_MOON) {
            resolveVictory(nexus);
        }
        nexus.day(nexus.day() + daysPassed);
        warnStage.put(nexus.id(), 0);

        int until = nexus.daysUntilBloodMoon(config.bloodMoonInterval());
        if (until == 0) {
            broadcast(nexus, Text.error("Day " + nexus.day() + " — 今夜は血の月だ。備えろ。"));
            for (Player p : stages.participants(nexus)) {
                p.showTitle(Title.title(
                        Text.of("血の月", Text.ACCENT),
                        Text.of("日没とともに総攻撃が始まる", Text.WARN),
                        Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(3), Duration.ofMillis(600))));
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 0.6f);
            }
        } else {
            broadcast(nexus, Text.info("Day " + nexus.day() + " — 血の月まで残り " + until + " 日"));
        }
    }

    /** 準備フェーズ。自己修復と、日没時の襲撃開始判定を行う。 */
    private void tickPreparation(Nexus nexus, long worldDay, long timeOfDay) {
        int regen = nexus.upgradeLevel(NexusUpgrade.REGENERATION);
        if (regen > 0 && nexus.health() < nexuses.maxHealth(nexus)) {
            nexus.health(Math.min(nexuses.maxHealth(nexus), nexus.health() + regen * 1.5));
        }

        boolean isNight = timeOfDay >= DUSK && timeOfDay < DAWN;
        if (!isNight) {
            announceCountdown(nexus, timeOfDay);
            return;
        }
        Long handled = duskHandled.get(nexus.id());
        if (handled != null && handled == worldDay) {
            return;
        }
        duskHandled.put(nexus.id(), worldDay);

        if (nexus.isBloodMoonDay(config.bloodMoonInterval())) {
            startBloodMoon(nexus);
        } else if (config.nightSkirmishEnabled()) {
            spawnSkirmish(nexus);
        }
    }

    /**
     * 血の月当日の日中に、日没へ向けたカウントダウンを流す。
     * 「気づいたら夜になっていて壊滅」を防ぎ、準備フェーズに締切を与える。
     */
    private void announceCountdown(Nexus nexus, long timeOfDay) {
        if (!nexus.isBloodMoonDay(config.bloodMoonInterval())) {
            return;
        }
        int stage = warnStage.getOrDefault(nexus.id(), 0);
        if (stage >= WARN_TIMES.length || timeOfDay < WARN_TIMES[stage]) {
            return;
        }
        warnStage.put(nexus.id(), stage + 1);

        broadcast(nexus, Text.warn(WARN_TEXTS[stage]));
        for (Player p : stages.participants(nexus)) {
            p.playSound(p.getLocation(),
                    stage >= WARN_TIMES.length - 1 ? Sound.ENTITY_WITHER_SPAWN : Sound.BLOCK_BELL_RESONATE,
                    0.7f, stage >= WARN_TIMES.length - 1 ? 0.6f : 1.0f);
        }
    }

    /** ブラッドムーン中。ウェーブ投入と終了判定。 */
    private void tickBloodMoon(Nexus nexus, long fullTime, long timeOfDay) {
        boolean isNight = timeOfDay >= DUSK && timeOfDay < DAWN;
        if (!isNight) {
            // 夜明けを跨いだ（/time set 等でも安全に閉じる）
            resolveVictory(nexus);
            return;
        }

        int alive = registry.aliveCount(nexus.id());

        if (nexus.currentWave() >= nexus.totalWaves()) {
            if (alive == 0) {
                resolveVictory(nexus);
            }
            return;
        }
        WaveModifier.applyAmbient(nexus.activeModifier(), nexus, stages.participants(nexus));

        if (fullTime < nexus.nextWaveTick()) {
            return;
        }
        if (alive >= config.waveMaxAlive()) {
            // 同時出現数の上限。押し切られている間は追加投入しない。
            return;
        }
        spawnWave(nexus);
    }

    // ---------- ブラッドムーン ----------

    private void startBloodMoon(Nexus nexus) {
        List<Player> participants = stages.participants(nexus);
        int stage = stages.compute(nexus, participants);

        // GameStage 15 ごとに1ウェーブ増える。上限は config で頭打ちにする。
        int extra = (stage / 15) * config.waveCountPerFiveStage();
        int waves = Math.max(config.waveBaseCount(),
                Math.min(config.waveMaxCount(), config.waveBaseCount() + extra));

        nexus.phase(Nexus.Phase.BLOOD_MOON);
        nexus.currentWave(0);
        nexus.totalWaves(waves);
        nexus.activeModifier(WaveModifier.roll(stage));

        World world = nexus.world();
        if (world != null) {
            nexus.nextWaveTick(world.getFullTime());
        }
        contributions.reset(nexus.id());

        broadcast(nexus, Text.error("━━━ 血の月 ━━━"));
        broadcast(nexus, Text.warn("GameStage " + stage + " / 全 " + waves + " ウェーブ"));
        String modifier = nexus.activeModifier();
        if (modifier != null) {
            broadcast(nexus, Text.warn("変調: " + WaveModifier.describe(modifier)));
        }
        for (Player p : participants) {
            p.showTitle(Title.title(
                    Text.of("血の月", Text.ACCENT),
                    Text.of("ネクサスを守り抜け", Text.WARN),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))));
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.6f);
        }
    }

    private void spawnWave(Nexus nexus) {
        List<Player> participants = stages.participants(nexus);
        int stage = stages.compute(nexus, participants);
        int wave = nexus.currentWave() + 1;
        nexus.currentWave(wave);
        nexus.bestWave(wave);

        int count = (int) Math.round(config.waveBaseMobs()
                + stage * config.waveMobsPerStage()
                + wave * 1.5);
        count = Math.max(3, Math.min(count, config.waveMaxAlive() - registry.aliveCount(nexus.id())));

        double modifierScale = WaveModifier.countScale(nexus.activeModifier());
        count = Math.max(3, (int) Math.round(count * modifierScale));

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            InvaderType type = rollType(stage);
            MutationTier tier = WaveModifier.forceFeral(nexus.activeModifier())
                    ? MutationTier.FERAL
                    : MutationTier.roll(stage);
            if (spawner.spawn(nexus, type, tier, stage, wave) != null) {
                spawned++;
            }
        }

        // 5波ごとにボス。同じ血の月で同じボスが連続しないよう直前の種別を除外する。
        if (wave % 5 == 0) {
            BossType boss = BossType.roll(stage, lastBoss.get(nexus.id()));
            if (spawner.spawnBoss(nexus, boss, MutationTier.roll(stage), stage, wave) != null) {
                lastBoss.put(nexus.id(), boss);
                broadcast(nexus, Text.error("── " + boss.displayName() + " が現れた ──"));
                broadcast(nexus, Text.prefixed(Text.of(boss.description(), Text.WARN)));
                for (Player p : participants) {
                    p.showTitle(Title.title(
                            Text.of(boss.displayName(), boss.color()),
                            Text.of(boss.description(), Text.MUTED),
                            Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3),
                                    Duration.ofMillis(700))));
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.7f);
                }
            }
        }

        World world = nexus.world();
        if (world != null) {
            nexus.nextWaveTick(world.getFullTime() + config.waveIntervalTicks());
        }

        broadcast(nexus, Text.warn("Wave " + wave + " / " + nexus.totalWaves()
                + " — 侵略者 " + spawned + " 体"));
        for (Player p : participants) {
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.8f, 1.0f);
        }
    }

    /** 血の月以外の夜の小規模襲撃。準備フェーズを安全な作業時間にしないための刺し。 */
    private void spawnSkirmish(Nexus nexus) {
        List<Player> participants = stages.participants(nexus);
        if (participants.isEmpty()) {
            return;
        }
        int stage = stages.compute(nexus, participants);
        int count = Math.max(2, (int) Math.round(
                (config.waveBaseMobs() + stage * config.waveMobsPerStage()) * config.nightSkirmishScale()));

        for (int i = 0; i < count; i++) {
            spawner.spawn(nexus, rollType(stage), MutationTier.roll(stage), stage, 0);
        }
        broadcast(nexus, Text.info("夜の徘徊者が近づいている（" + count + " 体）"));
    }

    /**
     * GameStage で解禁された種別から重み付き抽選する。
     * 重みは GameStage により減衰するので、後半ほど上位個体の比率が上がる。
     */
    private InvaderType rollType(int stage) {
        List<InvaderType> pool = new ArrayList<>();
        double total = 0.0;
        for (InvaderType type : InvaderType.values()) {
            double weight = type.effectiveWeight(stage);
            if (weight <= 0.0) {
                continue;
            }
            pool.add(type);
            total += weight;
        }
        if (pool.isEmpty() || total <= 0.0) {
            return InvaderType.GRUNT;
        }
        double roll = ThreadLocalRandom.current().nextDouble(total);
        for (InvaderType type : pool) {
            roll -= type.effectiveWeight(stage);
            if (roll < 0.0) {
                return type;
            }
        }
        return pool.get(pool.size() - 1);
    }

    // ---------- 勝敗 ----------

    private void resolveVictory(Nexus nexus) {
        int wavesCleared = nexus.currentWave();
        List<Player> participants = stages.participants(nexus);
        int stage = stages.compute(nexus, participants);

        ai.cleanupPlacedBlocks(nexus.id());
        registry.despawnAll(nexus.id());
        hud.hideBar(nexus.id());

        nexus.phase(Nexus.Phase.PREPARATION);
        nexus.currentWave(0);
        nexus.totalWaves(0);
        nexus.activeModifier(null);
        if (nexus.level() < config.nexusMaxLevel()) {
            nexus.level(nexus.level() + 1);
        }
        // 撃退できたなら全快させる。半端に削れたまま次の7日に入ると詰みやすい。
        nexus.health(nexuses.maxHealth(nexus));

        List<ItemStack> loot = rewards.victoryLoot(stage, wavesCleared);
        nexus.rewardBuffer().addAll(loot);

        // 壁の維持・強化と、消耗ガジェットの再補充が回る量にする。
        // ここが渋いと「勝ったのに次の準備ができない」詰みが起きる。
        int shardPool = 60 + stage * 3 + wavesCleared * 15;
        List<UUID> ids = new ArrayList<>();
        for (Player p : participants) {
            ids.add(p.getUniqueId());
        }
        Map<UUID, Integer> split = contributions.distribute(nexus.id(), ids, shardPool, 0.4);
        for (Player p : participants) {
            int amount = split.getOrDefault(p.getUniqueId(), 0);
            if (amount > 0) {
                shards.give(p, amount);
            }
            // 踏破実績とパークポイントを付与する
            perks.recordVictory(p.getUniqueId(), wavesCleared);
        }

        broadcast(nexus, Text.good("━━━ 防衛成功 ━━━"));
        broadcast(nexus, Text.good("ネクサス Lv." + nexus.level() + " へ上昇。報酬をネクサスから受け取れる。"));
        announceRanking(nexus, participants);
        contributions.reset(nexus.id());

        for (Player p : participants) {
            p.showTitle(Title.title(
                    Text.of("防衛成功", Text.GOOD),
                    Text.of("Lv." + nexus.level() + " / GameStage " + stage, Text.INFO),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
    }

    private void resolveDefeat(Nexus nexus) {
        List<Player> participants = stages.participants(nexus);
        broadcast(nexus, Text.error("━━━ ネクサス崩壊 ━━━"));
        broadcast(nexus, Text.error("拠点は落ちた。ネクサスを修復すれば再起動できる。"));

        for (Player p : participants) {
            p.showTitle(Title.title(
                    Text.of("ネクサス崩壊", Text.ACCENT),
                    Text.of("防衛に失敗した", Text.MUTED),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(700))));
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 0.7f);
        }

        // レベルは1つ落ちるが全損はさせない。全損すると再挑戦の意欲が消える。
        nexus.level(Math.max(1, nexus.level() - 1));
        deactivate(nexus, true);
        contributions.reset(nexus.id());
    }

    // ---------- ダメージ ----------

    @Override
    public void damageNexus(Nexus nexus, double amount) {
        if (nexus.phase() == Nexus.Phase.BROKEN || amount <= 0) {
            return;
        }
        nexus.health(nexus.health() - amount);
        if (nexus.health() <= 0) {
            resolveDefeat(nexus);
        }
    }

    // ---------- 表示 ----------

    private void updateHud(Nexus nexus) {
        List<Player> participants = stages.participants(nexus);
        if (participants.isEmpty()) {
            hud.hideBar(nexus.id());
            return;
        }
        float ratio = (float) nexuses.healthRatio(nexus);

        Component name;
        BossBar.Color color;
        if (nexus.phase() == Nexus.Phase.BLOOD_MOON) {
            color = BossBar.Color.RED;
            name = Component.text()
                    .append(Text.of("血の月 ", Text.ACCENT))
                    .append(Text.of("Wave " + nexus.currentWave() + "/" + nexus.totalWaves(), Text.WARN))
                    .append(Text.of("  残敵 " + registry.aliveCount(nexus.id()), Text.MUTED))
                    .append(Text.of("  ネクサス " + (int) nexus.health(), Text.INFO))
                    .build();
        } else {
            color = BossBar.Color.BLUE;
            int until = nexus.daysUntilBloodMoon(config.bloodMoonInterval());
            name = Component.text()
                    .append(Text.of("Day " + nexus.day() + " ", Text.INFO))
                    .append(Text.of(until == 0 ? "今夜は血の月" : "血の月まで " + until + " 日", Text.WARN))
                    .append(Text.of("  ネクサス " + (int) nexus.health(), Text.MUTED))
                    .build();
        }
        hud.updateBar(nexus.id(), name, ratio, color);
        hud.showBarTo(nexus.id(), participants);
    }

    private void announceRanking(Nexus nexus, List<Player> participants) {
        List<Map.Entry<UUID, Double>> ranking = contributions.ranking(nexus.id());
        if (ranking.isEmpty()) {
            return;
        }
        broadcast(nexus, Text.info("貢献度"));
        int shown = Math.min(3, ranking.size());
        for (int i = 0; i < shown; i++) {
            Map.Entry<UUID, Double> e = ranking.get(i);
            Player p = plugin.getServer().getPlayer(e.getKey());
            String name = p != null ? p.getName() : e.getKey().toString().substring(0, 8);
            broadcast(nexus, Text.prefixed(Text.of("  " + (i + 1) + ". " + name
                    + " — " + Math.round(e.getValue()), Text.MUTED)));
        }
    }

    public void broadcast(Nexus nexus, Component message) {
        for (Player p : stages.participants(nexus)) {
            p.sendMessage(message);
        }
    }

    /** 侵略Mob撃破時にウェーブの進行状況を更新する。 */
    public void onInvaderDeath(Nexus nexus) {
        if (nexus.phase() != Nexus.Phase.BLOOD_MOON) {
            return;
        }
        // 撃破時点で登録簿からは除外済みなので、0 になっていれば全滅している
        if (nexus.currentWave() >= nexus.totalWaves() && registry.aliveCount(nexus.id()) == 0) {
            // 夜明けを待たずに勝利させる（死亡イベント中に状態を変えないよう1tick遅らせる）
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (nexus.phase() == Nexus.Phase.BLOOD_MOON
                        && registry.aliveCount(nexus.id()) == 0) {
                    resolveVictory(nexus);
                }
            });
        }
    }

    public BreachLog breachLog() {
        return breachLog;
    }
}
