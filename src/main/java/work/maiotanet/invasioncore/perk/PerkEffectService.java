package work.maiotanet.invasioncore.perk;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import work.maiotanet.invasioncore.nexus.Nexus;
import work.maiotanet.invasioncore.nexus.NexusManager;

/**
 * 常時効果を持つパークの適用。
 *
 * <p>ダメージ計算のように「その瞬間に掛けるだけ」で済まないパーク（移動速度など）を、
 * 一定間隔でプレイヤーへ反映させる。侵攻に関係ない場所では効かせない。</p>
 */
public final class PerkEffectService {

    private final NexusManager nexuses;
    private final PerkService perks;

    public PerkEffectService(NexusManager nexuses, PerkService perks) {
        this.nexuses = nexuses;
        this.perks = perks;
    }

    /** 1秒ごとに呼ばれる。 */
    public void tick(Iterable<? extends Player> players) {
        for (Player player : players) {
            if (player.isDead() || !player.isOnline()) {
                continue;
            }
            int level = perks.skirmisherLevel(player.getUniqueId());
            if (level <= 0) {
                continue;
            }
            // 拠点から遠く離れた通常プレイでは効かせない。侵攻用のパークなので。
            // 距離を問わない nearestActive だと、同じワールドにいるだけで常時効いてしまう。
            Nexus nexus = nexuses.nearest(player.getLocation());
            if (nexus == null || !nexus.isActive()) {
                continue;
            }
            // Lv1〜2 は速度I、Lv3〜4 は速度II。切れ目なく上書きし続ける。
            int amplifier = level >= 3 ? 1 : 0;
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED, 60, amplifier, true, false, false));
        }
    }
}
