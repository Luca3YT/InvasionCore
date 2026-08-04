package work.maiotanet.invasioncore.defense;

import org.bukkit.Material;

/**
 * 7DTD のブロック強化ティア。
 * 木枠から鋼まで段階的に強化していく。侵略MODの単純なバリケードより設計自由度が高い。
 */
public enum BlockTier {

    // コストは「1ブロックあたり」であることに注意。
    // 壁は数十ブロック単位で積むので、1個 100 Shard を超えると現実的な時間で完成しない。
    // 未強化の丸石が約110なので、木枠がそれを明確に上回らないと強化する動機が生まれない。
    WOOD_FRAME("木枠", Material.OAK_PLANKS, 250, 3),
    REINFORCED_WOOD("補強木材", Material.STRIPPED_OAK_WOOD, 550, 8),
    STONE("石積み", Material.COBBLESTONE, 1000, 16),
    CONCRETE("鉄筋コンクリート", Material.GRAY_CONCRETE, 1900, 30),
    STEEL("鋼鉄", Material.IRON_BLOCK, 3600, 55);

    private static final BlockTier[] ORDER = values();

    private final String displayName;
    private final Material material;
    private final int maxHealth;
    private final int upgradeCost;

    BlockTier(String displayName, Material material, int maxHealth, int upgradeCost) {
        this.displayName = displayName;
        this.material = material;
        this.maxHealth = maxHealth;
        this.upgradeCost = upgradeCost;
    }

    public String displayName() {
        return displayName;
    }

    public Material material() {
        return material;
    }

    public int maxHealth() {
        return maxHealth;
    }

    /** このティアへ上げるのに必要な Shard。 */
    public int upgradeCost() {
        return upgradeCost;
    }

    public BlockTier next() {
        int idx = ordinal() + 1;
        return idx < ORDER.length ? ORDER[idx] : null;
    }

    public boolean isMax() {
        return next() == null;
    }

    public static BlockTier first() {
        return WOOD_FRAME;
    }

    /** マテリアルから対応ティアを逆引きする。該当なしなら null。 */
    public static BlockTier byMaterial(Material material) {
        for (BlockTier tier : ORDER) {
            if (tier.material == material) {
                return tier;
            }
        }
        return null;
    }
}
