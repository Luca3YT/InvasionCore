package work.maiotanet.invasioncore.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * プラグイン内で使う NamespacedKey の集約。
 * PDC のキー名を各所に散らすと typo で沈黙バグになるため一箇所にまとめる。
 */
public final class Keys {

    private static Plugin plugin;

    /** 侵略Mobが属するネクサスのUUID (String) */
    public static NamespacedKey INVADER_NEXUS;
    /** 侵略Mobの種別 (String / InvaderType.name()) */
    public static NamespacedKey INVADER_TYPE;
    /** 侵略Mobの変異ティア (String / MutationTier.name()) */
    public static NamespacedKey INVADER_TIER;
    /** 侵略Mobが湧いたウェーブ番号 (Integer) */
    public static NamespacedKey INVADER_WAVE;
    /** 侵略Mobが落とすShard量 (Integer) */
    public static NamespacedKey INVADER_SHARD;
    /** ボスの種別 (String / BossType.name()) */
    public static NamespacedKey INVADER_BOSS;
    /** 侵略Mobが撃ち出した質量爆弾であることの目印 (String / nexus uuid) */
    public static NamespacedKey PROJECTILE_NEXUS;
    /** 質量爆弾の威力倍率 (Double) */
    public static NamespacedKey PROJECTILE_POWER;

    /** カスタムアイテムのID (String) */
    public static NamespacedKey ITEM_ID;
    /** 修理キットなどの残り使用回数 (Integer) */
    public static NamespacedKey ITEM_USES;

    private Keys() {
    }

    public static void init(Plugin owner) {
        plugin = owner;
        INVADER_NEXUS = key("invader_nexus");
        INVADER_TYPE = key("invader_type");
        INVADER_TIER = key("invader_tier");
        INVADER_WAVE = key("invader_wave");
        INVADER_SHARD = key("invader_shard");
        INVADER_BOSS = key("invader_boss");
        PROJECTILE_NEXUS = key("projectile_nexus");
        PROJECTILE_POWER = key("projectile_power");
        ITEM_ID = key("item_id");
        ITEM_USES = key("item_uses");
    }

    public static NamespacedKey key(String name) {
        return new NamespacedKey(plugin, name);
    }
}
