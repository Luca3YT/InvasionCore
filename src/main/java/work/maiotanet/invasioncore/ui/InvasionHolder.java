package work.maiotanet.invasioncore.ui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * プラグインが開いた GUI であることの目印。
 * タイトル文字列で判定するとリソースパックや翻訳で壊れるため、Holder で識別する。
 */
public final class InvasionHolder implements InventoryHolder {

    public enum Kind {
        /** ネクサスのメインメニュー。 */
        MAIN,
        /** ネクサスのアップグレードツリー。 */
        UPGRADE,
        /** プレイヤーのパーク。 */
        PERK,
        /** ネクサス内の Shard の引き出し（管理者のみ）。 */
        SHARD,
        /** Shard で道具や素材を買う SHOP。 */
        SHOP,
        /** SHOP の 1 品の売買（個数を選ぶ）。 */
        SHOP_TRADE,
        /** SHOP の売買の確認。 */
        SHOP_CONFIRM,
        /** /invasion item のアイテムの取り出し画面。ネクサスに関係しない。 */
        ITEMS
    }

    private final Kind kind;
    private final UUID nexusId;
    private Inventory inventory;
    /** SHOP の売買の画面で選んでいる品（SHOP に並んでいる順の番号）・個数・売るか。 */
    private int shopIndex;
    private int shopCount;
    private boolean shopSell;

    public InvasionHolder(Kind kind, UUID nexusId) {
        this.kind = kind;
        this.nexusId = nexusId;
    }

    public InvasionHolder shop(int index, int count, boolean sell) {
        this.shopIndex = index;
        this.shopCount = count;
        this.shopSell = sell;
        return this;
    }

    public int shopIndex() {
        return shopIndex;
    }

    public int shopCount() {
        return shopCount;
    }

    public boolean shopSell() {
        return shopSell;
    }

    public Kind kind() {
        return kind;
    }

    public UUID nexusId() {
        return nexusId;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
