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

    public static List<Category> loadAll(Context ctx) {
        List<Category> list = new ArrayList<>();
        String json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null);
        if (json == null) {
            // first run: the three defaults
            list.add(new Category("Academic", COLORS[5]));  // blue
            list.add(new Category("Work", COLORS[2]));      // orange
            list.add(new Category("Personal", COLORS[4]));  // green
            return list;
        }
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Category(o.getString("name"), o.getInt("color")));
            }
        } catch (Exception ignored) { }
        return list;
    }

    public static void saveAll(Context ctx, List<Category> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Category c : list) {
                arr.put(new JSONObject().put("name", c.name).put("color", c.color));
            }
        } catch (Exception ignored) { }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, arr.toString()).apply();
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
