package me.dozen.dpreference;


import java.util.Set;

/**
 * Created by wangyida on 15/12/18.
 */
interface IPrefImpl {

    String getPrefString(String key, String defaultValue);

    void setPrefString(String key, String value);

    boolean getPrefBoolean(String key, boolean defaultValue);

    void setPrefBoolean(final String key, final boolean value);

    void setPrefInt(final String key, final int value);

    int getPrefInt(final String key, final int defaultValue);

    void setPrefFloat(final String key, final float value);

    float getPrefFloat(final String key, final float defaultValue);

    void setPrefLong(final String key, final long value);

    long getPrefLong(final String key, final long defaultValue);

    Set<String> getPrefStringSet(String key, Set<String> defaultValue);

    void setPrefStringSet(String key, Set<String> value);

    void removePreference(final String key);

    boolean hasKey(String key);

}
