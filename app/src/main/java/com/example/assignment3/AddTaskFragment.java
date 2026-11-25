package com.example.assignment3;

import android.app.DatePickerDialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ListPopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

public class AddTaskFragment extends Fragment {

    /** Key used by the calendar screen to pass the selected date (ISO, e.g. 2026-06-13). */
    public static final String ARG_DATE = "date";

    private static final DateTimeFormatter DUE_FORMAT =
            DateTimeFormatter.ofPattern("EEE, MMMM d, yyyy", Locale.getDefault());

    private LocalDate dueDate = LocalDate.now();
    private TextView dueDateValue;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null && getArguments().getString(ARG_DATE) != null) {
            dueDate = LocalDate.parse(getArguments().getString(ARG_DATE));
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_task, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText title = view.findViewById(R.id.task_title);
        dueDateValue = view.findViewById(R.id.due_date_value);
        TextView notificationValue = view.findViewById(R.id.notification_value);
        View notificationClear = view.findViewById(R.id.notification_clear);

        repeatValue = view.findViewById(R.id.repeat_value);
        showDueDate();
        setUpCollaboration(view);

        // Repeat: popup with radio buttons + Custom (Google Calendar style)
        view.findViewById(R.id.repeat_row).setOnClickListener(v ->
                RepeatPicker.show(requireContext(), repeatRule, dueDate, this::showRepeat));

        // Category: Notion-style single-select dropdown
        categoryValue = view.findViewById(R.id.category_value);
        view.findViewById(R.id.category_row).setOnClickListener(v ->
                CategoryPicker.show(requireContext(), categoryName, selected -> {
                    categoryName = selected == null ? null : selected.name;
                    showCategory(selected);
                }));

        // Priority: dropdown with High / Medium / Low
        priorityValue = view.findViewById(R.id.priority_value);
        view.findViewById(R.id.priority_row).setOnClickListener(v -> showPriorityMenu());
        stylePill(priorityValue, PRIORITIES[priority], PRIORITY_COLORS[priority]);  // "None", gray

        // Status: dropdown with Not Started / In Progress / Completed
        statusValue = view.findViewById(R.id.status_chip);
        view.findViewById(R.id.status_row).setOnClickListener(v -> showStatusMenu());
        stylePill(statusValue, STATUSES[status], STATUS_COLORS[status]);         // "Not Started", gray

        // X: go back to the calendar without saving
        view.findViewById(R.id.close_button).setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());

        // Save: needs a title; storing the task comes later
        view.findViewById(R.id.save_button).setOnClickListener(v -> {
            if (title.getText().toString().trim().isEmpty()) {
                title.setError("Add a title");
                return;
            }
            // an email typed but not yet added still counts
            String pending = emailInput.getText().toString();
            if (isEmail(pending.trim())) addCollaborator(pending);

            Toast.makeText(requireContext(), "Task saved", Toast.LENGTH_SHORT).show();
            Navigation.findNavController(v).navigateUp();
        });

        // Tap the Due Date row to pick a different date
        view.findViewById(R.id.due_date_row).setOnClickListener(v ->
                new DatePickerDialog(requireContext(), (picker, y, m, d) -> {
                    dueDate = LocalDate.of(y, m + 1, d);
                    showDueDate();
                }, dueDate.getYear(), dueDate.getMonthValue() - 1, dueDate.getDayOfMonth()).show());

        // x next to the notification removes it
        notificationClear.setOnClickListener(v -> {
            notificationValue.setText("None");
            notificationClear.setVisibility(View.GONE);
        });

        View.OnClickListener comingSoon = v ->
                Toast.makeText(requireContext(), "Coming soon", Toast.LENGTH_SHORT).show();
        view.findViewById(R.id.checklist_button).setOnClickListener(comingSoon);
        view.findViewById(R.id.attachment_button).setOnClickListener(comingSoon);
    }

    private void showDueDate() {
        dueDateValue.setText(dueDate.format(DUE_FORMAT));
        showRepeat();   // "Every week on Wednesday" etc. follows the due date
    }

    private final RepeatRule repeatRule = new RepeatRule();
    private TextView repeatValue;

    private void showRepeat() {
        repeatValue.setText(repeatRule.summary(dueDate));
    }

    // ---------------------------------------------------------------------
    // Priority & Status: white pill + coloured dot, tap for a dropdown
    // ---------------------------------------------------------------------

    private static final String[] PRIORITIES = {"High", "Medium", "Low", "None"};
    private static final int[] PRIORITY_COLORS = {
            0xFFE03E3E,   // High   = red
            0xFFDFAB01,   // Medium = yellow
            0xFF337EA9,   // Low    = blue
            0xFF9E9E9E};  // None   = gray (default)

    private static final String[] STATUSES = {"Not Started", "In Progress", "Completed"};
    private static final int[] STATUS_COLORS = {
            0xFF9E9E9E,   // Not Started = gray (default)
            0xFFDFAB01,   // In Progress = yellow
            0xFF0F7B6C};  // Completed   = green

    private TextView priorityValue;
    private TextView statusValue;
    private int priority = 3;   // "None"
    private int status = 0;     // "Not Started"

    private void showPriorityMenu() {
        showPillMenu(priorityValue, PRIORITIES, PRIORITY_COLORS, priority, picked -> {
            priority = picked;
            stylePill(priorityValue, PRIORITIES[priority], PRIORITY_COLORS[priority]);
        });
    }

    private void showStatusMenu() {
        showPillMenu(statusValue, STATUSES, STATUS_COLORS, status, picked -> {
            status = picked;
            stylePill(statusValue, STATUSES[status], STATUS_COLORS[status]);
        });
    }

    /** Dropdown under the pill: each option has its dot, the current one gets a ✓. */
    private void showPillMenu(TextView anchor, String[] names, int[] colors, int current,
                              IntConsumer onPick) {
        float d = getResources().getDisplayMetrics().density;

        ListPopupWindow popup = new ListPopupWindow(requireContext());
        popup.setAnchorView(anchor);
        popup.setWidth((int) (170 * d));
        popup.setModal(true);
        popup.setAdapter(new ArrayAdapter<String>(requireContext(), 0, names) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView tv = convertView instanceof TextView
                        ? (TextView) convertView : new TextView(getContext());
                tv.setPadding((int) (14 * d), (int) (12 * d), (int) (14 * d), (int) (12 * d));
                tv.setTextSize(14);
                tv.setTextColor(0xFF000000);
                tv.setText(names[position] + (position == current ? "   \u2713" : ""));
                tv.setCompoundDrawablesRelativeWithIntrinsicBounds(dot(colors[position]), null, null, null);
                tv.setCompoundDrawablePadding((int) (10 * d));
                return tv;
            }
        });
        popup.setOnItemClickListener((parent, v, position, id) -> {
            onPick.accept(position);
            popup.dismiss();
        });
        popup.show();
    }

    /** White rounded pill with a small coloured dot before the text. */
    private void stylePill(TextView tv, String text, int dotColor) {
        float d = getResources().getDisplayMetrics().density;
        tv.setText(text);
        tv.setTextColor(0xFF000000);
        tv.setTextSize(11);
        tv.setBackgroundResource(R.drawable.bg_pill);
        tv.setCompoundDrawablesRelativeWithIntrinsicBounds(dot(dotColor), null, null, null);
        tv.setCompoundDrawablePadding((int) (6 * d));
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding((int) (10 * d), (int) (2 * d), (int) (10 * d), (int) (2 * d));
    }

    /** Small coloured circle, same size as the Status dot. */
    private GradientDrawable dot(int color) {
        int size = (int) (7 * getResources().getDisplayMetrics().density);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        g.setSize(size, size);
        return g;
    }

    // ---------------------------------------------------------------------
    // Category
    // ---------------------------------------------------------------------

    private TextView categoryValue;
    @Nullable private String categoryName;

    private void showCategory(@Nullable Category c) {
        if (c == null) {
            categoryValue.setText("Empty");
            categoryValue.setTextColor(0xFF8A8A8A);
            categoryValue.setBackground(null);
            categoryValue.setPadding(0, 0, 0, 0);
        } else {
            Category.stylePill(categoryValue, c);
        }
    }

    // ---------------------------------------------------------------------
    // Collaboration: type emails, with recently used emails suggested
    // ---------------------------------------------------------------------

    private static final String PREFS = "collaboration_prefs";
    private static final String KEY_RECENT = "recent_emails";
    private static final int MAX_RECENT = 10;

    private AutoCompleteTextView emailInput;
    private ChipGroup collaboratorChips;
    private final List<String> collaborators = new ArrayList<>();

    private void setUpCollaboration(View view) {
        emailInput = view.findViewById(R.id.collaboration_input);
        collaboratorChips = view.findViewById(R.id.collaborator_chips);
        refreshSuggestions();

        // Tapping the field (before typing) shows the recent emails
        emailInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) showRecentDropDown();
        });
        emailInput.setOnClickListener(v -> showRecentDropDown());
        view.findViewById(R.id.collaboration_row).setOnClickListener(v -> emailInput.requestFocus());

        // Pick a suggestion -> add it
        emailInput.setOnItemClickListener((parent, v, position, id) ->
                addCollaborator((String) parent.getItemAtPosition(position)));

        // Press Done/Enter on the keyboard -> add what was typed
        emailInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_DONE || enter) {
                String typed = emailInput.getText().toString().trim();
                if (typed.isEmpty()) return false;
                if (!isEmail(typed)) {
                    emailInput.setError("Enter a valid email");
                } else {
                    addCollaborator(typed);
                }
                return true;
            }
            return false;
        });
    }

    private void showRecentDropDown() {
        if (emailInput.getText().length() == 0 && emailInput.getAdapter() != null
                && emailInput.getAdapter().getCount() > 0) {
            emailInput.post(() -> emailInput.showDropDown());
        }
    }

    private void addCollaborator(String raw) {
        String email = raw.trim().toLowerCase(Locale.ROOT);
        if (!collaborators.contains(email)) {
            collaborators.add(email);
            addChip(email);
        }
        saveRecent(email);
        emailInput.setText("");
        emailInput.setError(null);
        refreshSuggestions();
    }

    /** Each added email shows as a chip with an x to remove it. */
    private void addChip(String email) {
        Chip chip = new Chip(requireContext());
        chip.setText(email);
        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(v -> {
            collaborators.remove(email);
            collaboratorChips.removeView(chip);
            updateChipVisibility();
            refreshSuggestions();
        });
        collaboratorChips.addView(chip);
        updateChipVisibility();
    }

    private void updateChipVisibility() {
        collaboratorChips.setVisibility(collaborators.isEmpty() ? View.GONE : View.VISIBLE);
        emailInput.setHint(collaborators.isEmpty() ? "Empty" : "Add email");
    }

    /** Suggestions = recent emails (newest first) that aren't already added. */
    private void refreshSuggestions() {
        List<String> suggestions = new ArrayList<>();
        for (String e : loadRecent()) if (!collaborators.contains(e)) suggestions.add(e);
        emailInput.setAdapter(new EmailAdapter(requireContext(), suggestions));
    }

    private List<String> loadRecent() {
        String saved = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_RECENT, "");
        List<String> list = new ArrayList<>();
        for (String s : saved.split("\n")) if (!s.isEmpty()) list.add(s);
        return list;
    }

    /** Moves the email to the top of the recent list (max 10). */
    private void saveRecent(String email) {
        List<String> recent = loadRecent();
        recent.remove(email);
        recent.add(0, email);
        while (recent.size() > MAX_RECENT) recent.remove(recent.size() - 1);
        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_RECENT, TextUtils.join("\n", recent)).apply();
    }

    private static boolean isEmail(String s) {
        return s != null && Patterns.EMAIL_ADDRESS.matcher(s).matches();
    }

    /**
     * Dropdown adapter that matches anywhere in the email
     * (typing "gmail" finds "sam@gmail.com"), not just the start.
     */
    private static class EmailAdapter extends ArrayAdapter<String> {
        private final List<String> all;

        EmailAdapter(Context context, List<String> emails) {
            super(context, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(emails));
            this.all = emails;
        }

        @NonNull
        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence query) {
                    String q = query == null ? "" : query.toString().trim().toLowerCase(Locale.ROOT);
                    List<String> matches = new ArrayList<>();
                    for (String e : all) if (e.contains(q)) matches.add(e);
                    FilterResults results = new FilterResults();
                    results.values = matches;
                    results.count = matches.size();
                    return results;
                }

                @Override
                @SuppressWarnings("unchecked")
                protected void publishResults(CharSequence query, FilterResults results) {
                    clear();
                    addAll((List<String>) results.values);
                    notifyDataSetChanged();
                }
            };
        }
    }
}
