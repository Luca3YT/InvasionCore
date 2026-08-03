package work.maiotanet.invasioncore.mob;

import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 稼働中の侵略Mobの登録簿。
 * エンティティUUID からの逆引きと、ネクサス単位の集計を両方担う。
 */
public final class InvaderRegistry {

    private final Map<UUID, Invader> byEntity = new HashMap<>();
    private final Map<UUID, Set<UUID>> byNexus = new HashMap<>();

    public void register(Invader invader) {
        byEntity.put(invader.entity().getUniqueId(), invader);
        byNexus.computeIfAbsent(invader.nexusId(), k -> new HashSet<>())
                .add(invader.entity().getUniqueId());
    }

    public Invader get(Entity entity) {
        return entity == null ? null : byEntity.get(entity.getUniqueId());
    }

    public Invader get(UUID entityId) {
        return byEntity.get(entityId);
    }

    public boolean isInvader(Entity entity) {
        return entity != null && byEntity.containsKey(entity.getUniqueId());
    }

    public Invader unregister(UUID entityId) {
        Invader removed = byEntity.remove(entityId);
        if (removed != null) {
            Set<UUID> set = byNexus.get(removed.nexusId());
            if (set != null) {
                set.remove(entityId);
                if (set.isEmpty()) {
                    byNexus.remove(removed.nexusId());
                }
            }
        }
        return removed;
    }

    public int aliveCount(UUID nexusId) {
        Set<UUID> set = byNexus.get(nexusId);
        return set == null ? 0 : set.size();
    }

    public int totalAlive() {
        return byEntity.size();
    }

    public List<Invader> forNexus(UUID nexusId) {
        Set<UUID> set = byNexus.get(nexusId);
        if (set == null || set.isEmpty()) {
            return List.of();
        }
        List<Invader> out = new ArrayList<>(set.size());
        for (UUID id : set) {
            Invader inv = byEntity.get(id);
            if (inv != null) {
                out.add(inv);
            }
        }
        return out;
    }

    public Collection<Invader> all() {
        return byEntity.values();
    }

    /** 死亡済み・アンロード済みの残骸を掃除する。 */
    public void purgeInvalid() {
        Iterator<Map.Entry<UUID, Invader>> it = byEntity.entrySet().iterator();
        List<UUID> dead = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<UUID, Invader> e = it.next();
            if (!e.getValue().isValid()) {
                dead.add(e.getKey());
            }
        }
        for (UUID id : dead) {
            unregister(id);
        }
    }

    /** 指定ネクサスの侵略Mobを全て消去する（撤退・強制終了時）。 */
    public void despawnAll(UUID nexusId) {
        for (Invader inv : forNexus(nexusId)) {
            if (inv.isValid()) {
                inv.entity().remove();
            }
            unregister(inv.entity().getUniqueId());
        }
    }

    public void clear() {
        for (Invader inv : new ArrayList<>(byEntity.values())) {
            if (inv.isValid()) {
                inv.entity().remove();
            }
        }
        byEntity.clear();
        byNexus.clear();
    }
}
