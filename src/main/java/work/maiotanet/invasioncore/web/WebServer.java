package work.maiotanet.invasioncore.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import work.maiotanet.invasioncore.config.InvasionConfig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * 管理画面と OBS オーバーレイを配信する小さな Web サーバー。
 *
 * <p>JDK 同梱の HttpServer を使い、外部ライブラリは入れない。同じ Wi-Fi のスマホから開けるよう
 * 既定では全インターフェースで待ち受ける。管理画面と管理 API にはパスワード（URL の token）が要り、
 * オーバーレイと顔画像は名前と Shard の数しか出さないので token なしで開ける。
 * ハンドラは Web 用のスレッドで動くため、Bukkit の API は触らず、WebState が用意した値だけを返す。</p>
 */
public final class WebServer {

    /** 管理画面から選べるオーバーレイの表示パターン。 */
    public static final Set<String> STYLES = Set.of("b", "c", "e", "f");

    private final Plugin plugin;
    private final InvasionConfig config;
    private final WebState state;
    private final ShardFeed feed;
    private final FaceService faces;

    private HttpServer server;
    private ExecutorService executor;
    private String token;

    public WebServer(Plugin plugin, InvasionConfig config, WebState state, ShardFeed feed, FaceService faces) {
        this.plugin = plugin;
        this.config = config;
        this.state = state;
        this.feed = feed;
        this.faces = faces;
    }

    // ---------- 起動・停止 ----------

