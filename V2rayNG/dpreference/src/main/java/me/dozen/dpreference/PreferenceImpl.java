package me.dozen.dpreference;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Set;

/**
 * Created by wangyida on 15/12/18.
 */
class PreferenceImpl implements IPrefImpl {

    private Context mContext;

    private String mPrefName;

    public PreferenceImpl(Context context, String prefName) {
        mContext = context;
        mPrefName = prefName;
    }

    public String getPrefString(final String key,
                                final String defaultValue) {
        final SharedPreferences settings =
                mContext.getSharedPreferences(mPrefName, Context.MODE_PRIVATE);
        return settings.getString(key, defaultValue);
    }

    public void setPrefString(final String key, final String value) {
        final SharedPreferences settings =
                mContext.getSharedPreferences(mPrefName, Context.MODE_PRIVATE);
        settings.edit().putString(key, value).apply();
    }

    public boolean getPrefBoolean(final String key,
                                  final boolean defaultValue) {
        final SharedPreferences settings =
                mContext.getSharedPreferences(mPrefName, Context.MODE_PRIVATE);
        return settings.getBoolean(key, defaultValue);
    }

    public boolean hasKey(final String key) {
        return mContext.getSharedPreferences(mPrefName, Context.MODE_PRIVATE)
                .contains(key);
    }

    public void setPrefBoolean(final String key, final boolean value) {
        final SharedPreferences settings =
                mContext.getSharedPreferences(mPrefName, Context.MODE_PRIVATE);
        settings.edit().putBoolean(key, value).apply();
    }

    public void setPrefInt(final String key, final int value) {
        final SharedPreferences settings =
                mContext.getSharedPreferences(mPrefName, Context.MODE_PRIVATE);
        settings.edit().putInt(key, value).apply();
    }

    @Override
    public Set<String> getPrefStringSet(String key, Set<String> defaultValue) {
        final SharedPreferences settings =
                mContext.getSharedPreferences(mPrefName, Context.MODE_PRIVATE);
        return settings.getStringSet(key, defaultValue);
    }

    @Override
    public void setPrefStringSet(String key, Set<String> value) {
        final SharedPreferences settings =
                mContext.getSharedPreferences(mPrefName, Context.MODE_PRIVATE);
        settings.edit().putStringSet(key, value).apply();
    }

    public void increasePrefInt(final String key) {
        final SharedPreferences settings =
                mContext.getSharedPreferences(mPrefName, Context.MODE_PRIVATE);
        increasePrefInt(settings, key);
    }

    public void increasePrefInt(final SharedPreferences sp, final String key) {
        final int v = sp.getInt(key, 0) + 1;
        sp.edit().putInt(key, v).apply();
    }

    public void increasePrefInt(final SharedPreferences sp, final String key,
                                final int increment) {
        final int v = sp.getInt(key, 0) + increment;
        sp.edit().putInt(key, v).apply();
    }

    public int getPrefInt(final String key, final int defaultValue) {
        final SharedPreferences settings =
                mContext.getSharedPreferences(mPrefName, Context.MODE_PRIVATE);
        return settings.getInt(key, defaultValue);
    }

    public void setPrefFloat(final String key, final float value) {
        final SharedPreferences settings =
                mContext.getSharedPreferences(mPrefName, Context.MODE_PRIVATE);
        settings.edit().putFloat(key, value).apply();
    }

    public float getPrefFloat(final String key, final float defaultValue) {
        final SharedPreferences settings =
                mContext.getSharedPreferences(mPrefName, Context.MODE_PRIVATE);
        return settings.getFloat(key, defaultValue);
    }

    public void setPrefLong(final String key, final long value) {
        final SharedPreferences settings =
                mContext.getSharedPreferences(mPrefName, Context.MODE_PRIVATE);
        settings.edit().putLong(key, value).apply();
    }

    public long getPrefLong(final String key, final long defaultValue) {
        final SharedPreferences settings =
                mContext.getSharedPreferences(mPrefName, Context.MODE_PRIVATE);
        return settings.getLong(key, defaultValue);
    }


    public void removePreference(final String key) {
        final SharedPreferences prefs =
                mContext.getSharedPreferences(mPrefName, Context.MODE_PRIVATE);
        prefs.edit().remove(key).apply();
    }

    public void clearPreference(final SharedPreferences p) {
        final SharedPreferences.Editor editor = p.edit();
        editor.clear();
        editor.apply();
    }

}
