package hu.chrys78.midea;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.view.Gravity;

import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView screen = new TextView(this);
        screen.setTextSize(22);
        screen.setGravity(Gravity.CENTER);
        screen.setPadding(30, 30, 30, 30);
        screen.setText("MIDEA\n\nPython rendszer indítása...");

        setContentView(screen);

        try {
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(this));
            }

            Python py = Python.getInstance();

            String result = py.getModule("midea_tv")
                    .callAttr("library_check")
                    .toString();

            screen.setText("MIDEA KÜLSŐ HŐMÉRSÉKLET\n\n" + result);

        } catch (Exception e) {
            screen.setText("HIBA:\n\n" + e.getMessage());
        }
    }
}
