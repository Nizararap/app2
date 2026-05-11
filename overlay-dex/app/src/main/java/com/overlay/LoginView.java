package com.overlay;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class LoginView extends LinearLayout {
    private static final int C_BG = Color.parseColor("#0D0D12");
    private static final int C_ACCENT = Color.parseColor("#00D4FF");
    private static final int C_TEXT = Color.parseColor("#EEEEF5");
    private static final int C_SUBTEXT = Color.parseColor("#66667A");
    private static final int C_CARD = Color.parseColor("#16161E");

    private final WindowManager wm;
    private final WindowManager.LayoutParams lp;
    private final KeyAuthManager authManager;
    private final Runnable onLoginSuccess;

    private EditText etKey;
    private TextView btnLogin;
    private ProgressBar loader;

    public LoginView(Context context, WindowManager wm, WindowManager.LayoutParams lp, Runnable onLoginSuccess) {
        super(context);
        this.wm = wm;
        this.lp = lp;
        this.onLoginSuccess = onLoginSuccess;
        this.authManager = new KeyAuthManager(context);

        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        
        // Background Dim
        setBackgroundColor(Color.argb(180, 0, 0, 0));

        buildUI(context);
    }

    private void buildUI(Context ctx) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(VERTICAL);
        card.setPadding(dp(25), dp(30), dp(25), dp(30));
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(C_BG);
        gd.setCornerRadius(dp(20));
        gd.setStroke(dp(1), Color.argb(100, 0, 212, 255));
        card.setBackground(gd);

        LayoutParams cardLp = new LayoutParams(dp(300), LayoutParams.WRAP_CONTENT);
        card.setLayoutParams(cardLp);

        // Title
        TextView title = new TextView(ctx);
        title.setText("VIP LOGIN");
        title.setTextColor(C_ACCENT);
        title.setTextSize(22f);
        title.setTypeface(null, Typeface.BOLD);
        card.addView(title);

        TextView sub = new TextView(ctx);
        sub.setText("Enter your key to access features");
        sub.setTextColor(C_SUBTEXT);
        sub.setTextSize(12f);
        sub.setPadding(0, 0, 0, dp(25));
        card.addView(sub);

        // Input Key
        etKey = new EditText(ctx);
        etKey.setHint("TZY-XXXXXXXX");
        etKey.setHintTextColor(Color.GRAY);
        etKey.setTextColor(Color.WHITE);
        etKey.setInputType(InputType.TYPE_CLASS_TEXT);
        etKey.setSingleLine(true);
        etKey.setPadding(dp(15), dp(12), dp(15), dp(12));
        
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(C_CARD);
        inputBg.setCornerRadius(dp(10));
        inputBg.setStroke(dp(1), Color.parseColor("#1E1E28"));
        etKey.setBackground(inputBg);
        
        LayoutParams etLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        etLp.setMargins(0, 0, 0, dp(20));
        etKey.setLayoutParams(etLp);
        card.addView(etKey);

        // Loader
        loader = new ProgressBar(ctx, null, android.R.attr.progressBarStyleSmall);
        loader.setVisibility(GONE);
        card.addView(loader);

        // Button Login
        btnLogin = new TextView(ctx);
        btnLogin.setText("LOGIN NOW");
        btnLogin.setTextColor(Color.BLACK);
        btnLogin.setTypeface(null, Typeface.BOLD);
        btnLogin.setGravity(Gravity.CENTER);
        btnLogin.setPadding(0, dp(12), 0, dp(12));
        
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(C_ACCENT);
        btnBg.setCornerRadius(dp(10));
        btnLogin.setBackground(btnBg);
        
        btnLogin.setOnClickListener(v -> attemptLogin());
        card.addView(btnLogin);

        // Get Key & Telegram
        LinearLayout footer = new LinearLayout(ctx);
        footer.setOrientation(HORIZONTAL);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(20), 0, 0);

        TextView tvGet = new TextView(ctx);
        tvGet.setText("Get Key");
        tvGet.setTextColor(C_ACCENT);
        tvGet.setPadding(dp(10), dp(5), dp(10), dp(5));
        tvGet.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/modfreew"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        });
        
        TextView tvTele = new TextView(ctx);
        tvTele.setText("Telegram");
        tvTele.setTextColor(C_ACCENT);
        tvTele.setPadding(dp(10), dp(5), dp(10), dp(5));
        tvTele.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/modfreew"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        });

        footer.addView(tvGet);
        footer.addView(new TextView(ctx) {{ setText("|"); setTextColor(C_SUBTEXT); }});
        footer.addView(tvTele);
        card.addView(footer);

        addView(card);
    }

    private void attemptLogin() {
        String key = etKey.getText().toString().trim();
        if (key.isEmpty()) {
            Toast.makeText(getContext(), "Key tidak boleh kosong!", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        authManager.validateKey(key, new KeyAuthManager.AuthCallback() {
            @Override
            public void onSuccess() {
                setLoading(false);
                Toast.makeText(getContext(), "Login Berhasil!", Toast.LENGTH_SHORT).show();
                onLoginSuccess.run();
            }

            @Override
            public void onFailure(String reason) {
                setLoading(false);
                Toast.makeText(getContext(), "Gagal: " + reason, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        btnLogin.setVisibility(loading ? GONE : VISIBLE);
        loader.setVisibility(loading ? VISIBLE : GONE);
        etKey.setEnabled(!loading);
    }

    private int dp(int v) {
        return (int) (v * getContext().getResources().getDisplayMetrics().density);
    }
}
