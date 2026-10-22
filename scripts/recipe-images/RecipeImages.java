import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 独自アイテムのクラフト画像（作業台の画面にアイテムの絵を並べたもの）を作る。
 *
 * <p>絵は手元の Minecraft クライアントの jar から読む。リポジトリにはゲームの素材そのものは置かず、
 * 合成した完成画像だけを docs/images/recipes/ に出す。
 * レシピを変えたら、RecipeRegistrar と下の RECIPES の両方を直して、これを実行し直す。</p>
 *
 * <pre>
 * java scripts/recipe-images/RecipeImages.java "%APPDATA%\.minecraft\versions\26.2\26.2.jar" docs/images/recipes
 * </pre>
 */
public final class RecipeImages {

    /** 画像の拡大率。ゲームの GUI 1 ピクセル = この数のピクセル。 */
    private static final int SCALE = 4;

    /** crafting_table.png から切り出す範囲（GUI の座標）。タイトルの文字は入れない。 */
    private static final int CROP_X = 24;
    private static final int CROP_Y = 12;
    private static final int CROP_W = 128;
    private static final int CROP_H = 64;

    /** 3×3 の左上のマスにアイテムを描く位置と、マスの間隔。完成品のマスの位置。 */
    private static final int GRID_X = 30;
    private static final int GRID_Y = 17;
    private static final int SLOT = 18;
    private static final int RESULT_X = 124;
    private static final int RESULT_Y = 35;

    /** アイテムの絵の描き方。 */
    private sealed interface Icon permits Flat, Cube {
    }

    /** 平らなアイテム（インゴットなど）。 */
    private record Flat(String texture) implements Icon {
    }

    /**
     * ブロック。持ち物の欄と同じく斜め上から見た立方体で描く。
     *
     * @param height 1.0 が普通のブロック。感圧板やトラップドアは薄く描く
     */
    private record Cube(String top, String front, String side, double height) implements Icon {
        Cube(String all) {
            this(all, all, all, 1.0);
        }
    }

    private record Recipe(String file, Icon result, int count, String[] rows, Map<Character, Icon> key) {
    }

    private static final Icon OBSIDIAN = new Cube("block/obsidian");
    private static final Icon DIAMOND = new Flat("item/diamond");
    private static final Icon NETHER_STAR = new Flat("item/nether_star");
    private static final Icon ENDER_EYE = new Flat("item/ender_eye");
    private static final Icon IRON_INGOT = new Flat("item/iron_ingot");
    private static final Icon STICK = new Flat("item/stick");
    private static final Icon STRING = new Flat("item/string");
    private static final Icon IRON_NUGGET = new Flat("item/iron_nugget");
    private static final Icon HONEYCOMB = new Flat("item/honeycomb");
    private static final Icon DISPENSER = new Cube("block/furnace_top", "block/dispenser_front", "block/furnace_side", 1.0);
    private static final Icon REDSTONE_BLOCK = new Cube("block/redstone_block");
    private static final Icon BLAZE_ROD = new Flat("item/blaze_rod");
    private static final Icon BLUE_ICE = new Cube("block/blue_ice");
    private static final Icon GOLD_BLOCK = new Cube("block/gold_block");
    private static final Icon STONE = new Cube("block/stone");
    private static final Icon TNT = new Cube("block/tnt_top", "block/tnt_side", "block/tnt_side", 1.0);
    private static final Icon REDSTONE = new Flat("item/redstone");
    private static final Icon GLOWSTONE_DUST = new Flat("item/glowstone_dust");
    private static final Icon GUNPOWDER = new Flat("item/gunpowder");
    private static final Icon GLISTERING_MELON = new Flat("item/glistering_melon_slice");
    private static final Icon GOLDEN_CARROT = new Flat("item/golden_carrot");
    private static final Icon IRON_BLOCK = new Cube("block/iron_block");
    private static final Icon PAPER = new Flat("item/paper");

