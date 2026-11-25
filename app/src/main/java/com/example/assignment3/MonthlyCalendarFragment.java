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

    // Event panel: the selected day's tasks
    private TaskListRenderer taskList;
    private final Set<String> expandedTasks = new HashSet<>();   // cards showing their checklist

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

        // Tasks for the selected day; the edit button opens Add Task with that task loaded
        taskList = new TaskListRenderer(view.findViewById(R.id.task_sections), expandedTasks, task -> {
            Bundle args = new Bundle();
            args.putString(AddTaskFragment.ARG_TASK_ID, task.id);
            androidx.navigation.Navigation.findNavController(view)
                    .navigate(R.id.addTaskFragment, args);
        }, task -> {
            // tapping a card opens its details page
            Bundle args = new Bundle();
            args.putString(TaskDetailsFragment.ARG_TASK_ID, task.id);
            androidx.navigation.Navigation.findNavController(view)
                    .navigate(R.id.taskDetailsFragment, args);
        });
        taskList.setOnChanged(this::buildGrid);   // ticking/deleting updates the dots

        grid.setLayoutManager(new GridLayoutManager(requireContext(), 7));

        // Spinners
        List<String> months = new ArrayList<>();
        for (Month m : Month.values())
            months.add(m.getDisplayName(TextStyle.SHORT, Locale.getDefault()));   // "Nov"
        monthSpinner.setAdapter(compactAdapter(months));

        List<String> years = new ArrayList<>();
        int thisYear = LocalDate.now().getYear();
        for (int y = thisYear - 10; y <= thisYear + 10; y++) years.add(String.valueOf(y));
        yearSpinner.setAdapter(compactAdapter(years));

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

        view.findViewById(R.id.prev_month).setOnClickListener(v -> changeMonth(-1));
        view.findViewById(R.id.next_month).setOnClickListener(v -> changeMonth(1));

        // Swipe the calendar left = next month, right = previous month
        setUpMonthSwipe();

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
        setUpPanelDrag(view);
    }

    /** Compact text when closed (so "2026" fits), roomy rows when the list is open. */
    private ArrayAdapter<String> compactAdapter(List<String> items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
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

        grid.setAdapter(new CalendarAdapter(days, shown.getMonthValue(), selected, taskDots(days), date -> {
            selected = date;
            updateDateHeader();
        }));
    }

    /**
     * Dot colours for each day that has tasks due: one dot per category with open tasks
     * (category colour, in the user's category order, max 3). Days where everything is
     * completed get no dot.
     */
    private Map<LocalDate, List<Integer>> taskDots(List<LocalDate> days) {
        Map<LocalDate, List<Integer>> result = new HashMap<>();
        if (days.isEmpty()) return result;
        LocalDate first = days.get(0), last = days.get(days.size() - 1);

        Map<LocalDate, List<Task>> byDay = new HashMap<>();
        for (Task t : Task.loadAll(requireContext())) {
            if (t.dueDate.isBefore(first) || t.dueDate.isAfter(last)) continue;
            byDay.computeIfAbsent(t.dueDate, k -> new ArrayList<>()).add(t);
        }

        List<Category> categories = Category.loadAll(requireContext());
        for (Map.Entry<LocalDate, List<Task>> e : byDay.entrySet()) {
            List<String> open = new ArrayList<>();
            for (Task t : e.getValue()) {
                String c = t.category == null ? "" : t.category;
                if (!t.isCompleted() && !open.contains(c)) open.add(c);
            }
            // keep the same order as the Category dropdown
            open.sort(Comparator.comparingInt(c -> {
                Category match = Category.find(categories, c);
                return match == null ? Integer.MAX_VALUE : categories.indexOf(match);
            }));
            List<Integer> colors = new ArrayList<>();
            for (String c : open) {
                if (colors.size() == 3) break;
                Category match = Category.find(categories, c);
                colors.add(TaskListRenderer.darker(match != null ? match.color : Category.COLORS[0]));
            }
            if (!colors.isEmpty()) result.put(e.getKey(), colors);   // everything done: no dot
        }
        return result;
    }

    // ---------------------------------------------------------------------
    // Drag the date bar up: task panel fills the screen. Drag down / Back: calendar again.
    // ---------------------------------------------------------------------

    private boolean panelExpanded = false;   // kept while visiting other screens
    private androidx.activity.OnBackPressedCallback collapseOnBack;

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private void setUpPanelDrag(View root) {
        View header = root.findViewById(R.id.date_header);
        View controls = root.findViewById(R.id.controls_row);
        View calendar = root.findViewById(R.id.calendar_section);
        float d = getResources().getDisplayMetrics().density;
        float threshold = 40 * d;   // how far to drag before it snaps

        // Back button collapses the panel first (only active while expanded)
        collapseOnBack = new androidx.activity.OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                setPanelExpanded(root, controls, calendar, false);
            }
        };
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), collapseOnBack);

        header.setClickable(true);
        header.setContentDescription("Drag up to expand tasks, down to show the calendar");
        float[] startY = new float[1];
        header.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    startY[0] = e.getRawY();
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                    float dy = e.getRawY() - startY[0];
                    if (dy < -threshold) setPanelExpanded(root, controls, calendar, true);        // dragged up
                    else if (dy > threshold) setPanelExpanded(root, controls, calendar, false);   // dragged down
                    else setPanelExpanded(root, controls, calendar, !panelExpanded);              // tap: toggle
                    return true;
            }
            return true;
        });

        // restore the state when coming back from another screen (no animation)
        controls.setVisibility(panelExpanded ? View.GONE : View.VISIBLE);
        calendar.setVisibility(panelExpanded ? View.GONE : View.VISIBLE);
        collapseOnBack.setEnabled(panelExpanded);
    }

    private void setPanelExpanded(View root, View controls, View calendar, boolean expand) {
        if (expand == panelExpanded) return;
        panelExpanded = expand;
        // smooth slide: animate the layout change
        android.transition.TransitionManager.beginDelayedTransition((ViewGroup) root,
                new android.transition.AutoTransition().setDuration(250));
        controls.setVisibility(expand ? View.GONE : View.VISIBLE);
        calendar.setVisibility(expand ? View.GONE : View.VISIBLE);
        collapseOnBack.setEnabled(expand);
    }

    // ---------------------------------------------------------------------
    // Month swipe
    // ---------------------------------------------------------------------

    /** Moves the calendar by +/- months, sliding the grid in from that side. */
    private void changeMonth(int delta) {
        shown = shown.plusMonths(delta);
        syncSpinners();
        buildGrid();
        float w = grid.getWidth() > 0 ? grid.getWidth() : 300;
        grid.setTranslationX(delta > 0 ? w * 0.3f : -w * 0.3f);
        grid.setAlpha(0f);
        grid.animate().translationX(0f).alpha(1f).setDuration(200).start();
    }

    /**
     * Horizontal swipe on the month grid. Once the finger clearly moves sideways the grid
     * takes over the touch, so the day under the finger doesn't get selected by accident.
     */
    private void setUpMonthSwipe() {
        float d = getResources().getDisplayMetrics().density;
        int slop = android.view.ViewConfiguration.get(requireContext()).getScaledTouchSlop();
        float minSwipe = 50 * d;

        grid.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            private float downX, downY;

            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        downX = e.getX();
                        downY = e.getY();
                        return false;
                    case android.view.MotionEvent.ACTION_MOVE:
                        float dx = e.getX() - downX, dy = e.getY() - downY;
                        // mostly sideways -> it's a swipe, take over from the day cells
                        return Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy);
                }
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull android.view.MotionEvent e) {
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
                    float dx = e.getX() - downX;
                    if (dx <= -minSwipe) changeMonth(1);        // swiped left
                    else if (dx >= minSwipe) changeMonth(-1);   // swiped right
                }
            }
        });
    }

    private void updateDateHeader() {
        String month = selected.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault());
        selectedDateText.setText(month + " " + selected.getDayOfMonth() + " " + selected.getYear());
        taskList.show(selected);   // the panel always follows the selected date
    }
}