package work.maiotanet.invasioncore.economy;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import work.maiotanet.invasioncore.item.InvasionItems;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 報酬生成。7DTD の LootStage 相当で、GameStage に応じて中身が良くなる。
 *
 * <p>報酬はネクサス内のバッファに積まれ、プレイヤーが GUI から受け取る。
 * 侵略MODの「ネクサスに戦利品が溜まる」挙動をそのまま踏襲している。</p>
 */
public final class RewardService {

    /** GameStage 帯ごとの抽選テーブル。 */
    private record Entry(Material material, int min, int max, int weight) {
    }

    private static final List<Entry> TIER_1 = List.of(
            new Entry(Material.IRON_INGOT, 3, 8, 100),
            new Entry(Material.COAL, 8, 20, 90),
            new Entry(Material.ARROW, 16, 48, 80),
            new Entry(Material.BREAD, 4, 10, 70),
            new Entry(Material.OAK_LOG, 16, 32, 60),
            new Entry(Material.EXPERIENCE_BOTTLE, 2, 6, 40));

    private static final List<Entry> TIER_2 = List.of(
            new Entry(Material.IRON_BLOCK, 1, 3, 100),
            new Entry(Material.GOLD_INGOT, 4, 10, 90),
            new Entry(Material.REDSTONE_BLOCK, 1, 4, 70),
            new Entry(Material.EXPERIENCE_BOTTLE, 6, 16, 70),
            new Entry(Material.GOLDEN_APPLE, 1, 2, 45),
            new Entry(Material.OBSIDIAN, 4, 10, 40));

    private static final List<Entry> TIER_3 = List.of(
            new Entry(Material.DIAMOND, 2, 6, 100),
            new Entry(Material.EMERALD, 4, 12, 80),
            new Entry(Material.OBSIDIAN, 8, 20, 70),
            new Entry(Material.ENCHANTED_BOOK, 1, 1, 50),
            new Entry(Material.GOLDEN_APPLE, 2, 4, 50),
            new Entry(Material.DIAMOND_BLOCK, 1, 1, 25));

    private static final List<Entry> TIER_4 = List.of(
            new Entry(Material.DIAMOND_BLOCK, 1, 3, 100),
            new Entry(Material.NETHERITE_SCRAP, 1, 3, 70),
            new Entry(Material.TOTEM_OF_UNDYING, 1, 1, 45),
            new Entry(Material.ENCHANTED_GOLDEN_APPLE, 1, 1, 25),
            new Entry(Material.NETHERITE_INGOT, 1, 1, 20));

    /**
     * ブラッドムーン踏破報酬を生成する。
     *
     * @param gameStage 現在の GameStage
     * @param waves     踏破したウェーブ数（多いほど点数が増える）
     */
    public List<ItemStack> victoryLoot(int gameStage, int waves) {
        List<ItemStack> out = new ArrayList<>();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        int rolls = 3 + waves / 3 + gameStage / 40;
        rolls = Math.min(14, rolls);

        for (int i = 0; i < rolls; i++) {
            List<Entry> table = tableFor(gameStage, rng);
            Entry entry = pick(table, rng);
            out.add(roll(entry, rng));
        }

        // 防衛の再投資用に、補修キットは必ず入れる
        out.add(InvasionItems.repairKit(1 + gameStage / 50));

        // 高ステージではタレットが直接出る
        if (gameStage >= 40 && rng.nextDouble() < 0.35) {
            String[] turrets = {
                    InvasionItems.TURRET_ARROW, InvasionItems.TURRET_FLAME,
                    InvasionItems.TURRET_FROST, InvasionItems.TURRET_HEAL};
            out.add(InvasionItems.turret(turrets[rng.nextInt(turrets.length)], 1));
        }
        return out;
    }

    /**
     * 抽選するテーブルを選ぶ。
     * 帯の境目で急に中身が変わらないよう、隣接する下位テーブルも一定確率で引く。
     */
    private List<Entry> tableFor(int gameStage, ThreadLocalRandom rng) {
        if (gameStage >= 100) {
            return rng.nextDouble() < 0.65 ? TIER_4 : TIER_3;
        }
        if (gameStage >= 50) {
            return rng.nextDouble() < 0.65 ? TIER_3 : TIER_2;
        }
        if (gameStage >= 20) {
            return rng.nextDouble() < 0.7 ? TIER_2 : TIER_1;
        }
        return TIER_1;
    }

    private Entry pick(List<Entry> table, ThreadLocalRandom rng) {
        int total = 0;
        for (Entry e : table) {
            total += e.weight();
        }
        int roll = rng.nextInt(total);
        for (Entry e : table) {
            roll -= e.weight();
            if (roll < 0) {
                return e;
            }
        }
        return table.get(table.size() - 1);
    }

    /** 空中投下の中身。戦闘報酬より消耗品寄りにして、取りに行く動機を作る。 */
    public List<ItemStack> airDropLoot(int gameStage) {
        List<ItemStack> out = new ArrayList<>();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        out.add(InvasionItems.shard(12 + gameStage / 3));
        out.add(InvasionItems.repairKit(1 + rng.nextInt(2)));
        out.add(new ItemStack(Material.ARROW, 32 + rng.nextInt(32)));
        out.add(new ItemStack(Material.COOKED_BEEF, 8 + rng.nextInt(8)));
        for (int i = 0; i < 2; i++) {
            List<Entry> table = tableFor(gameStage, rng);
            out.add(roll(pick(table, rng), rng));
        }
        return out;
    }

    /** 抽選したエントリを実物にする。 */
    private ItemStack roll(Entry entry, ThreadLocalRandom rng) {
        if (entry.material() == Material.ENCHANTED_BOOK) {
            return enchantedBook(rng);
        }
        return new ItemStack(entry.material(), rng.nextInt(entry.min(), entry.max() + 1));
    }

    /**
     * エンチャントの本。素の ItemStack だとエンチャントが何も付いていない本になってしまうので、
     * 防衛に役立つものから1つを付けて渡す。
     */
    private ItemStack enchantedBook(ThreadLocalRandom rng) {
        org.bukkit.enchantments.Enchantment[] pool = {
                org.bukkit.enchantments.Enchantment.PROTECTION, org.bukkit.enchantments.Enchantment.SHARPNESS,
                org.bukkit.enchantments.Enchantment.POWER, org.bukkit.enchantments.Enchantment.EFFICIENCY,
                org.bukkit.enchantments.Enchantment.UNBREAKING, org.bukkit.enchantments.Enchantment.MENDING};
        org.bukkit.enchantments.Enchantment enchantment = pool[rng.nextInt(pool.length)];
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        if (book.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta meta) {
            meta.addStoredEnchant(enchantment, enchantment.getMaxLevel(), false);
            book.setItemMeta(meta);
        }
        return book;
    }
}
