package com.overlay;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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

    // Modern Glassmorphism Palette - Refined for MONDEV
    private static final int C_BG      = Color.argb(220, 10, 10, 15); 
    private static final int C_CARD    = Color.argb(170, 20, 20, 30); 
    private static final int C_HEADER  = Color.argb(245, 5, 5, 10);
    private static final int C_ACCENT  = Color.parseColor("#D4AF37"); // Muted Gold (More Elegant)
    private static final int C_TELEGRAM = Color.parseColor("#0088cc"); // Telegram Blue
    private static final int C_TEXT    = Color.parseColor("#FFFFFF");
    private static final int C_SUBTEXT = Color.parseColor("#A0A0A0"); // Muted Gray for subtext
    private static final int C_DIVIDER = Color.argb(60, 212, 175, 55); // Muted Gold divider
    private static final int C_BTN_DRK = Color.argb(200, 30, 30, 45);

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
    private LinearLayout tabRoom;
    private LinearLayout roomTableContainer;
    private boolean isRoomSocketRunning = true;

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
        startRoomSocketThread();
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
        tvPill.setText("M");
        tvPill.setTextColor(Color.BLACK);
        tvPill.setTextSize(14f);
        tvPill.setTypeface(null, Typeface.BOLD);
        tvPill.setGravity(Gravity.CENTER);
        tvPill.setPadding(dp(12), dp(12), dp(12), dp(12));
        
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColors(new int[]{C_ACCENT, Color.parseColor("#8B7500")}); 
        bg.setOrientation(GradientDrawable.Orientation.TL_BR);
        bg.setStroke(dp(1), Color.argb(150, 255, 255, 255));
        
        tvPill.setBackground(bg);
        tvPill.setElevation(dp(6));
        tvPill.setOnTouchListener(dragL);
        
        LayoutParams pillLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        tvPill.setLayoutParams(pillLp);
        
        addView(tvPill);
    }

    private void buildPanel(Context ctx) {
        panel = new LinearLayout(ctx);
        panel.setOrientation(VERTICAL);
        
        LayoutParams panelLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        panel.setLayoutParams(panelLp);
        panel.setMinimumWidth(dp(340)); 

        try {
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(ctx.getAssets().open("background.jpg"));
            if (bmp != null) {
                android.graphics.Bitmap overlay = android.graphics.Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), bmp.getConfig());
                android.graphics.Canvas canvas = new android.graphics.Canvas(overlay);
                canvas.drawBitmap(bmp, 0, 0, null);
                canvas.drawColor(Color.argb(200, 0, 0, 0)); 
                
                android.graphics.drawable.BitmapDrawable bd = new android.graphics.drawable.BitmapDrawable(ctx.getResources(), overlay);
                panel.setBackground(bd);
            } else {
                panel.setBackgroundColor(C_BG);
            }
        } catch (Exception e) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(C_BG);
            bg.setCornerRadius(dp(20));
            bg.setStroke(dp(1), C_ACCENT);
            panel.setBackground(bg);
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            panel.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(android.view.View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(20));
                }
            });
            panel.setClipToOutline(true);
        }

        panel.addView(buildHeader(ctx));
        panel.addView(buildTabs(ctx));
        panel.addView(buildContent(ctx));
        addView(panel);
        switchTab(0);
    }

    private View buildHeader(Context ctx) {
        LinearLayout h = new LinearLayout(ctx);
        GradientDrawable hbg = new GradientDrawable();
        hbg.setColor(C_HEADER);
        hbg.setCornerRadii(new float[]{dp(20), dp(20), dp(20), dp(20), 0, 0, 0, 0});
        h.setBackground(hbg);
        h.setPadding(dp(20), dp(16), dp(16), dp(16));
        h.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(VERTICAL);
        col.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        
        LinearLayout titleRow = new LinearLayout(ctx);
        titleRow.setGravity(Gravity.BOTTOM);
        
        TextView t1 = new TextView(ctx);
        t1.setText("MONDEV");
        t1.setLetterSpacing(0.1f);
        t1.setTextColor(C_TEXT); t1.setTextSize(18f); t1.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        titleRow.addView(t1);
        
        TextView tBeta = new TextView(ctx);
        tBeta.setText(" beta");
        tBeta.setTextColor(C_ACCENT); tBeta.setTextSize(10f); tBeta.setTypeface(Typeface.create("sans-serif-light", Typeface.ITALIC));
        tBeta.setPadding(dp(4), 0, 0, dp(2));
        titleRow.addView(tBeta);
        
        col.addView(titleRow);
        
        long rem = authManager.getRemainingTime();
        String timeStr = formatTime(rem);
        
        TextView t2 = new TextView(ctx);
        t2.setText("Subscription: " + timeStr);
        t2.setTextColor(C_ACCENT); t2.setTextSize(10f);
        t2.setAlpha(0.8f);
        col.addView(t2);
        h.addView(col);

        TextView minBtn = new TextView(ctx);
        minBtn.setText("—");
        minBtn.setTextColor(C_TEXT);
        minBtn.setTextSize(18f);
        minBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
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
        bar.setPadding(dp(12), dp(4), dp(12), dp(12));
        String[] labels = {"DASHBOARD", "RADAR", "COMBAT", "ROOM"};
        tabBtns = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            tabBtns[i] = new TextView(ctx);
            tabBtns[i].setText(labels[i]);
            tabBtns[i].setTextSize(11f); 
            tabBtns[i].setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            tabBtns[i].setLetterSpacing(0.05f);
            tabBtns[i].setPadding(dp(16), dp(8), dp(16), dp(8));
            tabBtns[i].setGravity(Gravity.CENTER);
            LayoutParams tlp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            tlp.setMargins(0, 0, dp(8), 0);
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
            tabBtns[i].setTextColor(a ? Color.BLACK : C_SUBTEXT);
            GradientDrawable tbg = new GradientDrawable();
            tbg.setColor(a ? C_ACCENT : Color.TRANSPARENT);
            tbg.setCornerRadius(dp(8));
            tabBtns[i].setBackground(tbg);
        }
        if (tabDash != null) tabDash.setVisibility(idx == 0 ? VISIBLE : GONE);
        if (tabRad != null) tabRad.setVisibility(idx == 1 ? VISIBLE : GONE);
        if (tabCombat != null) tabCombat.setVisibility(idx == 2 ? VISIBLE : GONE);
        if (tabRoom != null) tabRoom.setVisibility(idx == 3 ? VISIBLE : GONE);
    }

    private View buildContent(Context ctx) {
        scrollView = new ScrollView(ctx);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setPadding(dp(14), dp(10), dp(14), dp(14));
        
        FrameLayout frame = new FrameLayout(ctx);
        tabDash   = buildDash(ctx);
        tabRad    = buildRadar(ctx);
        tabCombat = buildCombat(ctx);
        tabRoom   = buildRoomInfo(ctx);

        frame.addView(tabDash);
        frame.addView(tabRad);
        frame.addView(tabCombat);
        frame.addView(tabRoom);
        scrollView.addView(frame);

        int maxScrollH = (int)(realScreenH * 0.65f);
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
        LinearLayout t = new LinearLayout(ctx); 
        t.setOrientation(VERTICAL);

        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "SYSTEM SETTINGS"));
            l.addView(uiScaleSlider(ctx));
            l.addView(vgap(ctx, 8));
            l.addView(toggleRow(ctx, "Lock Position", "Disable menu dragging", "ui_lock", false));
        }));

        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "COMMUNITY & SUPPORT"));
            l.addView(btn(ctx, "JOIN TELEGRAM CHANNEL", C_TELEGRAM, () -> {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/modfreew"));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            }));
            l.addView(vgap(ctx, 10));
            l.addView(btn(ctx, "RESET ALL CONFIGURATIONS", C_BTN_DRK, () -> {
                prefs.edit().clear().apply();
                sendConfigToCpp(prefs);
                refreshAllUI();
                radar.invalidate();
                android.widget.Toast.makeText(ctx, "Settings reset to default", android.widget.Toast.LENGTH_SHORT).show();
            }));
        }));

        return t;
    }

    // ==================== RADAR MAP ====================
    private LinearLayout buildRadar(Context ctx) {
        LinearLayout t = new LinearLayout(ctx); 
        t.setOrientation(VERTICAL);

        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "MINIMAP RADAR"));
            l.addView(toggleRow(ctx, "Enable Radar", "Show enemy icons on minimap", "radar_enable", false));
            l.addView(vgap(ctx, 6));
            l.addView(toggleRow(ctx, "Draw Border", "Show border around radar", "radar_border", true));
        }));

        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "ADJUSTMENTS"));
            l.addView(slider(ctx, "X Position", "radar_pos_x", 0, 2000, 71));
            l.addView(slider(ctx, "Map Size", "radar_size", 80, 600, 338));
            l.addView(slider(ctx, "Icon Size", "radar_icon_size", 10, 100, 37));
            
            l.addView(vgap(ctx, 10));
            l.addView(btn(ctx, "RESET RADAR POSITION", C_BTN_DRK, () -> {
                prefs.edit().putFloat("radar_pos_x",71f)
                    .putFloat("radar_size",338f)
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
            ac.addView(secTitle(ctx, "AIMBOT"));
            ac.addView(checkRow(ctx, "Enable Aimbot", "aimbot_enable", false));
            ac.addView(vgap(ctx, 12));
            ac.addView(secTitle(ctx, "LING MODE"));
            ac.addView(radioRowVertical(ctx, "ling_mode", new String[]{"Disabled", "Manual", "Auto"}));

            cols.addView(ac, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

            View vd = new View(ctx);
            vd.setLayoutParams(new LayoutParams(dp(1), LayoutParams.MATCH_PARENT));
            vd.setBackgroundColor(C_DIVIDER); cols.addView(vd);

            LinearLayout rc = new LinearLayout(ctx); rc.setOrientation(VERTICAL);
            rc.setPadding(dp(12), 0, 0, 0);
            rc.addView(secTitle(ctx, "AUTO RETRIBUTION"));
            rc.addView(checkRowColored(ctx, "Buff (Blue)", "retri_buff", false, Color.parseColor("#42A5F5")));
            rc.addView(checkRowColored(ctx, "Buff (Red)", "retri_red_buff", false, Color.parseColor("#EF5350")));
            rc.addView(checkRow(ctx, "Lord", "retri_lord", false));
            rc.addView(checkRow(ctx, "Turtle", "retri_turtle", false));
            rc.addView(checkRow(ctx, "Litho", "retri_litho", false));
            cols.addView(rc, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            l.addView(cols);
        }));

        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "TARGET LOCKING"));
            l.addView(toggleRow(ctx, "Hero Lock System", "Focus on specific target", "lock_hero_enable", false));
            
            String currentHero = prefs.getString("locked_hero_name", "");
            if (currentHero.isEmpty()) currentHero = "None";

            final TextView[] btnHeroRef = new TextView[1];
            btnHeroRef[0] = (TextView) btn(ctx, "Target Hero: [" + currentHero + "]", C_BTN_DRK, () -> {
                java.util.List<String> listHero = radar.getActiveEnemyNames();
                if (listHero.isEmpty()) {
                    android.widget.Toast.makeText(ctx, "No enemies detected!", android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    String[] items = listHero.toArray(new String[0]);
                    showModernDialog(ctx, "SELECT TARGET HERO", items, selected -> {
                        prefs.edit().putString("locked_hero_name", selected).apply();
                        if (btnHeroRef[0] != null) btnHeroRef[0].setText("Target Hero: [" + selected + "]");
                        sendConfigToCpp(prefs);
                    });
                }
            });
            l.addView(btnHeroRef[0]);
        }));

        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "HERO SPECIALS"));
            
            String currentCombo = prefs.getString("selected_combo", "none");
            String displayCombo = "None";
            
            if (currentCombo.contains("gusion")) displayCombo = "Gusion";
            else if (currentCombo.contains("kadita")) displayCombo = "Kadita";
            else if (currentCombo.contains("beatrix")) displayCombo = "Beatrix (Ultimate Precision)";
            else if (currentCombo.contains("kimmy")) displayCombo = "Kimmy (Experimental)";
            
            final TextView[] btnComboRef = new TextView[1];
            btnComboRef[0] = (TextView) btn(ctx, "Active Combo: [" + displayCombo + "]", C_BTN_DRK, () -> {
                String[] comboList = {"None", "Gusion", "Kadita", "Beatrix (Ultimate Precision)", "Kimmy (Experimental)"};
                showModernDialog(ctx, "SELECT HERO COMBO", comboList, selected -> {
                    String valueToSave = selected.equals("None") ? "none" : selected.toLowerCase();
                    prefs.edit().putString("selected_combo", valueToSave).apply();
                    if (btnComboRef[0] != null) btnComboRef[0].setText("Active Combo: [" + selected + "]");
                    sendConfigToCpp(prefs);
                });
            });
            l.addView(btnComboRef[0]);
        }));

        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "PRIORITY & DETECTION"));
            l.addView(radioRow(ctx, "aimbot_target", new String[]{"Nearest", "Low HP", "Low HP %"}));
            l.addView(vgap(ctx, 8));
            l.addView(slider(ctx, "Aimbot FOV Range", "aimbot_fov", 0, 250, 200));
        }));
        return t;
    }

    private LinearLayout buildRoomInfo(Context ctx) {
        LinearLayout t = new LinearLayout(ctx);
        t.setOrientation(VERTICAL);
        
        t.addView(card(ctx, l -> {
            l.addView(secTitle(ctx, "ROOM MONITOR"));
            l.addView(toggleRow(ctx, "Enable Room Info", "View enemy ranks and data", "room_info_enable", false));
        }));

        roomTableContainer = new LinearLayout(ctx);
        roomTableContainer.setOrientation(VERTICAL);
        t.addView(roomTableContainer);

        return t;
    }

    // ==================== UI SCALE SLIDER ====================
    private View uiScaleSlider(Context ctx) {
        LinearLayout col = new LinearLayout(ctx); col.setOrientation(VERTICAL);
        col.setPadding(0, dp(4), 0, dp(4));

        LinearLayout labelRow = new LinearLayout(ctx); labelRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView ttl = new TextView(ctx); ttl.setText("Interface Scale");
        ttl.setTextColor(C_TEXT); ttl.setTextSize(12f);
        ttl.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        labelRow.addView(ttl);

        float initScale = prefs.getFloat("ui_scale", 1.0f);
        TextView tvVal = new TextView(ctx);
        tvVal.setText(String.format("%.1fx", initScale));
        tvVal.setTextColor(C_ACCENT); tvVal.setTextSize(11f); tvVal.setTypeface(null, Typeface.BOLD);
        labelRow.addView(tvVal);
        col.addView(labelRow);

        TextView sub = new TextView(ctx); sub.setText("Adjust window and text size");
        sub.setTextColor(C_SUBTEXT); sub.setTextSize(10f); sub.setPadding(0, 0, 0, dp(6));
        col.addView(sub);

        SeekBar sb = new SeekBar(ctx);
        sb.setMax(20);
        sb.setProgress((int)(((initScale - 0.5f) / 0.05f)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            sb.setProgressTintList(android.content.res.ColorStateList.valueOf(C_ACCENT));
            sb.setThumbTintList(android.content.res.ColorStateList.valueOf(C_ACCENT));
        }
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

        Context ctx = getContext();
        tabDash   = buildDash(ctx);
        tabRad    = buildRadar(ctx);
        tabCombat = buildCombat(ctx);
        tabRoom   = buildRoomInfo(ctx);

        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        tabDash.setLayoutParams(flp);
        tabRad.setLayoutParams(flp);
        tabCombat.setLayoutParams(flp);
        tabRoom.setLayoutParams(flp);

        frame.addView(tabDash);
        frame.addView(tabRad);
        frame.addView(tabCombat);
        frame.addView(tabRoom);

        switchTab(0);
    }

    // ==================== HELPER UI ====================
    interface CardB { void b(LinearLayout l); }

    private View card(Context ctx, CardB cb) {
        LinearLayout c = new LinearLayout(ctx); c.setOrientation(VERTICAL);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_CARD); 
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Color.argb(20, 255, 255, 255));
        c.setBackground(bg); cb.b(c);
        LayoutParams clp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, 0, 0, dp(14));
        c.setLayoutParams(clp);
        return c;
    }

    private View secTitle(Context ctx, String title) {
        LinearLayout r = new LinearLayout(ctx); r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(2), 0, dp(14));
        View bar = new View(ctx);
        LayoutParams blp = new LayoutParams(dp(4), dp(14)); blp.setMargins(0,0,dp(10),0);
        bar.setLayoutParams(blp);
        GradientDrawable bbg = new GradientDrawable(); bbg.setColor(C_ACCENT); bbg.setCornerRadius(dp(100));
        bar.setBackground(bbg); r.addView(bar);
        TextView tv = new TextView(ctx); tv.setText(title.toUpperCase()); tv.setTextColor(C_ACCENT);
        tv.setTextSize(10.5f); tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD)); tv.setLetterSpacing(0.1f);
        r.addView(tv);
        return r;
    }

    private View toggleRow(Context ctx, String title, String sub, String key, boolean def) {
        LinearLayout r = new LinearLayout(ctx); r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(4), 0, dp(4));
        LinearLayout tc = new LinearLayout(ctx); tc.setOrientation(VERTICAL);
        tc.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView t1 = new TextView(ctx); t1.setText(title); t1.setTextColor(C_TEXT); t1.setTextSize(12.5f);
        tc.addView(t1);
        if (sub != null && !sub.isEmpty()) {
            TextView t2 = new TextView(ctx); t2.setText(sub); t2.setTextColor(C_SUBTEXT); t2.setTextSize(10f);
            tc.addView(t2);
        }
        r.addView(tc);
        r.addView(buildToggle(ctx, prefs.getBoolean(key, def), on -> {
            if (!authManager.isKeyValid()) {
                android.widget.Toast.makeText(getContext(), "Session Expired! Please Login again.", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putBoolean(key, on).apply();
            sendConfigToCpp(prefs);
            radar.invalidate();
        }));
        return r;
    }

    private View checkRow(Context ctx, String title, String key, boolean def) {
        return checkRowColored(ctx, title, key, def, C_ACCENT);
    }

    private View checkRowColored(Context ctx, String title, String key, boolean def, int color) {
        LinearLayout r = new LinearLayout(ctx); 
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(7), 0, dp(7));
        
        boolean init = prefs.getBoolean(key, def);
        final boolean[] st = {init};

        TextView box = new TextView(ctx);
        box.setLayoutParams(new LayoutParams(dp(20), dp(20)));
        box.setGravity(Gravity.CENTER);
        box.setTextSize(12f);
        box.setTypeface(null, Typeface.BOLD);

        Runnable updateBox = () -> {
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(5));
            if (st[0]) {
                bg.setColor(color);
                box.setText("✓");
                box.setTextColor(C_BG);
            } else {
                bg.setColor(Color.TRANSPARENT);
                bg.setStroke(dp(1), C_SUBTEXT);
                box.setText("");
            }
            box.setBackground(bg);
        };
        updateBox.run();

        TextView lbl = new TextView(ctx); 
        lbl.setText(title); 
        lbl.setTextColor(C_TEXT); 
        lbl.setTextSize(12f);
        LayoutParams llp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        llp.setMargins(dp(12), 0, 0, 0);
        lbl.setLayoutParams(llp);

        r.addView(box); r.addView(lbl);
        r.setOnClickListener(v -> {
            if (!authManager.isKeyValid()) {
                android.widget.Toast.makeText(getContext(), "Session Expired! Please Login again.", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            st[0] = !st[0];
            prefs.edit().putBoolean(key, st[0]).apply();
            sendConfigToCpp(prefs);
            updateBox.run();
            radar.invalidate();
        });
        return r;
    }

    private View buildToggle(Context ctx, boolean init, TCb cb) {
        final boolean[] on = {init};
        LinearLayout track = new LinearLayout(ctx);
        track.setGravity(init ? Gravity.END|Gravity.CENTER_VERTICAL : Gravity.START|Gravity.CENTER_VERTICAL);
        track.setPadding(dp(4), dp(4), dp(4), dp(4));
        track.setLayoutParams(new LayoutParams(dp(46), dp(26)));
        
        final GradientDrawable tbg = new GradientDrawable();
        tbg.setColor(init ? C_ACCENT : Color.argb(50, 100, 100, 120)); 
        tbg.setCornerRadius(dp(100));
        track.setBackground(tbg);
        
        View thumb = new View(ctx); 
        thumb.setLayoutParams(new LayoutParams(dp(18), dp(18)));
        GradientDrawable thbg = new GradientDrawable(); 
        thbg.setShape(GradientDrawable.OVAL);
        thbg.setColor(Color.WHITE); 
        thumb.setBackground(thbg); 
        track.addView(thumb);
        
        track.setOnClickListener(v -> {
            on[0] = !on[0];
            tbg.setColor(on[0] ? C_ACCENT : Color.argb(50, 100, 100, 120));
            track.setGravity(on[0] ? Gravity.END|Gravity.CENTER_VERTICAL
                                   : Gravity.START|Gravity.CENTER_VERTICAL);
            cb.t(on[0]);
        });
        return track;
    }
    interface TCb { void t(boolean on); }

    private View slider(Context ctx, String title, String key, float min, float max, float def) {
        LinearLayout c = new LinearLayout(ctx); c.setOrientation(VERTICAL); c.setPadding(0, dp(6), 0, dp(6));
        
        LinearLayout lr = new LinearLayout(ctx); lr.setGravity(Gravity.CENTER_VERTICAL);
        TextView tt = new TextView(ctx); tt.setText(title); tt.setTextColor(C_SUBTEXT); tt.setTextSize(11f);
        tt.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)); lr.addView(tt);
        
        float cur = prefs.getFloat(key, def);
        TextView tv = new TextView(ctx); tv.setText(String.format("%.0f", cur));
        tv.setTextColor(C_ACCENT); tv.setTextSize(11f); tv.setTypeface(null, Typeface.BOLD); lr.addView(tv);
        c.addView(lr);

        SeekBar sb = new SeekBar(ctx); sb.setMax(100);
        sb.setProgress((int)(((cur-min)/(max-min))*100)); 
        sb.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        sb.setPadding(0, dp(10), 0, dp(10));
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            sb.setProgressTintList(android.content.res.ColorStateList.valueOf(C_ACCENT));
            sb.setThumbTintList(android.content.res.ColorStateList.valueOf(C_ACCENT));
        }

        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                if (!u) return;
                float v = min+((max-min)*(p/100f));
                tv.setText(String.format("%.0f", v));
                prefs.edit().putFloat(key, v).apply();
                sendConfigToCpp(prefs);
                radar.invalidate();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        c.addView(sb);
        return c;
    }

    private View btn(Context ctx, String txt, int color, Runnable r) {
        TextView b = new TextView(ctx); b.setText(txt); b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER); b.setPadding(dp(16), dp(12), dp(16), dp(12));
        b.setTextSize(11f); b.setTypeface(null, Typeface.BOLD); b.setLetterSpacing(0.05f);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color); bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        b.setOnClickListener(v -> r.run());
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        b.setLayoutParams(lp);
        return b;
    }

    private View radioRow(Context ctx, String key, String[] options) {
        RadioGroup rg = new RadioGroup(ctx); rg.setOrientation(RadioGroup.HORIZONTAL);
        rg.setGravity(Gravity.CENTER_HORIZONTAL);
        String cur = prefs.getString(key, options[0].toLowerCase());
        for (String op : options) {
            RadioButton rb = new RadioButton(ctx); rb.setText(op);
            rb.setTextColor(C_TEXT); rb.setTextSize(11f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                rb.setButtonTintList(android.content.res.ColorStateList.valueOf(C_ACCENT));
            }
            if (op.toLowerCase().equals(cur)) rb.setChecked(true);
            rb.setOnCheckedChangeListener((v, c) -> {
                if (c) {
                    prefs.edit().putString(key, op.toLowerCase()).apply();
                    sendConfigToCpp(prefs);
                }
            });
            rg.addView(rb);
        }
        return rg;
    }

    private View radioRowVertical(Context ctx, String key, String[] options) {
        RadioGroup rg = new RadioGroup(ctx); rg.setOrientation(RadioGroup.VERTICAL);
        String cur = prefs.getString(key, options[0].toLowerCase());
        for (String op : options) {
            RadioButton rb = new RadioButton(ctx); rb.setText(op);
            rb.setTextColor(C_TEXT); rb.setTextSize(11f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                rb.setButtonTintList(android.content.res.ColorStateList.valueOf(C_ACCENT));
            }
            if (op.toLowerCase().equals(cur)) rb.setChecked(true);
            rb.setOnCheckedChangeListener((v, c) -> {
                if (c) {
                    prefs.edit().putString(key, op.toLowerCase()).apply();
                    sendConfigToCpp(prefs);
                }
            });
            rg.addView(rb);
        }
        return rg;
    }

    private View vgap(Context ctx, int d) {
        View v = new View(ctx); v.setLayoutParams(new LayoutParams(1, dp(d)));
        return v;
    }

    private int dp(int p) {
        return (int) (p * getContext().getResources().getDisplayMetrics().density);
    }

    private String formatTime(long ms) {
        if (ms <= 0) return "Expired";
        long s = ms / 1000;
        long m = s / 60;
        long h = m / 60;
        long d = h / 24;
        if (d > 0) return d + " Days Remaining";
        if (h > 0) return h + " Hours Remaining";
        return m + " Minutes Remaining";
    }

    private void showExpanded() {
        tvPill.setVisibility(GONE);
        panel.setVisibility(VISIBLE);
        lp.width = LayoutParams.WRAP_CONTENT;
        lp.height = LayoutParams.WRAP_CONTENT;
        lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        wm.updateViewLayout(this, lp);
    }

    private void showCollapsed() {
        panel.setVisibility(GONE);
        tvPill.setVisibility(VISIBLE);
        lp.width = LayoutParams.WRAP_CONTENT;
        lp.height = LayoutParams.WRAP_CONTENT;
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        wm.updateViewLayout(this, lp);
    }

    private OnTouchListener dragL = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            if (prefs.getBoolean("ui_lock", false)) return false;
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
                        wm.updateViewLayout(OverlayView.this, lp);
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

    private void showModernDialog(Context ctx, String title, String[] items, java.util.function.Consumer<String> callback) {
        final WindowManager dialogWm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        final WindowManager.LayoutParams dlp = new WindowManager.LayoutParams();
        dlp.type = Build.VERSION.SDK_INT >= 26 ? 2038 : 2002;
        dlp.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        dlp.dimAmount = 0.6f;
        dlp.format = PixelFormat.TRANSLUCENT;
        dlp.width = dp(280);
        dlp.height = LayoutParams.WRAP_CONTENT;
        dlp.gravity = Gravity.CENTER;

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(VERTICAL);
        GradientDrawable rbg = new GradientDrawable();
        rbg.setColor(C_BG); rbg.setCornerRadius(dp(20));
        rbg.setStroke(dp(1), C_ACCENT);
        root.setBackground(rbg);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));

        TextView tvT = new TextView(ctx);
        tvT.setText(title); tvT.setTextColor(C_ACCENT);
        tvT.setTextSize(14f); tvT.setTypeface(null, Typeface.BOLD);
        tvT.setGravity(Gravity.CENTER);
        tvT.setPadding(0, 0, 0, dp(15));
        root.addView(tvT);

        ScrollView sv = new ScrollView(ctx);
        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(VERTICAL);
        for (String item : items) {
            TextView itv = new TextView(ctx);
            itv.setText(item); itv.setTextColor(C_TEXT);
            itv.setPadding(dp(15), dp(12), dp(15), dp(12));
            itv.setTextSize(13f);
            itv.setGravity(Gravity.CENTER);
            itv.setOnClickListener(v -> {
                callback.accept(item);
                dialogWm.removeView(root);
            });
            container.addView(itv);
            View div = new View(ctx);
            div.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(1)));
            div.setBackgroundColor(Color.argb(30, 255, 255, 255));
            container.addView(div);
        }
        sv.addView(container);
        sv.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(250)));
        root.addView(sv);

        TextView btnC = new TextView(ctx);
        btnC.setText("CANCEL"); btnC.setTextColor(C_SUBTEXT);
        btnC.setGravity(Gravity.CENTER); btnC.setPadding(0, dp(15), 0, 0);
        btnC.setOnClickListener(v -> dialogWm.removeView(root));
        root.addView(btnC);

        dialogWm.addView(root, dlp);
    }

    private void sendConfigToCpp(SharedPreferences p) {
        socketExecutor.execute(() -> {
            try {
                android.net.LocalSocket s = new android.net.LocalSocket();
                s.connect(new android.net.LocalSocketAddress("and.sys.sensor.data", android.net.LocalSocketAddress.Namespace.ABSTRACT));
                java.io.OutputStream os = s.getOutputStream();
                java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(1024).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                
                bb.putInt(p.getBoolean("aimbot_enable", false) ? 1 : 0);
                String target = p.getString("aimbot_target", "nearest");
                int tIdx = 0;
                if (target.equals("low hp")) tIdx = 1;
                else if (target.equals("low hp %")) tIdx = 2;
                bb.putInt(tIdx);
                bb.putFloat(p.getFloat("aimbot_fov", 200f));
                bb.putInt(p.getBoolean("radar_enable", false) ? 1 : 0);
                bb.putFloat(p.getFloat("radar_size", 338f));
                bb.putFloat(p.getFloat("radar_pos_x", 71f));
                bb.putFloat(p.getFloat("radar_icon_size", 37f));
                bb.putInt(p.getBoolean("retri_buff", false) ? 1 : 0);
                bb.putInt(p.getBoolean("retri_lord", false) ? 1 : 0);
                bb.putInt(p.getBoolean("retri_turtle", false) ? 1 : 0);
                bb.putInt(p.getBoolean("retri_litho", false) ? 1 : 0);
                
                String ling = p.getString("ling_mode", "disabled");
                int lIdx = 0;
                if (ling.equals("manual")) lIdx = 1;
                else if (ling.equals("auto")) lIdx = 2;
                bb.putInt(lIdx);
                
                bb.putInt(p.getBoolean("lock_hero_enable", false) ? 1 : 0);
                byte[] heroName = new byte[32];
                String hn = p.getString("locked_hero_name", "");
                System.arraycopy(hn.getBytes(), 0, heroName, 0, Math.min(hn.length(), 32));
                bb.put(heroName);
                
                byte[] comboName = new byte[32];
                String cn = p.getString("selected_combo", "none");
                System.arraycopy(cn.getBytes(), 0, comboName, 0, Math.min(cn.length(), 32));
                bb.put(comboName);
                
                bb.putInt(p.getBoolean("retri_red_buff", false) ? 1 : 0);

                os.write(bb.array(), 0, bb.position());
                os.flush();
                os.close();
                s.close();
            } catch (Exception ignored) {}
        });
    }

    static class RoomPlayerData {
        int camp, uid, zone, heroId, rank, mythPt, matches, wins, accLv, spellId, countryId;
        boolean isLeader;
        String name;
    }

    private void startRoomSocketThread() {
        new Thread(() -> {
            while (isRoomSocketRunning) {
                android.net.LocalSocket socket = null;
                java.io.DataInputStream dis = null;
                try {
                    socket = new android.net.LocalSocket();
                    socket.connect(new android.net.LocalSocketAddress("and.sys.sensor.data", android.net.LocalSocketAddress.Namespace.ABSTRACT));
                    dis = new java.io.DataInputStream(socket.getInputStream());
                    byte[] countBuf = new byte[4];
                    byte[] packetBuf = new byte[80];
                    while (isRoomSocketRunning) {
                        dis.readFully(countBuf);
                        int count = java.nio.ByteBuffer.wrap(countBuf).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
                        if (count > 10 || count < 0) count = 0;
                        final java.util.List<RoomPlayerData> players = new java.util.ArrayList<>();
                        boolean isEnabled = prefs.getBoolean("room_info_enable", false);
                        for (int i = 0; i < count; i++) {
                            dis.readFully(packetBuf);
                            if (!isEnabled) continue;
                            java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(packetBuf).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                            RoomPlayerData p = new RoomPlayerData();
                            p.camp = bb.getInt(); p.uid = bb.getInt(); p.zone = bb.getInt(); p.heroId = bb.getInt();
                            p.rank = bb.getInt(); p.mythPt = bb.getInt(); p.matches = bb.getInt(); p.wins = bb.getInt();
                            p.accLv = bb.getInt(); p.spellId = bb.getInt(); p.countryId = bb.getInt();
                            p.isLeader = bb.getInt() == 1;
                            byte[] nameBytes = new byte[32];
                            bb.get(nameBytes);
                            p.name = new String(nameBytes).trim();
                            players.add(p);
                        }
                        post(() -> {
                            if (roomTableContainer == null) return;
                            roomTableContainer.removeAllViews();
                            if (!isEnabled) {
                                TextView tv = new TextView(getContext());
                                tv.setText("Room Monitor is Disabled");
                                tv.setTextColor(C_SUBTEXT); tv.setTextSize(12f);
                                tv.setGravity(Gravity.CENTER);
                                roomTableContainer.addView(tv);
                                return;
                            }
                            if (players.isEmpty()) {
                                TextView tv = new TextView(getContext());
                                tv.setText("Waiting for Game Data...");
                                tv.setTextColor(C_ACCENT); tv.setTextSize(12f);
                                tv.setGravity(Gravity.CENTER);
                                roomTableContainer.addView(tv);
                                return;
                            }
                            for (RoomPlayerData p : players) {
                                roomTableContainer.addView(createPlayerCard(p));
                            }
                        });
                    }
                } catch (Exception e) {
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                } finally {
                    try { if (dis != null) dis.close(); } catch (Exception ignored) {}
                    try { if (socket != null) socket.close(); } catch (Exception ignored) {}
                }
            }
        }).start();
    }

    private View createPlayerCard(RoomPlayerData p) {
        Context ctx = getContext();
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        
        GradientDrawable bg = new GradientDrawable();
        if (p.camp == 1) {
            bg.setColor(Color.argb(40, 66, 165, 245));
            bg.setStroke(dp(1), Color.parseColor("#42A5F5")); 
        } else {
            bg.setColor(Color.argb(40, 239, 83, 80));
            bg.setStroke(dp(1), Color.parseColor("#EF5350")); 
        }
        bg.setCornerRadius(dp(10));
        card.setBackground(bg);
        
        LayoutParams cardLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(cardLp);

        LinearLayout colMid = new LinearLayout(ctx);
        colMid.setOrientation(VERTICAL);
        colMid.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        
        TextView tvName = new TextView(ctx);
        tvName.setText((p.isLeader ? "👑 " : "") + p.name);
        tvName.setTextColor(C_TEXT); tvName.setTextSize(13f); tvName.setTypeface(null, Typeface.BOLD);
        tvName.setSingleLine(true); tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);

        TextView tvUid = new TextView(ctx);
        tvUid.setText("UID: " + p.uid + " | Lv." + p.accLv);
        tvUid.setTextColor(C_SUBTEXT); tvUid.setTextSize(10f);

        colMid.addView(tvName);
        colMid.addView(tvUid);

        LinearLayout colRight = new LinearLayout(ctx);
        colRight.setOrientation(VERTICAL);
        colRight.setGravity(Gravity.END);
        
        TextView tvRank = new TextView(ctx);
        tvRank.setText(getRankName(p.rank, p.mythPt));
        tvRank.setTextColor(C_ACCENT); tvRank.setTextSize(11f); tvRank.setTypeface(null, Typeface.BOLD);

        TextView tvWr = new TextView(ctx);
        float wr = p.matches > 0 ? (p.wins * 100f / p.matches) : 0f;
        tvWr.setText(String.format("%.1f%% WR (%d Matches)", wr, p.matches));
        tvWr.setTextColor(C_TEXT); tvWr.setTextSize(9f);

        colRight.addView(tvRank);
        colRight.addView(tvWr);

        card.addView(colMid);
        card.addView(colRight);
        return card;
    }

    private String getRankName(int r, int pt) {
        if (r >= 8) return "Mythical Glory (" + pt + ")";
        if (r == 7) return "Mythic (" + pt + ")";
        if (r == 6) return "Legend";
        if (r == 5) return "Epic";
        if (r == 4) return "Grandmaster";
        if (r == 3) return "Master";
        if (r == 2) return "Elite";
        return "Warrior";
    }
}
