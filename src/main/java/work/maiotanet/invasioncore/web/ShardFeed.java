package work.maiotanet.invasioncore.web;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * OBS オーバーレイに流す Shard の増減。
 *
 * <p>ゲーム側（メインスレッド）が積み、Web サーバーのスレッドが読むので、すべて synchronized にしている。
 * オーバーレイは「前回受け取った番号より後」を取りに来るだけなので、古いものは一定数で捨てる。</p>
 */
public final class ShardFeed {

    /**
     * 1回の増減。
     *
     * @param total  変化した後の、その人の手持ち Shard
     * @param delta  増減（使用はマイナス）
     * @param reason 「拾った」「強化した」など、画面に出す理由
     */
    public record Event(long seq, UUID player, String name, int total, int delta, String reason) {
    }

    /** 取りこぼしても困らない程度に残しておく件数。オーバーレイは 0.5 秒おきに取りに来る。 */
    private static final int KEEP = 200;

    private final Deque<Event> events = new ArrayDeque<>();
    private long seq;

    public synchronized void publish(UUID player, String name, int total, int delta, String reason) {
        if (delta == 0) {
            return;
        }
        events.addLast(new Event(++seq, player, name, total, delta, reason));
        while (events.size() > KEEP) {
            events.pollFirst();
        }
    }

    /** after より新しい増減を古い順に返す。 */
    public synchronized List<Event> since(long after) {
        List<Event> out = new ArrayList<>();
        for (Event e : events) {
            if (e.seq() > after) {
                out.add(e);
            }
        }
        return out;
    }

    /** いちばん新しい番号。開いた直後のオーバーレイが過去の分を流さないために使う。 */
    public synchronized long latest() {
        return seq;
    }
}
