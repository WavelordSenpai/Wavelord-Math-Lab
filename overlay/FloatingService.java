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
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FloatingService extends Service {
    private WindowManager wm;
    private View buddyView, waiterView;
    private View miniWindow = null;
    private String currentTarget = "";

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        buddyView = makeBubble("Σ", "MATH BUDDY", "#22D3EE", 20, 1100, "buddy");
        waiterView = makeBubble("🧮", "WAITER", "#8B5CF6", 780, 1100, "waiter");
        wm.addView(buddyView, (WindowManager.LayoutParams) buddyView.getTag());
        wm.addView(waiterView, (WindowManager.LayoutParams) waiterView.getTag());
    }

    private View makeBubble(String emoji, String label, String colorHex, int startX, int startY, String target) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);

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
        p.gravity = Gravity.TOP | Gravity.START;
        p.x = startX;
        p.y = startY;
        container.setTag(p);

        container.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY, dx, dy;
            boolean moved = false;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                WindowManager.LayoutParams lp = (WindowManager.LayoutParams) v.getTag();
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        dx = downX - lp.x;
                        dy = downY - lp.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float mx = e.getRawX();
                        float my = e.getRawY();
                        if (Math.abs(mx - downX) > 15 || Math.abs(my - downY) > 15) moved = true;
                        lp.x = (int) (mx - dx);
                        lp.y = (int) (my - dy);
                        wm.updateViewLayout(v, lp);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) openMiniWindow(target);
                        return true;
                }
                return false;
            }
        });
        return container;
    }

    private void openMiniWindow(String target) {
        if (miniWindow != null) {
            try { wm.removeView(miniWindow); } catch (Exception e) {}
            miniWindow = null;
            if (target.equals(currentTarget)) return;
        }
        currentTarget = target;

        FrameLayout layout = new FrameLayout(this);
        layout.setBackgroundColor(Color.WHITE);

        WebView web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());
        if (target.equals("buddy")) {
            web.loadUrl("https://wavelord-math-lab.netlify.app/?open=buddy");
        } else {
            web.loadUrl("https://wavelord-math-lab.netlify.app/?open=waiter");
        }
        FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT);
        webParams.topMargin = 120;
        layout.addView(web, webParams);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setBackgroundColor(Color.BLACK);
        top.setPadding(20, 20, 20, 20);

        TextView title = new TextView(this);
        title.setText(target.equals("buddy") ? "Σ MATH BUDDY" : "🧮 WAITER");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        top.addView(title, titleParams);

        Button fullBtn = new Button(this);
        fullBtn.setText("Full App");
        fullBtn.setOnClickListener(v -> {
            Intent i = new Intent(FloatingService.this, MainActivity.class);
            i.putExtra("open", target);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        });
        top.addView(fullBtn);

        Button closeBtn = new Button(this);
        closeBtn.setText("X");
        closeBtn.setOnClickListener(v -> {
            try { wm.removeView(miniWindow); } catch (Exception e) {}
            miniWindow = null;
        });
        top.addView(closeBtn);

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, 120);
        layout.addView(top, topParams);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            900, 1200, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.CENTER;
        miniWindow = layout;
        wm.addView(miniWindow, p);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { if (buddyView != null) wm.removeView(buddyView); } catch (Exception e) {}
        try { if (waiterView != null) wm.removeView(waiterView); } catch (Exception e) {}
        try { if (miniWindow != null) wm.removeView(miniWindow); } catch (Exception e) {}
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
