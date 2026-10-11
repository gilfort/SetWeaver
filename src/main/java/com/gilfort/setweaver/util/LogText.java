package com.gilfort.setweaver.util;

import com.gilfort.setweaver.SetWeaver;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Turns translatable components into English text for the server log. A dedicated server does not load mod lang
 * files, so {@link Component#getString()} would only show the key there.
 */
public final class LogText {

    private static final String LANG_FILE = "/assets/" + SetWeaver.MODID + "/lang/en_us.json";
    private static JsonObject english;

    private LogText() {
    }

    /**
     * @param component a component, usually translatable
     * @return the English text
     */
    public static String of(Component component) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            JsonObject lang = english();
            if (lang.has(translatable.getKey())) {
                Object[] args = Arrays.stream(translatable.getArgs())
                        .map(arg -> arg instanceof Component c ? of(c) : String.valueOf(arg))
                        .toArray();
                return String.format(lang.get(translatable.getKey()).getAsString(), args);
            }
        }
        return component.getString();
    }

    private static synchronized JsonObject english() {
        if (english == null) {
            english = new JsonObject();
            try (InputStream in = LogText.class.getResourceAsStream(LANG_FILE)) {
                if (in != null) {
                    english = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                }
            } catch (Exception e) {
                SetWeaver.LOGGER.warn("[SetWeaver] Could not read {}: {}", LANG_FILE, e.getMessage());
            }
        }
        return english;
    }
}
