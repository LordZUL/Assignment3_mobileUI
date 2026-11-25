package com.example.assignment3;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** How a task repeats (Google Calendar style). */
public class RepeatRule {

    // preset choices, in the order they appear in the popup
    public static final int NONE = 0, DAILY = 1, WEEKLY = 2, MONTHLY = 3, YEARLY = 4, CUSTOM = 5;

    // units for Custom
    public static final int DAY = 0, WEEK = 1, MONTH = 2, YEAR = 3;
    public static final String[] UNITS = {"day", "week", "month", "year"};

    // how a Custom rule ends
    public static final int ENDS_NEVER = 0, ENDS_ON = 1, ENDS_AFTER = 2;

    public int type = NONE;

    // Custom settings
    public int interval = 1;
    public int unit = WEEK;
    public boolean[] days = new boolean[7];   // Sun..Sat (used when unit == WEEK)
    public int ends = ENDS_NEVER;
    public LocalDate endDate;
    public int count = 13;

    /** Popup labels, filled in from the task's due date like Google Calendar. */
    public static String[] presetLabels(LocalDate due) {
        return new String[]{
                "Does not repeat",
                "Every day",
                "Every week",
                "Every month",
                "Every year",
                "Custom"
        };
    }

    /** Text shown in the "Does not repeat" row. */
    public String summary(LocalDate due) {
        if (type != CUSTOM) return presetLabels(due)[type];

        StringBuilder sb = new StringBuilder("Every ");
        sb.append(interval == 1 ? UNITS[unit] : interval + " " + UNITS[unit] + "s");

        if (unit == WEEK) {
            List<String> names = new ArrayList<>();
            String[] shortNames = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
            for (int i = 0; i < 7; i++) if (days[i]) names.add(shortNames[i]);
            if (!names.isEmpty()) sb.append(" on ").append(String.join(", ", names));
        } else if (unit == MONTH) {
            sb.append(" on the ").append(ordinal(due.getDayOfMonth()));
        } else if (unit == YEAR) {
            sb.append(" on ").append(monthDay(due));
        }

        if (ends == ENDS_ON && endDate != null) {
            sb.append(", until ").append(endDate.format(
                    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())));
        } else if (ends == ENDS_AFTER) {
            sb.append(", ").append(count).append(count == 1 ? " time" : " times");
        }
        return sb.toString();
    }

    /** Sunday = 0 ... Saturday = 6 */
    public static int dayIndex(LocalDate date) {
        return date.getDayOfWeek().getValue() % 7;
    }

    static String ordinal(int n) {
        if (n % 100 >= 11 && n % 100 <= 13) return n + "th";
        switch (n % 10) {
            case 1: return n + "st";
            case 2: return n + "nd";
            case 3: return n + "rd";
            default: return n + "th";
        }
    }

    private static String monthDay(LocalDate d) {
        return d.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + d.getDayOfMonth();
    }
}
