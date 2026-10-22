package work.maiotanet.invasioncore.ui;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.mob.BossType;
import work.maiotanet.invasioncore.mob.Invader;
import work.maiotanet.invasioncore.mob.InvaderRegistry;
import work.maiotanet.invasioncore.nexus.Nexus;
import work.maiotanet.invasioncore.nexus.NexusManager;
import work.maiotanet.invasioncore.stage.GameStageService;
import work.maiotanet.invasioncore.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 侵略者の HP の表示。
 *
 * <p>ダメージを受けた侵略者だけ、頭上の名前に数秒間 HP を足す。攻撃が当たっているか・あと何発かが分かる。
 * 全員に常時出すと、血の月の 90 体で画面が文字だらけになるので出さない。
 * ボスは生きている間ずっと、参加者全員にボスバーで HP を見せる（いまボスと戦っていることを全員に伝えるため）。</p>
 */
public final class InvaderHealthService implements Listener {

    /** HP を出している侵略者。値は表示をやめる時刻（ミリ秒）。 */
    private final Map<UUID, Long> showUntil = new HashMap<>();
    /** HP を足す前の名前。表示をやめるときに戻す。 */
    private final Map<UUID, Component> originalNames = new HashMap<>();
    /** ボスごとのボスバーと、見せている人。 */
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Map<UUID, Set<UUID>> bossViewers = new HashMap<>();

    private final InvasionConfig config;
    private final InvaderRegistry registry;
    private final NexusManager nexuses;
    private final GameStageService stages;

    public InvaderHealthService(InvasionConfig config, InvaderRegistry registry,
                                NexusManager nexuses, GameStageService stages) {
        this.config = config;
        this.registry = registry;
        this.nexuses = nexuses;
        this.stages = stages;
    }

    /** プレイヤー・タレット・罠・爆発など、どんなダメージでも出す。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Invader invader = registry.get(event.getEntity());
        if (invader == null) {
            return;
        }
        showUntil.put(invader.entity().getUniqueId(),
                System.currentTimeMillis() + config.mobHealthDisplaySeconds() * 1000L);
        // ダメージはこのイベントの後で反映されるので、名前の書き換えは次の tick の更新に任せる
    }

    /** 5 tick ごと。HP の数字とボスバーを追従させる。 */
    public void tick() {
        long now = System.currentTimeMillis();
        updateNames(now);
        updateBossBars();
    }

    // ---------- 頭上の HP ----------

    private void updateNames(long now) {
        Iterator<Map.Entry<UUID, Long>> it = showUntil.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            Invader invader = registry.get(entry.getKey());
            if (invader == null || !invader.isValid()) {
                it.remove();
                originalNames.remove(entry.getKey());
                continue;
            }
            Mob mob = invader.entity();
            if (now >= entry.getValue()) {
                it.remove();
                Component original = originalNames.remove(entry.getKey());
                if (original != null) {
                    mob.customName(original);
                }
                // ボスは常に名前を出している。それ以外は元どおり隠す
                mob.setCustomNameVisible(invader.bossType() != null);
                continue;
            }
            Component original = originalNames.computeIfAbsent(entry.getKey(), k ->
                    mob.customName() == null ? Text.plain(invader.type().displayName()) : mob.customName());
            mob.customName(original.append(Text.of(" ❤ " + hp(mob) + "/" + maxHp(mob), Text.ACCENT)));
            mob.setCustomNameVisible(true);
        }
    }

    // ---------- ボスバー ----------

    private void updateBossBars() {
        Set<UUID> alive = new HashSet<>();
        for (Invader invader : registry.all()) {
            BossType boss = invader.bossType();
            if (boss == null || !invader.isValid()) {
                continue;
            }
            Mob mob = invader.entity();
            UUID id = mob.getUniqueId();
            alive.add(id);
            BossBar bar = bossBars.computeIfAbsent(id, k -> BossBar.bossBar(
                    Component.empty(), 1.0f, boss.barColor(), BossBar.Overlay.PROGRESS));
            bar.name(Text.of(boss.displayName(), boss.color())
                    .append(Text.of("  ❤ " + hp(mob) + " / " + maxHp(mob), Text.WARN)));
            double max = maxHp(mob);
            bar.progress((float) Math.max(0.0, Math.min(1.0, max <= 0 ? 0.0 : mob.getHealth() / max)));

            Nexus nexus = nexuses.byId(invader.nexusId());
            List<Player> viewers = nexus == null ? List.of() : stages.participants(nexus);
            Set<UUID> previous = bossViewers.getOrDefault(id, Set.of());
            Set<UUID> current = new HashSet<>();
            for (Player p : viewers) {
                current.add(p.getUniqueId());
                p.showBossBar(bar);
            }
            for (UUID old : previous) {
                if (!current.contains(old)) {
                    Player p = Bukkit.getPlayer(old);
                    if (p != null) {
                        p.hideBossBar(bar);
                    }
                }
            }
            bossViewers.put(id, current);
        }
        // 倒された・撤収したボスのバーを消す
        for (UUID id : new ArrayList<>(bossBars.keySet())) {
            if (!alive.contains(id)) {
                hideBossBar(id);
            }
        }
    }

    private void hideBossBar(UUID id) {
        BossBar bar = bossBars.remove(id);
        Set<UUID> viewers = bossViewers.remove(id);
        if (bar == null || viewers == null) {
            return;
        }
        for (UUID viewer : viewers) {
            Player p = Bukkit.getPlayer(viewer);
            if (p != null) {
                p.hideBossBar(bar);
            }
        }
    }

    private static int hp(Mob mob) {
        return (int) Math.ceil(Math.max(0.0, mob.getHealth()));
    }

    private static int maxHp(Mob mob) {
        AttributeInstance max = mob.getAttribute(Attribute.MAX_HEALTH);
        return max == null ? hp(mob) : (int) Math.ceil(max.getValue());
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(bossBars.keySet())) {
            hideBossBar(id);
        }
        // 名前は侵略者ごと撤収されるので戻さなくてよい
        showUntil.clear();
        originalNames.clear();
    }
}
