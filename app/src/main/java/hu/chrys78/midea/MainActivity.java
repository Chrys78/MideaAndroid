package hu.chrys78.midea;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.widget.ScrollView;
import android.util.Base64;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.chaquo.python.PyObject;

import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String KEY_ALIAS = "MideaCredentialsKey";
    private static final String PREFS = "midea_secure";

    private EditText emailInput;
    private EditText passwordInput;
    private Button startButton;

    private TextView temperatureText;
    private TextView statusText;
    private TextView updatedText;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private ScheduledExecutorService executor;
    private PyObject mideaModule;

    private boolean running = false;

    private static class EncryptedValue {
        String iv;
        String data;

        EncryptedValue(String iv, String data) {
            this.iv = iv;
            this.data = data;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }

        executor = Executors.newSingleThreadScheduledExecutor();

        createScreen();
        automaticLogin();
    }

    private void createScreen() {

        ScrollView scroll = new ScrollView(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.rgb(15, 20, 28));
        layout.setPadding(30, 35, 30, 35);

        TextView title = new TextView(this);
        title.setText("MIDEA");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);

        TextView subtitle = new TextView(this);
        subtitle.setText("KÜLSŐ HŐMÉRSÉKLET");
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setTextSize(17);
        subtitle.setGravity(Gravity.CENTER);

        emailInput = new EditText(this);
        emailInput.setHint("NetHome Plus e-mail cím");
        emailInput.setSingleLine(true);
        emailInput.setTextColor(Color.WHITE);
        emailInput.setHintTextColor(Color.LTGRAY);
        emailInput.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        );

        passwordInput = new EditText(this);
        passwordInput.setHint("Midea jelszó");
        passwordInput.setSingleLine(true);
        passwordInput.setTextColor(Color.WHITE);
        passwordInput.setHintTextColor(Color.LTGRAY);
        passwordInput.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        startButton = new Button(this);
        startButton.setText("BEJELENTKEZÉS");
        startButton.setOnClickListener(v -> manualLogin());

        temperatureText = new TextView(this);
        temperatureText.setText("--.- °C");
        temperatureText.setTextColor(
                Color.rgb(100, 220, 140)
        );
        temperatureText.setTextSize(58);
        temperatureText.setGravity(Gravity.CENTER);

        statusText = new TextView(this);
        statusText.setText("Midea bejelentkezés");
        statusText.setTextColor(Color.YELLOW);
        statusText.setTextSize(17);
        statusText.setGravity(Gravity.CENTER);

        updatedText = new TextView(this);
        updatedText.setText("Várakozás az adatokra");
        updatedText.setTextColor(Color.LTGRAY);
        updatedText.setTextSize(14);
        updatedText.setGravity(Gravity.CENTER);

        layout.addView(title);
        layout.addView(subtitle);

        addSpace(layout, 25);

        layout.addView(emailInput);
        layout.addView(passwordInput);
        layout.addView(startButton);

        addSpace(layout, 35);

        layout.addView(temperatureText);
        layout.addView(statusText);
        layout.addView(updatedText);

        scroll.addView(layout);
        setContentView(scroll);
    }

    private void addSpace(LinearLayout layout, int height) {
        View space = new View(this);

        layout.addView(
                space,
                new LinearLayout.LayoutParams(1, height)
        );
    }

    // AUTOMATIKUS BEJELENTKEZÉS

    private void automaticLogin() {

        String[] credentials = loadCredentials();

        if (credentials == null) {
            statusText.setText("Add meg a Midea belépési adatokat!");
            return;
        }

        emailInput.setText(credentials[0]);
        passwordInput.setText(credentials[1]);

        statusText.setText("Automatikus Midea bejelentkezés...");
        statusText.setTextColor(Color.YELLOW);

        attemptLogin(
                credentials[0],
                credentials[1]
        );
    }

    // KÉZI BEJELENTKEZÉS

    private void manualLogin() {

        String account =
                emailInput.getText().toString().trim();

        String password =
                passwordInput.getText().toString();

        if (account.isEmpty() || password.isEmpty()) {
            statusText.setText(
                    "Add meg az e-mail címet és a jelszót!"
            );
            statusText.setTextColor(Color.RED);
            return;
        }

        attemptLogin(account, password);
    }

    // MIDEA FELHŐKAPCSOLAT

    private void attemptLogin(
            String account,
            String password
    ) {

        if (running) return;

        running = true;

        startButton.setEnabled(false);
        statusText.setText("Kapcsolódás a Midea felhőhöz...");
        statusText.setTextColor(Color.YELLOW);

        executor.execute(() -> {

            try {

                mideaModule =
                        Python.getInstance().getModule("midea_tv");

                PyObject result = mideaModule.callAttr(
                        "login",
                        account,
                        password
                );

                // Sikeres bejelentkezés után titkosított mentés.
                saveCredentials(account, password);

                handler.post(() -> {

                    passwordInput.setText("");

                    emailInput.setVisibility(View.GONE);
                    passwordInput.setVisibility(View.GONE);
                    startButton.setVisibility(View.GONE);

                    statusText.setText(result.toString());
                    statusText.setTextColor(
                            Color.rgb(100, 220, 140)
                    );
                });

                // Első hőmérséklet-lekérdezés.
                loadTemperature();

                // Automatikus frissítés 5 másodpercenként.
                executor.scheduleWithFixedDelay(
                        this::loadTemperature,
                        5,
                        5,
                        TimeUnit.SECONDS
                );

            } catch (Exception e) {

                running = false;

                handler.post(() -> {

                    statusText.setText(
                            "Bejelentkezési hiba: " +
                            e.getMessage()
                    );

                    statusText.setTextColor(Color.RED);

                    emailInput.setVisibility(View.VISIBLE);
                    passwordInput.setVisibility(View.VISIBLE);
                    startButton.setVisibility(View.VISIBLE);

                    startButton.setEnabled(true);
                    startButton.setText("ÚJRA PRÓBÁLKOZÁS");
                });
            }
        });
    }

    // HŐMÉRSÉKLET LEKÉRDEZÉSE

    private void loadTemperature() {

        try {

            PyObject result =
                    mideaModule.callAttr("get_temperature");

            double temperature =
                    result.toDouble();

            handler.post(() -> {

                temperatureText.setText(
                        String.format(
                                Locale.getDefault(),
                                "%.1f °C",
                                temperature
                        )
                );

                statusText.setText("Midea kapcsolat rendben");
                statusText.setTextColor(
                        Color.rgb(100, 220, 140)
                );

                updatedText.setText(
                        "Utolsó frissítés: " +
                        new java.text.SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss",
                                Locale.getDefault()
                        ).format(new java.util.Date())
                );
            });

        } catch (Exception e) {

            handler.post(() -> {

                statusText.setText(
                        "Lekérdezési hiba: " + e.getMessage()
                );

                statusText.setTextColor(Color.RED);
            });
        }
    }

    // ANDROID KEYSTORE - TITKOSÍTÓKULCS

    private SecretKey getSecretKey() throws Exception {

        KeyStore keyStore =
                KeyStore.getInstance("AndroidKeyStore");

        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {

            return (SecretKey) keyStore.getKey(
                    KEY_ALIAS,
                    null
            );
        }

        KeyGenerator generator =
                KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        "AndroidKeyStore"
                );

        generator.init(
                new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT |
                        KeyProperties.PURPOSE_DECRYPT
                )
                .setBlockModes(
                        KeyProperties.BLOCK_MODE_GCM
                )
                .setEncryptionPaddings(
                        KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setRandomizedEncryptionRequired(true)
                .build()
        );

        return generator.generateKey();
    }

    // TITKOSÍTÁS

    private EncryptedValue encrypt(String value)
            throws Exception {

        Cipher cipher =
                Cipher.getInstance("AES/GCM/NoPadding");

        cipher.init(
                Cipher.ENCRYPT_MODE,
                getSecretKey()
        );

        byte[] encrypted = cipher.doFinal(
                value.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        return new EncryptedValue(
                Base64.encodeToString(
                        cipher.getIV(),
                        Base64.NO_WRAP
                ),
                Base64.encodeToString(
                        encrypted,
                        Base64.NO_WRAP
                )
        );
    }

    // VISSZAFEJTÉS

    private String decrypt(
            String iv,
            String data
    ) throws Exception {

        Cipher cipher =
                Cipher.getInstance("AES/GCM/NoPadding");

        GCMParameterSpec spec =
                new GCMParameterSpec(
                        128,
                        Base64.decode(iv, Base64.NO_WRAP)
                );

        cipher.init(
                Cipher.DECRYPT_MODE,
                getSecretKey(),
                spec
        );

        byte[] decrypted = cipher.doFinal(
                Base64.decode(data, Base64.NO_WRAP)
        );

        return new String(
                decrypted,
                java.nio.charset.StandardCharsets.UTF_8
        );
    }

    // BELÉPÉSI ADATOK TITKOSÍTOTT MENTÉSE

    private void saveCredentials(
            String email,
            String password
    ) throws Exception {

        EncryptedValue encryptedEmail =
                encrypt(email);

        EncryptedValue encryptedPassword =
                encrypt(password);

        getSharedPreferences(
                PREFS,
                MODE_PRIVATE
        )
        .edit()
        .putString("email_iv", encryptedEmail.iv)
        .putString("email_data", encryptedEmail.data)
        .putString("password_iv", encryptedPassword.iv)
        .putString("password_data", encryptedPassword.data)
        .commit();
    }

    // MENTETT BELÉPÉSI ADATOK BETÖLTÉSE

    private String[] loadCredentials() {

        try {

            SharedPreferences prefs =
                    getSharedPreferences(
                            PREFS,
                            MODE_PRIVATE
                    );

            String emailIv =
                    prefs.getString("email_iv", null);

            String emailData =
                    prefs.getString("email_data", null);

            String passwordIv =
                    prefs.getString("password_iv", null);

            String passwordData =
                    prefs.getString("password_data", null);

            if (emailIv == null ||
                emailData == null ||
                passwordIv == null ||
                passwordData == null) {

                return null;
            }

            String email = decrypt(
                    emailIv,
                    emailData
            );

            String password = decrypt(
                    passwordIv,
                    passwordData
            );

            return new String[]{
                    email,
                    password
            };

        } catch (Exception e) {

            getSharedPreferences(
                    PREFS,
                    MODE_PRIVATE
            ).edit().clear().apply();

            return null;
        }
    }

    @Override
    protected void onDestroy() {

        if (executor != null) {
            executor.shutdownNow();
        }

        handler.removeCallbacksAndMessages(null);

        super.onDestroy();
    }
}
