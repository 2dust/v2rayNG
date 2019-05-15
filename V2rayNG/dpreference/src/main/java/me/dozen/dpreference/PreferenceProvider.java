package me.dozen.dpreference;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by wangyida on 15/12/18.
 */
public class PreferenceProvider extends ContentProvider {

    private static final String TAG = PreferenceProvider.class.getSimpleName();

    private static final String AUTHORITY = "com.v2ray.ang.dpreference";

    public static final String CONTENT_PREF_BOOLEAN_URI = "content://" + AUTHORITY + "/boolean/";
    public static final String CONTENT_PREF_STRING_URI = "content://" + AUTHORITY + "/string/";
    public static final String CONTENT_PREF_INT_URI = "content://" + AUTHORITY + "/integer/";
    public static final String CONTENT_PREF_LONG_URI = "content://" + AUTHORITY + "/long/";
    public static final String CONTENT_PREF_STRING_SET_URI = "content://" + AUTHORITY + "/string_set/";


    public static final String PREF_KEY = "key";
    public static final String PREF_VALUE = "value";

    public static final int PREF_BOOLEAN = 1;
    public static final int PREF_STRING = 2;
    public static final int PREF_INT = 3;
    public static final int PREF_LONG = 4;
    public static final int PREF_STRING_SET = 5;

