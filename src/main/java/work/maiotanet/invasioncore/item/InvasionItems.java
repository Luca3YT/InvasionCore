package work.maiotanet.invasioncore.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import work.maiotanet.invasioncore.util.Keys;
import work.maiotanet.invasioncore.util.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public static final String TRAP_SPIKE = "trap_spike";
    public static final String TRAP_MINE = "trap_mine";
    public static final String FLASH_CHARGE = "flash_charge";
    public static final String MEDKIT = "medkit";
    public static final String BARRIER = "emergency_barrier";
    public static final String RECALL = "recall_compass";

    /**
     * 独自アイテムに付ける CustomModelData（アイテムの ID → 値）。
     * 値が数字なら数値（floats）、それ以外は文字列（strings）として付ける。
     * 数値はよくあるリソースパックの「custom_model_data: 1」の形、文字列は名前で見分ける形。
     * リソースパックはこの値を見て、独自アイテムだけ見た目を差し替える。空なら付けない。
     * config.yml の custom-model-data から読み、reload でも入れ直す。
     */
    private static volatile Map<String, Object> modelData = Map.of();

    /**
     * 帰還の羅針盤の詠唱中だけ付ける CustomModelData（item_model.yml の recall_charging）。
     * アイテムの名前ではないので modelData とは分けて持つ。null なら詠唱中も見た目を変えない。
     */
    private static volatile Object recallChargingModel;

    /** item_model.yml で詠唱中の見た目を書く項目名。 */
    public static final String RECALL_CHARGING_KEY = "recall_charging";

    private InvasionItems() {
    }

    /**
     * item_model.yml の値を反映する。これより後に作るアイテムから付く。
     *
     * @param byName /invasion give と同じ短い名前（shard・recall など）→ 値。
     *               値は Number（数値として付ける）か String（文字列として付ける）。null や空文字なら付けない
     */
    public static void modelData(Map<String, Object> byName) {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, Object> e : byName.entrySet()) {
            String id = idOfName(e.getKey());
            if (id != null) {
                putModelData(map, id, e.getValue());
            }
        }
        Map<String, Object> charging = new HashMap<>();
        putModelData(charging, RECALL_CHARGING_KEY, byName.get(RECALL_CHARGING_KEY));
        recallChargingModel = charging.get(RECALL_CHARGING_KEY);
        modelData = Map.copyOf(map);
    }

    /** /invasion give の短い名前から、アイテムの ID（PDC の ITEM_ID）へ。知らない名前なら null。 */
    private static String idOfName(String name) {
        ItemStack sample = create(name, 1);
        return sample == null ? null : idOf(sample);
    }

    private static void putModelData(Map<String, Object> map, String id, Object value) {
        if (value instanceof Number number) {
            map.put(id, number.floatValue());
        } else if (value != null && !value.toString().isBlank()) {
            map.put(id, value.toString().trim());
        }
    }

    // ---------- 生成 ----------

    /**
     * /invasion give や SHOP の設定で使う短い名前から作る。知らない名前なら null。
     */
    public static ItemStack create(String name, int amount) {
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "nexus" -> nexusBlock();
            case "hammer" -> hammer();
            case "shard" -> shard(amount);
            case "kit", "repair_kit" -> repairKit(amount);
            case "turret_arrow" -> turret(TURRET_ARROW, amount);
            case "turret_flame" -> turret(TURRET_FLAME, amount);
            case "turret_frost" -> turret(TURRET_FROST, amount);
            case "turret_heal" -> turret(TURRET_HEAL, amount);
            case "spike" -> trap(TRAP_SPIKE, amount);
            case "mine" -> trap(TRAP_MINE, amount);
            case "flash" -> flashCharge(amount);
            case "medkit" -> medkit(amount);
            case "barrier" -> barrier(amount);
            case "recall" -> recall(amount);
            default -> null;
        };
    }

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

    public static ItemStack trap(String id, int amount) {
        if (TRAP_MINE.equals(id)) {
            return build(Material.HEAVY_WEIGHTED_PRESSURE_PLATE, amount, TRAP_MINE,
                    Component.text("爆裂地雷", TextColor.color(0xFF7043)),
                    Text.lore("設置型・使い切り。",
                            "踏んだ侵略者と周囲を吹き飛ばす。",
                            "重装体やボスの足止めに有効。"));
        }
        return build(Material.POINTED_DRIPSTONE, amount, TRAP_SPIKE,
                Component.text("スパイクトラップ", TextColor.color(0xB0BEC5)),
                Text.lore("設置型・繰り返し作動。",
                        "上を通る侵略者に継続ダメージと鈍足。",
                        "誘導路と組み合わせると効果が高い。"));
    }

    public static ItemStack flashCharge(int amount) {
        return build(Material.SNOWBALL, amount, FLASH_CHARGE,
                Component.text("閃光弾", TextColor.color(0xFFF59D)),
                Text.lore("右クリックで投擲。",
                        "着弾点の侵略者を盲目化し、鈍足にする。",
                        "押し切られた時の立て直しに使う。"));
    }

    public static ItemStack medkit(int amount) {
        return build(Material.GHAST_TEAR, amount, MEDKIT,
                Component.text("救急キット", TextColor.color(0xEF9A9A)),
                Text.lore("右クリックで使用。",
                        "自分と周囲の味方を回復する。"));
    }

    public static ItemStack barrier(int amount) {
        return build(Material.IRON_TRAPDOOR, amount, BARRIER,
                Component.text("緊急バリア", TextColor.color(0x90CAF9)),
                Text.lore("ブロックを右クリックで展開。",
                        "その面に鋼鉄級の壁を一時的に張る。",
                        "45秒で消滅する。決壊時の応急処置。"));
    }

    public static ItemStack recall(int amount) {
        // 普通の素材（紙など）の見た目にすると、クラフトの材料として使えてしまう。
        // 回復コンパスはどのレシピにも使われないので、取り違えが起きない。
        return build(Material.RECOVERY_COMPASS, amount, RECALL,
                Component.text("帰還の羅針盤", TextColor.color(0x80DEEA)),
                Text.lore("右クリックで詠唱を始め、終わるとネクサスのそばへ戻る。",
                        "動くかダメージを受けると中断する。",
                        "どのディメンションからでも使える。使うとなくなる。"));
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
            Object model = modelData.get(id);
            if (model != null) {
                applyModel(meta, model);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** CustomModelData を model（Float なら数値、それ以外は文字列）にする。null なら外す。 */
    private static void applyModel(ItemMeta meta, Object model) {
        if (model == null) {
            meta.setCustomModelDataComponent(null);
            return;
        }
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        if (model instanceof Float number) {
            cmd.setFloats(List.of(number));
            cmd.setStrings(List.of());
        } else {
            cmd.setFloats(List.of());
            cmd.setStrings(List.of(model.toString()));
        }
        meta.setCustomModelDataComponent(cmd);
    }

    // ---------- 帰還の羅針盤の詠唱中の見た目 ----------

    /** item_model.yml の recall_charging に値があるか。無ければ詠唱中も見た目を変えない。 */
    public static boolean hasRecallChargingModel() {
        return recallChargingModel != null;
    }

    /**
     * 羅針盤の見た目を詠唱中（charging = true）か普段のものにする。
     * 詠唱中にした羅針盤には目印を付け、サーバーが落ちて戻し損ねても、あとで見つけて戻せるようにする。
     *
     * @return 書き換えたら true（羅針盤でない・戻す必要が無いときは false）
     */
    public static boolean setRecallCharging(ItemStack stack, boolean charging) {
        if (!is(stack, RECALL) || (charging && recallChargingModel == null)
                || (!charging && !isRecallCharging(stack))) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (charging) {
            applyModel(meta, recallChargingModel);
            meta.getPersistentDataContainer().set(Keys.RECALL_CHARGING, PersistentDataType.BYTE, (byte) 1);
        } else {
            // 普段の値は今の item_model.yml のもの。新しく手に入れた羅針盤と同じになり、重ねて持てる
            applyModel(meta, modelData.get(RECALL));
            meta.getPersistentDataContainer().remove(Keys.RECALL_CHARGING);
        }
        stack.setItemMeta(meta);
        return true;
    }

    /** 詠唱中の見た目になっている羅針盤か。 */
    public static boolean isRecallCharging(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(Keys.RECALL_CHARGING, PersistentDataType.BYTE);
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

    public static boolean isTrap(ItemStack stack) {
        String id = idOf(stack);
        return TRAP_SPIKE.equals(id) || TRAP_MINE.equals(id);
    }
}
