package com.example.assignment3;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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

        showDueDate();

        // X: go back to the calendar without saving
        view.findViewById(R.id.close_button).setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());

        // Save: needs a title; storing the task comes later
        view.findViewById(R.id.save_button).setOnClickListener(v -> {
            if (title.getText().toString().trim().isEmpty()) {
                title.setError("Add a title");
                return;
            }
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
    }
}
