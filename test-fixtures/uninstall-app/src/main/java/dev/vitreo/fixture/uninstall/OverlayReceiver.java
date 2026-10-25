package dev.vitreo.fixture.uninstall;

/** Disposable second-UID Aoede-style overlay + notifications, used only by emulator instrumentation. */
public final class OverlayReceiver extends android.content.BroadcastReceiver {
    private static android.view.View window;
    private static android.view.WindowManager manager;
    private static android.media.session.MediaSession session;
    @Override public void onReceive(android.content.Context context, android.content.Intent intent) {
        if ("notice".equals(intent.getStringExtra("operation"))) {
            android.app.NotificationManager notices = context.getSystemService(android.app.NotificationManager.class);
            notices.createNotificationChannel(new android.app.NotificationChannel("island", "Island fixture", android.app.NotificationManager.IMPORTANCE_LOW));
            if (intent.getBooleanExtra("cancel", false)) { notices.cancel(31); notices.cancel(32); if(session != null) { session.release();session=null; } return; }
            android.app.PendingIntent open = android.app.PendingIntent.getActivity(context, 31,
                new android.content.Intent(context, MainActivity.class).putExtra("fromNotification", true),android.app.PendingIntent.FLAG_IMMUTABLE);
            android.app.Notification.Builder builder = new android.app.Notification.Builder(context, "island")
                .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(intent.getStringExtra("title"))
                .setContentText("Notification island native integration fixture").setContentIntent(open);
            if (intent.getBooleanExtra("media",false)) {
                if(session == null) session=new android.media.session.MediaSession(context,"Overlay media fixture");
                builder.setStyle(new android.app.Notification.MediaStyle().setMediaSession(session.getSessionToken())).setOngoing(true);
            }
            notices.notify(intent.getBooleanExtra("media",false) ? 32 : 31, builder.build()); return;
        }
        if(window != null) { manager.removeViewImmediate(window);window=null;return; }
        if(!android.provider.Settings.canDrawOverlays(context)) return;
        android.view.Display display = context.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(0);
        android.content.Context overlay = context.createDisplayContext(display).createWindowContext(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null);
        manager = overlay.getSystemService(android.view.WindowManager.class);
        android.widget.FrameLayout frame = new android.widget.FrameLayout(overlay);
        frame.setFocusableInTouchMode(true);frame.setContentDescription("Foreign music island expanded");
        frame.setOnTouchListener((v,event) -> { if(event.getAction()==android.view.MotionEvent.ACTION_DOWN && window != null) {
            manager.removeViewImmediate(window);window=null;
        } return true; });
        android.widget.TextView card = new android.widget.TextView(overlay);card.setText("Music island fixture");card.setTextColor(android.graphics.Color.WHITE);card.setBackgroundColor(android.graphics.Color.BLACK);
        frame.addView(card,new android.widget.FrameLayout.LayoutParams(-1,180));
        android.view.WindowManager.LayoutParams params = new android.view.WindowManager.LayoutParams(-1,-1,
            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND,android.graphics.PixelFormat.TRANSLUCENT);
        params.dimAmount=.12f;params.setTitle("Foreign island fixture");
        manager.addView(frame,params);window=frame;frame.requestFocus();
    }
}
