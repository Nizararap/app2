package com.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class OverlayView extends LinearLayout {

    private static final int C_BG      = Color.parseColor("#0D0D12");
    private static final int C_CARD    = Color.parseColor("#16161E");
    private static final int C_HEADER  = Color.parseColor("#0A0A10");
    private static final int C_ACCENT  = Color.parseColor("#00D4FF");
    private static final int C_GREEN   = Color.parseColor("#00E676");
    private static final int C_TEXT    = Color.parseColor("#EEEEF5");
    private static final int C_SUBTEXT = Color.parseColor("#66667A");
    private static final int C_DIVIDER = Color.parseColor("#1E1E28");
    private static final int C_BTN_BLU = Color.parseColor("#1565C0");
    private static final int C_BTN_DRK = Color.parseColor("#1E1E28");

    private final WindowManager wm;
    private final WindowManager.LayoutParams lp;
    private final RadarView radar;
    private final SharedPreferences prefs;
    private final KeyAuthManager authManager;
    private final Context context;

    private static final java.util.concurrent.ExecutorService socketExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    private int realScreenW;
    private int realScreenH;

    private float tx, ty;
    private int ix, iy;
    private boolean dragging;

    private LinearLayout panel, tabDash, tabRad, tabCombat;
    private TextView tvPill;
    private TextView[] tabBtns;
    private ScrollView scrollView;

    public OverlayView(Context ctx, WindowManager wm, WindowManager.LayoutParams lp, RadarView radar) {
        super(ctx);
        this.context = ctx;
        this.wm    = wm;
        this.lp    = lp;
        this.radar = radar;
        this.prefs = ctx.getSharedPreferences("mod_settings", Context.MODE_PRIVATE);
        this.authManager = new KeyAuthManager(ctx);
        fetchRealScreenSize();
        setOrientation(VERTICAL);
        buildPill(ctx);
        buildPanel(ctx);
        showExpanded();
        sendConfigToCpp(ctx, this.prefs);
    }

    @SuppressWarnings("deprecation")
    private void fetchRealScreenSize() {
        Display display = wm.getDefaultDisplay();
        DisplayMetrics realMetrics = new DisplayMetrics();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            display.getRealMetrics(realMetrics);
        } else {
            display.getMetrics(realMetrics);
        }
        realScreenW = realMetrics.widthPixels;
        realScreenH = realMetrics.heightPixels;
    }

    private void buildPill(Context ctx) {
        tvPill = new TextView(ctx);
        tvPill.setText("⚡");
        tvPill.setTextColor(C_ACCENT);
        tvPill.setTextSize(16f);
        tvPill.setGravity(Gravity.CENTER);
        tvPill.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_BG);
        bg.setCornerRadius(dp(50));
        bg.setStroke(dp(1), Color.argb(60, 0, 212, 255));
        tvPill.setBackground(bg);
        tvPill.setOnTouchListener(dragL);
        addView(tvPill);
    }

    private void buildPanel(Context ctx) {
        panel = new LinearLayout(ctx);
        panel.setOrientation(VERTICAL);
        panel.setMinimumWidth(dp(310));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_BG);
        bg.setCornerRadius(dp(14));
        bg.setStroke(1, Color.argb(40, 0, 212, 255));
        panel.setBackground(bg);
        panel.addView(buildHeader(ctx));
        panel.addView(buildTabs(ctx));
        panel.addView(buildContent(ctx));
        addView(panel);
        switchTab(0);
    }

    private View buildHeader(Context ctx) {
        LinearLayout h = new LinearLayout(ctx);
        h.setBackgroundColor(C_HEADER);
        h.setPadding(dp(14), dp(11), dp(10), dp(11));
        h.setGravity(Gravity.CENTER_VERTICAL);

        View bar = new View(ctx);
        LayoutParams blp = new LayoutParams(dp(3), dp(18));
        blp.setMargins(0, 0, dp(8), 0);
        bar.setLayoutParams(blp);
        GradientDrawable bbg = new GradientDrawable();
        bbg.setColor(C_ACCENT); bbg.setCornerRadius(dp(4));
        bar.setBackground(bbg);
        h.addView(bar);

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(VERTICAL);
        col.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView t1 = new TextView(ctx);
        t1.setText("MLBB Radar Premium");
        t1.setTextColor(C_TEXT); t1.setTextSize(13f); t1.setTypeface(null, Typeface.BOLD);
        col.addView(t1);
        
        long expiry = SecureSession.getSessionTimestamp(ctx) + (5 * 60 * 60 * 1000);
        long rem = Math.max(0, (expiry - System.currentTimeMillis()) / 1000);
        String timeStr = formatTime(rem);
        
        TextView t2 = new TextView(ctx);
        t2.setText("VIP Expire: " + timeStr);
        t2.setTextColor(C_ACCENT); t2.setTextSize(10f);
        col.addView(t2);
        h.addView(col);

        TextView minBtn = pillBtn(ctx, "─", C_SUBTEXT, C_BTN_DRK);
        minBtn.setOnClickListener(v -> showCollapsed());
        h.addView(minBtn);
        h.setOnTouchListener(dragL);
        return h;
    }

    private View buildTabs(Context ctx) {
        HorizontalScrollView hsv = new HorizontalScrollView(ctx);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setBackgroundColor(C_HEADER);
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(HORIZONTAL);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        String[] labels = {"Dashboard", "Radar Map", "Combat & Aim"};
        tabBtns = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            tabBtns[i] = new TextView(ctx);
            tabBtns[i].setText(labels[i]);
            tabBtns[i].setTextSize(11.5f); tabBtns[i].setTypeface(null, Typeface.BOLD);
            tabBtns[i].setPadding(dp(14), dp(6), dp(14), dp(6));
            LayoutParams tlp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            tlp.setMargins(0, 0, dp(4), 0);
            tabBtns[i].setLayoutParams(tlp);
            tabBtns[i].setOnClickListener(v -> switchTab(idx));
            bar.addView(tabBtns[i]);
        }
        hsv.addView(bar);
        return hsv;
    }

    private void switchTab(int idx) {
        for (int i = 0; i < tabBtns.length; i++) {
            boolean a = i == idx;
            tabBtns[i].setTextColor(a ? C_ACCENT : C_SUBTEXT);
            GradientDrawable tbg = new GradientDrawable();
            tbg.setColor(a ? Color.argb(30, 0, 212, 255) : Color.TRANSPARENT);
            tbg.setCornerRadius(dp(20));
            if (a) tbg.setStroke(1, Color.argb(80, 0, 212, 255));
            tabBtns[i].setBackground(tbg);
        }
        if (tabDash   != null) tabDash.setVisibility(idx == 0 ? VISIBLE : GONE);
        if (tabRad    != null) tabRad.setVisibility(idx == 1 ? VISIBLE : GONE);
        if (tabCombat != null) tabCombat.setVisibility(idx == 2 ? VISIBLE : GONE);
    }

    private View buildContent(Context ctx) {
        scrollView = new ScrollView(ctx);
        scrollView.setFillViewport(false);

        FrameLayout frame = new FrameLayout(ctx);
        frame.setPadding(dp(10), dp(8), dp(10), dp(10));

        tabDash   = buildDash(ctx);
        tabRad    = buildRadar(ctx);
        tabCombat = buildCombat(ctx);

        frame.addView(tabDash);
        frame.addView(tabRad);
        frame.addView(tabCombat);
        scrollView.addView(frame);

        scrollView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        int statusBarH = 0;
        try {
            int resId = ctx.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resId > 0) statusBarH = ctx.getResources().getDimensionPixelSize(resId);
        } catch (Exception ignored) {}
        final int maxScrollH = Math.max(dp(80), realScreenH - statusBarH - dp(100));

        scrollView.getViewTreeObserver().addOnGlobalLayoutListener(
            new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    scrollView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    if (scrollView.getHeight() > maxScrollH) {
                        scrollView.setLayoutParams(
                            new LayoutParams(LayoutParams.MATCH_PARENT, maxScrollH));
                    }
                }
            }
        );

        return scrollView;
    }

    private LinearLayout buildDash(Context ctx) {
        LinearLayout t = new LinearLayout(ctx); t.setOrientation(VERTICAL);

        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "MENU SYSTEM"));
            l.addView(uiScaleSlider(ctx));
            l.addView(vgap(ctx, 4));
            l.addView(toggleRow(ctx, "Lock Position", "Disable drag & move", "ui_lock", false));
        }));

        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "ACTIONS"));
            l.addView(btn(ctx, "Hide Menu", C_BTN_DRK, this::showCollapsed));
        }));

        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "CONFIG"));
            l.addView(btn(ctx, "Reset All Config", C_BTN_DRK, () -> {
                prefs.edit().clear().apply();
                sendConfigToCpp(ctx, prefs);
                refreshAllUI();
                radar.invalidate();
                android.widget.Toast.makeText(ctx, "All settings reset", android.widget.Toast.LENGTH_SHORT).show();
            }));
        }));
        return t;
    }

    private LinearLayout buildRadar(Context ctx) {
        LinearLayout t = new LinearLayout(ctx); t.setOrientation(VERTICAL);
        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "RADAR"));
            l.addView(toggleRow(ctx, "Show Radar", "Enable map radar", "radar_enable", false));
            l.addView(toggleRow(ctx, "Show Monster", "Enable jungle radar", "radar_monster", false));
            l.addView(toggleRow(ctx, "Show Line", "Enable ESP line", "radar_line", false));
        }));
        return t;
    }

    private LinearLayout buildCombat(Context ctx) {
        LinearLayout t = new LinearLayout(ctx); t.setOrientation(VERTICAL);
        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "AIMBOT"));
            l.addView(toggleRow(ctx, "Enable Aimbot", "Auto aim skill", "aimbot_enable", false));
            l.addView(secTitle(ctx, "Target Mode"));
            l.addView(radioRow(ctx, "aimbot_target", new String[]{"HP Terendah", "Jarak Terdekat"}));
            l.addView(secTitle(ctx, "FOV Range"));
            l.addView(sliderRow(ctx, "aimbot_fov", 50, 800, 200, "px"));
        }));

        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "HERO COMBO"));
            l.addView(radioRowVertical(ctx, "selected_combo", new String[]{"None", "Gusion", "Kadita", "Beatrix"}));
        }));

        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "AUTO RETRI"));
            l.addView(toggleRow(ctx, "Retri Buff", "Auto retri jungle", "retri_buff", false));
            l.addView(toggleRow(ctx, "Retri Lord", "Auto retri lord", "retri_lord", false));
            l.addView(toggleRow(ctx, "Retri Turtle", "Auto retri turtle", "retri_turtle", false));
        }));
        return t;
    }

    private View card(Context ctx, CardBuilder cb) {
        LinearLayout l = new LinearLayout(ctx); l.setOrientation(VERTICAL);
        l.setPadding(dp(12), dp(12), dp(12), dp(12));
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10)); l.setLayoutParams(lp);
        GradientDrawable gd = new GradientDrawable(); gd.setColor(C_CARD); gd.setCornerRadius(dp(10));
        l.setBackground(gd); cb.build(l); return l;
    }
    interface CardBuilder { void build(LinearLayout l); }

    private View secTitle(Context ctx, String text) {
        TextView tv = new TextView(ctx); tv.setText(text); tv.setTextColor(C_SUBTEXT);
        tv.setTextSize(10.5f); tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(0, 0, 0, dp(6)); return tv;
    }

    private View toggleRow(Context ctx, String title, String sub, String key, boolean def) {
        LinearLayout row = new LinearLayout(ctx); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        
        LinearLayout col = new LinearLayout(ctx); col.setOrientation(VERTICAL);
        col.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView t1 = new TextView(ctx); t1.setText(title); t1.setTextColor(C_TEXT); t1.setTextSize(13f);
        TextView t2 = new TextView(ctx); t2.setText(sub); t2.setTextColor(C_SUBTEXT); t2.setTextSize(10f);
        col.addView(t1); col.addView(t2); row.addView(col);

        boolean cur = prefs.getBoolean(key, def);
        TextView btn = pillBtn(ctx, cur ? "ON" : "OFF", cur ? Color.BLACK : C_SUBTEXT, cur ? C_ACCENT : C_BTN_DRK);
        btn.setOnClickListener(v -> {
            boolean newVal = !prefs.getBoolean(key, def);
            prefs.edit().putBoolean(key, newVal).apply();
            btn.setText(newVal ? "ON" : "OFF");
            btn.setTextColor(newVal ? Color.BLACK : C_SUBTEXT);
            ((GradientDrawable)btn.getBackground()).setColor(newVal ? C_ACCENT : C_BTN_DRK);
            sendConfigToCpp(ctx, prefs);
            if (radar != null) radar.invalidate();
        });
        row.addView(btn); return row;
    }

    private View sliderRow(Context ctx, String key, int min, int max, int def, String unit) {
        LinearLayout l = new LinearLayout(ctx); l.setOrientation(VERTICAL);
        l.setPadding(0, dp(4), 0, dp(4));
        final TextView valTv = new TextView(ctx); valTv.setTextColor(C_ACCENT); valTv.setTextSize(11f);
        float curF = (key.equals("aimbot_fov")) ? prefs.getFloat(key, (float)def) : prefs.getInt(key, def);
        valTv.setText((int)curF + " " + unit);
        
        SeekBar sb = new SeekBar(ctx); sb.setMax(max - min); sb.setProgress((int)curF - min);
        sb.setPadding(0, dp(10), 0, dp(10));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = progress + min;
                valTv.setText(val + " " + unit);
                if (key.equals("aimbot_fov")) prefs.edit().putFloat(key, (float)val).apply();
                else prefs.edit().putInt(key, val).apply();
                sendConfigToCpp(ctx, prefs);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        l.addView(valTv); l.addView(sb); return l;
    }

    private View uiScaleSlider(Context ctx) {
        LinearLayout l = new LinearLayout(ctx); l.setOrientation(VERTICAL);
        TextView t = new TextView(ctx); t.setText("Menu Scale"); t.setTextColor(C_TEXT); t.setTextSize(12f);
        l.addView(t);
        SeekBar sb = new SeekBar(ctx); sb.setMax(50); sb.setProgress(0);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float scale = 1.0f + (progress / 100.0f);
                panel.setScaleX(scale); panel.setScaleY(scale);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        l.addView(sb); return l;
    }

    private void refreshAllUI() {
        // Simple logic to redraw everything or re-init
        removeAllViews();
        buildPill(getContext());
        buildPanel(getContext());
        showExpanded();
    }

    private View radioRow(Context ctx, String key, String[] opts) {
        RadioGroup rg = new RadioGroup(ctx); rg.setOrientation(HORIZONTAL); rg.setPadding(0,dp(4),0,dp(4));
        int cur = prefs.getInt(key, 0);
        for (int i = 0; i < opts.length; i++) {
            RadioButton rb = new RadioButton(ctx); rb.setText(opts[i]); rb.setTextColor(C_TEXT);
            rb.setTextSize(11.5f); rb.setId(i);
            if (i == cur) rb.setChecked(true);
            rb.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            rg.addView(rb);
        }
        rg.setOnCheckedChangeListener((g,id) -> {
            prefs.edit().putInt(key,id).apply();
            sendConfigToCpp(ctx, prefs);
        });
        return rg;
    }

    private View radioRowVertical(Context ctx, String key, String[] opts) {
        RadioGroup rg = new RadioGroup(ctx);
        rg.setOrientation(VERTICAL);
        int cur = 0;
        String saved = prefs.getString(key, "none");
        if (saved.equals("gusion")) cur = 1;
        else if (saved.equals("kadita")) cur = 2;
        else if (saved.equals("beatrix")) cur = 3;

        for (int i = 0; i < opts.length; i++) {
            RadioButton rb = new RadioButton(ctx);
            rb.setText(opts[i]);
            rb.setTextColor(C_TEXT);
            rb.setTextSize(11.5f);
            rb.setId(i);
            if (i == cur) rb.setChecked(true);
            rg.addView(rb);
        }
        rg.setOnCheckedChangeListener((g,id) -> {
            String val = "none";
            if (id == 1) val = "gusion";
            else if (id == 2) val = "kadita";
            else if (id == 3) val = "beatrix";
            prefs.edit().putString(key, val).apply();
            sendConfigToCpp(ctx, prefs);
        });
        return rg;
    }

    private View btn(Context ctx, String text, int color, Runnable r) {
        TextView b = new TextView(ctx); b.setText(text); b.setTextColor(C_TEXT);
        b.setTextSize(12f); b.setGravity(Gravity.CENTER); b.setPadding(0, dp(9), 0, dp(9));
        b.setTypeface(null, Typeface.BOLD);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(dp(8));
        b.setBackground(bg);
        LayoutParams blp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        blp.setMargins(0, dp(4), 0, dp(4)); b.setLayoutParams(blp);
        b.setOnClickListener(v -> r.run()); return b;
    }

    private TextView pillBtn(Context ctx, String text, int tc, int bgC) {
        TextView tv = new TextView(ctx); tv.setText(text); tv.setTextColor(tc);
        tv.setTextSize(12f); tv.setGravity(Gravity.CENTER); tv.setPadding(dp(10),dp(6),dp(10),dp(6));
        GradientDrawable d = new GradientDrawable(); d.setColor(bgC); d.setCornerRadius(dp(6));
        tv.setBackground(d); return tv;
    }

    private View vgap(Context ctx, int dpVal) {
        View v = new View(ctx); v.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(dpVal)));
        return v;
    }

    private void showCollapsed() { panel.setVisibility(GONE); tvPill.setVisibility(VISIBLE); }
    private void showExpanded()  { tvPill.setVisibility(GONE); panel.setVisibility(VISIBLE); }
    private String formatTime(long seconds) {
        if (seconds <= 0) return "Expired";
        if (seconds > 86400) return (seconds / 86400) + " Hari";
        if (seconds > 3600) return (seconds / 3600) + " Jam";
        return (seconds / 60) + " Menit";
    }

    private int dp(int v) { return (int)(v * getContext().getResources().getDisplayMetrics().density); }

    private final OnTouchListener dragL = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            boolean locked = prefs.getBoolean("ui_lock", false);
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    tx = e.getRawX(); ty = e.getRawY();
                    ix = lp.x;       iy = lp.y;
                    dragging = false;
                    fetchRealScreenSize();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (locked) return true;
                    int dx = (int)(e.getRawX() - tx);
                    int dy = (int)(e.getRawY() - ty);
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                        dragging = true;
                        int viewW = getWidth();
                        int viewH = getHeight();
                        int maxX = (viewW > 0) ? realScreenW - viewW : realScreenW - dp(310);
                        int maxY = (viewH > 0) ? realScreenH - viewH : realScreenH - dp(200);
                        lp.x = Math.max(0, Math.min(ix + dx, maxX));
                        lp.y = Math.max(0, Math.min(iy + dy, maxY));
                        wm.updateViewLayout(OverlayView.this, lp);
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

    private static void sendConfigToCpp(Context context, SharedPreferences prefs) {
        socketExecutor.execute(() -> {
            try {
                android.net.LocalSocket socket = new android.net.LocalSocket();
                socket.connect(new android.net.LocalSocketAddress("mlbb_config_socket", android.net.LocalSocketAddress.Namespace.ABSTRACT));
                java.io.OutputStream out = socket.getOutputStream();
                
                // [MAGIC:4] [EXPIRY:8] [CONFIG_DATA:76] = 88 bytes
                java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(88);
                bb.order(java.nio.ByteOrder.LITTLE_ENDIAN);
                
                // 1. Security Header
                bb.putInt(0x4D4C4242); // MAGIC "MLBB"
                long expiry = SecureSession.getSessionTimestamp(context) + (5 * 60 * 60 * 1000);
                bb.putLong(expiry);

                // 2. Config Data
                int lingMode = prefs.getInt("ling_mode", 0);
                int lingManual = (lingMode == 1) ? 1 : 0;
                int lingAuto   = (lingMode == 2) ? 1 : 0;
                String selectedCombo = prefs.getString("selected_combo", "none");
                int activeCombo = 0;
                if (selectedCombo.equals("gusion")) activeCombo = 1;
                else if (selectedCombo.equals("kadita")) activeCombo = 2;
                else if (selectedCombo.equals("beatrix")) activeCombo = 3;

                bb.putInt(prefs.getBoolean("aimbot_enable", false) ? 1 : 0);
                bb.putInt(lingManual);
                bb.putInt(lingAuto);
                bb.putInt(activeCombo);
                bb.putInt(prefs.getInt("aimbot_target", 0));
                bb.putFloat(prefs.getFloat("aimbot_fov", 200f));
                bb.putInt(prefs.getBoolean("retri_buff", false) ? 1 : 0);
                bb.putInt(prefs.getBoolean("retri_lord", false) ? 1 : 0);
                bb.putInt(prefs.getBoolean("retri_turtle", false) ? 1 : 0);
                bb.putInt(prefs.getBoolean("retri_litho", false) ? 1 : 0);
                bb.putInt(prefs.getBoolean("lock_hero_enable", false) ? 1 : 0);
                
                String heroName = prefs.getString("locked_hero_name", "");
                byte[] nameBytes = heroName.getBytes();
                byte[] finalName = new byte[32];
                System.arraycopy(nameBytes, 0, finalName, 0, Math.min(nameBytes.length, 31));
                bb.put(finalName);

                out.write(bb.array());
                out.flush();
                socket.close();
            } catch (Exception ignored) {}
        });
    }
}
