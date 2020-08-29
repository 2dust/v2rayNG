package me.dozen.dpreference;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.Set;

public class StringSetConverter {
    private static final Gson gson = new Gson();

    public static String encode(Set<String> src) {
        return gson.toJson(src);
    }

    public static LinkedHashSet<String> decode(String json) {
        Type setType = new TypeToken<LinkedHashSet<String>>() {
        }.getType();
        return gson.fromJson(json, setType);
    }
}
