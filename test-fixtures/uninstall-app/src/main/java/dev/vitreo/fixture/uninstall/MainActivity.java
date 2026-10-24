package dev.vitreo.fixture.uninstall;

public final class MainActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        if (getIntent().getBooleanExtra("postNotifications", false)) {
            android.app.NotificationManager manager = getSystemService(android.app.NotificationManager.class);
            manager.createNotificationChannel(new android.app.NotificationChannel("integration", "Integration tests", android.app.NotificationManager.IMPORTANCE_DEFAULT));
            android.app.PendingIntent open = android.app.PendingIntent.getActivity(this, 0,
                new android.content.Intent(this, MainActivity.class).setAction("dev.vitreo.fixture.OPEN_NOTIFICATION")
                    .putExtra("fromNotification",true).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP), android.app.PendingIntent.FLAG_IMMUTABLE);
            manager.notify(21, new android.app.Notification.Builder(this, "integration")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Native fixture alert")
                .setContentText("A real Android notification displayed by Vitreo.").setContentIntent(open).build());
            manager.notify(22, new android.app.Notification.Builder(this, "integration")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Native ongoing alert")
                .setContentText("This ongoing alert cannot be cleared by Vitreo.").setOngoing(true).build());
            finish(); return;
        }
        android.widget.TextView label = new android.widget.TextView(this);
        label.setText(getIntent().getBooleanExtra("fromNotification",false) ? "Opened from notification" : "Disposable uninstall fixture");
        setContentView(label);
    }
}
