package dev.tvnclr.slap.engine;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.List;

/**
 * Interface com o WhatsApp, com os mesmos caminhos e colunas do app de exemplo oficial
 * (github.com/WhatsApp/stickers). Os arquivos sao servidos do armazenamento interno.
 */
public class StickerProvider extends ContentProvider {

    public static final String AUTHORITY = "dev.tvnclr.slap.stickercontentprovider";

    // nomes usados pelo WhatsApp: nao mudar
    private static final String STICKER_PACK_IDENTIFIER_IN_QUERY = "sticker_pack_identifier";
    private static final String STICKER_PACK_NAME_IN_QUERY = "sticker_pack_name";
    private static final String STICKER_PACK_PUBLISHER_IN_QUERY = "sticker_pack_publisher";
    private static final String STICKER_PACK_ICON_IN_QUERY = "sticker_pack_icon";
    private static final String ANDROID_APP_DOWNLOAD_LINK_IN_QUERY = "android_play_store_link";
    private static final String IOS_APP_DOWNLOAD_LINK_IN_QUERY = "ios_app_download_link";
    private static final String PUBLISHER_EMAIL = "sticker_pack_publisher_email";
    private static final String PUBLISHER_WEBSITE = "sticker_pack_publisher_website";
    private static final String PRIVACY_POLICY_WEBSITE = "sticker_pack_privacy_policy_website";
    private static final String LICENSE_AGREEMENT_WEBSITE = "sticker_pack_license_agreement_website";
    private static final String IMAGE_DATA_VERSION = "image_data_version";
    private static final String AVOID_CACHE = "whatsapp_will_not_cache_stickers";
    private static final String ANIMATED_STICKER_PACK = "animated_sticker_pack";
    private static final String STICKER_FILE_NAME_IN_QUERY = "sticker_file_name";
    private static final String STICKER_FILE_EMOJI_IN_QUERY = "sticker_emoji";
    private static final String STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY = "sticker_accessibility_text";

    private static final String METADATA = "metadata";
    private static final String STICKERS = "stickers";
    private static final String STICKERS_ASSET = "stickers_asset";

    private static final int METADATA_CODE = 1;
    private static final int METADATA_CODE_FOR_SINGLE_PACK = 2;
    private static final int STICKERS_CODE = 3;
    private static final int STICKERS_ASSET_CODE = 4;

    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        MATCHER.addURI(AUTHORITY, METADATA, METADATA_CODE);
        MATCHER.addURI(AUTHORITY, METADATA + "/*", METADATA_CODE_FOR_SINGLE_PACK);
        MATCHER.addURI(AUTHORITY, STICKERS + "/*", STICKERS_CODE);
        MATCHER.addURI(AUTHORITY, STICKERS_ASSET + "/*/*", STICKERS_ASSET_CODE);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        int code = MATCHER.match(uri);
        if (code == METADATA_CODE) {
            List<PackStore.Pack> all = PackStore.list(getContext());
            java.util.ArrayList<PackStore.Pack> ready = new java.util.ArrayList<>();
            for (PackStore.Pack p : all) if (p.stickers.size() >= PackStore.MIN_STICKERS) ready.add(p);
            return packCursor(uri, ready);
        } else if (code == METADATA_CODE_FOR_SINGLE_PACK) {
            PackStore.Pack p = PackStore.get(getContext(), uri.getLastPathSegment());
            return packCursor(uri, p == null ? Collections.<PackStore.Pack>emptyList() : Collections.singletonList(p));
        } else if (code == STICKERS_CODE) {
            MatrixCursor c = new MatrixCursor(new String[]{STICKER_FILE_NAME_IN_QUERY, STICKER_FILE_EMOJI_IN_QUERY, STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY});
            PackStore.Pack p = PackStore.get(getContext(), uri.getLastPathSegment());
            if (p != null) {
                for (PackStore.Sticker s : p.stickers) c.addRow(new Object[]{s.file, s.emoji, ""});
            }
            c.setNotificationUri(getContext().getContentResolver(), uri);
            return c;
        }
        throw new IllegalArgumentException("Unknown URI: " + uri);
    }

    private Cursor packCursor(Uri uri, List<PackStore.Pack> packs) {
        MatrixCursor c = new MatrixCursor(new String[]{
                STICKER_PACK_IDENTIFIER_IN_QUERY,
                STICKER_PACK_NAME_IN_QUERY,
                STICKER_PACK_PUBLISHER_IN_QUERY,
                STICKER_PACK_ICON_IN_QUERY,
                ANDROID_APP_DOWNLOAD_LINK_IN_QUERY,
                IOS_APP_DOWNLOAD_LINK_IN_QUERY,
                PUBLISHER_EMAIL,
                PUBLISHER_WEBSITE,
                PRIVACY_POLICY_WEBSITE,
                LICENSE_AGREEMENT_WEBSITE,
                IMAGE_DATA_VERSION,
                AVOID_CACHE,
                ANIMATED_STICKER_PACK,
        });
        for (PackStore.Pack p : packs) {
            MatrixCursor.RowBuilder b = c.newRow();
            b.add(p.id);
            b.add(p.name);
            b.add("Slap");
            b.add(PackStore.TRAY_FILE);
            b.add("");
            b.add("");
            b.add("");
            b.add("");
            b.add("");
            b.add("");
            b.add(String.valueOf(p.version));
            b.add(0);
            b.add(p.animated ? 1 : 0);
        }
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        if (MATCHER.match(uri) != STICKERS_ASSET_CODE) return null;
        List<String> seg = uri.getPathSegments();
        if (seg.size() != 3) throw new FileNotFoundException("caminho invalido: " + uri);
        String id = seg.get(1);
        String name = seg.get(2);
        PackStore.Pack p = PackStore.get(getContext(), id);
        if (p == null) throw new FileNotFoundException("pacote nao existe: " + id);
        boolean allowed = PackStore.TRAY_FILE.equals(name) || p.find(name) != null;
        if (!allowed) throw new FileNotFoundException("arquivo nao pertence ao pacote: " + name);
        File f = p.file(name);
        if (!f.isFile()) throw new FileNotFoundException("arquivo nao existe: " + name);
        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(pfd, 0, f.length());
    }

    @Override
    public String getType(Uri uri) {
        switch (MATCHER.match(uri)) {
            case METADATA_CODE:
                return "vnd.android.cursor.dir/vnd." + AUTHORITY + "." + METADATA;
            case METADATA_CODE_FOR_SINGLE_PACK:
                return "vnd.android.cursor.item/vnd." + AUTHORITY + "." + METADATA;
            case STICKERS_CODE:
                return "vnd.android.cursor.dir/vnd." + AUTHORITY + "." + STICKERS;
            case STICKERS_ASSET_CODE:
                return uri.getLastPathSegment() != null && uri.getLastPathSegment().endsWith(".png") ? "image/png" : "image/webp";
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Not supported");
    }
}
