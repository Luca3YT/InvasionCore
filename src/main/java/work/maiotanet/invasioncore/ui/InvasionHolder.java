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
        SHOP
    }

    private final Kind kind;
    private final UUID nexusId;
    private Inventory inventory;

    public InvasionHolder(Kind kind, UUID nexusId) {
        this.kind = kind;
        this.nexusId = nexusId;
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