    /** 独自アイテムの見た目（InvasionItems の Material と同じもの）。 */
    private static final Icon NEXUS = new Cube("block/lodestone_top", "block/lodestone_side", "block/lodestone_side", 1.0);
    private static final Icon HAMMER = new Flat("item/iron_axe");
    private static final Icon TURRET_FLAME = new Cube("block/blast_furnace_top", "block/blast_furnace_front", "block/blast_furnace_side", 1.0);
    private static final Icon TURRET_FROST = new Cube("block/packed_ice");
    private static final Icon TURRET_HEAL = new Cube("block/beacon");
    private static final Icon SPIKE = new Flat("item/pointed_dripstone");
    private static final Icon MINE = new Cube("block/iron_block", "block/iron_block", "block/iron_block", 1.0 / 16);
    private static final Icon FLASH = new Flat("item/snowball");
    private static final Icon MEDKIT = new Flat("item/ghast_tear");
    private static final Icon BARRIER = new Cube("block/iron_trapdoor", "block/iron_trapdoor", "block/iron_trapdoor", 3.0 / 16);
    private static final Icon RECALL = new Flat("item/recovery_compass_00");

    /** RecipeRegistrar と同じ並び。並べ方自由のレシピは、見本の並べ方を 1 つ描く。 */
    private static final List<Recipe> RECIPES = List.of(
            new Recipe("nexus", NEXUS, 1, new String[]{"ODO", "DNP", "ODO"},
                    Map.of('O', OBSIDIAN, 'D', DIAMOND, 'N', NETHER_STAR, 'P', ENDER_EYE)),
            new Recipe("hammer", HAMMER, 1, new String[]{"III", "ISI", " S "},
                    Map.of('I', IRON_INGOT, 'S', STICK)),
            new Recipe("repair_kit", HONEYCOMB, 2, new String[]{"SIS", "IHI", "SIS"},
                    Map.of('S', STRING, 'I', IRON_NUGGET, 'H', HONEYCOMB)),
            new Recipe("turret_arrow", DISPENSER, 1, new String[]{"III", "IDI", "IRI"},
                    Map.of('I', IRON_INGOT, 'D', DISPENSER, 'R', REDSTONE_BLOCK)),
            new Recipe("turret_flame", TURRET_FLAME, 1, new String[]{"IBI", "BDB", "IRI"},
                    Map.of('I', IRON_INGOT, 'B', BLAZE_ROD, 'D', DISPENSER, 'R', REDSTONE_BLOCK)),
            new Recipe("turret_frost", TURRET_FROST, 1, new String[]{"IBI", "BDB", "IRI"},
                    Map.of('I', IRON_INGOT, 'B', BLUE_ICE, 'D', DISPENSER, 'R', REDSTONE_BLOCK)),
            new Recipe("turret_heal", TURRET_HEAL, 1, new String[]{"IGI", "GDG", "IRI"},
                    Map.of('I', IRON_INGOT, 'G', GOLD_BLOCK, 'D', DISPENSER, 'R', REDSTONE_BLOCK)),
            new Recipe("trap_spike", SPIKE, 4, new String[]{" I ", "III", "SSS"},
                    Map.of('I', IRON_INGOT, 'S', STONE)),
            new Recipe("trap_mine", MINE, 2, new String[]{"III", "ITI", "IRI"},
                    Map.of('I', IRON_INGOT, 'T', TNT, 'R', REDSTONE)),
            new Recipe("flash_charge", FLASH, 4, new String[]{" G ", "GBG", " G "},
                    Map.of('G', GLOWSTONE_DUST, 'B', GUNPOWDER)),
            new Recipe("medkit", MEDKIT, 2, new String[]{"SPS", "PGP", "SPS"},
                    Map.of('S', STRING, 'P', GLISTERING_MELON, 'G', GOLDEN_CARROT)),
            new Recipe("barrier", BARRIER, 2, new String[]{"IRI", "RDR", "IRI"},
                    Map.of('I', IRON_BLOCK, 'R', REDSTONE_BLOCK, 'D', DIAMOND)),
            new Recipe("recall", RECALL, 1, new String[]{"PPP", "III", "II "},
                    Map.of('P', PAPER, 'I', IRON_INGOT))
    );

    private final ZipFile jar;
    private final Map<String, BufferedImage> cache = new LinkedHashMap<>();

