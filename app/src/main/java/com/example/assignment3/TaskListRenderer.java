package com.example.assignment3;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fills the calendar's event panel for one day:
 * a section per category (in the order set in the Category dropdown), then "Completed".
 * Each task is a card; tapping it shows its checklist underneath.
 */
public class TaskListRenderer {

    public interface OnEdit { void edit(Task task); }
    public interface OnOpen { void open(Task task); }

    private static final int GREEN = 0xFF0F7B6C;
    private static final int BLUE = 0xFF0C66E4;

    private final Context ctx;
    private final LinearLayout container;
    private final Set<String> expanded;      // ids of cards showing their checklist
    private final OnEdit onEdit;
    private final float d;
    private LocalDate date;

    private final OnOpen onOpen;

    /** onOpen: tapping a card (shows the task's details). */
    public TaskListRenderer(LinearLayout container, Set<String> expanded, OnEdit onEdit, OnOpen onOpen) {
        this.onOpen = onOpen;
        this.ctx = container.getContext();
        this.container = container;
        this.expanded = expanded;
        this.onEdit = onEdit;
        this.d = ctx.getResources().getDisplayMetrics().density;
    }

    private Runnable onChanged = () -> { };

    /** Called after a task is ticked or deleted here (the calendar refreshes its dots). */
    public void setOnChanged(Runnable r) { onChanged = r; }

    public void show(LocalDate date) {
        this.date = date;
        render();
    }

    private void render() {
        container.removeAllViews();

        // The day's tasks: timed ones first (earliest first), then the rest by title
        List<Task> today = new ArrayList<>();
        for (Task t : Task.loadAll(ctx)) if (t.dueDate.equals(date)) today.add(t);
        today.sort(Comparator
                .comparing((Task t) -> t.dueTime == null ? LocalTime.MAX : t.dueTime)
                .thenComparing(t -> t.title.toLowerCase(Locale.ROOT)));

        if (today.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setBackgroundResource(R.drawable.bg_task_card);
            empty.setPadding(px(16), px(14), px(16), px(14));
            empty.setText("No items");
            empty.setTextColor(0xFF8A8A8A);
            container.addView(empty);
            return;
        }

        // Sections: categories in the user's order, then any old/deleted ones, then Completed
        List<Category> categories = Category.loadAll(ctx);
        Map<String, List<Task>> sections = new LinkedHashMap<>();
        Map<String, Integer> colors = new LinkedHashMap<>();
        for (Category c : categories) {
            sections.put(c.name, new ArrayList<>());
            colors.put(c.name, c.color);
        }
        List<Task> completed = new ArrayList<>();
        for (Task t : today) {
            if (t.isCompleted()) {
                completed.add(t);
                continue;
            }
            String key = t.category == null ? "Other" : t.category;
            Category match = Category.find(categories, key);
            if (match != null) key = match.name;
            if (!sections.containsKey(key)) {
                sections.put(key, new ArrayList<>());
                colors.put(key, Category.COLORS[0]);
            }
            sections.get(key).add(t);
        }

        for (Map.Entry<String, List<Task>> e : sections.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            addHeader(e.getKey(), darker(colors.get(e.getKey())));
            for (Task t : e.getValue()) container.addView(buildCard(t));
        }
        if (!completed.isEmpty()) {
            addHeader("Completed", GREEN);
            for (Task t : completed) container.addView(buildCard(t));
        }
    }

    private void addHeader(String name, int dotColor) {
        TextView h = new TextView(ctx);
        h.setText(name);
        h.setTextColor(0xFF000000);
        h.setTextSize(14);
        h.setTypeface(h.getTypeface(), android.graphics.Typeface.BOLD);
        h.setCompoundDrawablesRelativeWithIntrinsicBounds(dot(dotColor, 8), null, null, null);
        h.setCompoundDrawablePadding(px(6));
        h.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = container.getChildCount() == 0 ? 0 : px(10);
        lp.bottomMargin = px(6);
        h.setLayoutParams(lp);
        container.addView(h);
    }

    // ---------------------------------------------------------------------
    // Task card
    // ---------------------------------------------------------------------

