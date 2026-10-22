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
    /**
     * 構造物のチェストや補給物資に入れた Shard のうち、まだ取り出されていない数 (Integer)。
     * 取り出したときに「拾った」として数えるのは、この数までにする（自分で入れた Shard を出し入れしても数えない）。
     */
    public static NamespacedKey LOOT_SHARDS;
    /** 変調「増殖」で分裂して出てきた侵略Mobの目印 (Byte)。この個体はもう分裂しない。 */
    public static NamespacedKey INVADER_SPLIT_CHILD;
    /** 死んだあと観戦モードで待っている間、復活する時刻 (Long, ミリ秒)。再起動を跨いでも戻せるよう本人に記録する。 */
    public static NamespacedKey DEATH_SPECTATE_UNTIL;

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
        LOOT_SHARDS = key("loot_shards");
        INVADER_SPLIT_CHILD = key("invader_split_child");
        DEATH_SPECTATE_UNTIL = key("death_spectate_until");
    }

    public static NamespacedKey key(String name) {
        return new NamespacedKey(plugin, name);
    }
}