    public void start() {
        token = loadOrCreateToken();
        try {
            server = HttpServer.create(new InetSocketAddress(config.webBind(), config.webPort()), 0);
        } catch (IOException ex) {
            plugin.getLogger().warning("Web サーバーを起動できませんでした（ポート " + config.webPort()
                    + " が使用中の可能性）。config.yml の web.port を変えてください: " + ex.getMessage());
            server = null;
            return;
        }
        executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "InvasionCore-Web");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);
        server.createContext("/", this::route);
        server.start();
        for (String line : describeUrls()) {
            plugin.getLogger().info(line);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    // ---------- URL ----------

    /** 管理画面の URL（同じ Wi-Fi の端末から開ける IP ごと）。 */
    public List<String> adminUrls() {
        List<String> out = new ArrayList<>();
        for (String host : lanHosts()) {
            out.add("http://" + host + ":" + config.webPort() + "/admin?token=" + token);
        }
        return out;
    }

    /** OBS のブラウザソースに入れる URL。OBS は配信 PC で動くので localhost でよい。 */
    public String overlayUrl() {
        return "http://localhost:" + config.webPort() + "/overlay";
    }

    public List<String> describeUrls() {
        List<String> lines = new ArrayList<>();
        lines.add("管理画面（同じ Wi-Fi のスマホ・PC から）:");
        for (String url : adminUrls()) {
            lines.add("  " + url);
        }
        lines.add("OBS オーバーレイ（ブラウザソース）: " + overlayUrl());
        return lines;
    }

    private List<String> lanHosts() {
        List<String> hosts = new ArrayList<>();
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) {
                    continue;
                }
                for (InetAddress addr : Collections.list(nif.getInetAddresses())) {
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        hosts.add(addr.getHostAddress());
                    }
                }
            }
        } catch (IOException ignored) {
            // 取れなければ localhost だけ出す
        }
        hosts.add("localhost");
        return hosts;
    }

    // ---------- ルーティング ----------

    private void route(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            Map<String, String> query = query(ex.getRequestURI().getRawQuery());
            if (path.equals("/overlay")) {
                sendResource(ex, "web/overlay.html");
            } else if (path.equals("/api/overlay/events")) {
                sendJson(ex, 200, overlayEvents(query.get("since")));
            } else if (path.startsWith("/face/") && path.endsWith(".png")) {
                sendFace(ex, path.substring("/face/".length(), path.length() - 4));
            } else if (path.equals("/admin") || path.startsWith("/api/admin/")) {
                if (!authorized(query.get("token"))) {
                    send(ex, 403, "text/plain; charset=utf-8",
                            "パスワードが違います。/invasion admin web で表示される URL から開いてください。"
                                    .getBytes(StandardCharsets.UTF_8));
                } else if (path.equals("/admin")) {
                    sendResource(ex, "web/admin.html");
                } else if (path.equals("/api/admin/state")) {
                    sendJson(ex, 200, state.adminJson());
                } else if (path.equals("/api/admin/style") && "POST".equals(ex.getRequestMethod())) {
                    changeStyle(ex, query.get("style"));
                } else {
                    send(ex, 404, "text/plain", new byte[0]);
                }
            } else {
                send(ex, 404, "text/plain", new byte[0]);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Web リクエストの処理に失敗しました", e);
            send(ex, 500, "text/plain", new byte[0]);
        } finally {
            ex.close();
        }
    }

    private String overlayEvents(String sinceRaw) {
        long since;
        try {
            since = sinceRaw == null ? -1 : Long.parseLong(sinceRaw);
        } catch (NumberFormatException e) {
            since = -1;
        }
        JsonObject root = new JsonObject();
        root.addProperty("style", state.overlayStyle());
        long latest = feed.latest();
        root.addProperty("latest", latest);
        JsonArray events = new JsonArray();
        // 開いた直後（since 未指定）は過去の分を流さない
        if (since >= 0) {
            for (ShardFeed.Event e : feed.since(since)) {
                JsonObject o = new JsonObject();
                o.addProperty("seq", e.seq());
                o.addProperty("uuid", e.player().toString());
                o.addProperty("name", e.name());
                o.addProperty("total", e.total());
                o.addProperty("delta", e.delta());
                o.addProperty("reason", e.reason());
                events.add(o);
            }
        }
        root.add("events", events);
        return root.toString();
    }

    private void sendFace(HttpExchange ex, String rawId) throws IOException {
        UUID id;
        try {
            id = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            send(ex, 404, "text/plain", new byte[0]);
            return;
        }
        ex.getResponseHeaders().set("Cache-Control", "max-age=300");
        send(ex, 200, "image/png", faces.face(id));
    }

    /** 管理画面からオーバーレイの表示パターンを変える。config.yml の書き換えはメインスレッドで行う。 */
    private void changeStyle(HttpExchange ex, String style) throws IOException {
        if (style == null || !STYLES.contains(style)) {
            send(ex, 400, "text/plain", new byte[0]);
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> config.overlayStyle(style));
        send(ex, 204, "text/plain", null);
    }

    // ---------- パスワード ----------

    private boolean authorized(String given) {
        return given != null && MessageDigest.isEqual(
                given.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    /** パスワードは初回に作って web.yml に保存する。消せば次の起動で作り直される。 */
    private String loadOrCreateToken() {
        File file = new File(plugin.getDataFolder(), "web.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String saved = yaml.getString("token");
        if (saved != null && saved.length() >= 16) {
            return saved;
        }
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        String created = HexFormat.of().formatHex(bytes);
        yaml.set("token", created);
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("web.yml を保存できませんでした。再起動のたびにパスワードが変わります。");
        }
        return created;
    }

    // ---------- 送信 ----------

    private void sendResource(HttpExchange ex, String name) throws IOException {
        try (InputStream in = plugin.getResource(name)) {
            if (in == null) {
                send(ex, 404, "text/plain", new byte[0]);
                return;
            }
            send(ex, 200, "text/html; charset=utf-8", in.readAllBytes());
        }
    }

    private void sendJson(HttpExchange ex, int status, String json) throws IOException {
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        send(ex, status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private void send(HttpExchange ex, int status, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        if (body == null || body.length == 0) {
            ex.sendResponseHeaders(status, -1);
            return;
        }
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            out.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return out;
    }
}
