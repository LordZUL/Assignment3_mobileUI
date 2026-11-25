package com.example.assignment3;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.format.DateFormat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Due-date reminders, built the Tutorial 5 way:
 * Notification.Builder -> Intent to NotificationBroadcaster -> PendingIntent -> AlarmManager.
 * The alarm time = due date + due time - the Notification setting (e.g. 1 minute before).
 */
public class TaskNotifications {

    public static final String CHANNEL = "task_reminders";

    private static final long[] UNIT_MILLIS = {
            60_000L,              // minutes
            3_600_000L,           // hours
            86_400_000L,          // days
            7 * 86_400_000L};     // weeks

    /** Tutorial 5: every notification needs a channel (safe to call more than once). */
    public static void createChannel(Context ctx) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Task reminders",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Reminders before a task is due");
        nm.createNotificationChannel(channel);
    }

    /**
     * Sets (or clears) the reminder for one task. Only tasks with a due TIME, a notification,
     * that aren't completed and whose reminder time is still in the future get an alarm.
     */
    public static void schedule(Context ctx, Task task) {
        cancel(ctx, task.id);
        if (task.dueTime == null || task.notifyAmount <= 0 || task.isCompleted()) return;

        LocalDateTime due = LocalDateTime.of(task.dueDate, task.dueTime);
        long dueMillis = due.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long myAlarmTime = dueMillis - task.notifyAmount * UNIT_MILLIS[task.notifyUnit];
        if (myAlarmTime <= System.currentTimeMillis()) return;   // already passed

        createChannel(ctx);
        Notification notification = buildNotification(ctx, task);

        // Tutorial 5: Intent with the id + notification as extras, wrapped in a PendingIntent
        Intent intent = new Intent(ctx, NotificationBroadcaster.class);
        intent.putExtra(NotificationBroadcaster.NOTIFICATION_ID, notificationId(task.id));
        intent.putExtra(NotificationBroadcaster.NOTIFICATION, notification);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(ctx, notificationId(task.id), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Tutorial 5 uses am.set(RTC_WAKEUP, ...). Newer Android may delay plain set() by
        // several minutes, so use the exact version when allowed (calendar apps are).
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.set(AlarmManager.RTC_WAKEUP, myAlarmTime, pendingIntent);
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, myAlarmTime, pendingIntent);
        }
    }

    /**
     * Short explanation for the Save message: when the reminder will go off,
     * or why there isn't one.
     */
    public static String describe(Context ctx, Task task) {
        if (task.isCompleted()) return "no reminder (task is completed)";
        if (task.notifyAmount <= 0) return "no reminder (Notification is off)";
        if (task.dueTime == null) return "no reminder (add a Due Time to get one)";
        LocalDateTime due = LocalDateTime.of(task.dueDate, task.dueTime);
        LocalDateTime at = due.minusMinutes(task.notifyAmount * UNIT_MILLIS[task.notifyUnit] / 60_000L);
        if (!at.isAfter(LocalDateTime.now())) return "no reminder (that time has already passed)";
        String pattern = DateFormat.is24HourFormat(ctx) ? "MMM d, HH:mm" : "MMM d, h:mm a";
        return "reminder at " + at.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()));
    }

    /** Sets every saved task's reminder - run when the app starts, so tasks saved
     *  earlier (or before the phone restarted) still get their alarms. */
    public static void scheduleAll(Context ctx) {
        for (Task t : Task.loadAll(ctx)) schedule(ctx, t);
    }

    /** Removes a task's pending reminder (deleted, completed, or time removed). */
    public static void cancel(Context ctx, String taskId) {
        Intent intent = new Intent(ctx, NotificationBroadcaster.class);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, notificationId(taskId), intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) {
            ((AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE)).cancel(pi);
            pi.cancel();
        }
    }

    /** Tutorial 5's buildNotification: title = task, text = when it's due. */
    private static Notification buildNotification(Context ctx, Task task) {
        String pattern = DateFormat.is24HourFormat(ctx) ? "HH:mm" : "h:mm a";
        String time = task.dueTime.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()));
        int unit = task.notifyUnit;
        String[] one = {"minute", "hour", "day", "week"};
        String[] many = {"minutes", "hours", "days", "weeks"};
        String in = task.notifyAmount + " " + (task.notifyAmount == 1 ? one[unit] : many[unit]);

        // tapping the notification opens the app
        Intent open = new Intent(ctx, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openApp = PendingIntent.getActivity(ctx, notificationId(task.id), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle(task.title)
                .setContentText("Due in " + in + " (at " + time + ")")
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .build();
    }

    /** One stable number per task, so each task has its own notification + alarm. */
    private static int notificationId(String taskId) {
        return taskId.hashCode();
    }
}
