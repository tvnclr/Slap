package dev.tvnclr.slap.engine;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Typeface;

import java.util.HashMap;
import java.util.Map;

/**
 * Fonts offered for text on stickers: the system Roboto and monospace plus the most used open
 * fonts from Google Fonts, bundled in assets/fonts (licenses in assets/licenses/fonts).
 * Fonts without a Reserved Font Name were cut down to one weight and the Latin characters;
 * the four with one (Dancing Script, Lobster, Playfair Display, Press Start 2P) ship unmodified,
 * and the variable ones get their weight at runtime.
 */
public final class Fonts {

    public static final class Def {
        public final String id, name, license;
        final String asset, family, variation;
        final int style;

        Def(String id, String name, String asset, String family, int style, String variation, String license) {
            this.id = id;
            this.name = name;
            this.asset = asset;
            this.family = family;
            this.style = style;
            this.variation = variation;
            this.license = license;
        }
    }

    private static final String OFL = "SIL Open Font License 1.1";

    private static Def sys(String id, String name, String family, int style) {
        return new Def(id, name, null, family, style, null, null);
    }

    private static Def file(String id, String name, String variation, String license) {
        return new Def(id, name, "fonts/" + id + ".ttf", null, Typeface.NORMAL, variation, license);
    }

    /** Display order; the first one is the default for new text. */
    public static final Def[] ALL = {
            sys("roboto", "Roboto", "sans-serif-black", Typeface.NORMAL),
            file("montserrat", "Montserrat", null, OFL),
            file("poppins", "Poppins", null, OFL),
            file("anton", "Anton", null, OFL),
            file("bebas_neue", "Bebas Neue", null, OFL),
            file("oswald", "Oswald", null, OFL),
            file("archivo_black", "Archivo Black", null, OFL),
            file("fredoka", "Fredoka", null, OFL),
            file("bangers", "Bangers", null, OFL),
            file("black_ops_one", "Black Ops One", null, OFL),
            file("permanent_marker", "Permanent Marker", null, "Apache License 2.0"),
            file("caveat", "Caveat", null, OFL),
            file("dancing_script", "Dancing Script", "'wght' 700", OFL),
            file("pacifico", "Pacifico", null, OFL),
            file("lobster", "Lobster", null, OFL),
            file("playfair_display", "Playfair Display", "'wght' 800", OFL),
            file("press_start_2p", "Press Start 2P", null, OFL),
            sys("mono", "Mono", "monospace", Typeface.BOLD),
    };

    /** Fonts of the first text editor (1.1.0 dev builds), by their old index. */
    private static final String[] LEGACY = {"roboto", "montserrat", "oswald", "playfair_display", "mono", "caveat", "dancing_script"};

    private static AssetManager assets;
    private static final Map<String, Typeface> cache = new HashMap<>();

    private Fonts() {}

    public static void init(Context c) {
        if (assets == null) assets = c.getApplicationContext().getAssets();
    }

    public static int indexOf(String id) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i].id.equals(id)) return i;
        return 0;
    }

    public static String legacy(int oldIndex) {
        return LEGACY[Math.max(0, Math.min(LEGACY.length - 1, oldIndex))];
    }

    public static synchronized Typeface typeface(String id) {
        Typeface t = cache.get(id);
        if (t != null) return t;
        Def d = ALL[indexOf(id)];
        try {
            if (d.asset == null) {
                t = Typeface.create(d.family, d.style);
            } else if (assets == null) {
                t = Typeface.DEFAULT_BOLD;
            } else if (d.variation != null) {
                t = new Typeface.Builder(assets, d.asset).setFontVariationSettings(d.variation).build();
            } else {
                t = Typeface.createFromAsset(assets, d.asset);
            }
        } catch (RuntimeException e) {
            t = Typeface.DEFAULT_BOLD;
        }
        if (t == null) t = Typeface.DEFAULT_BOLD;
        // only cache real fonts, so a call before init() does not stick to the fallback
        if (d.asset == null || assets != null) cache.put(d.id, t);
        return t;
    }
}
