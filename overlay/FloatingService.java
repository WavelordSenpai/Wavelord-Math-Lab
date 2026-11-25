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
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FloatingService extends Service {
    private static final String APP_URL = "https://wavelord-math-lab.netlify.app/";
    private WindowManager wm;
    private View buddyBubble;
    private View menuCard = null;
    private View miniWindow = null;

    public class OverlayBridge {
        @JavascriptInterface
        public void returnToApp() {
            launchMainApp(null);
        }

        @JavascriptInterface
        public void exitOverlay() {
            stopSelf();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        buddyBubble = makeWaveBuddyBubble(32, 620);
        wm.addView(buddyBubble, (WindowManager.LayoutParams) buddyBubble.getTag());
    }

    private int overlayWindowType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private View makeWaveBuddyBubble(int startX, int startY) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);

        // Calculator-inspired dark orb matching app icon aesthetic
        TextView bubble = new TextView(this);
        bubble.setText("🧮");
        bubble.setTextSize(26);
        bubble.setTextColor(Color.WHITE);
        bubble.setGravity(Gravity.CENTER);
        bubble.setWidth(148);
        bubble.setHeight(148);
        GradientDrawable bubbleBg = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[] { Color.parseColor("#141728"), Color.parseColor("#070811") }
        );
        bubbleBg.setCornerRadius(40f);
        bubbleBg.setStroke(5, Color.parseColor("#22D3EE"));
        bubble.setBackground(bubbleBg);

        TextView labelView = new TextView(this);
        labelView.setText("WAVE BUDDY");
        labelView.setTextSize(9);
        labelView.setTextColor(Color.parseColor("#F8FAFC"));
        labelView.setPadding(16, 5, 16, 5);
        GradientDrawable labelBg = new GradientDrawable();
        labelBg.setColor(Color.parseColor("#070811"));
        labelBg.setCornerRadius(50);
        labelBg.setStroke(2, Color.parseColor("#A78BFA"));
        labelView.setBackground(labelBg);
        labelView.setGravity(Gravity.CENTER);

        container.addView(bubble);
        container.addView(labelView);

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
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
                        if (!moved) toggleQuickMenu();
                        return true;
                }
                return false;
            }
        });
        return container;
    }

    private void closeMenuCard() {
        if (menuCard != null) {
            try { wm.removeView(menuCard); } catch (Exception ignored) {}
            menuCard = null;
        }
    }

    private void closeMiniWindow() {
        if (miniWindow != null) {
            try { wm.removeView(miniWindow); } catch (Exception ignored) {}
            miniWindow = null;
        }
    }

    private void toggleQuickMenu() {
        if (miniWindow != null) {
            closeMiniWindow();
            return;
        }
        if (menuCard != null) {
            closeMenuCard();
            return;
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(28, 24, 28, 24);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#101220"));
        cardBg.setCornerRadius(42f);
        cardBg.setStroke(4, Color.parseColor("#22D3EE"));
        card.setBackground(cardBg);

        TextView heading = new TextView(this);
        heading.setText("🧮 WAVE BUDDY");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(16);
        heading.setPadding(8, 0, 8, 16);
        card.addView(heading);

        card.addView(makeMenuButton("💬 Talk to AI (Voice + Text)", "#22D3EE", "#070811", v -> {
            closeMenuCard();
            openMiniWindow("buddy");
        }));
        card.addView(makeMenuButton("🧮 Use Calculator", "#A78BFA", "#070811", v -> {
            closeMenuCard();
            openMiniWindow("calc");
        }));
        card.addView(makeMenuButton("↗ Return to App", "#1B1F36", "#F8FAFC", v -> {
            closeMenuCard();
            launchMainApp(null);
        }));
        card.addView(makeMenuButton("✕ Exit App", "#3B151B", "#FCA5A5", v -> {
            closeMenuCard();
            closeMiniWindow();
            stopSelf();
        }));

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            760,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.CENTER;
        menuCard = card;
        wm.addView(menuCard, p);
    }

    private Button makeMenuButton(String label, String bgHex, String fgHex, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextColor(Color.parseColor(fgHex));
        btn.setTextSize(14);
        btn.setPadding(24, 20, 24, 20);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(bgHex));
        bg.setCornerRadius(24f);
        btn.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = 14;
        btn.setLayoutParams(lp);
        btn.setOnClickListener(listener);
        return btn;
    }

    private void launchMainApp(String target) {
        Intent i = new Intent(FloatingService.this, MainActivity.class);
        if (target != null) i.putExtra("open", target);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }

    private void openMiniWindow(String target) {
        closeMiniWindow();

        FrameLayout layout = new FrameLayout(this);
        layout.setBackgroundColor(Color.parseColor("#070811"));

        WebView web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new OverlayBridge(), "AndroidOverlay");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                request.grant(request.getResources());
            }
        });
        web.loadUrl(APP_URL + "?open=" + target);

        FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT);
        webParams.topMargin = 128;
        layout.addView(web, webParams);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setBackgroundColor(Color.parseColor("#0E101C"));
        top.setPadding(20, 16, 20, 16);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(target.equals("buddy") ? "💬 WAVE BUDDY AI" : "🧮 CALCULATOR");
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        top.addView(title, titleParams);

        Button fullBtn = new Button(this);
        fullBtn.setText("Return");
        fullBtn.setAllCaps(false);
        fullBtn.setOnClickListener(v -> launchMainApp(target));
        top.addView(fullBtn);

        Button collapseBtn = new Button(this);
        collapseBtn.setText("⌄");
        collapseBtn.setOnClickListener(v -> closeMiniWindow());
        top.addView(collapseBtn);

        Button exitBtn = new Button(this);
        exitBtn.setText("✕");
        exitBtn.setOnClickListener(v -> {
            closeMiniWindow();
            stopSelf();
        });
        top.addView(exitBtn);

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, 128);
        layout.addView(top, topParams);

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            920, 1260, overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.CENTER;
        miniWindow = layout;
        wm.addView(miniWindow, p);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { if (buddyBubble != null) wm.removeView(buddyBubble); } catch (Exception ignored) {}
        closeMenuCard();
        closeMiniWindow();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
