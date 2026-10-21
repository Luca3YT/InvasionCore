package work.maiotanet.invasioncore.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.economy.ContributionTracker;
import work.maiotanet.invasioncore.economy.ShardService;
import work.maiotanet.invasioncore.mob.InvaderRegistry;
import work.maiotanet.invasioncore.nexus.Nexus;
import work.maiotanet.invasioncore.nexus.NexusManager;
import work.maiotanet.invasioncore.perk.Perk;
import work.maiotanet.invasioncore.perk.PerkService;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 管理画面に出す状態のスナップショット。
 *
 * <p>Bukkit の API はメインスレッドからしか触れないので、1秒ごとにメインスレッドで JSON を組み立てておき、
 * Web サーバーのスレッドはできあがった文字列を返すだけにしている。</p>
 */
public final class WebState {

    private final InvasionConfig config;
    private final NexusManager nexuses;
    private final InvaderRegistry registry;
    private final ShardService shards;
    private final PerkService perks;
    private final ContributionTracker contributions;
    private final FaceService faces;

    private volatile String adminJson = "{}";

    /**
     * オーバーレイの表示パターン。Web 用のスレッドから設定（YAML）を直接読むと、
     * メインスレッドの書き換えと重なったときに壊れうるので、メインスレッドで写しておく。
     */
    private volatile String style = "b";

    public WebState(InvasionConfig config, NexusManager nexuses, InvaderRegistry registry, ShardService shards,
                    PerkService perks, ContributionTracker contributions, FaceService faces) {
        this.config = config;
        this.nexuses = nexuses;
        this.registry = registry;
        this.shards = shards;
        this.perks = perks;
        this.contributions = contributions;
        this.faces = faces;
    }

    public String adminJson() {
        return adminJson;
    }

    /** オーバーレイの表示パターン（b / c / e / f）。どのスレッドから呼んでもよい。 */
    public String overlayStyle() {
        return style;
    }

    /** 管理画面から表示パターンを変えたとき（メインスレッド）。次の tick を待たずに反映する。 */
    public void changeStyle(String next) {
        config.overlayStyle(next);
        style = next;
    }

    /** 1秒ごと（メインスレッド）。 */
    public void tick() {
        style = config.overlayStyle();
        for (Player player : Bukkit.getOnlinePlayers()) {
            faces.prefetch(player);
        }
        adminJson = build().toString();
    }

    private JsonObject build() {
        JsonObject root = new JsonObject();
        root.addProperty("updatedAt", System.currentTimeMillis());
        root.addProperty("style", style);
        root.addProperty("online", Bukkit.getOnlinePlayers().size());

        Nexus nexus = nexuses.all().stream().findFirst().orElse(null);
        root.add("nexus", nexus == null ? null : nexus(nexus));

        // ネクサスのメンバー（サーバーに入った人は自動で入る）と、いまオンラインの人
        Set<UUID> ids = new LinkedHashSet<>();
        if (nexus != null) {
            ids.addAll(nexus.members());
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            ids.add(p.getUniqueId());
        }
        JsonArray players = new JsonArray();
        for (UUID id : ids) {
            players.add(player(id, nexus));
        }
        root.add("players", players);
        return root;
    }

    private JsonObject nexus(Nexus nexus) {
        JsonObject o = new JsonObject();
        o.addProperty("phase", nexus.phase().name());
        o.addProperty("phaseLabel", nexus.phase().label());
        o.addProperty("day", nexus.day());
        o.addProperty("untilBloodMoon", nexus.daysUntilBloodMoon(config.bloodMoonInterval()));
        o.addProperty("wave", nexus.currentWave());
        o.addProperty("totalWaves", nexus.totalWaves());
        o.addProperty("alive", registry.aliveCount(nexus.id()));
        o.addProperty("level", nexus.level());
        o.addProperty("health", Math.round(nexus.health()));
        o.addProperty("maxHealth", Math.round(nexuses.maxHealth(nexus)));
        o.addProperty("pool", nexus.sharedShards());
        return o;
    }

    private JsonObject player(UUID id, Nexus nexus) {
        JsonObject o = new JsonObject();
        Player online = Bukkit.getPlayer(id);
        PerkService.Profile profile = perks.profile(id);
        String name = online != null ? online.getName()
                : !profile.lastKnownName().isEmpty() ? profile.lastKnownName()
                : Bukkit.getOfflinePlayer(id).getName();
        o.addProperty("uuid", id.toString());
        o.addProperty("name", name == null ? id.toString().substring(0, 8) : name);
        o.addProperty("online", online != null);
        if (online != null) {
            AttributeInstance max = online.getAttribute(Attribute.MAX_HEALTH);
            o.addProperty("alive", !online.isDead());
            o.addProperty("health", (int) Math.ceil(online.getHealth()));
            o.addProperty("maxHealth", max == null ? 20 : (int) Math.round(max.getValue()));
            o.addProperty("food", online.getFoodLevel());
            o.addProperty("shard", shards.count(online));
        }

        JsonObject contrib = new JsonObject();
        if (nexus != null) {
            for (Map.Entry<ContributionTracker.Kind, Double> e : contributions.breakdown(nexus.id(), id).entrySet()) {
                contrib.addProperty(e.getKey().name().toLowerCase(), Math.round(e.getValue()));
            }
        }
        o.add("contrib", contrib);

        o.addProperty("kills", profile.totalKills());
        o.addProperty("moons", profile.bloodMoonsSurvived());
        o.addProperty("best", profile.bestWave());
        o.addProperty("points", profile.points());
        JsonArray perkList = new JsonArray();
        for (Perk perk : Perk.values()) {
            int level = profile.level(perk);
            if (level > 0) {
                JsonObject pk = new JsonObject();
                pk.addProperty("name", perk.displayName());
                pk.addProperty("level", level);
                perkList.add(pk);
            }
        }
        o.add("perks", perkList);
        return o;
    }
}