    private static final UriMatcher sUriMatcher;

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(AUTHORITY, "boolean/*/*", PREF_BOOLEAN);
        sUriMatcher.addURI(AUTHORITY, "string/*/*", PREF_STRING);
        sUriMatcher.addURI(AUTHORITY, "integer/*/*", PREF_INT);
        sUriMatcher.addURI(AUTHORITY, "long/*/*", PREF_LONG);
        sUriMatcher.addURI(AUTHORITY, "string_set/*/*", PREF_STRING_SET);

    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        MatrixCursor cursor = null;
        PrefModel model = getPrefModelByUri(uri);
        switch (sUriMatcher.match(uri)) {
            case PREF_BOOLEAN:
                if (getDPreference(model.getName()).hasKey(model.getKey())) {
                    cursor = preferenceToCursor(getDPreference(model.getName()).getPrefBoolean(model.getKey(), false) ? 1 : 0);
                }
                break;
            case PREF_STRING:
                if (getDPreference(model.getName()).hasKey(model.getKey())) {
                    cursor = preferenceToCursor(getDPreference(model.getName()).getPrefString(model.getKey(), ""));
                }
                break;
            case PREF_INT:
                if (getDPreference(model.getName()).hasKey(model.getKey())) {
                    cursor = preferenceToCursor(getDPreference(model.getName()).getPrefInt(model.getKey(), -1));
                }
                break;
            case PREF_LONG:
                if (getDPreference(model.getName()).hasKey(model.getKey())) {
                    cursor = preferenceToCursor(getDPreference(model.getName()).getPrefLong(model.getKey(), -1));
                }
                break;
            case PREF_STRING_SET:
                if (getDPreference(model.getName()).hasKey(model.getKey())) {
                    cursor = preferenceToCursor(getDPreference(model.getName()).getPrefStringSet(model.getKey(), null));
                }
                break;
        }
        return cursor;
    }

    @Nullable
    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new IllegalStateException("insert unsupport!!!");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        switch (sUriMatcher.match(uri)) {
            case PREF_BOOLEAN:
            case PREF_LONG:
            case PREF_STRING:
            case PREF_INT:
            case PREF_STRING_SET:
                PrefModel model = getPrefModelByUri(uri);
                if (model != null) {
                    getDPreference(model.getName()).removePreference(model.getKey());
                }
                break;
            default:
                throw new IllegalStateException(" unsupported uri : " + uri);
        }
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        PrefModel model = getPrefModelByUri(uri);
        if (model == null) {
            throw new IllegalArgumentException("update prefModel is null");
        }
        switch (sUriMatcher.match(uri)) {
            case PREF_BOOLEAN:
                persistBoolean(model.getName(), values);
                break;
            case PREF_LONG:
                persistLong(model.getName(), values);
                break;
            case PREF_STRING:
                persistString(model.getName(), values);
                break;
            case PREF_INT:
                persistInt(model.getName(), values);
                break;
            case PREF_STRING_SET:
                persistStringSet(model.getName(), values);
                break;
            default:
                throw new IllegalStateException("update unsupported uri : " + uri);
        }
        return 0;
    }

    private static String[] PREFERENCE_COLUMNS = {PREF_VALUE};

    private <T> MatrixCursor preferenceToCursor(T value) {
        MatrixCursor matrixCursor = new MatrixCursor(PREFERENCE_COLUMNS, 1);
        MatrixCursor.RowBuilder builder = matrixCursor.newRow();
        builder.add(value);
        return matrixCursor;
    }

    private void persistInt(String name, ContentValues values) {
        if (values == null) {
            throw new IllegalArgumentException(" values is null!!!");
        }
        String kInteger = values.getAsString(PREF_KEY);
        int vInteger = values.getAsInteger(PREF_VALUE);
        getDPreference(name).setPrefInt(kInteger, vInteger);
    }

    private void persistBoolean(String name, ContentValues values) {
        if (values == null) {
            throw new IllegalArgumentException(" values is null!!!");
        }
        String kBoolean = values.getAsString(PREF_KEY);
        boolean vBoolean = values.getAsBoolean(PREF_VALUE);
        getDPreference(name).setPrefBoolean(kBoolean, vBoolean);
    }

    private void persistLong(String name, ContentValues values) {
        if (values == null) {
            throw new IllegalArgumentException(" values is null!!!");
        }
        String kLong = values.getAsString(PREF_KEY);
        long vLong = values.getAsLong(PREF_VALUE);
        getDPreference(name).setPrefLong(kLong, vLong);
    }

    private void persistString(String name, ContentValues values) {
        if (values == null) {
            throw new IllegalArgumentException(" values is null!!!");
        }
        String kString = values.getAsString(PREF_KEY);
        String vString = values.getAsString(PREF_VALUE);
        getDPreference(name).setPrefString(kString, vString);
    }

    private void persistStringSet(String name, ContentValues values) {
        if (values == null) {
            throw new IllegalArgumentException(" values is null!!!");
        }
        String kString = values.getAsString(PREF_KEY);
        String vString = values.getAsString(PREF_VALUE);
        getDPreference(name).setPrefStringSet(kString, StringSetConverter.decode(vString));
    }

    private static Map<String, IPrefImpl> sPreferences = new HashMap<>();

    private IPrefImpl getDPreference(String name) {
        if (TextUtils.isEmpty(name)) {
            throw new IllegalArgumentException("getDPreference name is null!!!");
        }
        if (sPreferences.get(name) == null) {
            IPrefImpl pref = new PreferenceImpl(getContext(), name);
            sPreferences.put(name, pref);
        }
        return sPreferences.get(name);
    }

    private PrefModel getPrefModelByUri(Uri uri) {
        if (uri == null || uri.getPathSegments().size() != 3) {
            throw new IllegalArgumentException("getPrefModelByUri uri is wrong : " + uri);
        }
        String name = uri.getPathSegments().get(1);
        String key = uri.getPathSegments().get(2);
        return new PrefModel(name, key);
    }


    public static Uri buildUri(String name, String key, int type) {
        return Uri.parse(getUriByType(type) + name + "/" + key);
    }

    private static String getUriByType(int type) {
        switch (type) {
            case PreferenceProvider.PREF_BOOLEAN:
                return PreferenceProvider.CONTENT_PREF_BOOLEAN_URI;
            case PreferenceProvider.PREF_INT:
                return PreferenceProvider.CONTENT_PREF_INT_URI;
            case PreferenceProvider.PREF_LONG:
                return PreferenceProvider.CONTENT_PREF_LONG_URI;
            case PreferenceProvider.PREF_STRING:
                return PreferenceProvider.CONTENT_PREF_STRING_URI;
            case PreferenceProvider.PREF_STRING_SET:
                return PreferenceProvider.CONTENT_PREF_STRING_SET_URI;
        }
        throw new IllegalStateException("unsupport preftype : " + type);
    }

    private static class PrefModel {
        String name;

        String key;

        public PrefModel(String name, String key) {
            this.name = name;
            this.key = key;
        }

        public String getName() {
            return name;
        }

        public String getKey() {
            return key;
        }
    }

}
