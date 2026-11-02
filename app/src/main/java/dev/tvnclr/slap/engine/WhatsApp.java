package dev.tvnclr.slap.engine;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;

/** Mesma logica do app de exemplo oficial para adicionar pacotes e checar se ja foram adicionados. */
public final class WhatsApp {

    public static final String CONSUMER = "com.whatsapp";
    public static final String BUSINESS = "com.whatsapp.w4b";

    private WhatsApp() {}

    public static boolean installed(Context c, String pkg) {
        try {
            ApplicationInfo ai = c.getPackageManager().getApplicationInfo(pkg, 0);
            return ai != null && ai.enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** true = adicionado; false = nao adicionado; null = nao deu para saber. */
    public static Boolean isAdded(Context c, String pkg, String identifier) {
        if (!installed(c, pkg)) return null;
        String auth = pkg + ".provider.sticker_whitelist_check";
        ProviderInfo pi = c.getPackageManager().resolveContentProvider(auth, PackageManager.GET_META_DATA);
        if (pi == null) return null;
        Uri q = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(auth)
                .appendPath("is_whitelisted")
                .appendQueryParameter("authority", StickerProvider.AUTHORITY)
                .appendQueryParameter("identifier", identifier).build();
        try (Cursor cur = c.getContentResolver().query(q, null, null, null, null)) {
            if (cur != null && cur.moveToFirst()) {
                return cur.getInt(cur.getColumnIndexOrThrow("result")) == 1;
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /** Intent para adicionar o pacote; null se nenhum WhatsApp estiver instalado. */
    public static Intent addIntent(Context c, PackStore.Pack p) {
        boolean wa = installed(c, CONSUMER), wb = installed(c, BUSINESS);
        if (!wa && !wb) return null;
        Intent i = new Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK");
        i.putExtra("sticker_pack_id", p.id);
        i.putExtra("sticker_pack_authority", StickerProvider.AUTHORITY);
        i.putExtra("sticker_pack_name", p.name);
        if (wa && wb) {
            Boolean inA = isAdded(c, CONSUMER, p.id), inB = isAdded(c, BUSINESS, p.id);
            if (Boolean.TRUE.equals(inA) && !Boolean.TRUE.equals(inB)) {
                i.setPackage(BUSINESS);
                return i;
            }
            if (Boolean.TRUE.equals(inB) && !Boolean.TRUE.equals(inA)) {
                i.setPackage(CONSUMER);
                return i;
            }
            return Intent.createChooser(i, c.getString(dev.tvnclr.slap.R.string.whatsapp_chooser));
        }
        i.setPackage(wa ? CONSUMER : BUSINESS);
        return i;
    }
}
