package work.maiotanet.invasioncore.util;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * 操作に対する効果音のヘルパ。
 *
 * <p>文字だけの結果表示は、アクションバーが上書きされたりチャットが流れたりすると見落とされる。
 * 押した・断られた・できた、の3種類を耳でも分かるようにそろえる。
 * 成功に専用の演出音がある操作（強化・起動など）はその音を使い、ここの success は音の無い成功だけに使う。
 * 本人にだけ聞こえるよう、ワールドではなくプレイヤーに対して鳴らす。</p>
 */
public final class Sounds {

    private Sounds() {
    }

    /** 画面を開く・切り替える・戻る、情報を見るだけの操作。 */
    public static void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }

    /** 失敗した・断られた。 */
    public static void fail(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
    }

    /** 専用の音が無い操作が成功した。 */
    public static void success(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.6f);
    }
}
