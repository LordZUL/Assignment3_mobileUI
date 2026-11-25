package com.example.assignment3;

import android.content.Context;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A saved task (everything from the Add Task screen), plus saving/loading all tasks. */
public class Task {

    // Status values (same order as the Status dropdown)
    public static final int NOT_STARTED = 0, IN_PROGRESS = 1, COMPLETED = 2;
    public static final String[] STATUSES = {"Not Started", "In Progress", "Completed"};
    public static final int[] STATUS_COLORS = {0xFF9E9E9E, 0xFFDFAB01, 0xFF0F7B6C};

    public String id = UUID.randomUUID().toString();
    public String title = "";
    @Nullable public String category;
    @Nullable public String className;
    @Nullable public String courseworkType;
    public String weight = "";
    public int priority = 3;              // index into the Priority list ("None")
    public int status = NOT_STARTED;
    public String location = "";
    public String description = "";
    public LocalDate dueDate = LocalDate.now();
    @Nullable public LocalTime dueTime;
    public int notifyAmount = 30;         // 0 = no notification
    public int notifyUnit = 0;            // minutes / hours / days / weeks
    public RepeatRule repeat = new RepeatRule();
    public List<String> collaborators = new ArrayList<>();
    public List<String> attachments = new ArrayList<>();   // file Uris as text
    public List<ChecklistController.Item> checklist = new ArrayList<>();

    public boolean isCompleted() { return status == COMPLETED; }

    /** % of checklist items done, or -1 if there's no checklist. */
    public int checklistPercent() {
        if (checklist.isEmpty()) return -1;
        int done = 0;
        for (ChecklistController.Item i : checklist) if (i.done) done++;
        return Math.round(done * 100f / checklist.size());
    }

    // ---------------------------------------------------------------------
    // Saving all tasks - permanently, to the device's storage
    // ---------------------------------------------------------------------
    //
    // All tasks are written as JSON to a file in the app's private internal storage
    // (/data/data/com.example.assignment3/files/tasks.json). The file stays on the device
    // after the app is closed, so the list is restored every time the app runs.

    private static final String FILE = "tasks.json";

    // Older versions kept tasks in SharedPreferences; they're moved into the file once.
    private static final String OLD_PREFS = "task_prefs";
    private static final String OLD_KEY = "tasks";

