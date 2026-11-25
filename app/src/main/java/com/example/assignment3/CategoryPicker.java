package com.example.assignment3;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Notion-style single-select dropdown for Category.
 *  - tap an option to select it (only one at a time)
 *  - drag the handle to reorder
 *  - "..." to rename / recolour / delete
 *  - type something new + Enter (or tap "Create") to add an option
 */
public class CategoryPicker {

    public interface Listener {
        void onChanged(@Nullable Category selected);
    }

    /** The Category field. */
    public static void show(Context ctx, @Nullable String selectedName, Listener listener) {
        new CategoryPicker(ctx, null, null, selectedName, listener).open();
    }

    /**
     * Same dropdown for another field (e.g. Class, Coursework Type).
     * Each field keeps its own options under {@code storeKey}.
     */
    public static void show(Context ctx, String storeKey, List<Category> defaults,
                            @Nullable String selectedName, Listener listener) {
        new CategoryPicker(ctx, storeKey, defaults, selectedName, listener).open();
    }

    private final Context ctx;
    private final Listener listener;
    private final List<Category> all;              // saved order
    private final List<Category> shown = new ArrayList<>();  // after search filter
    private String selectedName;

    private AlertDialog dialog;
    private EditText search;
    private TextView selectedChip;
    private TextView createRow;
    private OptionAdapter adapter;
    private ItemTouchHelper touchHelper;

    @Nullable private final String storeKey;   // null = the Category field

    private CategoryPicker(Context ctx, @Nullable String storeKey, @Nullable List<Category> defaults,
                           @Nullable String selectedName, Listener listener) {
        this.ctx = ctx;
        this.storeKey = storeKey;
        this.selectedName = selectedName;
        this.listener = listener;
        this.all = storeKey == null
                ? Category.loadAll(ctx)
                : Category.loadAll(ctx, storeKey, defaults == null ? new ArrayList<>() : defaults);
    }

    private void save() {
        if (storeKey == null) Category.saveAll(ctx, all);
        else Category.saveAll(ctx, storeKey, all);
    }

    private void open() {
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_category_picker, null);
        search = v.findViewById(R.id.category_search);
        selectedChip = v.findViewById(R.id.selected_chip);
        createRow = v.findViewById(R.id.create_row);
        RecyclerView list = v.findViewById(R.id.category_list);

        adapter = new OptionAdapter();
        list.setLayoutManager(new LinearLayoutManager(ctx));
        list.setAdapter(adapter);

