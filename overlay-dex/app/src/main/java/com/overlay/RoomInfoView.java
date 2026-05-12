package com.overlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.List;

public class RoomInfoView extends LinearLayout {

    private static final int C_BG = Color.parseColor("#0D0D12");
    private static final int C_ACCENT = Color.parseColor("#00D4FF");
    private static final int C_TEXT = Color.parseColor("#EEEEF5");
    private static final int C_SUBTEXT = Color.parseColor("#66667A");

    private final Handler mainHandler;
    private LinearLayout tableContainer;
    private boolean running = true;

    // ---------- Struktur data dari native ----------
    private static class RoomPacket {
        int team;
        int heroID;
        int spellID;
        int uiRank;
        int mythPoint;
        int flagID;
        int accountLevel;
        float winRate;
        String name;
        String uid;
        String squad;
        String rankStr;
        String starStr;
        String verified;
    }

    public RoomInfoView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(C_BG);
        int pad = dp(12);
        setPadding(pad, pad, pad, pad);

        mainHandler = new Handler(Looper.getMainLooper());

        // Header
        TextView title = new TextView(context);
        title.setText("Room Info");
        title.setTextColor(C_ACCENT);
        title.setTextSize(16f);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(8));
        addView(title);

        // Scrollable container
        ScrollView sv = new ScrollView(context);
        tableContainer = new LinearLayout(context);
        tableContainer.setOrientation(VERTICAL);
        sv.addView(tableContainer);
        addView(sv, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        startSocket();
    }

    private void startSocket() {
        new Thread(() -> {
            while (running) {
                try (LocalSocket socket = new LocalSocket()) {
                    socket.connect(new LocalSocketAddress("mlbb_room_socket",
                            LocalSocketAddress.Namespace.ABSTRACT));
                    DataInputStream dis = new DataInputStream(socket.getInputStream());

                    while (running) {
                        int count = dis.readInt();
                        List<RoomPacket> list = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            RoomPacket p = new RoomPacket();
                            p.team = dis.readInt();
                            p.heroID = dis.readInt();
                            p.spellID = dis.readInt();
                            p.uiRank = dis.readInt();
                            p.mythPoint = dis.readInt();
                            p.flagID = dis.readInt();
                            p.accountLevel = dis.readInt();
                            p.winRate = dis.readFloat();

                            byte[] buf = new byte[32];
                            dis.readFully(buf); p.name = new String(buf).trim();
                            dis.readFully(buf); p.uid = new String(buf).trim();
                            dis.readFully(buf); p.squad = new String(buf).trim();
                            dis.readFully(buf); p.rankStr = new String(buf).trim();
                            buf = new byte[8];
                            dis.readFully(buf); p.starStr = new String(buf).trim();
                            buf = new byte[4];
                            dis.readFully(buf); p.verified = new String(buf).trim();

                            list.add(p);
                        }

                        mainHandler.post(() -> updateTable(list));
                    }
                } catch (Exception e) {
                    // reconnect after delay
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                }
            }
        }).start();
    }

    private void updateTable(List<RoomPacket> list) {
        tableContainer.removeAllViews();

        if (list.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("Waiting for room data...");
            empty.setTextColor(C_SUBTEXT);
            tableContainer.addView(empty);
            return;
        }

        // Pisah per team
        List<RoomPacket> blue = new ArrayList<>();
        List<RoomPacket> red = new ArrayList<>();
        for (RoomPacket p : list) {
            if (p.team == 0) blue.add(p);
            else red.add(p);
        }

        addTeamSection("BLUE TEAM", blue, Color.parseColor("#448AFF"));
        addTeamSection("RED TEAM", red, Color.parseColor("#FF5252"));
    }

    private void addTeamSection(String title, List<RoomPacket> team, int teamColor) {
        Context ctx = getContext();

        // Section header
        TextView header = new TextView(ctx);
        header.setText(title);
        header.setTextColor(teamColor);
        header.setTextSize(14f);
        header.setTypeface(null, Typeface.BOLD);
        header.setPadding(0, dp(8), 0, dp(4));
        tableContainer.addView(header);

        if (team.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText("(no players)");
            empty.setTextColor(C_SUBTEXT);
            tableContainer.addView(empty);
            return;
        }

        // Tampilkan setiap pemain
        for (RoomPacket p : team) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(HORIZONTAL);
            row.setPadding(0, dp(2), 0, dp(2));

            // Kolom: Name | Rank | WR | Lv | VIP
            TextView nameTv = new TextView(ctx);
            nameTv.setText(p.name);
            nameTv.setTextColor(C_TEXT);
            nameTv.setTextSize(12f);
            nameTv.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 2f));
            row.addView(nameTv);

            TextView rankTv = new TextView(ctx);
            String rankText = p.rankStr + (p.starStr.isEmpty() ? "" : " " + p.starStr);
            rankTv.setText(rankText);
            rankTv.setTextColor(C_TEXT);
            rankTv.setTextSize(11f);
            rankTv.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 2f));
            row.addView(rankTv);

            TextView wrTv = new TextView(ctx);
            wrTv.setText(String.format("%.1f%%", p.winRate));
            wrTv.setTextColor(C_TEXT);
            wrTv.setTextSize(11f);
            wrTv.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            row.addView(wrTv);

            TextView lvTv = new TextView(ctx);
            lvTv.setText("Lv." + p.accountLevel);
            lvTv.setTextColor(C_TEXT);
            lvTv.setTextSize(11f);
            lvTv.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            row.addView(lvTv);

            TextView vipTv = new TextView(ctx);
            vipTv.setText(p.verified);
            vipTv.setTextColor("Yes".equals(p.verified) ? Color.parseColor("#76FF03") : C_SUBTEXT);
            vipTv.setTextSize(11f);
            vipTv.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 0.5f));
            row.addView(vipTv);

            tableContainer.addView(row);
        }
    }

    public void destroy() {
        running = false;
    }

    private int dp(int px) {
        return (int) (px * getContext().getResources().getDisplayMetrics().density);
    }
}