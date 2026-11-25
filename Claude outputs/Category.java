package com.example.assignment3;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** A category option (name + colour), plus helpers to save/load and draw them as pills. */
public class Category {

    public String name;
    public int color;

    public Category(String name, int color) {
        this.name = name;
        this.color = color;
    }

    // Notion-style soft background colours, dark text on top
    public static final int TEXT_COLOR = 0xFF37352F;
    public static final String[] COLOR_NAMES =
            {"Gray", "Brown", "Orange", "Yellow", "Green", "Blue", "Purple", "Pink", "Red"};
    public static final int[] COLORS = {
            0xFFE3E2E0, 0xFFEEE0DA, 0xFFFADEC9, 0xFFFDECC8, 0xFFDBEDDB,
            0xFFD3E5EF, 0xFFE8DEEE, 0xFFF5E0E9, 0xFFFFE2DD};

    // ----- saving / loading (SharedPreferences, as JSON) -----

    private static final String PREFS = "category_prefs";
    private static final String KEY = "categories";

    /** The Category field's options (Academic / Work / Personal to start). */
    public static List<Category> loadAll(Context ctx) {
        List<Category> defaults = new ArrayList<>();
        defaults.add(new Category("Academic", COLORS[5]));  // blue
        defaults.add(new Category("Work", COLORS[2]));      // orange
        defaults.add(new Category("Personal", COLORS[4]));  // green
        return loadAll(ctx, KEY, defaults);
    }

    public static void saveAll(Context ctx, List<Category> list) {
        saveAll(ctx, KEY, list);
    }

    /** Any option list, saved under its own key (e.g. "classes", "coursework_types"). */
    public static List<Category> loadAll(Context ctx, String key, List<Category> defaults) {
        String json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null);
        if (json == null) return new ArrayList<>(defaults);   // first run
        List<Category> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Category(o.getString("name"), o.getInt("color")));
            }
        } catch (Exception ignored) { }
        return list;
    }

    public static void saveAll(Context ctx, String key, List<Category> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Category c : list) {
                arr.put(new JSONObject().put("name", c.name).put("color", c.color));
            }
        } catch (Exception ignored) { }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(key, arr.toString()).apply();
    }

    /** Builds defaults from names, cycling through the colours. */
    public static List<Category> defaults(String... names) {
        List<Category> list = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            list.add(new Category(names[i], COLORS[(i + 2) % COLORS.length]));
        }
        return list;
    }

    @Nullable
    public static Category find(List<Category> list, @Nullable String name) {
        if (name == null) return null;
        for (Category c : list) if (c.name.equalsIgnoreCase(name)) return c;
        return null;
    }

    // ----- drawing -----

    /** Turns a TextView into a coloured rounded "pill" showing the category. */
    public static void stylePill(TextView tv, Category c) {
        float d = tv.getResources().getDisplayMetrics().density;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(c.color);
        bg.setCornerRadius(4 * d);
        tv.setBackground(bg);
        tv.setText(c.name);
        tv.setTextColor(TEXT_COLOR);
        tv.setPadding((int) (8 * d), (int) (2 * d), (int) (8 * d), (int) (2 * d));
    }
}
