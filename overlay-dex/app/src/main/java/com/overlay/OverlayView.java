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
        sendConfigToCpp(this.prefs);
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
        
        long rem = authManager.getRemainingTime();
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

    // ==================== DASHBOARD ====================
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
                sendConfigToCpp(prefs);
                refreshAllUI();
                radar.invalidate();
                android.widget.Toast.makeText(ctx, "All settings reset", android.widget.Toast.LENGTH_SHORT).show();
            }));
        }));
        return t;
    }

    // ==================== RADAR MAP ====================
    private LinearLayout buildRadar(Context ctx) {
        LinearLayout t = new LinearLayout(ctx); t.setOrientation(VERTICAL);
        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "RADAR"));
            l.addView(toggleRow(ctx, "Enable Radar", "Show minimap overlay", "radar_enable", false));
            l.addView(vgap(ctx, 4));
            l.addView(toggleRow(ctx, "Draw Border", "Border around radar", "radar_border", true));
        }));
        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "POSITIONING"));
            l.addView(slider(ctx, "X Position", "radar_pos_x", 0, 2000, 71));
            l.addView(slider(ctx, "Map Size", "radar_size", 80, 600, 338));
            l.addView(slider(ctx, "Icon Size", "radar_icon_size", 10, 100, 37));
            l.addView(btn(ctx, "Reset Defaults", C_BTN_DRK, () -> {
                prefs.edit().putFloat("radar_pos_x",71f).putFloat("radar_size",338f)
                        .putFloat("radar_icon_size",37f).apply();
                radar.invalidate();
            }));
        }));
        return t;
    }

    // ==================== COMBAT & AIM ====================
    private LinearLayout buildCombat(Context ctx) {
        LinearLayout t = new LinearLayout(ctx); t.setOrientation(VERTICAL);

        t.addView(card(ctx, l -> {
            LinearLayout cols = new LinearLayout(ctx); cols.setOrientation(HORIZONTAL);

            LinearLayout ac = new LinearLayout(ctx); ac.setOrientation(VERTICAL);
            ac.addView(secTitle(ctx, "AIM"));
            ac.addView(checkRow(ctx, "Aimbot All",   "aimbot_enable", false));
            ac.addView(vgap(ctx, 8));
            ac.addView(secTitle(ctx, "LING MODE"));
            ac.addView(radioRowVertical(ctx, "ling_mode", new String[]{"Off", "Manual", "Auto"}));

            cols.addView(ac, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

            View vd = new View(ctx);
            vd.setLayoutParams(new LayoutParams(dp(1), LayoutParams.MATCH_PARENT));
            vd.setBackgroundColor(C_DIVIDER); cols.addView(vd);

            LinearLayout rc = new LinearLayout(ctx); rc.setOrientation(VERTICAL);
            rc.setPadding(dp(10), 0, 0, 0);
            rc.addView(secTitle(ctx, "RETRIBUTION"));
            rc.addView(checkRow(ctx, "Buff",   "retri_buff",   false));
            rc.addView(checkRow(ctx, "Lord",   "retri_lord",   false));
            rc.addView(checkRow(ctx, "Turtle", "retri_turtle", false));
            rc.addView(checkRow(ctx, "Litho",  "retri_litho",  false));
            cols.addView(rc, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            l.addView(cols);
        }));

        // ---------- LOCK HERO ----------
        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "LOCK HERO"));
            l.addView(toggleRow(ctx, "Enable Hero Lock", "Prioritize specific target", "lock_hero_enable", false));
            
            String currentHero = prefs.getString("locked_hero_name", "");
            if (currentHero.isEmpty()) currentHero = "None";

            final TextView[] btnHeroRef = new TextView[1];
            
            btnHeroRef[0] = (TextView) btn(ctx, "Pilih Hero:[" + currentHero + "]", C_BTN_DRK, () -> {
                java.util.List<String> listHero = radar.getActiveEnemyNames();
                if (listHero.isEmpty()) {
                    android.widget.Toast.makeText(ctx, "Tidak ada musuh terdeteksi (Tunggu muncul di map)", android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    String[] items = listHero.toArray(new String[0]);
                    
                    android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(ctx, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                        .setTitle("Pilih Target Hero")
                        .setItems(items, (d, which) -> {
                            String selected = items[which];
                            prefs.edit().putString("locked_hero_name", selected).apply();
                            
                            if (btnHeroRef[0] != null) {
                                btnHeroRef[0].setText("Pilih Hero: [" + selected + "]");
                            }
                            sendConfigToCpp(prefs);
                        })
                        .create();
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        dialog.getWindow().setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
                    } else {
                        dialog.getWindow().setType(android.view.WindowManager.LayoutParams.TYPE_PHONE);
                    }
                    
                    dialog.show();
                }
            });
            l.addView(btnHeroRef[0]);
        }));

        // ---------- HERO COMBO ----------
        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "HERO COMBO"));
            
            String currentCombo = prefs.getString("selected_combo", "none");
            if (currentCombo.isEmpty() || currentCombo.equals("none")) currentCombo = "None";
            if (currentCombo.equals("gusion")) currentCombo = "Gusion";
            if (currentCombo.equals("kadita")) currentCombo = "Kadita";
            if (currentCombo.equals("beatrix")) currentCombo = "Beatrix";
            
            final TextView[] btnComboRef = new TextView[1];
            
            btnComboRef[0] = (TextView) btn(ctx, "Pilih Combo: [" + currentCombo + "]", C_BTN_DRK, () -> {
                String[] comboList = {"None", "Gusion", "Kadita", "Beatrix"};
                android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(ctx, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                    .setTitle("Pilih Hero Combo")
                    .setItems(comboList, (d, which) -> {
                        String selected = comboList[which];
                        String valueToSave = selected.equals("None") ? "none" : selected.toLowerCase();
                        prefs.edit().putString("selected_combo", valueToSave).apply();
                        
                        if (btnComboRef[0] != null) {
                            btnComboRef[0].setText("Pilih Combo: [" + selected + "]");
                        }
                        sendConfigToCpp(prefs);
                    })
                    .create();
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    dialog.getWindow().setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
                } else {
                    dialog.getWindow().setType(android.view.WindowManager.LayoutParams.TYPE_PHONE);
                }
                
                dialog.show();
            });
            l.addView(btnComboRef[0]);
        }));

        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "TARGET PRIORITY"));
            l.addView(radioRow(ctx, "aimbot_target", new String[]{"Nearest", "Low HP", "Low HP %"}));
        }));

        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "DETECTION"));
            l.addView(slider(ctx, "Aimbot FOV Range", "aimbot_fov", 0, 250, 200));
        }));
        return t;
    }

    // ==================== UI SCALE SLIDER ====================
    private View uiScaleSlider(Context ctx) {
        LinearLayout col = new LinearLayout(ctx); col.setOrientation(VERTICAL);
        col.setPadding(0, dp(4), 0, dp(4));

        LinearLayout labelRow = new LinearLayout(ctx); labelRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView ttl = new TextView(ctx); ttl.setText("UI Scale");
        ttl.setTextColor(C_TEXT); ttl.setTextSize(12f);
        ttl.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        labelRow.addView(ttl);

        float initScale = prefs.getFloat("ui_scale", 1.0f);
        TextView tvVal = new TextView(ctx);
        tvVal.setText(String.format("%.1fx", initScale));
        tvVal.setTextColor(C_ACCENT); tvVal.setTextSize(11f); tvVal.setTypeface(null, Typeface.BOLD);
        labelRow.addView(tvVal);
        col.addView(labelRow);

        TextView sub = new TextView(ctx); sub.setText("Scale window & text");
        sub.setTextColor(C_SUBTEXT); sub.setTextSize(10f); sub.setPadding(0, 0, 0, dp(4));
        col.addView(sub);

        SeekBar sb = new SeekBar(ctx);
        sb.setMax(20);
        sb.setProgress((int)(((initScale - 0.5f) / 0.05f)));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                float scale = 0.5f + (p * 0.05f);
                tvVal.setText(String.format("%.1fx", scale));
                prefs.edit().putFloat("ui_scale", scale).apply();
                applyUIScale(scale);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        col.addView(sb);

        post(() -> applyUIScale(initScale));
        return col;
    }

    private void applyUIScale(float scale) {
        if (panel == null) return;
        panel.setPivotX(0f);
        panel.setPivotY(0f);
        panel.setScaleX(scale);
        panel.setScaleY(scale);
    }

    private void refreshAllUI() {
        if (scrollView == null) return;
        FrameLayout frame = (FrameLayout) scrollView.getChildAt(0);
        if (frame == null) return;

        frame.removeAllViews();

        tabDash   = buildDash(getContext());
        tabRad    = buildRadar(getContext());
        tabCombat = buildCombat(getContext());

        frame.addView(tabDash);
        frame.addView(tabRad);
        frame.addView(tabCombat);

        switchTab(0);
    }

    // ==================== HELPER UI ====================
    interface CardB { void b(LinearLayout l); }

    private View card(Context ctx, CardB cb) {
        LinearLayout c = new LinearLayout(ctx); c.setOrientation(VERTICAL);
        c.setPadding(dp(12), dp(10), dp(12), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_CARD); bg.setCornerRadius(dp(10));
        c.setBackground(bg); cb.b(c);
        LayoutParams clp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, 0, 0, dp(6));
        c.setLayoutParams(clp);
        return c;
    }

    private View secTitle(Context ctx, String title) {
        LinearLayout r = new LinearLayout(ctx); r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, 0, 0, dp(8));
        View bar = new View(ctx);
        LayoutParams blp = new LayoutParams(dp(3), dp(12)); blp.setMargins(0,0,dp(7),0);
        bar.setLayoutParams(blp);
        GradientDrawable bbg = new GradientDrawable(); bbg.setColor(C_ACCENT); bbg.setCornerRadius(dp(4));
        bar.setBackground(bbg); r.addView(bar);
        TextView tv = new TextView(ctx); tv.setText(title); tv.setTextColor(C_ACCENT);
        tv.setTextSize(11f); tv.setTypeface(null, Typeface.BOLD); tv.setLetterSpacing(0.08f);
        r.addView(tv);
        return r;
    }

    private View toggleRow(Context ctx, String title, String sub, String key, boolean def) {
        LinearLayout r = new LinearLayout(ctx); r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(4), 0, dp(4));
        LinearLayout tc = new LinearLayout(ctx); tc.setOrientation(VERTICAL);
        tc.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView t1 = new TextView(ctx); t1.setText(title); t1.setTextColor(C_TEXT); t1.setTextSize(12f);
        tc.addView(t1);
        if (sub != null && !sub.isEmpty()) {
            TextView t2 = new TextView(ctx); t2.setText(sub); t2.setTextColor(C_SUBTEXT); t2.setTextSize(10f);
            tc.addView(t2);
        }
        r.addView(tc);
        r.addView(buildToggle(ctx, prefs.getBoolean(key, def), on -> {
            if (!authManager.isKeyValid()) {
                android.widget.Toast.makeText(getContext(), "VIP Key Expired! Please Relogin.", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putBoolean(key, on).apply();
            sendConfigToCpp(prefs);
            radar.invalidate();
        }));
        return r;
    }

    private View checkRow(Context ctx, String title, String key, boolean def) {
        LinearLayout r = new LinearLayout(ctx); r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(6), 0, dp(6));
        boolean init = prefs.getBoolean(key, def);
        final boolean[] st = {init};
        TextView dot = new TextView(ctx); dot.setTextSize(14f); dot.setPadding(0,0,dp(8),0);
        dot.setText(init ? "◉" : "○"); dot.setTextColor(init ? C_ACCENT : C_SUBTEXT);
        TextView lbl = new TextView(ctx); lbl.setText(title); lbl.setTextColor(C_TEXT); lbl.setTextSize(12f);
        r.addView(dot); r.addView(lbl);
        r.setOnClickListener(v -> {
            if (!authManager.isKeyValid()) {
                android.widget.Toast.makeText(getContext(), "VIP Key Expired! Please Relogin.", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            st[0] = !st[0];
            prefs.edit().putBoolean(key, st[0]).apply();
            sendConfigToCpp(prefs);
            dot.setText(st[0] ? "◉" : "○"); dot.setTextColor(st[0] ? C_ACCENT : C_SUBTEXT);
            radar.invalidate();
        });
        return r;
    }
    interface TCb { void t(boolean on); }

    private View buildToggle(Context ctx, boolean init, TCb cb) {
        final boolean[] on = {init};
        LinearLayout track = new LinearLayout(ctx);
        track.setGravity(init ? Gravity.END|Gravity.CENTER_VERTICAL : Gravity.START|Gravity.CENTER_VERTICAL);
        track.setPadding(dp(3), dp(3), dp(3), dp(3));
        track.setLayoutParams(new LayoutParams(dp(42), dp(24)));
        final GradientDrawable tbg = new GradientDrawable();
        tbg.setColor(init ? C_GREEN : C_SUBTEXT); tbg.setCornerRadius(dp(12));
        track.setBackground(tbg);
        View thumb = new View(ctx); thumb.setLayoutParams(new LayoutParams(dp(18), dp(18)));
        GradientDrawable thbg = new GradientDrawable(); thbg.setShape(GradientDrawable.OVAL);
        thbg.setColor(Color.WHITE); thumb.setBackground(thbg); track.addView(thumb);
        track.setOnClickListener(v -> {
            on[0] = !on[0];
            tbg.setColor(on[0] ? C_GREEN : C_SUBTEXT);
            track.setGravity(on[0] ? Gravity.END|Gravity.CENTER_VERTICAL
                                   : Gravity.START|Gravity.CENTER_VERTICAL);
            cb.t(on[0]);
        });
        return track;
    }

    private View slider(Context ctx, String title, String key, float min, float max, float def) {
        LinearLayout c = new LinearLayout(ctx); c.setOrientation(VERTICAL); c.setPadding(0, dp(4), 0, dp(4));
        LinearLayout lr = new LinearLayout(ctx); lr.setGravity(Gravity.CENTER_VERTICAL);
        TextView tt = new TextView(ctx); tt.setText(title); tt.setTextColor(C_SUBTEXT); tt.setTextSize(11f);
        tt.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)); lr.addView(tt);
        float cur = prefs.getFloat(key, def);
        TextView tv = new TextView(ctx); tv.setText(String.format("%.0f", cur));
        tv.setTextColor(C_ACCENT); tv.setTextSize(11f); tv.setTypeface(null, Typeface.BOLD); lr.addView(tv);
        c.addView(lr);
        SeekBar sb = new SeekBar(ctx); sb.setMax(100);
        sb.setProgress((int)(((cur-min)/(max-min))*100)); sb.setPadding(0, dp(4), 0, 0);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                float v = min+((max-min)*(p/100f)); prefs.edit().putFloat(key,v).apply();
                sendConfigToCpp(prefs);
                tv.setText(String.format("%.0f",v)); radar.invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        c.addView(sb); return c;
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
            sendConfigToCpp(prefs);
        });
        return rg;
    }

    private View radioRowVertical(Context ctx, String key, String[] opts) {
        RadioGroup rg = new RadioGroup(ctx);
        rg.setOrientation(VERTICAL);
        int cur = prefs.getInt(key, 0);
        for (int i = 0; i < opts.length; i++) {
            RadioButton rb = new RadioButton(ctx);
            rb.setText(opts[i]);
            rb.setTextColor(C_TEXT);
            rb.setTextSize(11.5f);
            rb.setId(i);
            rb.setPadding(0, 0, 0, 0);
            rb.setGravity(Gravity.CENTER_VERTICAL);
            if (i == cur) rb.setChecked(true);
            rg.addView(rb);
        }
        rg.setOnCheckedChangeListener((g,id) -> {
            prefs.edit().putInt(key,id).apply();
            sendConfigToCpp(prefs);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.START;
        rg.setLayoutParams(lp);
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

    // ==================== DRAG ====================
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

    // ==================== PENGIRIMAN SOCKET KE C++ ====================
    private void sendConfigToCpp(SharedPreferences prefs) {
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
                long expiry = authManager.getExpiryTimestamp(); // dari KeyAuthManager
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