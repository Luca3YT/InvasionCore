package work.maiotanet.invasioncore.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;
import work.maiotanet.invasioncore.util.Keys;

/**
 * 独自アイテムのクラフトレシピ。
 *
 * <p>コマンド配布だけだとサバイバルで完結しないので、
 * 主要アイテムは通常プレイの延長で作れるようにしてある。
 * ネクサスだけは意図的に重いコストにして、乱立を防いでいる。</p>
 */
public final class RecipeRegistrar {

    private final Plugin plugin;

    public RecipeRegistrar(Plugin plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        registerNexus();
        registerHammer();
        registerRepairKit();
        registerTurrets();
        registerGadgets();
        registerRecall();
    }

    /** 帰還の羅針盤。鉄インゴット 5 と紙 3 を、並べ方自由で。 */
    private void registerRecall() {
        ShapelessRecipe recipe = new ShapelessRecipe(Keys.key("recipe_recall"), InvasionItems.recall(1));
        recipe.addIngredient(5, Material.IRON_INGOT);
        recipe.addIngredient(3, Material.PAPER);
        add(recipe);
    }

    /** 消耗ガジェット。血の月の直前に量産できる程度のコストにしてある。 */
    private void registerGadgets() {
        ShapedRecipe spike = new ShapedRecipe(Keys.key("recipe_trap_spike"),
                InvasionItems.trap(InvasionItems.TRAP_SPIKE, 4));
        spike.shape(" I ", "III", "SSS");
        spike.setIngredient('I', Material.IRON_INGOT);
        spike.setIngredient('S', Material.STONE);
        add(spike);

        ShapedRecipe mine = new ShapedRecipe(Keys.key("recipe_trap_mine"),
                InvasionItems.trap(InvasionItems.TRAP_MINE, 2));
        mine.shape("III", "ITI", "IRI");
        mine.setIngredient('I', Material.IRON_INGOT);
        mine.setIngredient('T', Material.TNT);
        mine.setIngredient('R', Material.REDSTONE);
        add(mine);

        ShapedRecipe flash = new ShapedRecipe(Keys.key("recipe_flash_charge"),
                InvasionItems.flashCharge(4));
        flash.shape(" G ", "GBG", " G ");
        flash.setIngredient('G', Material.GLOWSTONE_DUST);
        flash.setIngredient('B', Material.GUNPOWDER);
        add(flash);

        ShapedRecipe medkit = new ShapedRecipe(Keys.key("recipe_medkit"),
                InvasionItems.medkit(2));
        medkit.shape("SPS", "PGP", "SPS");
        medkit.setIngredient('S', Material.STRING);
        medkit.setIngredient('P', Material.GLISTERING_MELON_SLICE);
        medkit.setIngredient('G', Material.GOLDEN_CARROT);
        add(medkit);

        ShapedRecipe barrier = new ShapedRecipe(Keys.key("recipe_barrier"),
                InvasionItems.barrier(2));
        barrier.shape("IRI", "RDR", "IRI");
        barrier.setIngredient('I', Material.IRON_BLOCK);
        barrier.setIngredient('R', Material.REDSTONE_BLOCK);
        barrier.setIngredient('D', Material.DIAMOND);
        add(barrier);
    }

    private void registerNexus() {
        ShapedRecipe recipe = new ShapedRecipe(Keys.key("recipe_nexus"), InvasionItems.nexusBlock());
        recipe.shape("ODO", "DNP", "ODO");
        recipe.setIngredient('O', Material.OBSIDIAN);
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('N', Material.NETHER_STAR);
        recipe.setIngredient('P', Material.ENDER_EYE);
        add(recipe);
    }

    private void registerHammer() {
        ShapedRecipe recipe = new ShapedRecipe(Keys.key("recipe_hammer"), InvasionItems.hammer());
        recipe.shape("III", "ISI", " S ");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('S', Material.STICK);
        add(recipe);
    }

    private void registerRepairKit() {
        ShapedRecipe recipe = new ShapedRecipe(Keys.key("recipe_repair_kit"), InvasionItems.repairKit(2));
        recipe.shape("SIS", "IHI", "SIS");
        recipe.setIngredient('S', Material.STRING);
        recipe.setIngredient('I', Material.IRON_NUGGET);
        recipe.setIngredient('H', Material.HONEYCOMB);
        add(recipe);
    }

    private void registerTurrets() {
        addTurret("recipe_turret_arrow", InvasionItems.TURRET_ARROW,
                new String[]{"III", "IDI", "IRI"},
                Material.IRON_INGOT, Material.DISPENSER, Material.REDSTONE_BLOCK);
        addTurret("recipe_turret_flame", InvasionItems.TURRET_FLAME,
                new String[]{"IBI", "BDB", "IRI"},
                Material.IRON_INGOT, Material.BLAZE_ROD, Material.REDSTONE_BLOCK);
        addTurret("recipe_turret_frost", InvasionItems.TURRET_FROST,
                new String[]{"IBI", "BDB", "IRI"},
                Material.IRON_INGOT, Material.BLUE_ICE, Material.REDSTONE_BLOCK);
        addTurret("recipe_turret_heal", InvasionItems.TURRET_HEAL,
                new String[]{"IGI", "GDG", "IRI"},
                Material.IRON_INGOT, Material.GOLD_BLOCK, Material.REDSTONE_BLOCK);
    }

    /**
     * タレット共通の枠組み。
     * I=外枠, B/G=系統素材, D=ディスペンサー, R=レッドストーンブロック。
     */
    private void addTurret(String key, String itemId, String[] shape,
                           Material frame, Material core, Material power) {
        ItemStack result = InvasionItems.turret(itemId, 1);
        ShapedRecipe recipe = new ShapedRecipe(Keys.key(key), result);
        recipe.shape(shape);
        recipe.setIngredient('I', frame);
        recipe.setIngredient('D', Material.DISPENSER);
        recipe.setIngredient('R', power);
        if (String.join("", shape).indexOf('B') >= 0) {
            recipe.setIngredient('B', core);
        }
        if (String.join("", shape).indexOf('G') >= 0) {
            recipe.setIngredient('G', core);
        }
        add(recipe);
    }

    private void add(CraftingRecipe recipe) {
        NamespacedKey key = recipe.getKey();
        // reload 時の二重登録を避ける
        if (plugin.getServer().getRecipe(key) != null) {
            plugin.getServer().removeRecipe(key);
        }
        plugin.getServer().addRecipe(recipe);
    }
}
