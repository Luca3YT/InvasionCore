package work.maiotanet.invasioncore;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import work.maiotanet.invasioncore.command.InvasionCommands;
import work.maiotanet.invasioncore.command.RuleCommand;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.defense.FortifyService;
import work.maiotanet.invasioncore.defense.TrapService;
import work.maiotanet.invasioncore.defense.TurretService;
import work.maiotanet.invasioncore.economy.ContributionTracker;
import work.maiotanet.invasioncore.economy.RewardService;
import work.maiotanet.invasioncore.economy.ShardPickupSound;
import work.maiotanet.invasioncore.economy.ShardService;
import work.maiotanet.invasioncore.heat.HeatService;
import work.maiotanet.invasioncore.item.RecipeRegistrar;
import work.maiotanet.invasioncore.listener.DefenseListener;
import work.maiotanet.invasioncore.listener.GadgetListener;
import work.maiotanet.invasioncore.listener.InvaderListener;
import work.maiotanet.invasioncore.listener.NexusListener;
import work.maiotanet.invasioncore.listener.PlayerListener;
import work.maiotanet.invasioncore.listener.PreGameListener;
import work.maiotanet.invasioncore.mob.BossAbilities;
import work.maiotanet.invasioncore.mob.InvaderAiService;
import work.maiotanet.invasioncore.mob.InvaderRegistry;
import work.maiotanet.invasioncore.mob.InvaderSpawner;
import work.maiotanet.invasioncore.nexus.NexusManager;
import work.maiotanet.invasioncore.perk.PerkEffectService;
import work.maiotanet.invasioncore.perk.PerkService;
import work.maiotanet.invasioncore.pregame.PreGameService;
import work.maiotanet.invasioncore.setup.SetupChecklist;
import work.maiotanet.invasioncore.stage.GameStageService;
import work.maiotanet.invasioncore.ui.HudService;
import work.maiotanet.invasioncore.ui.NexusGui;
import work.maiotanet.invasioncore.ui.SidebarUpdater;
import work.maiotanet.invasioncore.util.Keys;
import work.maiotanet.invasioncore.wave.WaveEngine;
import work.maiotanet.invasioncore.web.FaceService;
import work.maiotanet.invasioncore.web.ShardFeed;
import work.maiotanet.invasioncore.web.WebServer;
import work.maiotanet.invasioncore.web.WebState;
import work.maiotanet.invasioncore.world.BreachLog;
import work.maiotanet.invasioncore.world.WorldEventService;

import java.util.List;
import java.util.UUID;

/**
 * InvasionCore — 侵略MOD（ネクサス防衛）× 7 Days to Die（7日周期・拠点強化）。
 *
 * <p>組み立てと寿命管理だけを担い、ゲームロジックは各サービスへ委譲する。</p>
 */
public final class InvasionCore extends JavaPlugin {

    private InvasionConfig config;

    private NexusManager nexuses;
    private InvaderRegistry registry;
    private FortifyService fortify;
    private TurretService turrets;
    private TrapService traps;
    private HeatService heat;
    private PerkService perks;
    private HudService hud;
    private WorldEventService worldEvents;
    private WaveEngine waves;
    private GadgetListener gadgets;
    private WebServer web;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Keys.init(this);
        this.config = new InvasionConfig(this);

        // ---- サービス構築 ----
        this.nexuses = new NexusManager(this, config);
        this.registry = new InvaderRegistry();
        this.fortify = new FortifyService(this);
        this.heat = new HeatService(config);
        this.perks = new PerkService(this);
        this.hud = new HudService();

        GameStageService stages = new GameStageService(config);
        ShardService shards = new ShardService();
        ShardPickupSound shardSound = new ShardPickupSound();
        ContributionTracker contributions = new ContributionTracker();
        ShardFeed shardFeed = new ShardFeed();
        shards.feed(shardFeed);
        RewardService rewards = new RewardService();
        BreachLog breachLog = new BreachLog();

        InvaderSpawner spawner = new InvaderSpawner(config, nexuses, registry);
        BossAbilities bossAbilities = new BossAbilities(
                config, registry, spawner, stages, fortify, breachLog);
        InvaderAiService ai = new InvaderAiService(
                config, nexuses, registry, fortify, breachLog, spawner, stages, heat, bossAbilities);

        this.turrets = new TurretService(this, config, nexuses, registry, fortify, perks);
        this.traps = new TrapService(this, registry, perks);
        this.worldEvents = new WorldEventService(
                this, config, nexuses, spawner, stages, heat, rewards);
        PerkEffectService perkEffects = new PerkEffectService(nexuses, perks);

        this.waves = new WaveEngine(this, config, nexuses, registry, spawner, ai, stages,
                rewards, contributions, shards, perks, hudBridge(), breachLog);
        ai.combatSink(waves);

        NexusGui gui = new NexusGui(config, nexuses, stages, shards, perks, waves);
        SidebarUpdater sidebar = new SidebarUpdater(
                config, nexuses, stages, registry, shards, heat, hud);

        // ---- データ読み込み ----
        nexuses.load();
        fortify.load();
        turrets.load();
        traps.load();
        perks.load();

        // ---- 登録 ----
        new RecipeRegistrar(this).registerAll();

