package com.example.assignment3;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Trello-style checklist on the Add Task screen:
 * progress bar, list of items, and an "Add an item" box with Assign + Due date.
 * People you can assign come from the task's collaborators.
 */
public class ChecklistController {

    /** One checklist item. */
    static class Item {
        String text;
        boolean done;
        @Nullable String assignee;      // a collaborator's email
        @Nullable LocalDate date;
        @Nullable LocalTime time;

        Item(String text) { this.text = text; }
    }

    private static final int BLUE = 0xFF0C66E4;
    private static final int GREEN = 0xFF1F845A;

    private final Context ctx;
    private final Supplier<List<String>> collaborators;
    private final List<Item> items = new ArrayList<>();

    private final View section;
    private final TextView percentText;
    private final ProgressBar progress;
    private final LinearLayout itemsContainer;
    private final View addArea;
    private final EditText input;
    private final TextView assignButton;
    private final TextView dueButton;
    private final View showAddButton;

    // what the next new item will get
    @Nullable private String pendingAssignee;
    @Nullable private LocalDate pendingDate;
    @Nullable private LocalTime pendingTime;

    public ChecklistController(View root, Supplier<List<String>> collaborators) {
        this.ctx = root.getContext();
        this.collaborators = collaborators;

        section = root.findViewById(R.id.checklist_section);
        percentText = root.findViewById(R.id.checklist_percent);
        progress = root.findViewById(R.id.checklist_progress);
        itemsContainer = root.findViewById(R.id.checklist_items);
        addArea = root.findViewById(R.id.checklist_add_area);
        input = root.findViewById(R.id.checklist_input);
        assignButton = root.findViewById(R.id.checklist_assign);
        dueButton = root.findViewById(R.id.checklist_due);
        showAddButton = root.findViewById(R.id.checklist_show_add);

        root.findViewById(R.id.checklist_add).setOnClickListener(v -> addItem());
        root.findViewById(R.id.checklist_cancel).setOnClickListener(v -> showAddArea(false));
        showAddButton.setOnClickListener(v -> showAddArea(true));

        assignButton.setOnClickListener(v -> pickAssignee(pendingAssignee, email -> {
            pendingAssignee = email;
            updatePendingButtons();
        }));
        dueButton.setOnClickListener(v -> pickDue(pendingDate, pendingTime, (date, time) -> {
            pendingDate = date;
            pendingTime = time;
            updatePendingButtons();
        }));

        root.findViewById(R.id.checklist_delete).setOnClickListener(v ->
                new AlertDialog.Builder(ctx)
                        .setTitle("Delete checklist?")
                        .setMessage("All checklist items will be removed.")
                        .setPositiveButton("Delete", (d, w) -> {
                            items.clear();
                            render();
                            section.setVisibility(View.GONE);
                        })
                        .setNegativeButton("Cancel", null)
                        .show());

        render();
    }

    /** Called when the Checklist button is tapped. */
    public void open() {
        section.setVisibility(View.VISIBLE);
        showAddArea(true);
    }

    /** The current items (saved with the task). */
    public List<Item> getItems() {
        return new ArrayList<>(items);
    }

    /** Loads a saved task's items (when editing). */
    public void setItems(List<Item> saved) {
        items.clear();
        items.addAll(saved);
        if (!items.isEmpty()) {
            section.setVisibility(View.VISIBLE);
            showAddArea(false);
        }
        render();
    }

    /** If a collaborator is removed, their checklist items become unassigned. */
    public void onCollaboratorRemoved(String email) {
        for (Item it : items) if (email.equals(it.assignee)) it.assignee = null;
        if (email.equals(pendingAssignee)) pendingAssignee = null;
        updatePendingButtons();
        render();
    }

    // ---------------------------------------------------------------------
    // Adding items
    // ---------------------------------------------------------------------

    private void showAddArea(boolean show) {
        addArea.setVisibility(show ? View.VISIBLE : View.GONE);
        showAddButton.setVisibility(show ? View.GONE : View.VISIBLE);
        InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (show) {
            input.requestFocus();
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        } else {
            input.setText("");
            pendingAssignee = null;
            pendingDate = null;
            pendingTime = null;
            updatePendingButtons();
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }

    private void addItem() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            input.requestFocus();
            return;
        }
        Item item = new Item(text);
        item.assignee = pendingAssignee;
        item.date = pendingDate;
        item.time = pendingTime;
        items.add(item);

