package work.maiotanet.invasioncore.defense;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;

/**
 * ブロックの耐久管理と強化ティア。
 *
 * <p>侵略Mobは「壁を殴って壊す」。その壁がどれだけ持つかを決めるのが本サービス。
 * 強化済みブロックは独自HPを持ち、未強化のバニラブロックは硬度から耐久を導出する。
 * これにより「土で囲めば安全」にならず、かつ「鋼で固めれば持ちこたえる」が成立する。</p>
 */
public final class FortifyService {

    /** 強化済みブロック1個分の状態。 */
    public static final class Fortified {
        private BlockTier tier;
        private double health;
        private double maxHealth;
        /** true = プレイヤーが強化したブロック。false = 硬度から導出した暫定エントリ。 */
        private final boolean managed;

        Fortified(BlockTier tier, double health, double maxHealth, boolean managed) {
            this.tier = tier;
            this.health = health;
            this.maxHealth = maxHealth;
            this.managed = managed;
        }

        public BlockTier tier() {
            return tier;
        }

        public double health() {
            return health;
        }

        public double maxHealth() {
            return maxHealth;
        }

        public boolean isManaged() {
            return managed;
        }

        public double ratio() {
            return maxHealth <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, health / maxHealth));
        }
    }

    private final Plugin plugin;
    private final Map<String, Fortified> blocks = new HashMap<>();
    private final File file;

    public FortifyService(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "fortified.yml");
    }

    // ---------- 参照 ----------

    public Fortified get(Block block) {
        return blocks.get(key(block));
    }

    public boolean isFortified(Block block) {
        return blocks.containsKey(key(block));
    }

    public int count() {
        return blocks.size();
    }

    // ---------- 強化 / 修理 ----------

    /** 未強化ブロックを木枠ティアに変える。 */
    public Fortified fortify(Block block) {
        BlockTier tier = BlockTier.first();
        block.setType(tier.material());
        Fortified f = new Fortified(tier, tier.maxHealth(), tier.maxHealth(), true);
        blocks.put(key(block), f);
        effectUpgrade(block);
        return f;
    }

    /** 次のティアへ上げる。上限なら false。 */
    public boolean upgrade(Block block) {
        Fortified f = get(block);
        if (f == null || !f.managed) {
            return false;
        }
        BlockTier next = f.tier.next();
        if (next == null) {
            return false;
        }
        f.tier = next;
        f.maxHealth = next.maxHealth();
        f.health = next.maxHealth();
        block.setType(next.material());
        effectUpgrade(block);
        return true;
    }

    /** HPを回復する。回復した実量を返す。 */
    public double repair(Block block, double amount) {
        Fortified f = get(block);
        if (f == null || !f.managed) {
            return 0.0;
        }
        double before = f.health;
        f.health = Math.min(f.maxHealth, f.health + amount);
        double healed = f.health - before;
        if (healed > 0) {
            World w = block.getWorld();
            w.spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 1.0, 0.5), 6, 0.3, 0.3, 0.3, 0.0);
            w.playSound(block.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.4f);
        }
        return healed;
    }

    /** 修理に必要な Shard 量（割引前）。 */
    public int repairCost(Block block, int costPerHp) {
        Fortified f = get(block);
        if (f == null || !f.managed) {
            return 0;
        }
        double missing = f.maxHealth - f.health;
        if (missing <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(missing / Math.max(1, costPerHp)));
    }

    // ---------- 破壊 ----------

    /**
     * ブロックにダメージを与える。破壊されたら true。
     * 未強化ブロックは硬度ベースの暗黙HPを持ち、都度メモリに載せずに即時判定する。
     */
    public boolean damage(Block block, double amount) {
        if (block.getType().isAir()) {
            return false;
        }
        String k = key(block);
        Fortified f = blocks.get(k);
        if (f == null) {
            // 未強化ブロック。硬度から導出した耐久で殴り合う。
            double implicitMax = implicitHealth(block.getType());
            if (implicitMax <= 0) {
                // 破壊不可（岩盤など）
                return false;
            }
            // 暫定エントリ。破壊されなければ次回も再利用される。
            f = new Fortified(BlockTier.WOOD_FRAME, implicitMax, implicitMax, false);
            blocks.put(k, f);
        }
        f.health -= amount;
        if (f.health <= 0) {
            blocks.remove(k);
            World w = block.getWorld();
            Location center = block.getLocation().add(0.5, 0.5, 0.5);
            w.spawnParticle(Particle.BLOCK, center, 24, 0.3, 0.3, 0.3, 0.0, block.getBlockData());
            w.playSound(center, Sound.BLOCK_STONE_BREAK, 1.0f, 0.7f);
            block.setType(Material.AIR);
            return true;
        }
        // ひび割れ表現。破壊アニメーションAPIを使わずパーティクルで代替する。
        World w = block.getWorld();
        w.spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5), 4,
                0.25, 0.25, 0.25, 0.0, block.getBlockData());
        return false;
    }

    /** プレイヤーが自分で壊した／置き換えたときに記録を消す。 */
    public void forget(Block block) {
        blocks.remove(key(block));
    }

    /**
     * 硬度から未強化ブロックの耐久を導出する。
     * 土や砂は一瞬で抜かれ、黒曜石は現実的な時間で抜けなくなる。
     */
    public double implicitHealth(Material material) {
        if (material.isAir()) {
            return 0.0;
        }
        float hardness = material.getHardness();
        if (hardness < 0) {
            // 岩盤・バリアなど破壊不能
            return 0.0;
        }
        if (!material.isSolid()) {
            return 8.0;
        }
        return 20.0 + hardness * 26.0;
    }

    // ---------- 演出 ----------

    private void effectUpgrade(Block block) {
        World w = block.getWorld();
        Location center = block.getLocation().add(0.5, 1.0, 0.5);
        w.spawnParticle(Particle.CRIT, center, 14, 0.3, 0.3, 0.3, 0.1);
        w.playSound(center, Sound.BLOCK_ANVIL_USE, 0.7f, 1.1f);
    }

    // ---------- 保守 ----------

    /**
     * 実体と食い違った記録を捨てる。
     * 定期的に呼ばないと、地形改変で記録が無限に溜まる。
     */
    public void prune() {
        Iterator<Map.Entry<String, Fortified>> it = blocks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Fortified> e = it.next();
            Block block = fromKey(e.getKey());
            if (block == null) {
                // ワールドが読み込まれていないだけの可能性があるので残す
                continue;
            }
            Fortified f = e.getValue();
            if (block.getType().isAir()) {
                it.remove();
            } else if (f.managed && block.getType() != f.tier.material()) {
                // 強化ブロックのはずが別物になっている＝プレイヤーが置き換えた
                it.remove();
            } else if (!f.managed && f.health >= f.maxHealth) {
                // 無傷の暫定エントリは保持する意味がない
                it.remove();
            }
        }
    }

    // ---------- 永続化 ----------

    private String key(Block block) {
        return block.getWorld().getName() + ';' + block.getX() + ';' + block.getY() + ';' + block.getZ();
    }

    private Block fromKey(String key) {
        String[] parts = key.split(";");
        if (parts.length != 4) {
            return null;
        }
        World w = plugin.getServer().getWorld(parts[0]);
        if (w == null) {
            return null;
        }
        try {
            return w.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public void load() {
        blocks.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("blocks");
        if (root == null) {
            return;
        }
        for (String k : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(k);
            if (sec == null) {
                continue;
            }
            try {
                BlockTier tier = BlockTier.valueOf(sec.getString("tier", BlockTier.WOOD_FRAME.name()));
                double hp = sec.getDouble("hp", tier.maxHealth());
                blocks.put(k, new Fortified(tier, hp, tier.maxHealth(), true));
            } catch (IllegalArgumentException ignored) {
                // 未知ティアはスキップ
            }
        }
        plugin.getLogger().info("強化ブロックを " + blocks.size() + " 個読み込みました。");
    }

    public void save() {
        prune();
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("blocks");
        for (Map.Entry<String, Fortified> e : blocks.entrySet()) {
            // 硬度から導出した暫定エントリは保存しない（次回また導出すればよい）
            Fortified f = e.getValue();
            if (!f.managed) {
                continue;
            }
            ConfigurationSection sec = root.createSection(e.getKey());
            sec.set("tier", f.tier.name());
            sec.set("hp", f.health);
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("データフォルダを作成できませんでした: " + parent);
            }
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "fortified.yml の保存に失敗しました", ex);
        }
    }
}
