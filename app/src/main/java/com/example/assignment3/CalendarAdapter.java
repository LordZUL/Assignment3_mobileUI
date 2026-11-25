package com.example.assignment3;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.LinearLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.VH> {

    public interface OnDayClick { void onClick(LocalDate date); }

    private final List<LocalDate> days;
    private final int shownMonth;
    private LocalDate selected;
    private final OnDayClick listener;
    private final Map<LocalDate, List<Integer>> dots;   // dot colours per day with tasks due

    public CalendarAdapter(List<LocalDate> days, int shownMonth,
                           LocalDate selected, OnDayClick listener) {
        this(days, shownMonth, selected, Collections.emptyMap(), listener);
    }

    public CalendarAdapter(List<LocalDate> days, int shownMonth, LocalDate selected,
                           Map<LocalDate, List<Integer>> dots, OnDayClick listener) {
        this.dots = dots;
        this.days = days;
        this.shownMonth = shownMonth;
        this.selected = selected;
        this.listener = listener;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView text;
        LinearLayout dots;
        VH(View v) {
            super(v);
            text = v.findViewById(R.id.day_text);
            dots = v.findViewById(R.id.day_dots);
        }
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

        // Coloured dots under days that have tasks due
        h.dots.removeAllViews();
        List<Integer> colors = dots.get(date);
        if (colors != null) {
            float d = h.itemView.getResources().getDisplayMetrics().density;
            int size = (int) (5 * d);
            for (int color : colors) {
                View dot = new View(h.itemView.getContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                lp.setMargins((int) (1.5f * d), 0, (int) (1.5f * d), 0);
                dot.setLayoutParams(lp);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(color);
                dot.setBackground(g);
                dot.setAlpha(inMonth ? 1f : 0.4f);   // faded on the next/previous month's days
                h.dots.addView(dot);
            }
        }

        h.itemView.setOnClickListener(v -> {
            selected = date;
            notifyDataSetChanged();
            listener.onClick(date);
        });
    }

    @Override public int getItemCount() { return days.size(); }
}
