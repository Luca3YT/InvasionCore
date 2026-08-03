package work.maiotanet.invasioncore.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import work.maiotanet.invasioncore.util.Keys;
import work.maiotanet.invasioncore.util.Text;

import java.util.List;

/**
 * プラグイン独自アイテムの定義と判定。
 *
 * <p>識別は表示名ではなく PDC の ITEM_ID で行う。
 * 表示名で判定すると、アンビルでリネームされたバニラ品と衝突して事故になる。</p>
 */
public final class InvasionItems {

    public static final String NEXUS_BLOCK = "nexus_block";
    public static final String SHARD = "nexus_shard";
    public static final String HAMMER = "upgrade_hammer";
    public static final String REPAIR_KIT = "repair_kit";
    public static final String TURRET_ARROW = "turret_arrow";
    public static final String TURRET_FLAME = "turret_flame";
    public static final String TURRET_FROST = "turret_frost";
    public static final String TURRET_HEAL = "turret_heal";

    private InvasionItems() {
    }

    // ---------- 生成 ----------

    public static ItemStack nexusBlock() {
        return build(Material.LODESTONE, 1, NEXUS_BLOCK,
                Component.text("ネクサス", Text.ACCENT),
                Text.lore("設置して起動すると侵攻が始まる。",
                        "7日ごとの血の月に総攻撃を受ける。",
                        "破壊されれば拠点は落ちる。"));
    }

    public static ItemStack shard(int amount) {
        return build(Material.AMETHYST_SHARD, amount, SHARD,
                Component.text("ネクサスの残滓", TextColor.color(0xC792EA)),
                Text.lore("防衛設備の建設・強化に使う通貨。",
                        "侵略者を倒すと得られる。"));
    }

    public static ItemStack hammer() {
        return build(Material.IRON_AXE, 1, HAMMER,
                Component.text("強化ハンマー", TextColor.color(0xFFB74D)),
                Text.lore("右クリック: ブロックを強化／修理",
                        "木枠 → 補強木材 → 石積み",
                        "     → 鉄筋コンクリート → 鋼鉄",
                        "スニーク右クリック: 状態を確認"));
    }

    public static ItemStack repairKit(int amount) {
        return build(Material.HONEYCOMB, amount, REPAIR_KIT,
                Component.text("応急補修キット", TextColor.color(0xFFD166)),
                Text.lore("右クリック: 強化ブロックを即時全回復",
                        "Shard を消費せず使える緊急手段。"));
    }

    public static ItemStack turret(String id, int amount) {
        return switch (id) {
            case TURRET_FLAME -> build(Material.BLAST_FURNACE, amount, TURRET_FLAME,
                    Component.text("火炎タレット", TextColor.color(0xFF7043)),
                    Text.lore("設置型。範囲内の侵略者を燃やす。"));
            case TURRET_FROST -> build(Material.PACKED_ICE, amount, TURRET_FROST,
                    Component.text("凍結タレット", TextColor.color(0x81D4FA)),
                    Text.lore("設置型。範囲内の侵略者を鈍足化する。"));
            case TURRET_HEAL -> build(Material.BEACON, amount, TURRET_HEAL,
                    Component.text("修復タレット", TextColor.color(0xA5D6A7)),
                    Text.lore("設置型。周囲の味方と強化ブロックを回復する。"));
            default -> build(Material.DISPENSER, amount, TURRET_ARROW,
                    Component.text("射撃タレット", TextColor.color(0xB0BEC5)),
                    Text.lore("設置型。範囲内の侵略者を自動で撃つ。"));
        };
    }

    private static ItemStack build(Material material, int amount, String id,
                                   Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(Keys.ITEM_ID, PersistentDataType.STRING, id);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ---------- 判定 ----------

    public static String idOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(Keys.ITEM_ID, PersistentDataType.STRING);
    }

    public static boolean is(ItemStack stack, String id) {
        return id.equals(idOf(stack));
    }

    public static boolean isTurret(ItemStack stack) {
        String id = idOf(stack);
        return TURRET_ARROW.equals(id) || TURRET_FLAME.equals(id)
                || TURRET_FROST.equals(id) || TURRET_HEAL.equals(id);
    }
}
