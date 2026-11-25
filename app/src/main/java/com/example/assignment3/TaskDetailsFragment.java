package com.example.assignment3;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Task details (tap a card on the calendar): title, status, due date,
 * then only the fields that have something in them.
 */
public class TaskDetailsFragment extends Fragment {

    public static final String ARG_TASK_ID = "taskId";

    private static final String[] PRIORITIES = {"High", "Medium", "Low", "None"};
    private static final int[] PRIORITY_COLORS = {0xFFE03E3E, 0xFFDFAB01, 0xFF337EA9, 0xFF9E9E9E};
    private static final String[] UNIT_ONE = {"minute", "hour", "day", "week"};
    private static final String[] UNIT_MANY = {"minutes", "hours", "days", "weeks"};
    private static final int GREEN = 0xFF0F7B6C;
    private static final int BLUE = 0xFF0C66E4;

    private String taskId;
    private Task task;
    private View root;
    private float d;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_task_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        root = view;
        d = getResources().getDisplayMetrics().density;
        taskId = getArguments() == null ? null : getArguments().getString(ARG_TASK_ID);

        view.findViewById(R.id.details_back).setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());

        // Edit: same Add Task screen, filled in
        view.findViewById(R.id.details_edit).setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString(AddTaskFragment.ARG_TASK_ID, taskId);
            Navigation.findNavController(v).navigate(R.id.addTaskFragment, args);
        });

        // DEL: ask first, then go back to the calendar
        view.findViewById(R.id.details_delete).setOnClickListener(v ->
                new AlertDialog.Builder(requireContext())
                        .setTitle("Delete task?")
                        .setMessage("“" + task.title + "” will be removed.")
                        .setPositiveButton("Delete", (dlg, w) -> {
                            Task.delete(requireContext(), taskId);
                            Navigation.findNavController(view).navigateUp();
                        })
                        .setNegativeButton("Cancel", null)
                        .show());

        render();
    }

    private void render() {
        task = Task.find(requireContext(), taskId);
        if (task == null) {   // deleted in the meantime
            Navigation.findNavController(root).navigateUp();
            return;
        }

        ((TextView) root.findViewById(R.id.details_title)).setText(task.title);

        TextView status = root.findViewById(R.id.details_status);
        statusPill(status, Task.STATUSES[task.status], Task.STATUS_COLORS[task.status]);
        status.setOnClickListener(v -> showStatusMenu(status));

        ((TextView) root.findViewById(R.id.details_due)).setText(dueText());

        LinearLayout fields = root.findViewById(R.id.details_fields);
        fields.removeAllViews();
        Context ctx = requireContext();

        // --- simple rows: label on the left, value on the right ---
        if (task.category != null) {
            Category c = Category.find(Category.loadAll(ctx), task.category);
            addRow(fields, "Category", colourPill(task.category, c != null ? c.color : Category.COLORS[0]));
        }
        if (task.className != null) {
            Category c = Category.find(Category.loadAll(ctx, "classes", Category.defaults()), task.className);
            addRow(fields, "Class", colourPill(task.className, c != null ? c.color : Category.COLORS[0]));
        }
        if (task.courseworkType != null) {
            Category c = Category.find(Category.loadAll(ctx, "coursework_types", Category.defaults()),
                    task.courseworkType);
            addRow(fields, "Coursework Type",
                    colourPill(task.courseworkType, c != null ? c.color : Category.COLORS[0]));
        }
        if (!task.weight.isEmpty()) addRow(fields, "Weight", text(task.weight + "%"));
        if (task.priority != 3) {
            TextView p = new TextView(ctx);
            statusPill(p, PRIORITIES[task.priority], PRIORITY_COLORS[task.priority]);
            addRow(fields, "Priority", p);
        }
        if (!task.collaborators.isEmpty()) {
            addRow(fields, "Collaboration", text(String.join("\n", task.collaborators)));
        }
        if (!task.location.isEmpty()) addRow(fields, "Location", text(task.location));
        if (task.repeat.type != RepeatRule.NONE) {
            addRow(fields, "Repeat", text(task.repeat.summary(task.dueDate)));
        }
        if (task.notifyAmount > 0) {
            String unit = task.notifyAmount == 1 ? UNIT_ONE[task.notifyUnit] : UNIT_MANY[task.notifyUnit];
            addRow(fields, "Notification", text(task.notifyAmount + " " + unit + " before"));
        }

        // --- bigger sections ---
        if (!task.description.isEmpty()) {
            addHeader(fields, "Description");
            TextView desc = text(task.description);
            desc.setGravity(Gravity.START);
            desc.setPadding(px(6), 0, px(6), 0);
            fields.addView(desc);
        }
        if (!task.checklist.isEmpty()) addChecklist(fields);
        if (!task.attachments.isEmpty()) addAttachments(fields);
    }

    // ---------------------------------------------------------------------
    // Sections
    // ---------------------------------------------------------------------

    /** Progress bar + items; ticking an item saves straight away. */
    private void addChecklist(LinearLayout fields) {
        int pct = task.checklistPercent();
        addHeader(fields, "Checklist  ·  " + pct + "%");

        ProgressBar bar = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(pct);
        bar.setProgressDrawable(requireContext().getDrawable(R.drawable.progress_checklist));
        bar.setProgressTintList(ColorStateList.valueOf(pct == 100 ? GREEN : BLUE));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(8));
        blp.setMargins(px(6), 0, px(6), px(6));
        bar.setLayoutParams(blp);
        fields.addView(bar);

        for (int i = 0; i < task.checklist.size(); i++) {
            ChecklistController.Item item = task.checklist.get(i);
            final int index = i;

            LinearLayout row = card();
            TextView check = new TextView(requireContext());
            check.setLayoutParams(new LinearLayout.LayoutParams(px(20), px(20)));
            check.setGravity(Gravity.CENTER);
            check.setTextColor(0xFFFFFFFF);
            check.setTextSize(12);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(item.done ? GREEN : 0xFFD9D9D9);
            check.setBackground(g);
            check.setText(item.done ? "✓" : "");
            row.addView(check);

            LinearLayout texts = new LinearLayout(requireContext());
            texts.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tlp.setMarginStart(px(10));
            texts.setLayoutParams(tlp);
            TextView t = new TextView(requireContext());
            t.setText(item.text);
            t.setTextSize(14);
            t.setTextColor(item.done ? 0xFF8A8A8A : 0xFF000000);
            if (item.done) t.setPaintFlags(t.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            texts.addView(t);

            List<String> parts = new ArrayList<>();
            if (item.assignee != null) parts.add("👤 " + item.assignee);
            if (item.date != null) parts.add("⏰ " + formatDate(item.date, item.time, "MMM d"));
            if (!parts.isEmpty()) {
                TextView meta = new TextView(requireContext());
                meta.setText(String.join("   ", parts));
                meta.setTextSize(11);
                meta.setTextColor(0xFF626F86);
                texts.addView(meta);
            }
            row.addView(texts);

            row.setOnClickListener(v -> save(saved -> {
                ChecklistController.Item it = saved.checklist.get(index);
                it.done = !it.done;
                if (saved.status == Task.NOT_STARTED && saved.checklistPercent() > 0) {
                    saved.status = Task.IN_PROGRESS;
                }
            }));
            fields.addView(row);
        }
    }

    /** File rows; tap one to open it. */
    private void addAttachments(LinearLayout fields) {
        addHeader(fields, "Attachments");
        for (String s : task.attachments) {
            Uri uri = Uri.parse(s);
            LinearLayout row = card();
            android.widget.ImageView icon = new android.widget.ImageView(requireContext());
            icon.setImageResource(R.drawable.ic_attach);
            row.addView(icon);
            TextView name = text(fileName(uri));
            name.setGravity(Gravity.START);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            nlp.setMarginStart(px(8));
            name.setLayoutParams(nlp);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            row.addView(name);
            row.setOnClickListener(v -> {
                Intent open = new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, requireContext().getContentResolver().getType(uri))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivity(open);
                } catch (ActivityNotFoundException | SecurityException e) {
                    Toast.makeText(requireContext(), "Can't open this file", Toast.LENGTH_SHORT).show();
                }
            });
            fields.addView(row);
        }
    }

    // ---------------------------------------------------------------------
    // Status
    // ---------------------------------------------------------------------

    private void showStatusMenu(TextView anchor) {
        ListPopupWindow popup = new ListPopupWindow(requireContext());
        popup.setAnchorView(anchor);
        popup.setWidth(px(170));
        popup.setModal(true);
        popup.setAdapter(new ArrayAdapter<String>(requireContext(), 0, Task.STATUSES) {
            @NonNull
            @Override
            public View getView(int pos, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView tv = new TextView(getContext());
                tv.setPadding(px(14), px(12), px(14), px(12));
                tv.setTextSize(14);
                tv.setTextColor(0xFF000000);
                tv.setText(Task.STATUSES[pos] + (pos == task.status ? "   ✓" : ""));
                tv.setCompoundDrawablesRelativeWithIntrinsicBounds(dot(Task.STATUS_COLORS[pos]), null, null, null);
                tv.setCompoundDrawablePadding(px(10));
                return tv;
            }
        });
        popup.setOnItemClickListener((p, v, pos, id) -> {
            popup.dismiss();
            save(saved -> saved.status = pos);
        });
        popup.show();
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private interface Change { void apply(Task t); }

    /** Load all -> change this task -> save -> redraw. */
    private void save(Change change) {
        List<Task> all = Task.loadAll(requireContext());
        for (Task t : all) if (t.id.equals(taskId)) change.apply(t);
        Task.saveAll(requireContext(), all);
        render();
    }

    /** "Due today" or "Due Wed, Nov 26, 2026", plus " at 3:00 PM" when a time is set. */
    private String dueText() {
        String day = task.dueDate.equals(LocalDate.now())
                ? "Due today"
                : "Due " + task.dueDate.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()));
        return task.dueTime == null ? day : day + " at " + formatTime(task.dueTime);
    }

    private String formatDate(LocalDate date, @Nullable LocalTime time, String pattern) {
        String s = date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()));
        return time == null ? s : s + ", " + formatTime(time);
    }

    private String formatTime(LocalTime time) {
        String p = DateFormat.is24HourFormat(requireContext()) ? "HH:mm" : "h:mm a";
        return time.format(DateTimeFormatter.ofPattern(p, Locale.getDefault()));
    }

    private String fileName(Uri uri) {
        String name = uri.getLastPathSegment();
        try (Cursor c = requireContext().getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) name = c.getString(0);
        } catch (Exception ignored) { }
        return name;
    }

    /** "Label ............ value" row, like the Add Task screen. */
    private void addRow(LinearLayout parent, String label, View value) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(6), px(5), px(6), px(5));
        TextView l = new TextView(requireContext());
        l.setText(label);
        l.setTextSize(14);
        l.setTextColor(0xFF000000);
        l.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(l);
        row.addView(value);
        parent.addView(row);
    }

    private void addHeader(LinearLayout parent, String title) {
        View line = new View(requireContext());
        line.setBackgroundColor(0xFF000000);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(1));
        llp.setMargins(0, px(10), 0, px(8));
        line.setLayoutParams(llp);
        parent.addView(line);

        TextView h = new TextView(requireContext());
        h.setText(title);
        h.setTextSize(15);
        h.setTextColor(0xFF000000);
        h.setTypeface(h.getTypeface(), Typeface.BOLD);
        h.setPadding(px(6), 0, px(6), px(6));
        parent.addView(h);
    }

    private TextView text(String s) {
        TextView tv = new TextView(requireContext());
        tv.setText(s);
        tv.setTextSize(14);
        tv.setTextColor(0xFF000000);
        tv.setGravity(Gravity.END);
        return tv;
    }

    private LinearLayout card() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_task_card);
        row.setPadding(px(10), px(8), px(10), px(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = px(5);
        row.setLayoutParams(lp);
        return row;
    }

    /** Filled colour pill (Category / Class / Coursework). */
    private TextView colourPill(String label, int color) {
        TextView tv = new TextView(requireContext());
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(px(50));
        tv.setBackground(g);
        tv.setText(label);
        tv.setTextSize(13);
        tv.setTextColor(Category.TEXT_COLOR);
        tv.setPadding(px(10), px(2), px(10), px(2));
        return tv;
    }

    /** White pill with a coloured dot (Status / Priority). */
    private void statusPill(TextView tv, String label, int dotColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFFFFFFFF);
        g.setCornerRadius(px(50));
        tv.setBackground(g);
        tv.setText(label);
        tv.setTextSize(11);
        tv.setTextColor(0xFF000000);
        tv.setCompoundDrawablesRelativeWithIntrinsicBounds(dot(dotColor), null, null, null);
        tv.setCompoundDrawablePadding(px(6));
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(px(10), px(3), px(10), px(3));
    }

    private GradientDrawable dot(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        g.setSize(px(7), px(7));
        return g;
    }

    private int px(int dp) { return (int) (dp * d); }
}
