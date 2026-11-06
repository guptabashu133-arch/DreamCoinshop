package org.mydreamduels.dreamcoinshop.screen;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Player faces and country flags for the screens, as 8x8 RGB pictures (-1 = transparent).
 *
 * Faces come from the player's skin (face + hat layer): the skin URL from Bukkit's profile API
 * (online players directly, offline ones via a Mojang lookup), else the screens.head-skin-url
 * template. Flags are the country flag heads (same textures as Dreamtab) from
 * textures.minecraft.net. Everything downloads in the background and is cached in memory and
 * in plugins/Dreamcoinshop/cache/, so a picture is fetched once - not on every redraw.
 */
public final class Faces {

    private static final int[] MISSING = new int[0];

    private final JavaPlugin plugin;
    private final Map<UUID, int[]> heads = new ConcurrentHashMap<>();
    private final Map<String, int[]> flags = new ConcurrentHashMap<>();
    private final Map<String, Long> failedAt = new ConcurrentHashMap<>();
    private final Map<String, String> flagHashes = new ConcurrentHashMap<>();
    private final ExecutorService io;
    private final HttpClient http;
    private final File headDir, flagDir;
    private volatile Runnable onChange = () -> {
    };

    public Faces(JavaPlugin plugin) {
        this.plugin = plugin;
        this.io = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, plugin.getName() + " faces");
            t.setDaemon(true);
            return t;
        });
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NORMAL).build();
        this.headDir = new File(plugin.getDataFolder(), "cache/heads");
        this.flagDir = new File(plugin.getDataFolder(), "cache/flags");
        headDir.mkdirs();
        flagDir.mkdirs();
        try (InputStream in = plugin.getResource("country-flags.properties")) {
            Properties p = new Properties();
            if (in != null) p.load(in);
            for (String k : p.stringPropertyNames()) flagHashes.put(k.toUpperCase(Locale.ROOT), p.getProperty(k).trim());
        } catch (Exception e) {
            plugin.getLogger().warning("Couldn't read the flag list: " + e.getMessage());
        }
    }

    /** Runs (on a background thread) whenever a new face or flag arrived - screens redraw. */
    public void onChange(Runnable r) {
        this.onChange = r;
    }

    public void shutdown() {
        io.shutdownNow();
    }

    public boolean hasFlag(String code) {
        return code != null && flagHashes.containsKey(code.toUpperCase(Locale.ROOT));
    }

    private long headTtlMs() {
        return Math.max(1, plugin.getConfig().getLong("screens.head-cache-hours", 24)) * 3_600_000L;
    }

    /** The face if ready, else null (and it starts loading). Any thread. */
    public int[] face(UUID id, String name) {
        int[] f = heads.get(id);
        if (f != null) return f == MISSING ? null : f;
        String key = "h:" + id;
        if (recentlyFailed(key) || heads.putIfAbsent(id, MISSING) != null) return null;
        submit(key, () -> {
            int[] face = loadHead(id, name);
            if (face != null) {
                heads.put(id, face);
                onChange.run();
            } else {
                heads.remove(id);
                failedAt.put(key, System.currentTimeMillis());
            }
        });
        return null;
    }

    /** The flag of a country (ISO code) if ready, else null (and it starts loading). Any thread. */
    public int[] flag(String code) {
        if (code == null || code.isEmpty()) return null;
        String cc = code.toUpperCase(Locale.ROOT);
        int[] f = flags.get(cc);
        if (f != null) return f == MISSING ? null : f;
        String hash = flagHashes.get(cc);
        String key = "f:" + cc;
        if (hash == null || recentlyFailed(key) || flags.putIfAbsent(cc, MISSING) != null) return null;
        submit(key, () -> {
            int[] flag = cached(new File(flagDir, cc + ".png"), Long.MAX_VALUE);
            if (flag == null) {
                flag = faceOf(download("https://textures.minecraft.net/texture/" + hash));
                save(flag, new File(flagDir, cc + ".png"));
            }
            if (flag != null) {
                flags.put(cc, flag);
                onChange.run();
            } else {
                flags.remove(cc);
                failedAt.put(key, System.currentTimeMillis());
            }
        });
        return null;
    }

    /** Forget a player's face (e.g. they joined: the skin may have changed). */
    public void refresh(UUID id) {
        heads.remove(id);
        failedAt.remove("h:" + id);
        new File(headDir, id + ".png").delete();
    }

    private boolean recentlyFailed(String key) {
        Long at = failedAt.get(key);
        return at != null && System.currentTimeMillis() - at < 10 * 60_000L; // retry after 10 minutes
    }

    private void submit(String what, Runnable job) {
        try {
            io.execute(() -> {
                try {
                    job.run();
                } catch (Throwable t) {
                    failedAt.put(what, System.currentTimeMillis());
                }
            });
        } catch (RuntimeException rejected) {
            // shutting down
        }
    }

    // ------------------------------------------------------------ heads

    private int[] loadHead(UUID id, String name) {
        File file = new File(headDir, id + ".png");
        int[] cached = cached(file, headTtlMs());
        if (cached != null) return cached;
        String skinUrl = skinUrl(id, name);
        int[] face = skinUrl == null ? null : faceOf(download(skinUrl));
        if (face == null) {
            String template = plugin.getConfig().getString("screens.head-skin-url", "https://mc-heads.net/skin/%uuid%");
            if (template != null && !template.isBlank()) {
                face = faceOf(download(template.replace("%uuid%", id.toString().replace("-", ""))
                        .replace("%name%", name == null ? "" : name)));
            }
        }
        if (face != null) save(face, file);
        return face;
    }

    /** Skin URL through Bukkit's profile API (1.18.1+): online players at once, offline via Mojang. */
    private String skinUrl(UUID id, String name) {
        try {
            Player online = Bukkit.getPlayer(id);
            org.bukkit.profile.PlayerProfile profile = online != null ? online.getPlayerProfile()
                    : Bukkit.createPlayerProfile(id, name == null || name.isEmpty() ? null : name);
            URL skin = profile.getTextures().getSkin();
            if (skin == null) {
                profile = profile.update().get(10, TimeUnit.SECONDS);
                skin = profile.getTextures().getSkin();
            }
            return skin == null ? null : skin.toString();
        } catch (Throwable t) {
            return null; // older server / offline-mode / Mojang down: the URL template is tried next
        }
    }

    // ------------------------------------------------------------ pictures

    private byte[] download(String url) {
        try {
            HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Dreamcoinshop").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            return r.statusCode() == 200 ? r.body() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Face (8,8) + hat layer (40,8) of a skin (64x64 or old 64x32). */
    static int[] faceOf(byte[] png) {
        if (png == null) return null;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            if (img == null || img.getWidth() < 64 || img.getHeight() < 16) return null;
            int s = img.getWidth() / 64; // HD skins
            int[] out = new int[64];
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int base = img.getRGB((8 + x) * s, (8 + y) * s);
                    int hat = img.getRGB((40 + x) * s, (8 + y) * s);
                    int c = ((hat >>> 24) & 255) > 128 ? hat : base;
                    out[y * 8 + x] = c & 0xFFFFFF;
                }
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static int[] cached(File f, long ttlMs) {
        if (!f.isFile() || System.currentTimeMillis() - f.lastModified() > ttlMs) return null;
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null || img.getWidth() != 8 || img.getHeight() != 8) return null;
            int[] out = new int[64];
            for (int i = 0; i < 64; i++) out[i] = img.getRGB(i % 8, i / 8) & 0xFFFFFF;
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static void save(int[] px, File f) {
        if (px == null) return;
        try {
            BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
            for (int i = 0; i < 64; i++) img.setRGB(i % 8, i / 8, px[i]);
            ImageIO.write(img, "png", f);
        } catch (Exception ignored) {
        }
    }
}
