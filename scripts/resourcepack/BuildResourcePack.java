import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * resourcepack/textures/ に置いた PNG から、独自アイテムの見た目を差し替えるリソースパック（zip）を作る。
 *
 * <p>独自アイテムには item_model.yml の CustomModelData が付いている。パックはその値を見て、
 * 独自アイテムのときだけ自分の絵を使い、普通のアイテム（バニラのアメジストの欠片など）は元の見た目のままにする。
 * 元の見た目は手元の Minecraft クライアントの jar から写すので、パックのバージョンも jar に合わせる。</p>
 *
 * <pre>
 * java scripts/resourcepack/BuildResourcePack.java &lt;リポジトリ&gt; &lt;Minecraft クライアントの jar&gt; &lt;出力 zip&gt; [item_model.yml]
 * </pre>
 */
public final class BuildResourcePack {

    /**
     * 差し替えられる品。
     *
     * @param texture   resourcepack/textures/ に置く PNG の名前（拡張子なし）
     * @param configKey item_model.yml の項目名
     * @param vanilla   見た目の元にしているバニラのアイテム
     * @param handheld  手に持ったときに道具の持ち方にするか（ハンマーなど）
     */
    private record Item(String texture, String configKey, String vanilla, boolean handheld) {
    }

    /** item_model.yml の項目と同じ並び。PNG の名前も項目名と同じにする。 */
    private static final List<Item> ITEMS = List.of(
            new Item("nexus", "nexus", "lodestone", false),
            new Item("hammer", "hammer", "iron_axe", true),
            new Item("shard", "shard", "amethyst_shard", false),
            new Item("kit", "kit", "honeycomb", false),
            new Item("turret_arrow", "turret_arrow", "dispenser", false),
            new Item("turret_flame", "turret_flame", "blast_furnace", false),
            new Item("turret_frost", "turret_frost", "packed_ice", false),
            new Item("turret_heal", "turret_heal", "beacon", false),
            new Item("spike", "spike", "pointed_dripstone", false),
            new Item("mine", "mine", "heavy_weighted_pressure_plate", false),
            new Item("flash", "flash", "snowball", false),
            new Item("medkit", "medkit", "ghast_tear", false),
            new Item("barrier", "barrier", "iron_trapdoor", false),
            new Item("recall", "recall", "recovery_compass", false)
    );

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("usage: java BuildResourcePack.java <repo> <minecraft client jar> <output zip> [item_model.yml]");
            System.exit(1);
        }
        File repo = new File(args[0]);
        File textures = new File(repo, "resourcepack/textures");
        // 値はサーバーの item_model.yml から読む。指定が無ければ手元のテストサーバーのもの、それも無ければリポジトリの雛形
        File modelFile = args.length >= 4 ? new File(args[3]) : new File(repo, "server/plugins/InvasionCore/item_model.yml");
        if (!modelFile.isFile()) {
            modelFile = new File(repo, "src/main/resources/item_model.yml");
        }
        System.out.println("item_model: " + modelFile);
        Map<String, String> modelData = readModelData(modelFile);

        try (ZipFile jar = new ZipFile(args[1])) {
            int format = packFormat(jar);
            Map<String, byte[]> files = new LinkedHashMap<>();
            files.put("pack.mcmeta", ("{\n  \"pack\": {\n"
                    + "    \"description\": \"InvasionCore のアイテムの見た目\",\n"
                    + "    \"pack_format\": " + format + ",\n"
                    + "    \"min_format\": " + format + ",\n"
                    + "    \"max_format\": " + format + "\n  }\n}\n").getBytes(StandardCharsets.UTF_8));
            File icon = new File(repo, "resourcepack/pack.png");
            if (icon.isFile()) {
                files.put("pack.png", Files.readAllBytes(icon.toPath()));
            }

            List<String> done = new ArrayList<>();
            for (Item item : ITEMS) {
                File png = new File(textures, item.texture() + ".png");
                String value = modelData.getOrDefault(item.configKey(), "");
                boolean forcedString = value.startsWith("\u0000");
                if (forcedString) {
                    value = value.substring(1);
                }
                if (!png.isFile()) {
                    continue;
                }
                if (value.isEmpty()) {
                    System.out.println("skip " + item.texture() + ": item_model.yml の " + item.configKey() + " が空白");
                    continue;
                }
                String path = "assets/invasioncore/textures/item/" + item.texture() + ".png";
                files.put(path, Files.readAllBytes(png.toPath()));
                // アニメーションさせたいときは、PNG と同じ名前の .png.mcmeta を置く
                File meta = new File(textures, item.texture() + ".png.mcmeta");
                if (meta.isFile()) {
                    files.put(path + ".mcmeta", Files.readAllBytes(meta.toPath()));
                }
                files.put("assets/invasioncore/models/item/" + item.texture() + ".json", ("{\n"
                        + "  \"parent\": \"minecraft:item/" + (item.handheld() ? "handheld" : "generated") + "\",\n"
                        + "  \"textures\": { \"layer0\": \"invasioncore:item/" + item.texture() + "\" }\n"
                        + "}\n").getBytes(StandardCharsets.UTF_8));
                String fallback = vanillaModel(jar, item.vanilla());
                String custom = "{ \"type\": \"minecraft:model\", \"model\": \"invasioncore:item/" + item.texture() + "\" }";
                String definition;
                if (!forcedString && isNumber(value)) {
                    // 数値の CustomModelData は range_dispatch で見る。値が threshold 以上ならこちらの絵、
                    // 付いていない（0 扱い）ならバニラの見た目
                    definition = "{\n"
                            + "  \"model\": {\n"
                            + "    \"type\": \"minecraft:range_dispatch\",\n"
                            + "    \"property\": \"minecraft:custom_model_data\",\n"
                            + "    \"index\": 0,\n"
                            + "    \"entries\": [\n"
                            + "      { \"threshold\": " + value + ", \"model\": " + custom + " }\n"
                            + "    ],\n"
                            + "    \"fallback\": " + fallback + "\n"
                            + "  }\n"
                            + "}\n";
                } else {
                    definition = "{\n"
                            + "  \"model\": {\n"
                            + "    \"type\": \"minecraft:select\",\n"
                            + "    \"property\": \"minecraft:custom_model_data\",\n"
                            + "    \"index\": 0,\n"
                            + "    \"cases\": [\n"
                            + "      { \"when\": " + json(value) + ", \"model\": " + custom + " }\n"
                            + "    ],\n"
                            + "    \"fallback\": " + fallback + "\n"
                            + "  }\n"
                            + "}\n";
                }
                files.put("assets/minecraft/items/" + item.vanilla() + ".json", definition.getBytes(StandardCharsets.UTF_8));
                done.add(item.texture() + "（custom_model_data = " + (!forcedString && isNumber(value) ? value : "\"" + value + "\"") + "）");
            }
            if (done.isEmpty()) {
                System.out.println("差し替える絵がありません。resourcepack/textures/ に PNG（shard.png など）を置き、"
                        + "item_model.yml のその項目に値を書いてから実行してください。");
                System.exit(2);
            }
            File out = new File(args[2]);
            File parent = out.getAbsoluteFile().getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("cannot create " + parent);
            }
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(out))) {
                for (Map.Entry<String, byte[]> e : files.entrySet()) {
                    zip.putNextEntry(new ZipEntry(e.getKey()));
                    zip.write(e.getValue());
                    zip.closeEntry();
                }
            }
            System.out.println("wrote " + out + "（pack format " + format + "）");
            for (String line : done) {
                System.out.println("  " + line);
            }
        }
    }

    /** jar の version.json からリソースパックのバージョンを読む。 */
    private static int packFormat(ZipFile jar) throws IOException {
        String json = text(jar, "version.json");
        Matcher m = Pattern.compile("\"resource_major\"\\s*:\\s*(\\d+)").matcher(json);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        m = Pattern.compile("\"resource\"\\s*:\\s*(\\d+)").matcher(json);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        throw new IOException("version.json にリソースパックのバージョンがありません");
    }

    /** バニラのアイテム定義の "model" の中身。独自アイテムでないときは、これをそのまま使う。 */
    private static String vanillaModel(ZipFile jar, String item) throws IOException {
        String json = text(jar, "assets/minecraft/items/" + item + ".json");
        int key = json.indexOf("\"model\"");
        int start = json.indexOf('{', key);
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return json.substring(start, i + 1);
            }
        }
        throw new IOException("バニラのアイテム定義を読めません: " + item);
    }

    /** item_model.yml の値を読む（YAML の読み込みに依存しないよう、「項目: 値」の行だけ見る）。 */
    private static Map<String, String> readModelData(File file) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        Pattern line = Pattern.compile("^(\\w+):\\s*(\"[^\"]*\"|'[^']*'|[^#\\s]*)\\s*(#.*)?$");
        for (String text : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            Matcher m = line.matcher(text);
            if (!m.matches()) {
                continue;
            }
            String raw = m.group(2).trim();
            // "" で囲んだものは文字列。囲まずに数字だけを書いたものは数値として扱う
            boolean quoted = raw.length() >= 2 && (raw.startsWith("\"") || raw.startsWith("'"));
            String value = quoted ? raw.substring(1, raw.length() - 1) : raw;
            out.put(m.group(1), quoted && isNumber(value) ? "\u0000" + value : value);
        }
        return out;
    }

    private static String text(ZipFile jar, String path) throws IOException {
        ZipEntry entry = jar.getEntry(path);
        if (entry == null) {
            throw new IOException("not found in the client jar: " + path);
        }
        try (InputStream in = jar.getInputStream(entry); ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            in.transferTo(buf);
            return buf.toString(StandardCharsets.UTF_8);
        }
    }

    /** 数値として扱う値か。"" で囲んで書いた数字は readModelData が先頭に印を付けて文字列のままにする。 */
    private static boolean isNumber(String s) {
        return s.matches("-?\\d+(\\.\\d+)?");
    }

    private static String json(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
