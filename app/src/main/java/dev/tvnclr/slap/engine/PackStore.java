package dev.tvnclr.slap.engine;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.Rect;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pacotes de figurinhas guardados em filesDir/packs/<id>/ (pack.json, tray.png, *.webp). */
public final class PackStore {

    public static final String TRAY_FILE = "tray.png";
    public static final int MIN_STICKERS = 3;
    public static final int MAX_STICKERS = 30;

    public static final class Sticker {
        public String file;
        public String emoji;
    }

    public static final class Pack {
        public String id;
        public String name;
        public boolean animated;
        public int version;
        public final List<Sticker> stickers = new ArrayList<>();
        public File dir;

        public File file(String name) {
            return new File(dir, name);
        }

        public Sticker find(String file) {
            for (Sticker s : stickers) if (s.file.equals(file)) return s;
            return null;
        }
    }

    private PackStore() {}

    private static File root(Context c) {
        File r = new File(c.getFilesDir(), "packs");
        if (!r.exists()) r.mkdirs();
        return r;
    }

    private static Pack read(File dir) {
        File f = new File(dir, "pack.json");
        if (!f.isFile()) return null;
        try {
            JSONObject o = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            Pack p = new Pack();
            p.id = o.getString("id");
            p.name = o.getString("name");
            p.animated = o.getBoolean("animated");
            p.version = o.optInt("version", 1);
            p.dir = dir;
            JSONArray a = o.getJSONArray("stickers");
            for (int i = 0; i < a.length(); i++) {
                JSONObject so = a.getJSONObject(i);
                Sticker s = new Sticker();
                s.file = so.getString("file");
                s.emoji = so.optString("emoji", "\ud83d\ude00");
                if (new File(dir, s.file).isFile()) p.stickers.add(s);
            }
            return p;
        } catch (IOException | JSONException e) {
            return null;
        }
    }

    private static void write(Pack p) throws IOException {
        try {
            JSONObject o = new JSONObject();
            o.put("id", p.id);
            o.put("name", p.name);
            o.put("animated", p.animated);
            o.put("version", p.version);
            JSONArray a = new JSONArray();
            for (Sticker s : p.stickers) {
                JSONObject so = new JSONObject();
                so.put("file", s.file);
                so.put("emoji", s.emoji);
                a.put(so);
            }
            o.put("stickers", a);
            File tmp = new File(p.dir, "pack.json.tmp");
            Files.write(tmp.toPath(), o.toString(1).getBytes(StandardCharsets.UTF_8));
            File dst = new File(p.dir, "pack.json");
            if (!tmp.renameTo(dst)) {
                Files.write(dst.toPath(), o.toString(1).getBytes(StandardCharsets.UTF_8));
                tmp.delete();
            }
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    public static synchronized List<Pack> list(Context c) {
        List<Pack> out = new ArrayList<>();
        File[] dirs = root(c).listFiles();
        if (dirs != null) {
            for (File d : dirs) {
                if (!d.isDirectory()) continue;
                Pack p = read(d);
                if (p != null) out.add(p);
            }
        }
        Collections.sort(out, (a, b) -> a.id.compareTo(b.id));
        return out;
    }

    public static synchronized Pack get(Context c, String id) {
        if (id == null || !id.matches("[A-Za-z0-9_]+")) return null;
        return read(new File(root(c), id));
    }

    public static synchronized Pack create(Context c, String name, boolean animated) throws IOException {
        String id = "p" + System.currentTimeMillis();
        File dir = new File(root(c), id);
        if (!dir.mkdirs()) throw new IOException("could not create the pack folder");
        Pack p = new Pack();
        p.id = id;
        p.name = name;
        p.animated = animated;
        p.version = 1;
        p.dir = dir;
        write(p);
        return p;
    }

    public static synchronized void rename(Pack p, String name) throws IOException {
        p.name = name;
        p.version++;
        write(p);
    }

    public static synchronized void addSticker(Pack p, byte[] webp, String emoji) throws IOException {
        if (p.stickers.size() >= MAX_STICKERS) throw new IOException("the pack already has " + MAX_STICKERS + " stickers");
        String name = "s" + System.currentTimeMillis() + ".webp";
        try (FileOutputStream fo = new FileOutputStream(new File(p.dir, name))) {
            fo.write(webp);
        }
        Sticker s = new Sticker();
        s.file = name;
        s.emoji = emoji;
        p.stickers.add(s);
        p.version++;
        if (p.stickers.size() == 1 || !p.file(TRAY_FILE).isFile()) makeTray(p);
        write(p);
    }

    public static synchronized void removeSticker(Pack p, String file) throws IOException {
        Sticker s = p.find(file);
        if (s == null) return;
        boolean wasFirst = p.stickers.indexOf(s) == 0;
        p.stickers.remove(s);
        new File(p.dir, file).delete();
        p.version++;
        if (wasFirst) {
            if (p.stickers.isEmpty()) p.file(TRAY_FILE).delete();
            else makeTray(p);
        }
        write(p);
    }

    public static synchronized void setEmoji(Pack p, String file, String emoji) throws IOException {
        Sticker s = p.find(file);
        if (s == null) return;
        s.emoji = emoji;
        p.version++;
        write(p);
    }

    public static synchronized void delete(Pack p) {
        File[] fs = p.dir.listFiles();
        if (fs != null) for (File f : fs) f.delete();
        p.dir.delete();
    }

    /** Icone da bandeja: primeiro frame da primeira figurinha, 96x96 PNG. */
    private static void makeTray(Pack p) throws IOException {
        if (p.stickers.isEmpty()) return;
        File src = p.file(p.stickers.get(0).file);
        Bitmap first = ImageDecoder.decodeBitmap(ImageDecoder.createSource(src),
                (decoder, info, source) -> decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE));
        Bitmap tray = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(tray);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        cv.drawBitmap(first, new Rect(0, 0, first.getWidth(), first.getHeight()), new Rect(0, 0, 96, 96), paint);
        first.recycle();
        try (FileOutputStream fo = new FileOutputStream(p.file(TRAY_FILE))) {
            tray.compress(Bitmap.CompressFormat.PNG, 100, fo);
        }
        tray.recycle();
    }
}
