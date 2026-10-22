package work.maiotanet.invasioncore;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * この jar をどのブランチ・コミットから作ったか。
 *
 * <p>develop で作った jar と main で作った jar が同じ「0.4.0」だと、テストサーバーにどちらが入っているか分からない。
 * main 以外で作った jar は版に -dev を付け、詳しい表示ではコミットと作った日時も出す。
 * 値はビルドのときに Maven が build.properties に埋める。Git の無い所で作ると分からない値は空になる。</p>
 */
public final class BuildInfo {

    private final String version;
    private final String branch;
    private final String commit;
    private final String time;
    private final boolean dirty;

    private BuildInfo(String version, String branch, String commit, String time, boolean dirty) {
        this.version = version;
        this.branch = branch;
        this.commit = commit;
        this.time = time;
        this.dirty = dirty;
    }

    public static BuildInfo load(String fallbackVersion) {
        Properties props = new Properties();
        try (InputStream in = BuildInfo.class.getClassLoader().getResourceAsStream("build.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
            // 読めなければ分からないものとして扱う
        }
        return new BuildInfo(
                value(props, "version", fallbackVersion),
                value(props, "branch", ""),
                value(props, "commit", ""),
                value(props, "time", ""),
                Boolean.parseBoolean(value(props, "dirty", "false")));
    }

    /** 埋められなかった「${...}」は空として扱う。 */
    private static String value(Properties props, String key, String fallback) {
        String v = props.getProperty(key, fallback).trim();
        return v.startsWith("${") ? fallback : v;
    }

    /** 短い表示（タイトル用）。main 以外で作った jar には -dev を付ける。 */
    public String shortVersion() {
        return "main".equals(branch) ? version : version + "-dev";
    }

    /** 詳しい表示（チャット用）。例: 0.4.0-dev (e2cdd84・10/22 21:30・未コミットあり) */
    public String fullVersion() {
        StringBuilder detail = new StringBuilder();
        if (!commit.isEmpty()) {
            detail.append(commit);
        }
        if (!time.isEmpty()) {
            detail.append(detail.isEmpty() ? "" : "・").append(time);
        }
        if (dirty) {
            // コミットしていない変更が入ったまま作った jar。同じコミットでも中身が違うことがある
            detail.append(detail.isEmpty() ? "" : "・").append("未コミットあり");
        }
        return detail.isEmpty() ? shortVersion() : shortVersion() + " (" + detail + ")";
    }

    public String branch() {
        return branch;
    }
}