        // keep the box open for the next item, like Trello
        input.setText("");
        pendingAssignee = null;
        pendingDate = null;
        pendingTime = null;
        updatePendingButtons();
        render();
    }

    private void updatePendingButtons() {
        assignButton.setText(pendingAssignee == null ? "Assign" : shortName(pendingAssignee));
        assignButton.setTextColor(pendingAssignee == null ? 0xFF44546F : BLUE);
        dueButton.setText(pendingDate == null ? "Due date" : formatDue(pendingDate, pendingTime));
        dueButton.setTextColor(pendingDate == null ? 0xFF44546F : BLUE);
    }

    // ---------------------------------------------------------------------
    // Pickers
    // ---------------------------------------------------------------------

    /** Who "Me" is stored as on an item. */
    static final String ME = "Me";

    /** Choose Unassigned, Me, or one of the task's collaborators. */
    private void pickAssignee(@Nullable String current, Consumer<String> onPick) {
        List<String> people = new ArrayList<>();
        people.add(ME);
        people.addAll(collaborators.get());

        String[] options = new String[people.size() + 1];
        options[0] = "Unassigned";
        int checked = 0;
        for (int i = 0; i < people.size(); i++) {
            options[i + 1] = people.get(i);
            if (people.get(i).equals(current)) checked = i + 1;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx)
                .setTitle("Assign to")
                .setSingleChoiceItems(options, checked, (d, which) -> {
                    onPick.accept(which == 0 ? null : people.get(which - 1));
                    d.dismiss();
                })
                .setNegativeButton("Cancel", null);
        if (people.size() == 1) {
            // only "Me" so far - hint where other people come from
            Toast.makeText(ctx, "Add emails in Collaboration to assign others",
                    Toast.LENGTH_SHORT).show();
        }
        builder.show();
    }

    interface DueListener { void onPick(@Nullable LocalDate date, @Nullable LocalTime time); }

    /** Pick a date, then a time (cancel the time to keep just the date). */
    private void pickDue(@Nullable LocalDate curDate, @Nullable LocalTime curTime, DueListener onPick) {
        LocalDate start = curDate != null ? curDate : LocalDate.now();
        new DatePickerDialog(ctx, (picker, y, m, d) -> {
            LocalDate date = LocalDate.of(y, m + 1, d);
            onPick.onPick(date, curTime);
            LocalTime t = curTime != null ? curTime : LocalTime.of(12, 0);
            new TimePickerDialog(ctx, (tp, h, min) -> onPick.onPick(date, LocalTime.of(h, min)),
                    t.getHour(), t.getMinute(), DateFormat.is24HourFormat(ctx)).show();
        }, start.getYear(), start.getMonthValue() - 1, start.getDayOfMonth()).show();
    }

    // ---------------------------------------------------------------------
    // Drawing the list + progress bar
    // ---------------------------------------------------------------------

    private void render() {
        itemsContainer.removeAllViews();
        for (Item item : items) itemsContainer.addView(buildRow(item));
        updateProgress();
    }

    private void updateProgress() {
        int done = 0;
        for (Item i : items) if (i.done) done++;
        int pct = items.isEmpty() ? 0 : Math.round(done * 100f / items.size());
        percentText.setText(pct + "%");
        progress.setProgress(pct);
        // Trello turns the bar green when everything is done
        progress.setProgressTintList(ColorStateList.valueOf(pct == 100 ? GREEN : BLUE));
    }

    private View buildRow(Item item) {
        float d = ctx.getResources().getDisplayMetrics().density;

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) (2 * d), 0, (int) (2 * d));

        CheckBox box = new CheckBox(ctx);
        box.setChecked(item.done);
        box.setContentDescription("Done");
        row.addView(box);

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(ctx);
        title.setText(item.text);
        title.setTextSize(14);
        texts.addView(title);

        TextView meta = new TextView(ctx);
        meta.setTextSize(11);
        meta.setTextColor(0xFF626F86);
        String metaText = metaFor(item);
        meta.setText(metaText);
        meta.setVisibility(metaText.isEmpty() ? View.GONE : View.VISIBLE);
        texts.addView(meta);
        row.addView(texts);

        ImageButton more = new ImageButton(ctx);
        more.setImageResource(R.drawable.ic_more_horiz);
        more.setBackgroundResource(android.R.color.transparent);
        more.setContentDescription("Item options");
        more.setLayoutParams(new LinearLayout.LayoutParams((int) (36 * d), (int) (32 * d)));
        row.addView(more);

        styleDone(title, item.done);
        box.setOnCheckedChangeListener((b, checked) -> {
            item.done = checked;
            styleDone(title, checked);
            updateProgress();
        });

        more.setOnClickListener(v -> showItemMenu(v, item));
        texts.setOnClickListener(v -> showItemMenu(more, item));
        return row;
    }

    private void showItemMenu(View anchor, Item item) {
        PopupMenu menu = new PopupMenu(ctx, anchor);
        menu.getMenu().add(0, 1, 0, "Assign…");
        menu.getMenu().add(0, 2, 1, "Due date…");
        if (item.date != null) menu.getMenu().add(0, 3, 2, "Remove due date");
        menu.getMenu().add(0, 4, 3, "Delete");
        menu.setOnMenuItemClickListener(mi -> {
            switch (mi.getItemId()) {
                case 1:
                    pickAssignee(item.assignee, email -> { item.assignee = email; render(); });
                    return true;
                case 2:
                    pickDue(item.date, item.time, (date, time) -> {
                        item.date = date;
                        item.time = time;
                        render();
                    });
                    return true;
                case 3:
                    item.date = null;
                    item.time = null;
                    render();
                    return true;
                case 4:
                    items.remove(item);
                    render();
                    return true;
            }
            return false;
        });
        menu.show();
    }

    private static void styleDone(TextView title, boolean done) {
        if (done) {
            title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            title.setTextColor(0xFF8A8A8A);
        } else {
            title.setPaintFlags(title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            title.setTextColor(0xFF172B4D);
        }
    }

    // ---------------------------------------------------------------------
    // Text helpers
    // ---------------------------------------------------------------------

    private String metaFor(Item item) {
        List<String> parts = new ArrayList<>();
        if (item.assignee != null) parts.add("👤 " + item.assignee);   // 👤
        if (item.date != null) parts.add("⏰ " + formatDue(item.date, item.time)); // ⏰
        return String.join("   ", parts);
    }

    private String formatDue(LocalDate date, @Nullable LocalTime time) {
        String s = date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()));
        if (time != null) {
            String pattern = DateFormat.is24HourFormat(ctx) ? "HH:mm" : "h:mm a";
            s += ", " + time.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()));
        }
        return s;
    }

    /** "sam.lee@gmail.com" -> "sam.lee" for the small Assign button. */
    private static String shortName(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
