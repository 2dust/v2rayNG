package me.dozen.dpreference;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Set;

public class StringSetConverter {
    private static final Gson gson = new Gson();

    public static String encode(Set<String> src) {
        return gson.toJson(src);
    }

    public static Set<String> decode(String json) {
        Type setType = new TypeToken<Set<String>>() {
        }.getType();
        return gson.fromJson(json, setType);
    }
}
