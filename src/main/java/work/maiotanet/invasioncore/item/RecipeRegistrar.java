package work.maiotanet.invasioncore.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
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

    private void add(ShapedRecipe recipe) {
        NamespacedKey key = recipe.getKey();
        // reload 時の二重登録を避ける
        if (plugin.getServer().getRecipe(key) != null) {
            plugin.getServer().removeRecipe(key);
        }
        plugin.getServer().addRecipe(recipe);
    }
}
