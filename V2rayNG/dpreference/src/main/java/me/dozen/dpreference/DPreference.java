package me.dozen.dpreference;


import android.content.Context;
import android.text.TextUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by wangyida on 15-4-9.
 */
public class DPreference {

    Context mContext;

    /**
     * preference file name
     */
    String mName;

    private DPreference() {
    }

    public DPreference(Context context, String name) {
        this.mContext = context;
        this.mName = name;
    }

    public String getPrefString(final String key, final String defaultValue) {
        return PrefAccessor.getString(mContext, mName, key, defaultValue);
    }

    public void setPrefString(final String key, final String value) {
        PrefAccessor.setString(mContext, mName, key, value);
    }

    public boolean getPrefBoolean(final String key, final boolean defaultValue) {
        return PrefAccessor.getBoolean(mContext, mName, key, defaultValue);
    }

    public void setPrefBoolean(final String key, final boolean value) {
        PrefAccessor.setBoolean(mContext, mName, key, value);
    }

    public void setPrefInt(final String key, final int value) {
        PrefAccessor.setInt(mContext, mName, key, value);
    }

    public int getPrefInt(final String key, final int defaultValue) {
        return PrefAccessor.getInt(mContext, mName, key, defaultValue);
    }

    public void setPrefLong(final String key, final long value) {
        PrefAccessor.setLong(mContext, mName, key, value);
    }

    public long getPrefLong(final String key, final long defaultValue) {
        return PrefAccessor.getLong(mContext, mName, key, defaultValue);
    }

    public void setPrefStringSet(final String key, final Set<String> value) {
        PrefAccessor.setStringSet(mContext, mName, key, value);
    }

    public Set<String> getPrefStringSet(final String key, final Set<String> defaultValue) {
        return PrefAccessor.getStringSet(mContext, mName, key, defaultValue);
    }

    public void setPrefStringOrderedSet(final String key, final LinkedHashSet<String> value) {
        PrefAccessor.setString(mContext, mName, key, StringSetConverter.encode(value));
    }

    public LinkedHashSet<String> getPrefStringOrderedSet(final String key, final LinkedHashSet<String> defaultValue) {
        String value = PrefAccessor.getString(mContext, mName, key, "");
        if (TextUtils.isEmpty(value)) {
            return defaultValue;
        }
        return StringSetConverter.decode(value);
    }

    public void removePreference(final String key) {
        PrefAccessor.remove(mContext, mName, key);
    }

}
