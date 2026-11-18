package com.wavelord.mathlab;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FloatingService extends Service {
    private WindowManager wm;
    private View buddyView, waiterView;

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        buddyView = makeBubble("Σ", "MATH BUDDY", "#22D3EE", Gravity.BOTTOM | Gravity.START, 20, 120);
        waiterView = makeBubble("🧮", "WAITER", "#8B5CF6", Gravity.BOTTOM | Gravity.END, 20, 120);

        wm.addView(buddyView, (WindowManager.LayoutParams) buddyView.getTag());
        wm.addView(waiterView, (WindowManager.LayoutParams) waiterView.getTag());
    }

    private View makeBubble(String emoji, String label, String colorHex, int gravity, int x, int y) {
        // Container - vertical: label on top, circle below
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);

        // Label pill - black with white text like screenshot
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(9);
        labelView.setTextColor(Color.WHITE);
        labelView.setPadding(18, 8, 18, 8);
        GradientDrawable labelBg = new GradientDrawable();
        labelBg.setColor(Color.BLACK);
        labelBg.setCornerRadius(50);
        labelBg.setStroke(2, Color.WHITE);
        labelView.setBackground(labelBg);
        labelView.setGravity(Gravity.CENTER);

        // Circle bubble
        TextView bubble = new TextView(this);
        bubble.setText(emoji);
        bubble.setTextSize(32);
        bubble.setTextColor(Color.WHITE);
        bubble.setGravity(Gravity.CENTER);
        bubble.setWidth(170);
        bubble.setHeight(170);
        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setShape(GradientDrawable.OVAL);
        bubbleBg.setColor(Color.parseColor(colorHex));
        bubbleBg.setStroke(6, Color.WHITE);
        bubble.setBackground(bubbleBg);

        container.addView(labelView);
        container.addView(bubble);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);
        p.gravity = gravity;
        p.x = x;
        p.y = y;
        container.setTag(p);

        // Drag + Tap to reopen
        container.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy;
            long downTime;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                WindowManager.LayoutParams lp = (WindowManager.LayoutParams) v.getTag();
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = e.getRawX() - lp.x;
                        dy = e.getRawY() - lp.y;
                        downTime = System.currentTimeMillis();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.x = (int) (e.getRawX() - dx);
                        lp.y = (int) (e.getRawY() - dy);
                        wm.updateViewLayout(v, lp);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (System.currentTimeMillis() - downTime < 200) {
                            // Tap = Reopen entire app
                            Intent i = new Intent(FloatingService.this, MainActivity.class);
                            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(i);
                        }
                        return true;
                }
                return false;
            }
        });
        return container;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { if (buddyView != null) wm.removeView(buddyView); } catch (Exception e) {}
        try { if (waiterView != null) wm.removeView(waiterView); } catch (Exception e) {}
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
          }
