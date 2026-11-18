package com.wavelord.mathlab;

import 【entity-android¦canonical_name=Android】.app.Activity;
import 【entity-android¦canonical_name=Android】.content.Intent;
import 【entity-android¦canonical_name=Android】.net.Uri;
import 【entity-android¦canonical_name=Android】.os.Build;
import 【entity-android¦canonical_name=Android】.os.Bundle;
import 【entity-android¦canonical_name=Android】.provider.Settings;
import 【entity-android¦canonical_name=Android】.view.ViewGroup;
import 【entity-android¦canonical_name=Android】.webkit.JavascriptInterface;
import 【entity-android¦canonical_name=Android】.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public class MainActivity extends Activity {
    private WebView webView;
    private static final int OVERLAY_REQ = 1234;

    public class MinimizeBridge {
        @JavascriptInterface
        public void minimize() {
            runOnUiThread(() -> {
                // ONLY place bubbles start now - when YOU press Minimize
                startService(new Intent(MainActivity.this, FloatingService.class));
                moveTaskToBack(true);
                finish();
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);

        webView.addJavascriptInterface(new MinimizeBridge(), "AndroidMinimize");
        webView.setWebViewClient(new WebViewClient());

        String open = getIntent().getStringExtra("open");
        if (open != null) {
            webView.loadUrl("https://wavelord-math-lab.netlify.app/?open=" + open);
        } else {
            webView.loadUrl("https://wavelord-math-lab.netlify.app/");
        }
        checkOverlay();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String open = intent.getStringExtra("open");
        if (open != null && webView != null) {
            webView.loadUrl("https://wavelord-math-lab.netlify.app/?open=" + open);
        }
    }

    private void checkOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_REQ);
        }
    }

    // FIX: onPause is now EMPTY - Recents / Home / WhatsApp switch will NOT trigger bubbles anymore
    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        stopService(new Intent(this, FloatingService.class));
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) webView.destroy();
    }
}
