package dev.tvnclr.slap.engine;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Whole-collection backup as a .zip: packs/&lt;id&gt;/pack.json, tray.png, the stickers and the
 * editable source copies. Restoring keeps each pack's id (so packs already in WhatsApp stay linked)
 * and skips packs that already exist.
 */
public final class Backup {

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_]+");
    private static final Pattern FILE = Pattern.compile("pack\\.json|tray\\.png|s\\d+\\.webp|src/s\\d+\\.webp\\.(src|json)");

    public static final class Result {
        public int packs;
        public int skipped;
    }

    private Backup() {}

    public static Result write(Context c, OutputStream os) throws IOException {
        Result r = new Result();
        try (ZipOutputStream zip = new ZipOutputStream(os)) {
            for (PackStore.Pack p : PackStore.list(c)) {
                addTree(zip, p.dir, "packs/" + p.id + "/");
                r.packs++;
            }
        }
        return r;
    }

    private static void addTree(ZipOutputStream zip, File dir, String prefix) throws IOException {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        byte[] buf = new byte[64 * 1024];
        for (File f : fs) {
            if (f.isDirectory()) {
                addTree(zip, f, prefix + f.getName() + "/");
                continue;
            }
            String rel = prefix.substring(prefix.indexOf('/', "packs/".length()) + 1) + f.getName();
            if (!FILE.matcher(rel).matches()) continue;
            zip.putNextEntry(new ZipEntry(prefix + f.getName()));
            try (InputStream in = new FileInputStream(f)) {
                int n;
                while ((n = in.read(buf)) > 0) zip.write(buf, 0, n);
            }
            zip.closeEntry();
        }
    }

    public static Result restore(Context c, InputStream is) throws IOException {
        Result r = new Result();
        File root = PackStore.rootDir(c);
        Set<String> existing = new HashSet<>();
        for (PackStore.Pack p : PackStore.list(c)) existing.add(p.id);
        Set<String> created = new HashSet<>();
        Set<String> skipped = new HashSet<>();
        byte[] buf = new byte[64 * 1024];
        try (ZipInputStream zip = new ZipInputStream(is)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = e.getName();
                if (!name.startsWith("packs/")) continue;
                String rest = name.substring("packs/".length());
                int slash = rest.indexOf('/');
                if (slash <= 0) continue;
                String id = rest.substring(0, slash);
                String rel = rest.substring(slash + 1);
                if (!ID.matcher(id).matches() || !FILE.matcher(rel).matches()) continue;
                if (existing.contains(id)) {
                    skipped.add(id);
                    continue;
                }
                File out = new File(new File(root, id), rel);
                File parent = out.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("cannot create " + parent);
                try (OutputStream o = new FileOutputStream(out)) {
                    int n;
                    while ((n = zip.read(buf)) > 0) o.write(buf, 0, n);
                }
                created.add(id);
            }
        }
        // a pack without a readable pack.json is not a pack
        for (String id : created) {
            if (PackStore.get(c, id) == null) PackStore.deleteTree(new File(root, id));
            else r.packs++;
        }
        r.skipped = skipped.size();
        return r;
    }
}
