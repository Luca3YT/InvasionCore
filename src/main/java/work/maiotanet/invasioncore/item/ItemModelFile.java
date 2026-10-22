package work.maiotanet.invasioncore.item;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

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

    /** 読み込んで、InvasionItems に反映する。 */
    public static void load(Plugin plugin) {
        File file = new File(plugin.getDataFolder(), "item_model.yml");
        if (!file.exists()) {
            plugin.saveResource("item_model.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : yaml.getKeys(false)) {
            // 数字で書けば Number、"" で囲めば String のまま返る。空白は null
            values.put(key, yaml.get(key));
        }
        InvasionItems.modelData(values);
    }
}
