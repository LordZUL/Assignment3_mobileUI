package com.example.assignment3;

import android.app.DatePickerDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Repeat popup with radio buttons, plus a Google Calendar style "Custom" screen. */
public class RepeatPicker {

    private static final DateTimeFormatter END_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault());

    /** Step 1: radio list of presets. */
    public static void show(Context ctx, RepeatRule rule, LocalDate due, Runnable onChanged) {
        new AlertDialog.Builder(ctx)
                .setTitle("Repeat")
                .setSingleChoiceItems(RepeatRule.presetLabels(due), rule.type, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == RepeatRule.CUSTOM) {
                        showCustom(ctx, rule, due, onChanged);
                    } else {
                        rule.type = which;
                        onChanged.run();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Step 2: Custom - every N days/weeks/months/years, which weekdays, and when it ends. */
    private static void showCustom(Context ctx, RepeatRule rule, LocalDate due, Runnable onChanged) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_repeat_custom, null);
        EditText intervalInput = v.findViewById(R.id.repeat_interval);
        Spinner unitSpinner = v.findViewById(R.id.repeat_unit);
        View daysSection = v.findViewById(R.id.repeat_days_section);
        LinearLayout daysRow = v.findViewById(R.id.repeat_days);
        RadioButton endsNever = v.findViewById(R.id.ends_never);
        RadioButton endsOn = v.findViewById(R.id.ends_on);
        RadioButton endsAfter = v.findViewById(R.id.ends_after);
        TextView endDateText = v.findViewById(R.id.ends_on_date);
        EditText countInput = v.findViewById(R.id.ends_after_count);

        // Work on copies so Cancel doesn't change anything
        boolean firstTime = rule.type != RepeatRule.CUSTOM;
        int[] unit = {firstTime ? RepeatRule.WEEK : rule.unit};
        boolean[] days = rule.days.clone();
        if (firstTime || noneTrue(days)) {
            days = new boolean[7];
            days[RepeatRule.dayIndex(due)] = true;     // default: the due date's weekday
        }
        final boolean[] chosenDays = days;
        int[] ends = {firstTime ? RepeatRule.ENDS_NEVER : rule.ends};
        LocalDate[] endDate = {rule.endDate != null ? rule.endDate : due.plusMonths(3)};

        intervalInput.setText(String.valueOf(firstTime ? 1 : rule.interval));
        countInput.setText(String.valueOf(rule.count));
        endDateText.setText(endDate[0].format(END_FORMAT));

        // --- unit spinner (day / week / month / year, plural when interval > 1) ---
        Runnable refreshUnits = () -> {
            boolean plural = parse(intervalInput, 1) > 1;
            String[] labels = new String[RepeatRule.UNITS.length];
            for (int i = 0; i < labels.length; i++) {
                labels[i] = RepeatRule.UNITS[i] + (plural ? "s" : "");
            }
            ArrayAdapter<String> a = new ArrayAdapter<>(ctx,
                    android.R.layout.simple_spinner_item, labels);
            a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            unitSpinner.setAdapter(a);
            unitSpinner.setSelection(unit[0]);
        };
        refreshUnits.run();
        intervalInput.addTextChangedListener(new SimpleWatcher(refreshUnits));
        unitSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View view, int pos, long id) {
                unit[0] = pos;
                daysSection.setVisibility(pos == RepeatRule.WEEK ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });

        // --- weekday circles S M T W T F S ---
        float d = ctx.getResources().getDisplayMetrics().density;
        String[] letters = {"S", "M", "T", "W", "T", "F", "S"};
        String[] full = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        for (int i = 0; i < 7; i++) {
            final int idx = i;
            TextView circle = new TextView(ctx);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (34 * d), (int) (34 * d));
            lp.setMarginEnd((int) (5 * d));
            circle.setLayoutParams(lp);
            circle.setGravity(Gravity.CENTER);
            circle.setText(letters[i]);
            circle.setContentDescription(full[i]);
            styleDay(circle, chosenDays[i], d);
            circle.setOnClickListener(x -> {
                chosenDays[idx] = !chosenDays[idx];
                styleDay(circle, chosenDays[idx], d);
            });
            daysRow.addView(circle);
        }

        // --- Ends: Never / On date / After N occurrences (one at a time) ---
        Runnable refreshEnds = () -> {
            endsNever.setChecked(ends[0] == RepeatRule.ENDS_NEVER);
            endsOn.setChecked(ends[0] == RepeatRule.ENDS_ON);
            endsAfter.setChecked(ends[0] == RepeatRule.ENDS_AFTER);
            endDateText.setEnabled(ends[0] == RepeatRule.ENDS_ON);
            endDateText.setAlpha(ends[0] == RepeatRule.ENDS_ON ? 1f : 0.4f);
            countInput.setEnabled(ends[0] == RepeatRule.ENDS_AFTER);
        };
        endsNever.setOnClickListener(x -> { ends[0] = RepeatRule.ENDS_NEVER; refreshEnds.run(); });
        endsOn.setOnClickListener(x -> { ends[0] = RepeatRule.ENDS_ON; refreshEnds.run(); });
        endsAfter.setOnClickListener(x -> { ends[0] = RepeatRule.ENDS_AFTER; refreshEnds.run(); });
        refreshEnds.run();

        endDateText.setOnClickListener(x ->
                new DatePickerDialog(ctx, (picker, y, m, day) -> {
                    endDate[0] = LocalDate.of(y, m + 1, day);
                    endDateText.setText(endDate[0].format(END_FORMAT));
                }, endDate[0].getYear(), endDate[0].getMonthValue() - 1, endDate[0].getDayOfMonth()).show());

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle("Custom recurrence")
                .setView(v)
                .setPositiveButton("Done", null)        // set below so errors keep it open
                .setNegativeButton("Cancel", (dlg, w) -> show(ctx, rule, due, onChanged))
                .create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            int interval = parse(intervalInput, 0);
            if (interval < 1) {
                intervalInput.setError("At least 1");
                return;
            }
            int count = parse(countInput, 0);
            if (ends[0] == RepeatRule.ENDS_AFTER && count < 1) {
                countInput.setError("At least 1");
                return;
            }
            if (ends[0] == RepeatRule.ENDS_ON && endDate[0].isBefore(due)) {
                endDateText.setError("Must be after the due date");
                return;
            }
            if (unit[0] == RepeatRule.WEEK && noneTrue(chosenDays)) {
                chosenDays[RepeatRule.dayIndex(due)] = true;
            }
            rule.type = RepeatRule.CUSTOM;
            rule.interval = interval;
            rule.unit = unit[0];
            rule.days = chosenDays;
            rule.ends = ends[0];
            rule.endDate = endDate[0];
            if (count >= 1) rule.count = count;
            onChanged.run();
            dialog.dismiss();
        });
    }

    /** Filled dark circle = selected, outlined = not selected. */
    private static void styleDay(TextView tv, boolean on, float d) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(on ? 0xFF37352F : Color.TRANSPARENT);
        g.setStroke((int) (1 * d), on ? 0xFF37352F : 0xFF9E9E9E);
        tv.setBackground(g);
        tv.setTextColor(on ? Color.WHITE : 0xFF37352F);
    }

    private static boolean noneTrue(boolean[] a) {
        for (boolean b : a) if (b) return false;
        return true;
    }

    private static int parse(EditText e, int fallback) {
        try {
            return Integer.parseInt(e.getText().toString().trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static class SimpleWatcher implements TextWatcher {
        private final Runnable r;
        SimpleWatcher(Runnable r) { this.r = r; }
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
        @Override public void afterTextChanged(Editable s) { r.run(); }
    }
}
