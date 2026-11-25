package com.example.assignment3;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.text.format.DateFormat;
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
import java.time.LocalTime;
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
    @Nullable private LocalTime dueTime = null;   // null = no time set
    private TextView dueTimeValue;
    private View dueTimeClear;

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

        // Required fields get a red *
        title.setHint(required("Title"));
        ((TextView) view.findViewById(R.id.category_label)).setText(required("Category"));
        dueDateValue = view.findViewById(R.id.due_date_value);
        dueTimeValue = view.findViewById(R.id.due_time_value);
        dueTimeClear = view.findViewById(R.id.due_time_clear);

        // Tap the time to set when it's due; x removes the time
        dueTimeValue.setOnClickListener(v -> {
            LocalTime start = dueTime != null ? dueTime : LocalTime.of(23, 59);
            new TimePickerDialog(requireContext(), (picker, h, min) -> {
                dueTime = LocalTime.of(h, min);
                showDueTime();
            }, start.getHour(), start.getMinute(),
                    DateFormat.is24HourFormat(requireContext())).show();
        });
        dueTimeClear.setOnClickListener(v -> {
            dueTime = null;
            showDueTime();
        });

        repeatValue = view.findViewById(R.id.repeat_value);
        showDueDate();
        setUpCollaboration(view);

        // Checklist (Trello style): people to assign come from the collaborators
        checklist = new ChecklistController(view, () -> {
            commitTypedEmail();   // a finished email still in the field counts too
            return collaborators;
        });
        view.findViewById(R.id.checklist_button).setOnClickListener(v -> checklist.open());

        // Repeat: popup with radio buttons + Custom (Google Calendar style)
        view.findViewById(R.id.repeat_row).setOnClickListener(v ->
                RepeatPicker.show(requireContext(), repeatRule, dueDate, this::showRepeat));

        // Category: Notion-style single-select dropdown
        categoryValue = view.findViewById(R.id.category_value);
        categoryPill = view.findViewById(R.id.category_pill);
        categoryPillText = view.findViewById(R.id.category_pill_text);
        academicFields = view.findViewById(R.id.academic_fields);
        categoryPill.setOnClickListener(v -> view.findViewById(R.id.category_row).performClick());
        // x on the pill clears the category (and hides the Academic rows)
        view.findViewById(R.id.category_clear).setOnClickListener(v -> {
            categoryName = null;
            showCategory(null);
        });
        setUpAcademicFields(view);
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

        // Save: needs a title and a category (the * fields); storing the task comes later
        view.findViewById(R.id.save_button).setOnClickListener(v -> {
            if (title.getText().toString().trim().isEmpty()) {
                title.setError("Add a title");
                title.requestFocus();
                return;
            }
            if (categoryName == null) {
                Toast.makeText(requireContext(), "Choose a category", Toast.LENGTH_SHORT).show();
                return;
            }
            commitTypedEmail();   // an email typed but not yet added still counts

            Toast.makeText(requireContext(), "Task saved", Toast.LENGTH_SHORT).show();
            Navigation.findNavController(v).navigateUp();
        });

        // Tap the Due Date row to pick a different date
        view.findViewById(R.id.due_date_row).setOnClickListener(v ->
                new DatePickerDialog(requireContext(), (picker, y, m, d) -> {
                    dueDate = LocalDate.of(y, m + 1, d);
                    showDueDate();
                }, dueDate.getYear(), dueDate.getMonthValue() - 1, dueDate.getDayOfMonth()).show());

        setUpNotification(view);

        // Attachment: open the phone's file browser, pick one or more files
        attachmentSection = view.findViewById(R.id.attachment_section);
        attachmentList = view.findViewById(R.id.attachment_list);
        attachmentEmpty = view.findViewById(R.id.attachment_empty);
        view.findViewById(R.id.attachment_button).setOnClickListener(v -> {
            attachmentSection.setVisibility(View.VISIBLE);
            pickFiles.launch(new String[]{"*/*"});
        });
        view.findViewById(R.id.attachment_add_more).setOnClickListener(v ->
                pickFiles.launch(new String[]{"*/*"}));
        // X: close the Attachments section (asks first if files would be removed)
        view.findViewById(R.id.attachment_exit).setOnClickListener(v -> {
            if (attachments.isEmpty()) {
                attachmentSection.setVisibility(View.GONE);
                return;
            }
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Close attachments?")
                    .setMessage("The attached files will be removed from this task.")
                    .setPositiveButton("Remove", (dlg, w) -> {
                        attachments.clear();
                        showAttachments();
                        attachmentSection.setVisibility(View.GONE);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        showAttachments();
    }

    // ---------------------------------------------------------------------
    // Attachments
    // ---------------------------------------------------------------------

    private final List<android.net.Uri> attachments = new ArrayList<>();
    private android.widget.LinearLayout attachmentList;
    private View attachmentSection;
    private View attachmentEmpty;

    /** System file picker ("Files" app). Any file type, several at once. */
    private final androidx.activity.result.ActivityResultLauncher<String[]> pickFiles =
            registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments(),
                    uris -> {
                        for (android.net.Uri uri : uris) {
                            if (attachments.contains(uri)) continue;
                            try {   // keep access to the file after the app restarts
                                requireContext().getContentResolver().takePersistableUriPermission(
                                        uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (SecurityException ignored) { }
                            attachments.add(uri);
                        }
                        if (!uris.isEmpty()) showAttachments();
                    });

    private void showAttachments() {
        attachmentList.removeAllViews();
        attachmentEmpty.setVisibility(attachments.isEmpty() ? View.VISIBLE : View.GONE);
        float d = getResources().getDisplayMetrics().density;

        for (android.net.Uri uri : attachments) {
            String[] info = fileInfo(uri);   // {name, size}

            android.widget.LinearLayout row = new android.widget.LinearLayout(requireContext());
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundResource(R.drawable.bg_white_rounded);
            row.setPadding((int) (10 * d), (int) (8 * d), (int) (4 * d), (int) (8 * d));
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (6 * d);
            row.setLayoutParams(lp);

            android.widget.ImageView icon = new android.widget.ImageView(requireContext());
            icon.setImageResource(R.drawable.ic_attach);
            row.addView(icon);

            TextView name = new TextView(requireContext());
            name.setText(info[0]);
            name.setTextColor(0xFF000000);
            name.setTextSize(14);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            android.widget.LinearLayout.LayoutParams nlp = new android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            nlp.setMarginStart((int) (8 * d));
            name.setLayoutParams(nlp);
            row.addView(name);

            TextView size = new TextView(requireContext());
            size.setText(info[1]);
            size.setTextColor(0xFF8A8A8A);
            size.setTextSize(12);
            row.addView(size);

            android.widget.ImageButton remove = new android.widget.ImageButton(requireContext());
            remove.setImageResource(R.drawable.ic_close_small);
            remove.setBackgroundResource(android.R.color.transparent);
            remove.setContentDescription("Remove " + info[0]);
            remove.setLayoutParams(new android.widget.LinearLayout.LayoutParams((int) (32 * d), (int) (32 * d)));
            remove.setOnClickListener(v -> {
                attachments.remove(uri);
                showAttachments();
            });
            row.addView(remove);

            // tap the file to open it in another app
            row.setOnClickListener(v -> {
                android.content.Intent open = new android.content.Intent(android.content.Intent.ACTION_VIEW)
                        .setDataAndType(uri, requireContext().getContentResolver().getType(uri))
                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivity(open);
                } catch (android.content.ActivityNotFoundException e) {
                    Toast.makeText(requireContext(), "No app can open this file", Toast.LENGTH_SHORT).show();
                }
            });

            attachmentList.addView(row);
        }
    }

    /** File name and readable size, e.g. {"notes.pdf", "240 KB"}. */
    private String[] fileInfo(android.net.Uri uri) {
        String name = uri.getLastPathSegment();
        String size = "";
        try (android.database.Cursor c = requireContext().getContentResolver().query(uri,
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME,
                        android.provider.OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                if (!c.isNull(0)) name = c.getString(0);
                if (!c.isNull(1)) size = android.text.format.Formatter.formatShortFileSize(
                        requireContext(), c.getLong(1));
            }
        } catch (Exception ignored) { }
        return new String[]{name, size};
    }

    /** "Title" -> "Title *" with a red asterisk. */
    private static CharSequence required(String label) {
        android.text.SpannableString s = new android.text.SpannableString(label + " *");
        s.setSpan(new android.text.style.ForegroundColorSpan(0xFFE03E3E),
                label.length() + 1, label.length() + 2,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private void showDueDate() {
        dueDateValue.setText(dueDate.format(DUE_FORMAT));
        showDueTime();
        showRepeat();   // "Every week on Wednesday" etc. follows the due date
    }

    private void showDueTime() {
        if (dueTime == null) {
            dueTimeValue.setText("Add time");
            dueTimeValue.setTextColor(0xFF8A8A8A);
            dueTimeClear.setVisibility(View.GONE);
        } else {
            String pattern = DateFormat.is24HourFormat(requireContext()) ? "HH:mm" : "h:mm a";
            dueTimeValue.setText(dueTime.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault())));
            dueTimeValue.setTextColor(0xFF000000);
            dueTimeClear.setVisibility(View.VISIBLE);
        }
    }

    // ---------------------------------------------------------------------
    // Notification (Google Calendar style): presets + Custom where you type the amount
    // ---------------------------------------------------------------------

    private static final String[] UNIT_ONE = {"minute", "hour", "day", "week"};
    private static final String[] UNIT_MANY = {"minutes", "hours", "days", "weeks"};
    private static final int MIN = 0, HOUR = 1, DAY = 2, WEEK = 3;

    // presets shown in the popup: {amount, unit}; amount 0 = no notification
    private static final int[][] NOTIFY_PRESETS = {
            {0, MIN}, {5, MIN}, {10, MIN}, {30, MIN}, {1, HOUR}, {1, DAY}};

    private TextView notificationValue;
    private View notificationClear;
    private int notifyAmount = 30;      // 0 = no notification
    private int notifyUnit = MIN;

    private void setUpNotification(View view) {
        notificationValue = view.findViewById(R.id.notification_value);
        notificationClear = view.findViewById(R.id.notification_clear);

        view.findViewById(R.id.notification_row).setOnClickListener(v -> showNotificationMenu());
        notificationClear.setOnClickListener(v -> {
            notifyAmount = 0;
            showNotification();
        });
        showNotification();
    }

    private static String notifyText(int amount, int unit) {
        if (amount <= 0) return "No notification";
        return amount + " " + (amount == 1 ? UNIT_ONE[unit] : UNIT_MANY[unit]) + " before";
    }

    /** Step 1: radio list of common times + Custom. */
    private void showNotificationMenu() {
        String[] labels = new String[NOTIFY_PRESETS.length + 1];
        int checked = -1;
        for (int i = 0; i < NOTIFY_PRESETS.length; i++) {
            labels[i] = notifyText(NOTIFY_PRESETS[i][0], NOTIFY_PRESETS[i][1]);
            boolean same = NOTIFY_PRESETS[i][0] == notifyAmount
                    && (notifyAmount == 0 || NOTIFY_PRESETS[i][1] == notifyUnit);
            if (same) checked = i;
        }
        labels[labels.length - 1] = "Custom\u2026";
        if (checked == -1) checked = labels.length - 1;   // current value is a custom one

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Notification")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == labels.length - 1) {
                        showCustomNotification();
                    } else {
                        notifyAmount = NOTIFY_PRESETS[which][0];
                        notifyUnit = NOTIFY_PRESETS[which][1];
                        showNotification();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Step 2: type the amount, choose minutes / hours / days / weeks. */
    private void showCustomNotification() {
        android.content.Context ctx = requireContext();
        float d = getResources().getDisplayMetrics().density;

        android.widget.LinearLayout box = new android.widget.LinearLayout(ctx);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding((int) (24 * d), (int) (8 * d), (int) (24 * d), 0);

        EditText amountInput = new EditText(ctx);
        amountInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        amountInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(3)});
        amountInput.setHint("Amount");
        amountInput.setText(String.valueOf(notifyAmount > 0 ? notifyAmount : 30));
        amountInput.setSelectAllOnFocus(true);
        box.addView(amountInput);

        android.widget.RadioGroup units = new android.widget.RadioGroup(ctx);
        android.widget.RadioButton[] buttons = new android.widget.RadioButton[UNIT_MANY.length];
        for (int i = 0; i < UNIT_MANY.length; i++) {
            buttons[i] = new android.widget.RadioButton(ctx);
            buttons[i].setId(View.generateViewId());
            buttons[i].setText(UNIT_MANY[i]);
            units.addView(buttons[i]);
        }
        buttons[notifyAmount > 0 ? notifyUnit : MIN].setChecked(true);
        box.addView(units);

        // "minutes" / "minute" follows what's typed, like Google Calendar
        Runnable relabel = () -> {
            boolean one = amountInput.getText().toString().trim().equals("1");
            for (int i = 0; i < buttons.length; i++) buttons[i].setText(one ? UNIT_ONE[i] : UNIT_MANY[i]);
        };
        relabel.run();
        amountInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) { relabel.run(); }
        });

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setTitle("Custom notification")
                .setView(box)
                .setPositiveButton("Done", null)          // set below so errors keep it open
                .setNegativeButton("Cancel", (dlg, w) -> showNotificationMenu())
                .create();
        dialog.show();
        amountInput.requestFocus();

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int amount;
            try {
                amount = Integer.parseInt(amountInput.getText().toString().trim());
            } catch (NumberFormatException e) {
                amount = 0;
            }
            if (amount < 1) {
                amountInput.setError("Enter a number");
                return;
            }
            int unit = MIN;
            for (int i = 0; i < buttons.length; i++) if (buttons[i].isChecked()) unit = i;
            notifyAmount = amount;
            notifyUnit = unit;
            showNotification();
            dialog.dismiss();
        });
    }

    private void showNotification() {
        notificationValue.setText(notifyText(notifyAmount, notifyUnit));
        notificationValue.setTextColor(notifyAmount > 0 ? 0xFF000000 : 0xFF8A8A8A);
        notificationClear.setVisibility(notifyAmount > 0 ? View.VISIBLE : View.GONE);
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

    private View categoryPill;
    private TextView categoryPillText;
    private View academicFields;

    /** Empty text, or a white pill (dot + name + x). Academic shows the extra rows. */
    private void showCategory(@Nullable Category c) {
        if (c == null) {
            categoryValue.setVisibility(View.VISIBLE);
            categoryPill.setVisibility(View.GONE);
        } else {
            categoryValue.setVisibility(View.GONE);
            categoryPill.setVisibility(View.VISIBLE);
            categoryPillText.setText(c.name);
            // dot uses a stronger version of the category colour so it shows on white
            categoryPillText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    dot(darken(c.color)), null, null, null);
        }
        boolean academic = c != null && c.name.equalsIgnoreCase("Academic");
        academicFields.setVisibility(academic ? View.VISIBLE : View.GONE);
    }

    private static int darken(int color) {
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(color, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 3f);   // more saturated
        hsv[2] = hsv[2] * 0.8f;               // a bit darker
        return android.graphics.Color.HSVToColor(hsv);
    }

    // ---------------------------------------------------------------------
    // Academic details: Class, Coursework Type, Weight
    // ---------------------------------------------------------------------

    private static final String[] COURSEWORK_TYPES = {
            "Assignment", "Quiz", "Test", "Midterm", "Final Exam",
            "Lab", "Project", "Presentation", "Reading", "Other"};

    @Nullable private String courseworkType;
    private TextView courseworkValue;
    private EditText classInput;
    private EditText weightInput;

    private void setUpAcademicFields(View view) {
        classInput = view.findViewById(R.id.class_input);
        weightInput = view.findViewById(R.id.weight_input);
        courseworkValue = view.findViewById(R.id.coursework_value);
        View percent = view.findViewById(R.id.weight_percent);

        // Coursework Type: dropdown list
        view.findViewById(R.id.coursework_row).setOnClickListener(v -> {
            float d = getResources().getDisplayMetrics().density;
            ListPopupWindow popup = new ListPopupWindow(requireContext());
            popup.setAnchorView(courseworkValue);
            popup.setWidth((int) (180 * d));
            popup.setHeight((int) (300 * d));
            popup.setModal(true);
            popup.setAdapter(new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_list_item_1, COURSEWORK_TYPES));
            popup.setOnItemClickListener((parent, item, position, id) -> {
                courseworkType = COURSEWORK_TYPES[position];
                courseworkValue.setText(courseworkType);
                courseworkValue.setTextColor(0xFF000000);
                popup.dismiss();
            });
            popup.show();
        });

        // Weight: type a number, "%" appears after it; max 100
        weightInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                percent.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
                try {
                    if (Double.parseDouble(s.toString()) > 100) weightInput.setError("Max 100%");
                } catch (NumberFormatException ignored) { }
            }
        });
    }

    // ---------------------------------------------------------------------
    // Collaboration: type emails, with recently used emails suggested
    // ---------------------------------------------------------------------

    private static final String PREFS = "collaboration_prefs";
    private static final String KEY_RECENT = "recent_emails";
    private static final int MAX_RECENT = 10;

    private ChecklistController checklist;
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
            else commitTypedEmail();      // leaving the field adds a valid email
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

    /** Adds whatever is typed in the Collaboration field if it's a valid email. */
    private void commitTypedEmail() {
        String typed = emailInput.getText().toString().trim();
        if (isEmail(typed)) addCollaborator(typed);
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
            if (checklist != null) checklist.onCollaboratorRemoved(email);
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
