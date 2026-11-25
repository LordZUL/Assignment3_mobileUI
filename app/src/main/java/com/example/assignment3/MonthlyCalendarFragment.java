package com.example.assignment3;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;

public class MonthlyCalendarFragment extends Fragment {

    private Spinner monthSpinner, yearSpinner;
    private RecyclerView grid;
    private TextView selectedDateText;
    private LocalDate selected = LocalDate.now();
    private LocalDate shown = LocalDate.now().withDayOfMonth(1);
    private boolean updatingSpinners = false;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_monthly_calendar_view, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        monthSpinner = view.findViewById(R.id.month_spinner);
        yearSpinner = view.findViewById(R.id.year_spinner);
        grid = view.findViewById(R.id.calendar_grid);
        selectedDateText = view.findViewById(R.id.selected_date);

        grid.setLayoutManager(new GridLayoutManager(requireContext(), 7));

        // Spinners
        List<String> months = new ArrayList<>();
        for (Month m : Month.values())
            months.add(m.getDisplayName(TextStyle.FULL, Locale.getDefault()));
        monthSpinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, months));

        List<String> years = new ArrayList<>();
        int thisYear = LocalDate.now().getYear();
        for (int y = thisYear - 10; y <= thisYear + 10; y++) years.add(String.valueOf(y));
        yearSpinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, years));

        AdapterView.OnItemSelectedListener spinnerListener =
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                        if (updatingSpinners) return;
                        int month = monthSpinner.getSelectedItemPosition() + 1;
                        int year = Integer.parseInt((String) yearSpinner.getSelectedItem());
                        shown = LocalDate.of(year, month, 1);
                        buildGrid();
                    }
                    @Override public void onNothingSelected(AdapterView<?> p) {}
                };
        monthSpinner.setOnItemSelectedListener(spinnerListener);
        yearSpinner.setOnItemSelectedListener(spinnerListener);

        view.findViewById(R.id.next_month).setOnClickListener(v -> {
            shown = shown.plusMonths(1);
            syncSpinners();
            buildGrid();
        });

        view.findViewById(R.id.today_button).setOnClickListener(v -> {
            selected = LocalDate.now();
            shown = selected.withDayOfMonth(1);
            syncSpinners();
            buildGrid();
            updateDateHeader();
        });

        // + button: open Add Task, pre-filled with the selected date
        view.findViewById(R.id.add_event).setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString(AddTaskFragment.ARG_DATE, selected.toString());
            androidx.navigation.Navigation.findNavController(v)
                    .navigate(R.id.addTaskFragment, args);
        });

        syncSpinners();
        buildGrid();
        updateDateHeader();
    }

    private void syncSpinners() {
        updatingSpinners = true;
        monthSpinner.setSelection(shown.getMonthValue() - 1);
        int idx = shown.getYear() - (LocalDate.now().getYear() - 10);
        yearSpinner.setSelection(Math.max(0, Math.min(20, idx)));
        // let the programmatic selection callbacks fire before re-enabling
        monthSpinner.post(() -> updatingSpinners = false);
    }

    private void buildGrid() {
        List<LocalDate> days = new ArrayList<>();
        // Sunday-first: java.time Sunday = 7, so % 7 gives 0 for Sunday
        int offset = shown.getDayOfWeek().getValue() % 7;
        LocalDate start = shown.minusDays(offset);
        int weeks = (int) Math.ceil((offset + shown.lengthOfMonth()) / 7.0);
        for (int i = 0; i < weeks * 7; i++) days.add(start.plusDays(i));

        grid.setAdapter(new CalendarAdapter(days, shown.getMonthValue(), selected, date -> {
            selected = date;
            updateDateHeader();
        }));
    }

    private void updateDateHeader() {
        String month = selected.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault());
        selectedDateText.setText(month + " " + selected.getDayOfMonth() + " " + selected.getYear());
    }
}