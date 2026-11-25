package com.example.assignment3;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.time.LocalDate;
import java.util.List;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.VH> {

    public interface OnDayClick { void onClick(LocalDate date); }

    private final List<LocalDate> days;
    private final int shownMonth;
    private LocalDate selected;
    private final OnDayClick listener;

    public CalendarAdapter(List<LocalDate> days, int shownMonth,
                           LocalDate selected, OnDayClick listener) {
        this.days = days;
        this.shownMonth = shownMonth;
        this.selected = selected;
        this.listener = listener;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView text;
        VH(View v) { super(v); text = v.findViewById(R.id.day_text); }
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_day, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        LocalDate date = days.get(position);
        h.text.setText(String.valueOf(date.getDayOfMonth()));

        boolean inMonth = date.getMonthValue() == shownMonth;
        boolean isSelected = date.equals(selected);

        h.text.setTextColor(inMonth ? Color.BLACK : Color.LTGRAY);
        h.text.setBackgroundResource(isSelected ? R.drawable.day_selected : 0);
        if (isSelected) h.text.setTextColor(Color.WHITE);

        h.itemView.setOnClickListener(v -> {
            selected = date;
            notifyDataSetChanged();
            listener.onClick(date);
        });
    }

    @Override public int getItemCount() { return days.size(); }
}
