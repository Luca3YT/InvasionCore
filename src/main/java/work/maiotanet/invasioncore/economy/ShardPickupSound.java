package work.maiotanet.invasioncore.economy;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Shard を拾ったときの効果音。
 *
 * <p>拾った 1 個につき 1 回、1 tick（0.05 秒）おきに順番に鳴らす。64 個なら約 3.2 秒鳴り続ける。
 * 同じ tick にまとめて鳴らすと重なって 1 回の大きな音にしか聞こえないので、順番待ちにしている。
 * 鳴っている途中でさらに拾った分は、残りの後ろに足す。</p>
 */
public final class ShardPickupSound {

    private static final Sound SOUND = Sound.BLOCK_NOTE_BLOCK_CHIME;
    private static final float VOLUME = 0.8f;
    private static final float PITCH = 2.0f;

    /** プレイヤーごとの、まだ鳴らしていない回数。 */
    private final Map<UUID, Integer> pending = new HashMap<>();

    public void queue(Player player, int count) {
        if (count > 0) {
            pending.merge(player.getUniqueId(), count, Integer::sum);
        }
    }

    /** 毎 tick 呼ばれる。順番待ちのある人に 1 回ずつ鳴らす。 */
    public void tick() {
        Iterator<Map.Entry<UUID, Integer>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            player.playSound(player.getLocation(), SOUND, VOLUME, PITCH);
            int left = entry.getValue() - 1;
            if (left <= 0) {
                it.remove();
            } else {
                entry.setValue(left);
            }
        }
    }
}
