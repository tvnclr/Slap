package dev.tvnclr.slap.engine;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Which AI cutout engines this build has, and which one the user picked. ML Kit is looked up by
 * name, so the F-Droid branch only has to delete {@code MlKitSegmenter.java} and its dependency:
 * nothing here changes between branches.
 */
public final class Segmenters {

    private static final String PREFS = "slap";
    private static final String KEY = "ai_engine";
    private static final String ID_MLKIT = "mlkit";
    private static final String MLKIT_CLASS = "dev.tvnclr.slap.engine.MlKitSegmenter";

    private Segmenters() {}

    /** Engine ids available in this build, default first. */
    public static List<String> available() {
        List<String> ids = new ArrayList<>();
        if (hasMlKit()) ids.add(ID_MLKIT);
        ids.add(U2NetSegmenter.ID);
        return ids;
    }

    private static boolean hasMlKit() {
        try {
            Class.forName(MLKIT_CLASS);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The picked engine, or the default when the pick is not in this build. */
    public static String selected(Context c) {
        List<String> ids = available();
        String id = prefs(c).getString(KEY, ids.get(0));
        return ids.contains(id) ? id : ids.get(0);
    }

    public static void select(Context c, String id) {
        prefs(c).edit().putString(KEY, id).apply();
    }

    /** A new engine instance for the picked id; close it when done. */
    public static Segmenter create(Context c) {
        String id = selected(c);
        if (ID_MLKIT.equals(id)) {
            try {
                return (Segmenter) Class.forName(MLKIT_CLASS).getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError e) {
                // fall through to the bundled engine
            }
        }
        return new U2NetSegmenter();
    }
}