    private RecipeImages(ZipFile jar) {
        this.jar = jar;
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("usage: java RecipeImages.java <minecraft client jar> <output dir>");
            System.exit(1);
        }
        File out = new File(args[1]);
        if (!out.isDirectory() && !out.mkdirs()) {
            throw new IOException("cannot create " + out);
        }
        try (ZipFile jar = new ZipFile(args[0])) {
            RecipeImages tool = new RecipeImages(jar);
            for (Recipe recipe : RECIPES) {
                File file = new File(out, recipe.file() + ".png");
                ImageIO.write(tool.render(recipe), "png", file);
                System.out.println("wrote " + file);
            }
        }
    }

    // ---------- 合成 ----------

    private BufferedImage render(Recipe recipe) throws IOException {
        BufferedImage gui = texture("gui/container/crafting_table");
        BufferedImage img = new BufferedImage(CROP_W * SCALE, CROP_H * SCALE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < CROP_H * SCALE; y++) {
            for (int x = 0; x < CROP_W * SCALE; x++) {
                img.setRGB(x, y, gui.getRGB(CROP_X + x / SCALE, CROP_Y + y / SCALE));
            }
        }
        for (int r = 0; r < 3; r++) {
            String row = recipe.rows()[r];
            for (int c = 0; c < 3; c++) {
                char ch = c < row.length() ? row.charAt(c) : ' ';
                if (ch == ' ') {
                    continue;
                }
                drawIcon(img, recipe.key().get(ch), GRID_X + c * SLOT, GRID_Y + r * SLOT);
            }
        }
        drawIcon(img, recipe.result(), RESULT_X, RESULT_Y);
        if (recipe.count() > 1) {
            drawCount(img, recipe.count(), RESULT_X, RESULT_Y);
        }
        return img;
    }

    /** GUI 座標 (gx, gy) を左上とする 16×16 のマスにアイテムを描く。 */
    private void drawIcon(BufferedImage img, Icon icon, int gx, int gy) throws IOException {
        BufferedImage pic = switch (icon) {
            case Flat flat -> scaled(texture(flat.texture()));
            case Cube cube -> cube(cube);
        };
        int ox = (gx - CROP_X) * SCALE;
        int oy = (gy - CROP_Y) * SCALE;
        for (int y = 0; y < pic.getHeight(); y++) {
            for (int x = 0; x < pic.getWidth(); x++) {
                blend(img, ox + x, oy + y, pic.getRGB(x, y));
            }
        }
    }

    /** 16×16 の絵を拡大率に合わせて大きくする。アニメーション用の縦長の絵は 1 コマ目だけ使う。 */
    private static BufferedImage scaled(BufferedImage tex) {
        int size = 16 * SCALE;
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        int w = tex.getWidth();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                out.setRGB(x, y, tex.getRGB(x * w / size, y * w / size));
            }
        }
        return out;
    }

    /**
     * 持ち物の欄のブロックと同じく、上・正面（左）・横（右）の 3 面が見える立方体を描く。
     * 面の明るさを変えて立体に見せる（上 100%・左 80%・右 60%）。
     */
    private BufferedImage cube(Cube cube) throws IOException {
        BufferedImage top = texture(cube.top());
        BufferedImage front = texture(cube.front());
        BufferedImage side = texture(cube.side());
        int size = 16 * SCALE;
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

        // 16×16 のマスに収まる立方体の頂点（1/16 ピクセル単位を拡大率で掛ける）
        double s = SCALE;
        double half = 7.5 * s;          // 横幅の半分
        double rise = 3.75 * s;         // 斜めの上がり
        double depth = 8.5 * s * cube.height();
        double cx = 8 * s;
        // 薄いもの（感圧板など）もマスの上下の真ん中に来るようにする
        double topY = (16 * s - (2 * rise + depth)) / 2;
        double[] t0 = {cx, topY};                     // 奥
        double[] t1 = {cx + half, topY + rise};       // 右
        double[] t2 = {cx, topY + 2 * rise};          // 手前
        double[] t3 = {cx - half, topY + rise};       // 左

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double px = x + 0.5;
                double py = y + 0.5;
                int color = 0;
                // 手前の面から順に調べ、最初に当たった面の色を使う
                double[] uv = solve(px, py, t3, sub(t2, t3), new double[]{0, depth});
                if (inside(uv)) {
                    color = shade(sample(front, uv[0], uv[1] * cube.height() + (1 - cube.height())), 0.8);
                } else if (inside(uv = solve(px, py, t2, sub(t1, t2), new double[]{0, depth}))) {
                    color = shade(sample(side, uv[0], uv[1] * cube.height() + (1 - cube.height())), 0.6);
                } else if (inside(uv = solve(px, py, t3, sub(t0, t3), sub(t2, t3)))) {
                    color = sample(top, uv[0], uv[1]);
                }
                out.setRGB(x, y, color);
            }
        }
        return out;
    }

    /** 点 p = o + u*a + v*b を満たす (u, v) を求める。 */
    private static double[] solve(double px, double py, double[] o, double[] a, double[] b) {
        double det = a[0] * b[1] - a[1] * b[0];
        double dx = px - o[0];
        double dy = py - o[1];
        return new double[]{(dx * b[1] - dy * b[0]) / det, (a[0] * dy - a[1] * dx) / det};
    }

    private static boolean inside(double[] uv) {
        return uv[0] >= 0 && uv[0] < 1 && uv[1] >= 0 && uv[1] < 1;
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1]};
    }

    private static int sample(BufferedImage tex, double u, double v) {
        int w = tex.getWidth();
        int x = Math.min(w - 1, Math.max(0, (int) (u * w)));
        int y = Math.min(w - 1, Math.max(0, (int) (v * w)));
        return tex.getRGB(x, y);
    }

    private static int shade(int argb, double k) {
        int a = argb >>> 24;
        int r = (int) (((argb >> 16) & 0xFF) * k);
        int g = (int) (((argb >> 8) & 0xFF) * k);
        int b = (int) ((argb & 0xFF) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ---------- 個数 ----------

    /** 完成品の個数を、ゲームと同じ文字（ascii.png）で右下に影付きで書く。 */
    private void drawCount(BufferedImage img, int count, int gx, int gy) throws IOException {
        BufferedImage font = texture("font/ascii");
        String text = Integer.toString(count);
        int width = 0;
        int[] widths = new int[text.length()];
        for (int i = 0; i < text.length(); i++) {
            widths[i] = glyphWidth(font, text.charAt(i)) + 1;
            width += widths[i];
        }
        // ゲームでは「マスの右端 + 1」で右寄せし、上から 9 ピクセルの位置に書く
        int x = gx + 17 - width;
        int y = gy + 9;
        for (int pass = 0; pass < 2; pass++) {
            int color = pass == 0 ? 0xFF3F3F3F : 0xFFFFFFFF;
            int shadow = pass == 0 ? 1 : 0;
            int cx = x;
            for (int i = 0; i < text.length(); i++) {
                drawGlyph(img, font, text.charAt(i), cx + shadow, y + shadow, color);
                cx += widths[i];
            }
        }
    }

    private static int glyphWidth(BufferedImage font, char ch) {
        int cell = font.getWidth() / 16;
        int col = ch % 16 * cell;
        int row = ch / 16 * cell;
        int max = 0;
        for (int y = 0; y < cell; y++) {
            for (int x = 0; x < cell; x++) {
                if ((font.getRGB(col + x, row + y) >>> 24) > 0) {
                    max = Math.max(max, x + 1);
                }
            }
        }
        return max;
    }

    private static void drawGlyph(BufferedImage img, BufferedImage font, char ch, int gx, int gy, int color) {
        int cell = font.getWidth() / 16;
        int col = ch % 16 * cell;
        int row = ch / 16 * cell;
        for (int y = 0; y < cell; y++) {
            for (int x = 0; x < cell; x++) {
                if ((font.getRGB(col + x, row + y) >>> 24) == 0) {
                    continue;
                }
                for (int sy = 0; sy < SCALE; sy++) {
                    for (int sx = 0; sx < SCALE; sx++) {
                        int px = (gx + x - CROP_X) * SCALE + sx;
                        int py = (gy + y - CROP_Y) * SCALE + sy;
                        if (px >= 0 && py >= 0 && px < img.getWidth() && py < img.getHeight()) {
                            img.setRGB(px, py, color);
                        }
                    }
                }
            }
        }
    }

    // ---------- 共通 ----------

    private static void blend(BufferedImage img, int x, int y, int argb) {
        int a = argb >>> 24;
        if (a == 0 || x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) {
            return;
        }
        if (a == 255) {
            img.setRGB(x, y, argb);
            return;
        }
        int dst = img.getRGB(x, y);
        int r = mix(argb >> 16, dst >> 16, a);
        int g = mix(argb >> 8, dst >> 8, a);
        int b = mix(argb, dst, a);
        img.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
    }

    private static int mix(int src, int dst, int a) {
        return ((src & 0xFF) * a + (dst & 0xFF) * (255 - a)) / 255;
    }

    private BufferedImage texture(String path) throws IOException {
        BufferedImage cached = cache.get(path);
        if (cached != null) {
            return cached;
        }
        ZipEntry entry = jar.getEntry("assets/minecraft/textures/" + path + ".png");
        if (entry == null) {
            throw new IOException("texture not found in the client jar: " + path);
        }
        try (InputStream in = jar.getInputStream(entry)) {
            BufferedImage img = ImageIO.read(in);
            cache.put(path, img);
            return img;
        }
    }
}