        PreGameService pregame = new PreGameService(config, nexuses);
        getServer().getPluginManager().registerEvents(new PreGameListener(pregame), this);
        getServer().getPluginManager().registerEvents(
                new NexusListener(config, nexuses, gui, waves, shards), this);
        getServer().getPluginManager().registerEvents(
                new DefenseListener(config, nexuses, fortify, turrets, traps, shards, perks, contributions, pregame),
                this);
        this.gadgets = new GadgetListener(this, nexuses, registry, perks, fortify, pregame);
        getServer().getPluginManager().registerEvents(gadgets, this);
        InvaderListener invaderListener = new InvaderListener(config, nexuses, registry, spawner, ai,
                fortify, breachLog, shards, perks, contributions, stages, heat, waves);
        getServer().getPluginManager().registerEvents(invaderListener, this);
        // 有効化より前に読み込まれていたチャンク（スポーン周辺など）には読み込みイベントが来ないので、
        // 前回の異常終了で残った侵略Mobをここで一掃する。
        int orphans = 0;
        for (World world : getServer().getWorlds()) {
            orphans += invaderListener.sweepOrphans(world.getEntities());
        }
        if (orphans > 0) {
            getLogger().info("前回から残っていた侵略Mobを " + orphans + " 体撤去しました。");
        }
        getServer().getPluginManager().registerEvents(
                new PlayerListener(config, nexuses, heat, perks, hud, worldEvents, shardSound, shards), this);

        SetupChecklist setupChecklist = new SetupChecklist(config, nexuses, stages);
        InvasionCommands commands = new InvasionCommands(
                this, nexuses, stages, shards, perks, gui, waves, registry, spawner,
                worldEvents, breachLog, setupChecklist);

        // ---- Web（管理画面・OBS オーバーレイ） ----
        FaceService faces = new FaceService(this);
        WebState webState = new WebState(config, nexuses, registry, shards, perks, contributions, faces);
        getServer().getScheduler().runTaskTimer(this, webState::tick, 20L, 20L);
        if (config.webEnabled()) {
            try {
                this.web = new WebServer(this, config, webState, shardFeed, faces);
                web.start();
            } catch (LinkageError ex) {
                // JDK 同梱の HTTP サーバー（jdk.httpserver）を省いた Java だと、クラスが見つからず落ちる。
                // ゲーム本体まで止めないよう、Web 機能だけを諦める。
                this.web = null;
                getLogger().warning("この Java には Web サーバーの機能（jdk.httpserver）が無いため、"
                        + "管理画面と OBS オーバーレイは使えません。JDK 版の Java で起動してください。");
            }
        }
        commands.web(web);
        setupChecklist.web(config.webEnabled(), web);
        RuleCommand rule = new RuleCommand(config);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register("invasion", "InvasionCore の操作", List.of("inv"), commands);
            event.registrar().register("rule", "ルール説明の URL を表示する", List.of("rules"), rule);
        });

        // ---- 定期処理 ----
        // AI は毎tick呼ぶが、内部でバケット分割しているので1tickあたりの実処理は 1/N。
        getServer().getScheduler().runTaskTimer(this, ai::tick, 20L, 1L);
        getServer().getScheduler().runTaskTimer(this, waves::tick, 40L, 20L);
        getServer().getScheduler().runTaskTimer(this, worldEvents::tick, 60L, 20L);
        getServer().getScheduler().runTaskTimer(this, sidebar::tick, 80L, 20L);
        getServer().getScheduler().runTaskTimer(this, pregame::tick, 85L, 20L);
        getServer().getScheduler().runTaskTimer(this, shardSound::tick, 1L, 1L);
        getServer().getScheduler().runTaskTimer(this, turrets::tick,
                100L, config.turretTickInterval());
        getServer().getScheduler().runTaskTimer(this, traps::tick, 110L, 10L);
        getServer().getScheduler().runTaskTimer(this,
                () -> perkEffects.tick(getServer().getOnlinePlayers()), 120L, 20L);
        // 自動保存と記録の掃除は5分ごと。
        getServer().getScheduler().runTaskTimer(this, this::autoSave, 6000L, 6000L);

        getLogger().info("InvasionCore を有効化しました（Paper 26.2 / Java 25）。");
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);

        if (registry != null) {
            // 侵略Mobを残したままサーバーを落とすと、次回起動時に制御不能な野良Mobになる
            registry.clear();
        }
        if (worldEvents != null) {
            worldEvents.shutdown();
        }
        if (web != null) {
            web.stop();
        }
        if (gadgets != null) {
            // 保存より前に消す。残すと鋼鉄級の耐久記録ごと恒久的な壁になる。
            gadgets.shutdown();
        }
        if (hud != null) {
            hud.shutdown();
        }
        autoSave();
        getLogger().info("InvasionCore を無効化しました。");
    }

    private void autoSave() {
        if (nexuses != null) {
            nexuses.save();
        }
        if (fortify != null) {
            fortify.save();
        }
        if (turrets != null) {
            turrets.save();
        }
        if (traps != null) {
            traps.save();
        }
        if (perks != null) {
            perks.save();
        }
    }

    /** WaveEngine から HUD を触るための細い窓口。 */
    private WaveEngine.HudServiceBridge hudBridge() {
        return new WaveEngine.HudServiceBridge() {
            @Override
            public void updateBar(UUID nexusId, Component name, float progress, BossBar.Color color) {
                hud.updateBar(nexusId, name, progress, color);
            }

            @Override
            public void showBarTo(UUID nexusId, List<Player> viewers) {
                hud.showBarTo(nexusId, viewers);
            }

            @Override
            public void hideBar(UUID nexusId) {
                hud.hideBar(nexusId);
            }
        };
    }

    public InvasionConfig config() {
        return config;
    }
}
