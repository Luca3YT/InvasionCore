package work.maiotanet.invasioncore.stage;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.nexus.Nexus;

import java.util.ArrayList;
import java.util.List;

/**
 * 7DTD の GameStage 相当。侵略MODの「ネクサスLv」単体より遥かに扱いやすい難易度指標。
 *
 * <p>ネクサスLv・生存日数・参加者の平均経験値レベル・人数の4要素から算出する。
 * 敵の数、変異ティア、ボス出現、ルート品質の全てがこの値を参照する。</p>
 */
public final class GameStageService {

    private final InvasionConfig config;

    public GameStageService(InvasionConfig config) {
        this.config = config;
    }

    /**
     * ネクサスの現在 GameStage。
     *
     * @param nexus       対象ネクサス
     * @param participants 参加中のプレイヤー（オフライン分は数えない）
     */
    public int compute(Nexus nexus, List<Player> participants) {
        double fromNexus = nexus.level() * config.stagePerNexusLevel();
        double fromDays = nexus.day() * config.stagePerDay();

        double avgLevel = 0.0;
        if (!participants.isEmpty()) {
            int sum = 0;
            for (Player p : participants) {
                sum += p.getLevel();
            }
            avgLevel = (double) sum / participants.size();
        }
        double fromPlayers = avgLevel * config.stagePerPlayerLevel();

        // 人数が増えるほど総量は増やすが、1人あたりの負荷は緩やかに逓減させる。
        // 線形に増やすと4人以上で理不尽になり、増やさないと多人数で作業ゲーになる。
        int count = Math.max(1, participants.size());
        double partyFactor = 1.0 + config.stagePartyBonus() * Math.sqrt(count - 1.0) * 2.0;

        return (int) Math.max(1, Math.round((fromNexus + fromDays + fromPlayers) * partyFactor));
    }

    /**
     * 参加者。サーバーにいるメンバー全員で、どのディメンションにいても数える。
     *
     * <p>ネクサスはサーバーに1基だけで、サーバーに入った人は全員そのネクサスのメンバーになる。
     * 距離で絞ると、素材集めで遠出した人のサイドバーや通知が消え、報酬ももらえなくなる。
     * 死んでいる人と観戦モードの人（配信者の見回りなど）は数えない。
     * /invasion leave でメンバーから抜けた人も数えない。</p>
     */
    public List<Player> participants(Nexus nexus) {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (nexus.isMember(p.getUniqueId())) {
                out.add(p);
            }
        }
        return out;
    }

    public int computeCurrent(Nexus nexus) {
        return compute(nexus, participants(nexus));
    }
}
