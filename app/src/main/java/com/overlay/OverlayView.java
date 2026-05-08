package com.overlay;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class OverlayView extends LinearLayout {

    // ---------- warna ----------
    private static final int COLOR_BG      = Color.argb(245, 15, 15, 15);
    private static final int COLOR_HEADER  = Color.argb(255, 30, 30, 30);
    private static final int COLOR_ACCENT  = Color.parseColor("#00E676");
    private static final int COLOR_TEXT    = Color.WHITE;
    private static final int COLOR_OFF     = Color.parseColor("#FF5252");

    private WindowManager windowManager;
    private WindowManager.LayoutParams layoutParams;
    private SharedMemoryManager shm;

    private LinearLayout panelExpanded;
    private TextView tvCollapsed;
    private boolean isExpanded = false;

    // ---------- drag ----------
    private float touchStartX, touchStartY;
    private int initX, initY;
    private boolean isDragging = false;
    private static final int CLICK_THRESHOLD = 10; // px

    public OverlayView(Context context, WindowManager wm, WindowManager.LayoutParams params) {
        super(context);
        this.windowManager = wm;
        this.layoutParams = params;
        this.shm = new SharedMemoryManager(context);

        setOrientation(VERTICAL);
        buildCollapsedPill(context);
        buildExpandedPanel(context);

        showCollapsed();

        // refresh radar ~30 fps
        postDelayed(new Runnable() {
            @Override
            public void run() {
                invalidate();
                postDelayed(this, 33);
            }
        }, 100);
    }

    // -------------------- UI builder --------------------
    private void buildCollapsedPill(Context ctx) {
        tvCollapsed = new TextView(ctx);
        tvCollapsed.setText("⚡ MOD MENU");
        tvCollapsed.setTextColor(COLOR_ACCENT);
        tvCollapsed.setTextSize(12f);
        tvCollapsed.setTypeface(null, Typeface.BOLD);
        tvCollapsed.setPadding(30, 20, 30, 20);
        tvCollapsed.setBackgroundColor(COLOR_HEADER);
        tvCollapsed.setOnTouchListener(pillTouchListener);
        addView(tvCollapsed);
    }

    private void buildExpandedPanel(Context ctx) {
        panelExpanded = new LinearLayout(ctx);
        panelExpanded.setOrientation(VERTICAL);
        panelExpanded.setBackgroundColor(COLOR_BG);
        panelExpanded.setMinimumWidth(dp(240));

        // title
        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("MOD MENU v1.0");
        tvTitle.setTextColor(COLOR_ACCENT);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPadding(0, dp(15), 0, dp(15));
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setBackgroundColor(COLOR_HEADER);
        panelExpanded.addView(tvTitle);

        // toggles
        panelExpanded.addView(buildToggle(ctx, "Aimbot", on -> shm.setAimbot(on)));
        panelExpanded.addView(buildToggle(ctx, "Auto Retribution", on -> shm.setAutoRetri(on)));
        panelExpanded.addView(buildToggle(ctx, "Radar Hack", on -> { /* drawn in onDraw */ }));

        // telegram
        TextView btnTele = new TextView(ctx);
        btnTele.setText("✈ JOIN TELEGRAM");
        btnTele.setTextColor(Color.parseColor("#2196F3"));
        btnTele.setGravity(Gravity.CENTER);
        btnTele.setPadding(0, dp(12), 0, dp(12));
        btnTele.setTypeface(null, Typeface.BOLD);
        btnTele.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/your_channel"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        });
        panelExpanded.addView(btnTele);

        // minimize button
        TextView btnMin = new TextView(ctx);
        btnMin.setText("MINIMIZE");
        btnMin.setTextColor(Color.GRAY);
        btnMin.setGravity(Gravity.CENTER);
        btnMin.setPadding(0, dp(10), 0, dp(10));
        btnMin.setOnClickListener(v -> showCollapsed());
        panelExpanded.addView(btnMin);

        panelExpanded.setVisibility(GONE);
        addView(panelExpanded);
    }

    private View buildToggle(Context ctx, String name, ToggleCallback cb) {
        final TextView tv = new TextView(ctx);
        tv.setText(name + ": OFF");
        tv.setTextColor(COLOR_OFF);
        tv.setPadding(dp(15), dp(10), dp(15), dp(10));
        final boolean[] state = {false};
        tv.setOnClickListener(v -> {
            state[0] = !state[0];
            tv.setText(name + ": " + (state[0] ? "ON" : "OFF"));
            tv.setTextColor(state[0] ? COLOR_ACCENT : COLOR_OFF);
            cb.onToggle(state[0]);
        });
        return tv;
    }

    // -------------------- touch for pill (drag + tap) --------------------
    private final OnTouchListener pillTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchStartX = e.getRawX();
                    touchStartY = e.getRawY();
                    initX = layoutParams.x;
                    initY = layoutParams.y;
                    isDragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(e.getRawX() - touchStartX) > CLICK_THRESHOLD ||
                        Math.abs(e.getRawY() - touchStartY) > CLICK_THRESHOLD) {
                        isDragging = true;
                    }
                    if (isDragging) {
                        layoutParams.x = initX + (int)(e.getRawX() - touchStartX);
                        layoutParams.y = initY + (int)(e.getRawY() - touchStartY);
                        windowManager.updateViewLayout(OverlayView.this, layoutParams);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!isDragging) {
                        // tap -> toggle menu
                        if (isExpanded) showCollapsed(); else showExpanded();
                    }
                    return true;
            }
            return false;
        }
    };

    // -------------------- show / hide --------------------
    private void showCollapsed() {
        isExpanded = false;
        panelExpanded.setVisibility(GONE);
        tvCollapsed.setVisibility(VISIBLE);
    }
    private void showExpanded() {
        isExpanded = true;
        tvCollapsed.setVisibility(GONE);
        panelExpanded.setVisibility(VISIBLE);
    }

    // -------------------- radar drawing --------------------
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (shm == null || !shm.isBattleStarted()) return;

        int count = shm.getPlayerCount();
        if (count <= 0) return;

        Paint enemyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        enemyPaint.setStyle(Paint.Style.FILL);

        // sederhana: gambar lingkaran merah di posisi musuh yang sudah diskalakan
        for (int i = 0; i < count; i++) {
            if (!shm.isPlayerEnemy(i)) continue;  // hanya musuh
            float x = shm.getPlayerPosX(i);
            float y = shm.getPlayerPosY(i);
            // skalakan ke view (contoh statis, perlu world2screen sebenarnya)
            float sx = x * 0.02f + 100;   // dummy mapping
            float sy = y * 0.02f + 100;
            enemyPaint.setColor(Color.RED);
            canvas.drawCircle(sx, sy, 8, enemyPaint);
        }
    }

    // -------------------- helpers --------------------
    private int dp(int v) { return (int)(v * getContext().getResources().getDisplayMetrics().density); }

    interface ToggleCallback { void onToggle(boolean on); }
}