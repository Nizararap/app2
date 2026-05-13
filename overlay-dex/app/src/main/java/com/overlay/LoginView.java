package com.overlay;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
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
    private static final int C_BG = Color.argb(220, 10, 10, 15);
    private static final int C_ACCENT = Color.parseColor("#D4AF37"); // Muted Gold
    private static final int C_TEXT = Color.parseColor("#FFFFFF");
    private static final int C_SUBTEXT = Color.parseColor("#A0A0A0");
    private static final int C_CARD = Color.argb(170, 25, 25, 35);

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
    private boolean isAttached = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public LoginView(Context context, WindowManager wm, WindowManager.LayoutParams lp, Runnable onLoginSuccess) {
        super(context);
        this.wm = wm;
        this.lp = lp;
        this.onLoginSuccess = onLoginSuccess;
        this.authManager = new KeyAuthManager(context);

        setOrientation(VERTICAL);
        
        buildPill(context);
        buildUI(context);
        
        tvPill.setVisibility(GONE);
        loginCard.setVisibility(VISIBLE);
    }

    private void buildPill(Context ctx) {
        tvPill = new TextView(ctx);
        tvPill.setText("🔑 MONDEV");
        tvPill.setTextColor(C_ACCENT);
        tvPill.setTextSize(14f);
        tvPill.setTypeface(null, Typeface.BOLD);
        tvPill.setGravity(Gravity.CENTER);
        tvPill.setPadding(dp(15), dp(10), dp(15), dp(10));
        
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_BG);
        bg.setCornerRadius(dp(50));
        bg.setStroke(dp(1), C_ACCENT);
        tvPill.setBackground(bg);
        
        tvPill.setOnTouchListener(dragL);
        addView(tvPill);
    }

    private void buildUI(Context ctx) {
        loginCard = new LinearLayout(ctx);
        loginCard.setOrientation(VERTICAL);
        loginCard.setPadding(dp(25), dp(25), dp(25), dp(25));
        loginCard.setGravity(Gravity.CENTER_HORIZONTAL);
        
        try {
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(ctx.getAssets().open("background.jpg"));
            if (bmp != null) {
                android.graphics.Bitmap overlay = android.graphics.Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), bmp.getConfig());
                android.graphics.Canvas canvas = new android.graphics.Canvas(overlay);
                canvas.drawBitmap(bmp, 0, 0, null);
                canvas.drawColor(Color.argb(200, 0, 0, 0));
                android.graphics.drawable.BitmapDrawable bd = new android.graphics.drawable.BitmapDrawable(ctx.getResources(), overlay);
                loginCard.setBackground(bd);
            } else {
                loginCard.setBackgroundColor(C_BG);
            }
        } catch (Exception e) {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(C_BG);
            gd.setCornerRadius(dp(24));
            gd.setStroke(dp(1), C_ACCENT);
            loginCard.setBackground(gd);
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            loginCard.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(android.view.View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(24));
                }
            });
            loginCard.setClipToOutline(true);
        }

        LayoutParams cardLp = new LayoutParams(dp(300), LayoutParams.WRAP_CONTENT);
        loginCard.setLayoutParams(cardLp);

        // Header
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        header.setPadding(0, 0, 0, dp(20));
        
        TextView title = new TextView(ctx);
        title.setText("MONDEV");
        title.setTextColor(C_TEXT);
        title.setTextSize(22f);
        title.setLetterSpacing(0.1f);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        header.addView(title);

        TextView sub = new TextView(ctx);
        sub.setText("BETA ACCESS");
        sub.setTextColor(C_ACCENT);
        sub.setTextSize(10f);
        sub.setLetterSpacing(0.2f);
        header.addView(sub);
        
        loginCard.addView(header);

        // Input Area
        etKey = new EditText(ctx);
        etKey.setHint("Enter License Key");
        etKey.setHintTextColor(Color.GRAY);
        etKey.setTextColor(Color.WHITE);
        etKey.setTextSize(14f);
        etKey.setSingleLine(true);
        etKey.setPadding(dp(15), dp(12), dp(15), dp(12));
        etKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(C_CARD);
        inputBg.setCornerRadius(dp(12));
        inputBg.setStroke(dp(1), Color.argb(40, 255, 255, 255));
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
        btnLogin.setText("AUTHORIZE");
        btnLogin.setTextColor(Color.BLACK);
        btnLogin.setTypeface(null, Typeface.BOLD);
        btnLogin.setGravity(Gravity.CENTER);
        btnLogin.setPadding(0, dp(14), 0, dp(14));
        
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(C_ACCENT);
        btnBg.setCornerRadius(dp(12));
        btnLogin.setBackground(btnBg);
        btnLogin.setLetterSpacing(0.1f);
        
        btnLogin.setOnClickListener(v -> attemptLogin());
        loginCard.addView(btnLogin);

        // Footer
        TextView tvGet = new TextView(ctx);
        tvGet.setText("Request Access via Telegram");
        tvGet.setTextColor(C_ACCENT);
        tvGet.setTextSize(11f);
        tvGet.setPadding(0, dp(20), 0, 0);
        tvGet.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/modfreew"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        });
        loginCard.addView(tvGet);
        
        loginCard.setOnTouchListener(dragL);
        addView(loginCard);
    }

    private void attemptLogin() {
        String key = etKey.getText().toString().trim();
        if (key.isEmpty()) {
            Toast.makeText(getContext(), "Please enter a valid key", Toast.LENGTH_SHORT).show();
            return;
        }

        btnLogin.setVisibility(GONE);
        loader.setVisibility(VISIBLE);

        authManager.validateKey(key, new KeyAuthManager.AuthCallback() {
            @Override
            public void onResult(boolean success, String msg) {
                mainHandler.post(() -> {
                    loader.setVisibility(GONE);
                    btnLogin.setVisibility(VISIBLE);
                    if (success) {
                        Toast.makeText(getContext(), "Access Granted", Toast.LENGTH_SHORT).show();
                        if (onLoginSuccess != null) onLoginSuccess.run();
                    } else {
                        Toast.makeText(getContext(), "Access Denied: " + msg, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void showCollapsed() {
        loginCard.setVisibility(GONE);
        tvPill.setVisibility(VISIBLE);
        lp.width = LayoutParams.WRAP_CONTENT;
        lp.height = LayoutParams.WRAP_CONTENT;
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        wm.updateViewLayout(this, lp);
    }

    private OnTouchListener dragL = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    tx = e.getRawX(); ty = e.getRawY();
                    ix = lp.x; iy = lp.y;
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - tx;
                    float dy = e.getRawY() - ty;
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        lp.x = (int) (ix + dx);
                        lp.y = (int) (iy + dy);
                        wm.updateViewLayout(LoginView.this, lp);
                        dragging = true;
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!dragging) v.performClick();
                    return true;
            }
            return false;
        }
    };

    private int dp(int p) {
        return (int) (p * getContext().getResources().getDisplayMetrics().density);
    }
}