        // Drag to reorder (started from the handle only)
        touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean isLongPressDragEnabled() { return false; }

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                int a = from.getBindingAdapterPosition();
                int b = to.getBindingAdapterPosition();
                Collections.swap(shown, a, b);
                Collections.swap(all, a, b);   // drag is only allowed when not searching, so same order
                adapter.notifyItemMoved(a, b);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) { }

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                save();    // remember the new order
            }
        });
        touchHelper.attachToRecyclerView(list);

        // Typing filters the list; text that isn't an option can be created
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { refresh(); }
        });
        search.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                createOrSelect(search.getText().toString());
                return true;
            }
            return false;
        });

        // Tap the selected chip to clear the selection
        selectedChip.setOnClickListener(x -> {
            selectedName = null;
            updateSelectedChip();
        });

        // Bottom row: "+ Add an option" or "+ Create "xyz""
        createRow.setOnClickListener(x -> {
            String text = search.getText().toString().trim();
            if (text.isEmpty()) {
                search.setHint("Type a name for the new option");
                search.requestFocus();
                InputMethodManager imm =
                        (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
            } else {
                createOrSelect(text);
            }
        });

        dialog = new AlertDialog.Builder(ctx).setView(v).create();
        dialog.setOnDismissListener(d -> listener.onChanged(Category.find(all, selectedName)));
        dialog.show();

        refresh();
        updateSelectedChip();
    }

    private void refresh() {
        String q = search.getText().toString().trim();
        String lower = q.toLowerCase(Locale.ROOT);
        shown.clear();
        for (Category c : all) {
            if (lower.isEmpty() || c.name.toLowerCase(Locale.ROOT).contains(lower)) shown.add(c);
        }
        adapter.notifyDataSetChanged();

        if (q.isEmpty()) {
            createRow.setVisibility(View.VISIBLE);
            createRow.setText("+  Add an option");
        } else if (Category.find(all, q) != null) {
            createRow.setVisibility(View.GONE);     // already exists, just tap it
        } else {
            createRow.setVisibility(View.VISIBLE);
            createRow.setText("+  Create “" + q + "”");
        }
    }

    private void updateSelectedChip() {
        Category c = Category.find(all, selectedName);
        if (c == null) {
            selectedChip.setVisibility(View.GONE);
        } else {
            Category.stylePill(selectedChip, c);
            selectedChip.setText(c.name + "  ✕");   // ✕
            selectedChip.setVisibility(View.VISIBLE);
        }
    }

    private void createOrSelect(String raw) {
        String text = raw.trim();
        if (text.isEmpty()) return;
        Category c = Category.find(all, text);
        if (c == null) {
            c = new Category(text, Category.COLORS[all.size() % Category.COLORS.length]);
            all.add(c);                              // new options go at the bottom
            save();
        }
        select(c);
    }

    private void select(Category c) {
        selectedName = c.name;
        dialog.dismiss();                            // single select: close after picking
    }

    // ----- "..." editor: rename, recolour, delete -----

    private void openEditor(Category c) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_category_edit, null);
        EditText nameInput = v.findViewById(R.id.edit_name);
        LinearLayout swatchRow = v.findViewById(R.id.swatch_row);
        nameInput.setText(c.name);
        nameInput.setSelection(c.name.length());

        final int[] chosen = {c.color};
        float d = ctx.getResources().getDisplayMetrics().density;
        List<View> swatches = new ArrayList<>();
        for (int i = 0; i < Category.COLORS.length; i++) {
            int color = Category.COLORS[i];
            View s = new View(ctx);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (34 * d), (int) (34 * d));
            lp.setMarginEnd((int) (8 * d));
            s.setLayoutParams(lp);
            s.setContentDescription(Category.COLOR_NAMES[i]);
            s.setOnClickListener(x -> {
                chosen[0] = color;
                drawSwatches(swatches, chosen[0], d);
            });
            swatches.add(s);
            swatchRow.addView(s);
        }
        drawSwatches(swatches, chosen[0], d);

        AlertDialog editor = new AlertDialog.Builder(ctx)
                .setTitle("Edit option")
                .setView(v)
                .setPositiveButton("Save", null)     // set below so errors keep it open
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Delete", (dlg, w) -> {
                    all.remove(c);
                    if (c.name.equalsIgnoreCase(String.valueOf(selectedName))) selectedName = null;
                    save();
                    refresh();
                    updateSelectedChip();
                })
                .create();
        editor.show();

        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String newName = nameInput.getText().toString().trim();
            if (newName.isEmpty()) {
                nameInput.setError("Name can't be empty");
                return;
            }
            Category same = Category.find(all, newName);
            if (same != null && same != c) {
                nameInput.setError("That option already exists");
                return;
            }
            if (c.name.equalsIgnoreCase(String.valueOf(selectedName))) selectedName = newName;
            c.name = newName;
            c.color = chosen[0];
            save();
            refresh();
            updateSelectedChip();
            editor.dismiss();
        });
    }

    private static void drawSwatches(List<View> swatches, int chosen, float d) {
        for (int i = 0; i < swatches.size(); i++) {
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(Category.COLORS[i]);
            // dark ring around the chosen colour, faint ring on the others
            circle.setStroke((int) ((Category.COLORS[i] == chosen ? 3 : 1) * d),
                    Category.COLORS[i] == chosen ? Category.TEXT_COLOR : 0x33000000);
            swatches.get(i).setBackground(circle);
        }
    }

    // ----- list adapter -----

    private class OptionAdapter extends RecyclerView.Adapter<OptionAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            final ImageView handle;
            final TextView name;
            final ImageButton edit;

            VH(View v) {
                super(v);
                handle = v.findViewById(R.id.option_handle);
                name = v.findViewById(R.id.option_name);
                edit = v.findViewById(R.id.option_edit);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_category_option, parent, false));
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Category c = shown.get(position);
            Category.stylePill(h.name, c);

            h.itemView.setOnClickListener(x -> select(c));
            h.edit.setOnClickListener(x -> openEditor(c));
            h.handle.setOnTouchListener((x, e) -> {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN
                        && search.getText().toString().trim().isEmpty()) {
                    touchHelper.startDrag(h);
                }
                return false;
            });
        }

        @Override
        public int getItemCount() { return shown.size(); }
    }
}
