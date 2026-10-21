package work.maiotanet.invasioncore.web;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * プレイヤーの顔画像（スキンの顔部分）。
 *
 * <p>外部の顔画像サービスに頼らず、Mojang のスキン画像から顔（8×8）と帽子レイヤーを切り出して
 * 64×64 の PNG にする。一度作った顔はメモリと plugins/InvasionCore/faces/ に置き、
 * オフラインの人でも管理画面に顔が出るようにする。
 * 取得はスキン配信元（textures.minecraft.net）だけに限り、それ以外の URL は読まない。</p>
 */
public final class FaceService {

    private static final String SKIN_HOST = "textures.minecraft.net";
    private static final int SIZE = 64;

    private final Plugin plugin;
    private final File dir;
    private final Map<UUID, byte[]> cache = new ConcurrentHashMap<>();
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    /** 取得に失敗した人は、しばらく取りに行かない（毎秒 Mojang に問い合わせ続けないため）。 */
    private static final long RETRY_AFTER_MILLIS = 5 * 60 * 1000L;
    private final Map<UUID, Long> retryAt = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final byte[] fallback;

    public FaceService(Plugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "faces");
        this.fallback = toPng(defaultFace());
    }

    /**
     * まだ顔が無ければスキンを取りに行く。メインスレッドから呼ぶ（プロフィールを読むため）。
     * 取得と切り出しは別スレッドで行う。
     */
    public void prefetch(Player player) {
        UUID id = player.getUniqueId();
        Long wait = retryAt.get(id);
        if (cache.containsKey(id) || (wait != null && System.currentTimeMillis() < wait) || !pending.add(id)) {
            return;
        }
        URL skin = player.getPlayerProfile().getTextures().getSkin();
        if (skin == null || !SKIN_HOST.equals(skin.getHost())) {
            // スキンの無いアカウント（オフラインモードなど）。既定の顔のままにして、しばらく見に行かない
            retryAt.put(id, System.currentTimeMillis() + RETRY_AFTER_MILLIS);
            pending.remove(id);
            return;
        }
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(skin.toURI()).timeout(Duration.ofSeconds(10)).GET().build();
        } catch (Exception ex) {
            pending.remove(id);
            return;
        }
        http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(response -> {
                    byte[] png = response.statusCode() == 200 ? cropFace(response.body()) : null;
                    if (png != null) {
                        cache.put(id, png);
                        writeToDisk(id, png);
                        retryAt.remove(id);
                    } else {
                        retryAt.put(id, System.currentTimeMillis() + RETRY_AFTER_MILLIS);
                    }
                })
                .whenComplete((ok, error) -> {
                    if (error != null) {
                        retryAt.put(id, System.currentTimeMillis() + RETRY_AFTER_MILLIS);
                    }
                    pending.remove(id);
                });
    }

    /** 顔の PNG。まだ無ければディスクを見て、それも無ければ既定の顔を返す。 */
    public byte[] face(UUID id) {
        byte[] png = cache.get(id);
        if (png != null) {
            return png;
        }
        File file = new File(dir, id + ".png");
        if (file.isFile()) {
            try {
                png = Files.readAllBytes(file.toPath());
                cache.put(id, png);
                return png;
            } catch (IOException ignored) {
                // 読めなければ既定の顔にする
            }
        }
        return fallback;
    }

    // ---------- 画像処理 ----------

    /** スキン（64×64 または旧形式の 64×32）から顔と帽子レイヤーを重ねて切り出す。 */
    private byte[] cropFace(byte[] skinPng) {
        try {
            BufferedImage skin = ImageIO.read(new ByteArrayInputStream(skinPng));
            if (skin == null || skin.getWidth() < 64 || skin.getHeight() < 16) {
                return null;
            }
            BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            int scale = SIZE / 8;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int base = skin.getRGB(8 + x, 8 + y) | 0xFF000000;
                    int hat = skin.getRGB(40 + x, 8 + y);
                    int color = (hat >>> 24) >= 128 ? hat | 0xFF000000 : base;
                    for (int dy = 0; dy < scale; dy++) {
                        for (int dx = 0; dx < scale; dx++) {
                            out.setRGB(x * scale + dx, y * scale + dy, color);
                        }
                    }
                }
            }
            return toPng(out);
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    /** スキンが取れないときの顔。Steve に近い色で描く。 */
    private static BufferedImage defaultFace() {
        int skin = 0xFFB4845F, hair = 0xFF3B2A1F, white = 0xFFFFFFFF, eye = 0xFF4A3B8F, mouth = 0xFF7A4E36;
        int[][] map = {
                {1, 1, 1, 1, 1, 1, 1, 1},
                {1, 1, 1, 1, 1, 1, 1, 1},
                {1, 0, 0, 0, 0, 0, 0, 1},
                {0, 0, 0, 0, 0, 0, 0, 0},
                {0, 2, 3, 0, 0, 3, 2, 0},
                {0, 0, 0, 4, 4, 0, 0, 0},
                {0, 0, 4, 0, 0, 4, 0, 0},
                {0, 0, 4, 4, 4, 4, 0, 0},
        };
        int[] palette = {skin, hair, white, eye, mouth};
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        int scale = SIZE / 8;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                out.setRGB(x, y, palette[map[y / scale][x / scale]]);
            }
        }
        return out;
    }

    private static byte[] toPng(BufferedImage image) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", bytes);
            return bytes.toByteArray();
        } catch (IOException ex) {
            return new byte[0];
        }
    }

    private void writeToDisk(UUID id, byte[] png) {
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return;
            }
            Files.write(new File(dir, id + ".png").toPath(), png);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.FINE, "顔画像を保存できませんでした: " + id, ex);
        }
    }
}
