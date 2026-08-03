package work.maiotanet.invasioncore.defense;

import org.bukkit.Material;
import work.maiotanet.invasioncore.item.InvasionItems;

/**
 * タレットの系統。
 * 単体火力（射撃）・範囲継続（火炎）・妨害（凍結）・支援（修復）で役割を分けている。
 * 1種類だけ置けば勝てる構成にしないことが、拠点設計に意味を持たせる条件。
 */
public enum TurretType {

    ARROW("射撃タレット", InvasionItems.TURRET_ARROW, Material.DISPENSER, Material.ARROW,
            14.0, 20, 4.0, 256),
    FLAME("火炎タレット", InvasionItems.TURRET_FLAME, Material.BLAST_FURNACE, Material.BLAZE_POWDER,
            8.0, 40, 3.0, 128),
    FROST("凍結タレット", InvasionItems.TURRET_FROST, Material.PACKED_ICE, Material.SNOWBALL,
            11.0, 40, 0.0, 128),
    HEAL("修復タレット", InvasionItems.TURRET_HEAL, Material.BEACON, Material.REDSTONE,
            10.0, 60, 0.0, 128);

    private final String displayName;
    private final String itemId;
    private final Material block;
    private final Material ammo;
    private final double baseRange;
    private final int cooldownTicks;
    private final double power;
    private final int maxAmmo;

    TurretType(String displayName, String itemId, Material block, Material ammo,
               double baseRange, int cooldownTicks, double power, int maxAmmo) {
        this.displayName = displayName;
        this.itemId = itemId;
        this.block = block;
        this.ammo = ammo;
        this.baseRange = baseRange;
        this.cooldownTicks = cooldownTicks;
        this.power = power;
        this.maxAmmo = maxAmmo;
    }

    public String displayName() {
        return displayName;
    }

    public String itemId() {
        return itemId;
    }

    public Material block() {
        return block;
    }

    public Material ammo() {
        return ammo;
    }

    public double baseRange() {
        return baseRange;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    /** ダメージ量、または回復量の基準値。 */
    public double power() {
        return power;
    }

    public int maxAmmo() {
        return maxAmmo;
    }

    public static TurretType byItemId(String itemId) {
        for (TurretType t : values()) {
            if (t.itemId.equals(itemId)) {
                return t;
            }
        }
        return null;
    }

    public static TurretType byBlock(Material material) {
        for (TurretType t : values()) {
            if (t.block == material) {
                return t;
            }
        }
        return null;
    }
}
