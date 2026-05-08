package com.overlay;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class OverlayView extends LinearLayout {

    // --- COLORS (MATCHING JNI.ZIP) ---
    private static final int COLOR_BG      = Color.argb(245, 15, 15, 15);
    private static final int COLOR_HEADER  = Color.argb(255, 30, 30, 30);
    private static final int COLOR_ACCENT  = Color.parseColor("#00E676"); // Green
    private static final int COLOR_TEXT    = Color.WHITE;
    private static final int COLOR_OFF     = Color.parseColor("#FF5252"); // Red

    private final WindowManager windowManager;
    private final WindowManager.LayoutParams layoutParams;
    private final SharedMemoryManager shm;

    private LinearLayout panelExpanded;
    private TextView tvCollapsed;
    private boolean isExpanded = false;

    public OverlayView(Context context, WindowManager wm, WindowManager.LayoutParams params) {
        super(context);
        this.windowManager = wm;
        this.layoutParams = params;
        this.shm = new SharedMemoryManager(context);

        setOrientation(VERTICAL);
        buildCollapsedPill(context);
        buildExpandedPanel(context);
        
        showCollapsed();
        
        // Start Radar Refresh Loop
        postDelayed(new Runnable() {
            @Override
            public void run() {
                invalidate(); // Redraw Radar
                postDelayed(this, 33); // ~30 FPS
            }
        }, 100);
    }

    private void buildCollapsedPill(Context ctx) {
        tvCollapsed = new TextView(ctx);
        tvCollapsed.setText("⚡ MOD MENU");
        tvCollapsed.setTextColor(COLOR_ACCENT);
        tvCollapsed.setTextSize(12f);
        tvCollapsed.setTypeface(null, Typeface.BOLD);
        tvCollapsed.setPadding(30, 20, 30, 20);
        tvCollapsed.setBackgroundColor(COLOR_HEADER);
        tvCollapsed.setOnClickListener(v -> showExpanded());
        tvCollapsed.setOnTouchListener(dragListener);
        addView(tvCollapsed);
    }

    private void buildExpandedPanel(Context ctx) {
        panelExpanded = new LinearLayout(ctx);
        panelExpanded.setOrientation(VERTICAL);
        panelExpanded.setBackgroundColor(COLOR_BG);
        panelExpanded.setMinimumWidth(dp(240));

        // --- HEADER ---
        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("MOD MENU v1.0");
        tvTitle.setTextColor(COLOR_ACCENT);
        tvTitle.setGravity(android.view.Gravity.CENTER);
        tvTitle.setPadding(0, dp(15), 0, dp(15));
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setBackgroundColor(COLOR_HEADER);
        panelExpanded.addView(tvTitle);

        // --- FEATURES ---
        panelExpanded.addView(buildToggle(ctx, "Aimbot", on -> shm.setAimbot(on)));
        panelExpanded.addView(buildToggle(ctx, "Auto Retribution", on -> shm.setAutoRetri(on)));
        panelExpanded.addView(buildToggle(ctx, "Radar Hack", on -> { /* Logic in onDraw */ }));

        // --- TELEGRAM ---
        TextView btnTele = new TextView(ctx);
        btnTele.setText("✈ JOIN TELEGRAM");
        btnTele.setTextColor(Color.parseColor("#2196F3"));
        btnTele.setGravity(android.view.Gravity.CENTER);
        btnTele.setPadding(0, dp(12), 0, dp(12));
        btnTele.setTypeface(null, Typeface.BOLD);
        btnTele.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/your_channel"));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        });
        panelExpanded.addView(btnTele);

        // --- MINIMIZE ---
        TextView btnMin = new TextView(ctx);
        btnMin.setText("MINIMIZE");
        btnMin.setTextColor(Color.GRAY);
        btnMin.setGravity(android.view.Gravity.CENTER);
        btnMin.setPadding(0, dp(10), 0, dp(10));
        btnMin.setOnClickListener(v -> showCollapsed());
        panelExpanded.addView(btnMin);

        panelExpanded.setVisibility(GONE);
        addView(panelExpanded);
    }

    private View buildToggle(Context ctx, String name, final ToggleCallback cb) {
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

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // --- RADAR DRAWING LOGIC ---
        if (shm.isBattleStarted()) {
            Paint p = new Paint();
            p.setColor(Color.RED);
            p.setStyle(Paint.Style.FILL);
            
            // Contoh menggambar titik radar (nanti sesuaikan dengan koordinat dari SHM)
            // canvas.drawCircle(x, y, 10, p);
        }
    }

    // --- HELPERS & DRAG LOGIC (SAME AS BEFORE) ---
    private void showCollapsed() { isExpanded = false; panelExpanded.setVisibility(GONE); tvCollapsed.setVisibility(VISIBLE); }
    private void showExpanded() { isExpanded = true; tvCollapsed.setVisibility(GONE); panelExpanded.setVisibility(VISIBLE); }
    private int dp(int v) { return (int)(v * getContext().getResources().getDisplayMetrics().density); }
    
    private float touchStartX, touchStartY;
    private int initX, initY;
    private final OnTouchListener dragListener = (v, e) -> {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN: touchStartX = e.getRawX(); touchStartY = e.getRawY(); initX = layoutParams.x; initY = layoutParams.y; return true;
            case MotionEvent.ACTION_MOVE:
                layoutParams.x = initX + (int)(e.getRawX() - touchStartX);
                layoutParams.y = initY + (int)(e.getRawY() - touchStartY);
                windowManager.updateViewLayout(this, layoutParams);
                return true;
        }
        return false;
    };
    interface ToggleCallback { void onToggle(boolean on); }
}
