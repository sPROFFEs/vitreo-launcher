package dev.vitreo.fixture.uninstall;

public final class MainActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        android.widget.TextView label = new android.widget.TextView(this);
        label.setText("Disposable uninstall fixture");
        setContentView(label);
    }
}
