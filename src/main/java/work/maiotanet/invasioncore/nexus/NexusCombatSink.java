package work.maiotanet.invasioncore.nexus;

/**
 * ネクサスへのダメージ適用口。
 *
 * <p>AI 層がフェーズ管理（WaveEngine）を直接知らずに済むよう、
 * ダメージ処理だけをこのインターフェース越しに呼ばせる。</p>
 */
public interface NexusCombatSink {

    /**
     * ネクサスにダメージを与える。0になったらフェーズを崩壊へ遷移させるのは実装側の責務。
     *
     * @param nexus  対象
     * @param amount ダメージ量
     */
    void damageNexus(Nexus nexus, double amount);
}
