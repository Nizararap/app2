package com.overlay;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
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

    private LinearLayout loginCard;
    private TextView tvPill;
    private EditText etKey;
    private TextView btnLogin;
    private ProgressBar loader;

    private float tx, ty;
    private int ix, iy;
    private boolean dragging;

    public LoginView(Context context, WindowManager wm, WindowManager.LayoutParams lp, Runnable onLoginSuccess) {
        super(context);
        this.wm = wm;
        this.lp = lp;
        this.onLoginSuccess = onLoginSuccess;
        this.authManager = new KeyAuthManager(context);

        setOrientation(VERTICAL);
        
        buildPill(context);
        buildUI(context);
        
        showExpanded();
    }

    private void buildPill(Context ctx) {
        tvPill = new TextView(ctx);
        tvPill.setText("🔑 VIP");
        tvPill.setTextColor(C_ACCENT);
        tvPill.setTextSize(14f);
        tvPill.setTypeface(null, Typeface.BOLD);
        tvPill.setGravity(Gravity.CENTER);
        tvPill.setPadding(dp(15), dp(10), dp(15), dp(10));
        
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_BG);
        bg.setCornerRadius(dp(50));
        bg.setStroke(dp(1), Color.argb(100, 0, 212, 255));
        tvPill.setBackground(bg);
        
        tvPill.setOnTouchListener(dragL);
        addView(tvPill);
    }

    private void buildUI(Context ctx) {
        loginCard = new LinearLayout(ctx);
        loginCard.setOrientation(VERTICAL);
        loginCard.setPadding(dp(20), dp(20), dp(20), dp(20));
        loginCard.setGravity(Gravity.CENTER_HORIZONTAL);
        
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(C_BG);
        gd.setCornerRadius(dp(15));
        gd.setStroke(dp(1), Color.argb(100, 0, 212, 255));
        loginCard.setBackground(gd);

        LayoutParams cardLp = new LayoutParams(dp(280), LayoutParams.WRAP_CONTENT);
        loginCard.setLayoutParams(cardLp);

        // Header with Minimize
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(15));
        
        TextView title = new TextView(ctx);
        title.setText("VIP LOGIN");
        title.setTextColor(C_ACCENT);
        title.setTextSize(18f);
        title.setTypeface(null, Typeface.BOLD);
        title.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        header.addView(title);
        
        TextView minBtn = new TextView(ctx);
        minBtn.setText("─");
        minBtn.setTextColor(C_SUBTEXT);
        minBtn.setPadding(dp(10), dp(5), dp(10), dp(5));
        minBtn.setOnClickListener(v -> showCollapsed());
        header.addView(minBtn);
        
        loginCard.addView(header);

        // Input Key
        etKey = new EditText(ctx);
        etKey.setHint("Enter VIP Key");
        etKey.setHintTextColor(Color.GRAY);
        etKey.setTextColor(Color.WHITE);
        etKey.setInputType(InputType.TYPE_CLASS_TEXT);
        etKey.setSingleLine(true);
        etKey.setPadding(dp(12), dp(10), dp(12), dp(10));
        
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(C_CARD);
        inputBg.setCornerRadius(dp(8));
        inputBg.setStroke(dp(1), Color.parseColor("#1E1E28"));
        etKey.setBackground(inputBg);
        
        LayoutParams etLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        etLp.setMargins(0, 0, 0, dp(15));
        etKey.setLayoutParams(etLp);
        loginCard.addView(etKey);

        // Loader
        loader = new ProgressBar(ctx, null, android.R.attr.progressBarStyleSmall);
        loader.setVisibility(GONE);
        loginCard.addView(loader);

        // Button Login
        btnLogin = new TextView(ctx);
        btnLogin.setText("LOGIN");
        btnLogin.setTextColor(Color.BLACK);
        btnLogin.setTypeface(null, Typeface.BOLD);
        btnLogin.setGravity(Gravity.CENTER);
        btnLogin.setPadding(0, dp(10), 0, dp(10));
        
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(C_ACCENT);
        btnBg.setCornerRadius(dp(8));
        btnLogin.setBackground(btnBg);
        
        btnLogin.setOnClickListener(v -> attemptLogin());
        loginCard.addView(btnLogin);

        // Footer
        LinearLayout footer = new LinearLayout(ctx);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(15), 0, 0);

        TextView tvGet = new TextView(ctx);
        tvGet.setText("Get Key");
        tvGet.setTextColor(C_ACCENT);
        tvGet.setTextSize(12f);
        tvGet.setPadding(dp(10), dp(5), dp(10), dp(5));
        tvGet.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/modfreew"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        });
        footer.addView(tvGet);
        
        loginCard.addView(footer);
        
        loginCard.setOnTouchListener(dragL);
        addView(loginCard);
    }

    private void attemptLogin() {
        String key = etKey.getText().toString().trim();
        if (key.isEmpty()) {
            Toast.makeText(getContext(), "Key required!", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        authManager.validateKey(key, new KeyAuthManager.AuthCallback() {
            @Override
            public void onSuccess() {
                setLoading(false);
                Toast.makeText(getContext(), "Success!", Toast.LENGTH_SHORT).show();
                onLoginSuccess.run();
            }

            @Override
            public void onFailure(String reason) {
                setLoading(false);
                Toast.makeText(getContext(), reason, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        btnLogin.setVisibility(loading ? GONE : VISIBLE);
        loader.setVisibility(loading ? VISIBLE : GONE);
        etKey.setEnabled(!loading);
    }

    private void showCollapsed() {
        loginCard.setVisibility(GONE);
        tvPill.setVisibility(VISIBLE);
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        wm.updateViewLayout(this, lp);
    }

    private void showExpanded() {
        tvPill.setVisibility(GONE);
        loginCard.setVisibility(VISIBLE);
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        wm.updateViewLayout(this, lp);
    }

    private int dp(int v) {
        return (int) (v * getContext().getResources().getDisplayMetrics().density);
    }

    private final OnTouchListener dragL = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    tx = e.getRawX(); ty = e.getRawY();
                    ix = lp.x; iy = lp.y;
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int)(e.getRawX() - tx);
                    int dy = (int)(e.getRawY() - ty);
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        dragging = true;
                        lp.x = ix + dx;
                        lp.y = iy + dy;
                        wm.updateViewLayout(LoginView.this, lp);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!dragging && v == tvPill) showExpanded();
                    else if (!dragging) v.performClick();
                    return true;
            }
            return false;
        }
    };
}