    private View buildCard(Task t) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_task_card, container, false);
        View card = v.findViewById(R.id.task_card);
        card.setClipToOutline(true);   // keeps the top progress bar inside the rounded corners
        TextView check = v.findViewById(R.id.task_check);
        TextView title = v.findViewById(R.id.task_title);
        TextView classPill = v.findViewById(R.id.task_class);
        ProgressBar bar = v.findViewById(R.id.task_progress);
        // Every card's bar has the same id, so Android's "restore old screen state" (after
        // coming back from another page) would overwrite the real value - turn that off.
        bar.setSaveEnabled(false);
        TextView percent = v.findViewById(R.id.task_percent);
        TextView time = v.findViewById(R.id.task_time);
        View meta = v.findViewById(R.id.task_meta);
        TextView status = v.findViewById(R.id.task_status);
        View edit = v.findViewById(R.id.task_edit);
        View delete = v.findViewById(R.id.task_delete);
        View more = v.findViewById(R.id.task_more);
        android.widget.ImageButton toggle = v.findViewById(R.id.task_expand);
        LinearLayout checklistBox = v.findViewById(R.id.task_checklist);

        // Title (crossed out when completed)
        title.setText(t.title);
        strike(title, t.isCompleted());

        // Round check
        styleCheck(check, t.isCompleted());
        check.setContentDescription(t.isCompleted() ? "Mark as not completed" : "Mark as completed");
        check.setOnClickListener(x -> {
            update(t.id, task -> {
                if (task.isCompleted()) {
                    // un-tick: back to In Progress if some checklist work is done
                    task.status = task.checklistPercent() > 0 ? Task.IN_PROGRESS : Task.NOT_STARTED;
                } else {
                    task.status = Task.COMPLETED;
                }
            });
        });

        // Class pill (Academic)
        if (t.className != null) {
            Category c = Category.find(Category.loadAll(ctx, "classes", Category.defaults()), t.className);
            pill(classPill, t.className, c != null ? c.color : Category.COLORS[0]);
        } else {
            classPill.setVisibility(View.GONE);
        }

        // Checklist %
        int pct = t.checklistPercent();
        if (pct >= 0) {
            bar.setProgress(pct);
            bar.setProgressTintList(ColorStateList.valueOf(pct == 100 ? GREEN : BLUE));
            percent.setText(pct + "%");
        } else {
            bar.setVisibility(View.GONE);
            percent.setVisibility(View.GONE);
        }

        // Time
        if (t.dueTime != null) {
            String pattern = DateFormat.is24HourFormat(ctx) ? "HH:mm" : "h:mm a";
            time.setText(t.dueTime.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault())));
        } else {
            time.setVisibility(View.GONE);
        }
        if (t.className == null && pct < 0 && t.dueTime == null) meta.setVisibility(View.GONE);

        // Status pill (white, coloured dot) - same as Add Task
        statusPill(status, Task.STATUSES[t.status], Task.STATUS_COLORS[t.status]);

        // Edit + Delete buttons: show while the mouse is over the card (or any part of it),
        // or after a long-press on touch screens
        edit.setOnClickListener(x -> onEdit.edit(t));
        delete.setOnClickListener(x -> confirmDelete(t));

        // "..." is always there (touch screens and the emulator can't hover): Edit / Delete
        more.setOnClickListener(x -> {
            android.widget.PopupMenu menu = new android.widget.PopupMenu(ctx, more);
            menu.getMenu().add(0, 1, 0, "Edit");
            menu.getMenu().add(0, 2, 1, "Delete");
            menu.setOnMenuItemClickListener(mi -> {
                if (mi.getItemId() == 1) onEdit.edit(t); else confirmDelete(t);
                return true;
            });
            menu.show();
        });

        // With a real mouse, hovering swaps "..." for the edit + delete icons
        View[] hoverParts = {card, check, edit, delete, more, toggle};
        Runnable hideIfLeft = () -> {
            for (View p : hoverParts) if (p.isHovered()) return;   // still over the card
            setActions(edit, delete, more, false);
        };
        View.OnHoverListener hover = (x, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_HOVER_ENTER || a == MotionEvent.ACTION_HOVER_MOVE) {
                setActions(edit, delete, more, true);
            } else if (a == MotionEvent.ACTION_HOVER_EXIT) {
                card.postDelayed(hideIfLeft, 80);   // moving onto a child also "exits" the card
            }
            return false;
        };
        for (View p : hoverParts) p.setOnHoverListener(hover);
        card.setOnLongClickListener(x -> {
            setActions(edit, delete, more, edit.getVisibility() != View.VISIBLE);
            return true;
        });

        // Tap the card: open its details page
        card.setOnClickListener(x -> onOpen.open(t));

        // Toggle (only when there's a checklist): show / hide the checklist underneath
        if (!t.checklist.isEmpty()) {
            fillChecklist(checklistBox, t);
            toggle.setVisibility(View.VISIBLE);
            showExpanded(toggle, checklistBox, expanded.contains(t.id));
            toggle.setOnClickListener(x -> {
                if (expanded.contains(t.id)) expanded.remove(t.id); else expanded.add(t.id);
                showExpanded(toggle, checklistBox, expanded.contains(t.id));
            });
        } else {
            toggle.setVisibility(View.GONE);
        }
        return v;
    }

    /** Indented cards for the checklist items; ticking one updates the % right away. */
    private void fillChecklist(LinearLayout box, Task t) {
        for (int i = 0; i < t.checklist.size(); i++) {
            ChecklistController.Item item = t.checklist.get(i);
            final int index = i;

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_task_card);
            row.setPadding(px(10), px(8), px(10), px(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = px(4);
            row.setLayoutParams(lp);

            TextView check = new TextView(ctx);
            check.setLayoutParams(new LinearLayout.LayoutParams(px(20), px(20)));
            check.setGravity(Gravity.CENTER);
            check.setTextColor(0xFFFFFFFF);
            check.setTextSize(12);
            styleCheck(check, item.done);
            row.addView(check);

            LinearLayout texts = new LinearLayout(ctx);
            texts.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tlp.setMarginStart(px(10));
            texts.setLayoutParams(tlp);

            TextView text = new TextView(ctx);
            text.setText(item.text);
            text.setTextSize(14);
            strike(text, item.done);
            texts.addView(text);

            List<String> parts = new ArrayList<>();
            if (item.assignee != null) parts.add("👤 " + item.assignee);
            if (item.date != null) {
                String s = item.date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()));
                if (item.time != null) {
                    String p = DateFormat.is24HourFormat(ctx) ? "HH:mm" : "h:mm a";
                    s += ", " + item.time.format(DateTimeFormatter.ofPattern(p, Locale.getDefault()));
                }
                parts.add("⏰ " + s);
            }
            if (!parts.isEmpty()) {
                TextView meta = new TextView(ctx);
                meta.setText(String.join("   ", parts));
                meta.setTextSize(11);
                meta.setTextColor(0xFF626F86);
                texts.addView(meta);
            }
            row.addView(texts);

            // status pill on each checklist card, like the mockup
            TextView itemStatus = new TextView(ctx);
            int st = item.done ? Task.COMPLETED : Task.NOT_STARTED;
            statusPill(itemStatus, Task.STATUSES[st], Task.STATUS_COLORS[st]);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            slp.setMarginStart(px(6));
            itemStatus.setLayoutParams(slp);
            row.addView(itemStatus);

            View.OnClickListener toggle = x -> update(t.id, task -> {
                ChecklistController.Item it = task.checklist.get(index);
                it.done = !it.done;
                // starting the checklist moves a Not Started task to In Progress
                if (task.status == Task.NOT_STARTED && task.checklistPercent() > 0) {
                    task.status = Task.IN_PROGRESS;
                }
            });
            check.setOnClickListener(toggle);
            row.setOnClickListener(toggle);
            box.addView(row);
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /** Arrow points down when the checklist is open, right when closed. */
    private static void showExpanded(android.widget.ImageButton toggle, View box, boolean open) {
        box.setVisibility(open ? View.VISIBLE : View.GONE);
        toggle.setRotation(open ? 90f : 0f);
        toggle.setContentDescription(open ? "Hide checklist" : "Show checklist");
    }

    /** Show the edit + delete icons (hiding "..."), or go back to just "...". */
    private static void setActions(View edit, View delete, View more, boolean show) {
        int vis = show ? View.VISIBLE : View.GONE;
        edit.setVisibility(vis);
        delete.setVisibility(vis);
        more.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void confirmDelete(Task t) {
        new androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setTitle("Delete task?")
                .setMessage("\u201C" + t.title + "\u201D will be removed.")
                .setPositiveButton("Delete", (dlg, w) -> {
                    Task.delete(ctx, t.id);
                    expanded.remove(t.id);
                    render();
                    onChanged.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private interface Change { void apply(Task t); }

    /** Load -> change one task -> save -> redraw the panel. */
    private void update(String id, Change change) {
        List<Task> all = Task.loadAll(ctx);
        for (Task t : all) if (t.id.equals(id)) change.apply(t);
        Task.saveAll(ctx, all);
        render();
        onChanged.run();
    }

    private void styleCheck(TextView check, boolean done) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(done ? GREEN : 0xFFD9D9D9);
        check.setBackground(g);
        check.setText(done ? "✓" : "");
    }

    private static void strike(TextView tv, boolean on) {
        if (on) {
            tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            tv.setTextColor(0xFF8A8A8A);
        } else {
            tv.setPaintFlags(tv.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            tv.setTextColor(0xFF000000);
        }
    }

    /** Filled colour pill (Category style). */
    private void pill(TextView tv, String text, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(px(50));
        tv.setBackground(g);
        tv.setText(text);
        tv.setTextColor(Category.TEXT_COLOR);
        tv.setPadding(px(8), px(1), px(8), px(1));
    }

    /** White pill with a coloured dot (Status style). */
    private void statusPill(TextView tv, String text, int dotColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFFFFFFFF);
        g.setStroke(px(1), 0xFFE0E0E0);
        g.setCornerRadius(px(50));
        tv.setBackground(g);
        tv.setText(text);
        tv.setTextColor(0xFF000000);
        tv.setTextSize(11);
        tv.setCompoundDrawablesRelativeWithIntrinsicBounds(dot(dotColor, 7), null, null, null);
        tv.setCompoundDrawablePadding(px(5));
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(px(8), px(2), px(8), px(2));
    }

    private GradientDrawable dot(int color, int sizeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        g.setSize(px(sizeDp), px(sizeDp));
        return g;
    }

    /** Stronger version of a soft category colour, so the header dot shows on gray. */
    static int darker(int color) {
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(color, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 3f);
        hsv[2] = hsv[2] * 0.75f;
        return android.graphics.Color.HSVToColor(hsv);
    }

    private int px(int dp) { return (int) (dp * d); }
}