    public static List<Task> loadAll(Context ctx) {
        migrateOldStorage(ctx);
        List<Task> list = new ArrayList<>();
        String json = readFile(ctx);
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) list.add(fromJson(arr.getJSONObject(i)));
        } catch (Exception ignored) { }
        return list;
    }

    public static void saveAll(Context ctx, List<Task> tasks) {
        JSONArray arr = new JSONArray();
        for (Task t : tasks) {
            try { arr.put(t.toJson()); } catch (Exception ignored) { }
        }
        writeFile(ctx, arr.toString());

        // Keep reminders in sync (new time, completed, notification changed...)
        for (Task t : tasks) TaskNotifications.schedule(ctx, t);
    }

    /** Reads tasks.json from internal storage ("[]" if it doesn't exist yet). */
    private static String readFile(Context ctx) {
        try (FileInputStream in = ctx.openFileInput(FILE)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            return "[]";   // first run: no file yet
        }
    }

    /** Writes tasks.json to internal storage (replaces the old contents). */
    private static void writeFile(Context ctx, String json) {
        try (FileOutputStream out = ctx.openFileOutput(FILE, Context.MODE_PRIVATE)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) { }
    }

    /** One-time move of tasks saved by the earlier SharedPreferences version. */
    private static void migrateOldStorage(Context ctx) {
        android.content.SharedPreferences prefs =
                ctx.getSharedPreferences(OLD_PREFS, Context.MODE_PRIVATE);
        String old = prefs.getString(OLD_KEY, null);
        if (old == null) return;
        if (!ctx.getFileStreamPath(FILE).exists()) writeFile(ctx, old);
        prefs.edit().remove(OLD_KEY).commit();
    }

    @Nullable
    public static Task find(Context ctx, @Nullable String id) {
        if (id == null) return null;
        for (Task t : loadAll(ctx)) if (t.id.equals(id)) return t;
        return null;
    }

    /** Adds the task, or replaces the saved one with the same id (editing). */
    public static void upsert(Context ctx, Task task) {
        List<Task> all = loadAll(ctx);
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(task.id)) {
                all.set(i, task);
                replaced = true;
                break;
            }
        }
        if (!replaced) all.add(task);
        saveAll(ctx, all);
    }

    public static void delete(Context ctx, String id) {
        TaskNotifications.cancel(ctx, id);
        List<Task> all = loadAll(ctx);
        all.removeIf(t -> t.id.equals(id));
        saveAll(ctx, all);
    }

    // ---------------------------------------------------------------------
    // JSON
    // ---------------------------------------------------------------------

    JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", id).put("title", title)
                .put("category", category).put("className", className)
                .put("courseworkType", courseworkType).put("weight", weight)
                .put("priority", priority).put("status", status)
                .put("location", location).put("description", description)
                .put("dueDate", dueDate.toString())
                .put("dueTime", dueTime == null ? null : dueTime.toString())
                .put("notifyAmount", notifyAmount).put("notifyUnit", notifyUnit);

        JSONObject r = new JSONObject();
        r.put("type", repeat.type).put("interval", repeat.interval).put("unit", repeat.unit)
                .put("ends", repeat.ends).put("count", repeat.count)
                .put("endDate", repeat.endDate == null ? null : repeat.endDate.toString());
        JSONArray days = new JSONArray();
        for (boolean b : repeat.days) days.put(b);
        r.put("days", days);
        o.put("repeat", r);

        o.put("collaborators", new JSONArray(collaborators));
        o.put("attachments", new JSONArray(attachments));

        JSONArray items = new JSONArray();
        for (ChecklistController.Item it : checklist) {
            items.put(new JSONObject()
                    .put("text", it.text).put("done", it.done)
                    .put("assignee", it.assignee)
                    .put("date", it.date == null ? null : it.date.toString())
                    .put("time", it.time == null ? null : it.time.toString()));
        }
        o.put("checklist", items);
        return o;
    }

    static Task fromJson(JSONObject o) throws Exception {
        Task t = new Task();
        t.id = o.getString("id");
        t.title = o.optString("title", "");
        t.category = str(o, "category");
        t.className = str(o, "className");
        t.courseworkType = str(o, "courseworkType");
        t.weight = o.optString("weight", "");
        t.priority = o.optInt("priority", 3);
        t.status = o.optInt("status", NOT_STARTED);
        t.location = o.optString("location", "");
        t.description = o.optString("description", "");
        t.dueDate = LocalDate.parse(o.getString("dueDate"));
        String time = str(o, "dueTime");
        t.dueTime = time == null ? null : LocalTime.parse(time);
        t.notifyAmount = o.optInt("notifyAmount", 30);
        t.notifyUnit = o.optInt("notifyUnit", 0);

        JSONObject r = o.optJSONObject("repeat");
        if (r != null) {
            t.repeat.type = r.optInt("type", RepeatRule.NONE);
            t.repeat.interval = r.optInt("interval", 1);
            t.repeat.unit = r.optInt("unit", RepeatRule.WEEK);
            t.repeat.ends = r.optInt("ends", RepeatRule.ENDS_NEVER);
            t.repeat.count = r.optInt("count", 13);
            String end = str(r, "endDate");
            t.repeat.endDate = end == null ? null : LocalDate.parse(end);
            JSONArray days = r.optJSONArray("days");
            if (days != null) for (int i = 0; i < 7 && i < days.length(); i++) t.repeat.days[i] = days.getBoolean(i);
        }

        JSONArray c = o.optJSONArray("collaborators");
        if (c != null) for (int i = 0; i < c.length(); i++) t.collaborators.add(c.getString(i));
        JSONArray a = o.optJSONArray("attachments");
        if (a != null) for (int i = 0; i < a.length(); i++) t.attachments.add(a.getString(i));

        JSONArray items = o.optJSONArray("checklist");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject j = items.getJSONObject(i);
                ChecklistController.Item it = new ChecklistController.Item(j.optString("text"));
                it.done = j.optBoolean("done");
                it.assignee = str(j, "assignee");
                String d = str(j, "date");
                it.date = d == null ? null : LocalDate.parse(d);
                String tm = str(j, "time");
                it.time = tm == null ? null : LocalTime.parse(tm);
                t.checklist.add(it);
            }
        }
        return t;
    }

    /** optString returns "null" for JSON nulls; this returns a real null. */
    @Nullable
    private static String str(JSONObject o, String key) {
        return o.isNull(key) ? null : o.optString(key, null);
    }
}
