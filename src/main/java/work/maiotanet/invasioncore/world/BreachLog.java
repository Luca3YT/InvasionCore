package work.maiotanet.invasioncore.world;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 侵略Mobが壊したブロックの記録。
 *
 * <p>「拠点が穴だらけになって復旧できない」は防衛ゲームで最も萎える事故なので、
 * 破壊は必ずここに記録し、管理コマンドで巻き戻せるようにしておく。</p>
 */
public final class BreachLog {

    /** 1ネクサスあたりの記録上限。超えたら古いものから捨てる。 */
    private static final int MAX_PER_NEXUS = 20000;

    private record Broken(Location location, BlockData data) {
    }

    private final Map<UUID, Deque<Broken>> byNexus = new HashMap<>();

    public void record(UUID nexusId, Block block) {
        Deque<Broken> log = byNexus.computeIfAbsent(nexusId, k -> new ArrayDeque<>());
        if (log.size() >= MAX_PER_NEXUS) {
            log.pollFirst();
        }
        log.addLast(new Broken(block.getLocation(), block.getBlockData().clone()));
    }

    public int size(UUID nexusId) {
        Deque<Broken> log = byNexus.get(nexusId);
        return log == null ? 0 : log.size();
    }

    /**
     * 記録を新しいものから順に復元する。
     * 現在空気のブロックのみ戻すので、プレイヤーが後から建てた物は壊さない。
     *
     * @return 実際に復元した個数
     */
    public int restore(UUID nexusId) {
        Deque<Broken> log = byNexus.remove(nexusId);
        if (log == null) {
            return 0;
        }
        int restored = 0;
        while (!log.isEmpty()) {
            Broken b = log.pollLast();
            Location loc = b.location();
            if (loc.getWorld() == null) {
                continue;
            }
            Block block = loc.getBlock();
            if (block.getType().isAir()) {
                block.setBlockData(b.data(), false);
                restored++;
            }
        }
        return restored;
    }

    public void clear(UUID nexusId) {
        byNexus.remove(nexusId);
    }
}
