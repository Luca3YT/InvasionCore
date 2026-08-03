package work.maiotanet.invasioncore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.List;

/**
 * Adventure Component の生成ヘルパ。
 * 色付けを毎回書くと冗長なので、この層に寄せて表記を統一する。
 */
public final class Text {

    public static final TextColor ACCENT = TextColor.color(0xFF6B6B);
    public static final TextColor GOOD = TextColor.color(0x7BE495);
    public static final TextColor INFO = TextColor.color(0x8FD3F4);
    public static final TextColor WARN = TextColor.color(0xFFD166);
    public static final TextColor MUTED = TextColor.color(0x9AA0A6);

    private static final Component PREFIX = Component.text()
            .append(Component.text("[", MUTED))
            .append(Component.text("Invasion", ACCENT))
            .append(Component.text("] ", MUTED))
            .build()
            .decoration(TextDecoration.ITALIC, false);

    private Text() {
    }

    public static Component plain(String s) {
        return Component.text(s).decoration(TextDecoration.ITALIC, false);
    }

    public static Component of(String s, TextColor color) {
        return Component.text(s, color).decoration(TextDecoration.ITALIC, false);
    }

    public static Component prefixed(Component body) {
        return PREFIX.append(body);
    }

    public static Component info(String s) {
        return prefixed(of(s, INFO));
    }

    public static Component good(String s) {
        return prefixed(of(s, GOOD));
    }

    public static Component warn(String s) {
        return prefixed(of(s, WARN));
    }

    public static Component error(String s) {
        return prefixed(of(s, NamedTextColor.RED));
    }

    public static List<Component> lore(String... lines) {
        List<Component> out = new ArrayList<>(lines.length);
        for (String line : lines) {
            out.add(of(line, MUTED));
        }
        return out;
    }

    /** 0.0〜1.0 の比率を "■■■□□□" 形式のゲージにする。 */
    public static String bar(double ratio, int width) {
        int filled = (int) Math.round(Math.max(0.0, Math.min(1.0, ratio)) * width);
        StringBuilder sb = new StringBuilder(width);
        for (int i = 0; i < width; i++) {
            sb.append(i < filled ? '■' : '□');
        }
        return sb.toString();
    }
}
