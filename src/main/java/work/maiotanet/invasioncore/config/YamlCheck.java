package work.maiotanet.invasioncore.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 設定ファイル（YAML）の書き間違いを見つけて、直す人に分かる言葉で返す。
 *
 * <p>Bukkit の読み込みは、書き間違いがあるとログに英語のエラーを出すだけで、空の設定として読み進めてしまう。
 * 「/invasion admin reload したのに反映されない」「設定が全部初期値に戻った」が起きても気づけないので、
 * 読み込む前にここで確かめ、どの行が読めないかを返す。</p>
 */
public final class YamlCheck {

    private static final Pattern LINE = Pattern.compile("line (\\d+)");

    private YamlCheck() {
    }

    /** 読めれば null。読めなければ、何行目がおかしいかの説明。ファイルが無ければ null。 */
    public static String problem(File file) {
        if (!file.exists()) {
            return null;
        }
        try {
            new YamlConfiguration().load(file);
            return null;
        } catch (InvalidConfigurationException ex) {
            // YAML のエラーは「読み始めた所」と「つまずいた所」の行を挙げる。閉じ忘れの "" などはつまずく所が
            // ファイルの最後になるので、最初に挙がる行（書き間違いのある行）を出す
            Matcher m = LINE.matcher(String.valueOf(ex.getMessage()));
            String where = m.find() ? m.group(1) + " 行目あたり" : "どこか";
            return where + "の書き方が正しくない（「名前: 値」のように「:」の後に空白が要る。字下げや \"\" の閉じ忘れも確認）";
        } catch (IOException ex) {
            return "ファイルを読めない（" + ex.getMessage() + "）";
        }
    }
}
