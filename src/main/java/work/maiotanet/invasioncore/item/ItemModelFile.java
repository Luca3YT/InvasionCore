package work.maiotanet.invasioncore.item;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import work.maiotanet.invasioncore.config.YamlCheck;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * plugins/InvasionCore/item_model.yml — 独自アイテムに付ける CustomModelData。
 *
 * <p>テクスチャを作る人が触る設定なので、バランス用の config.yml とは分けている。
 * 初めて起動したときに全品が空白のファイルを置く（空白なら何も付けない）。</p>
 */
public final class ItemModelFile {

    private ItemModelFile() {
    }

    /**
     * 読み込んで、InvasionItems に反映する。
     *
     * @return 書き間違いで読めなかったときはその説明（前の値のまま変えない）。読めたら null
     */
    public static String load(Plugin plugin) {
        File file = new File(plugin.getDataFolder(), "item_model.yml");
        if (!file.exists()) {
            plugin.saveResource("item_model.yml", false);
        }
        String problem = YamlCheck.problem(file);
        if (problem != null) {
            // 読めないまま進めると全品が空白扱いになり、付けたはずの値が黙って消える
            return problem;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : yaml.getKeys(false)) {
            // 数字で書けば Number、"" で囲めば String のまま返る。空白は null
            values.put(key, yaml.get(key));
        }
        InvasionItems.modelData(values);
        return null;
    }
}
