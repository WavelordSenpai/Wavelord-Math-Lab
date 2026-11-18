package com.wavelord.mathlab;

import 【entity-android¦canonical_name=Android】.app.Activity;
import 【entity-android¦canonical_name=Android】.content.Intent;
import 【entity-android¦canonical_name=Android】.net.Uri;
import 【entity-android¦canonical_name=Android】.os.Build;
import 【entity-android¦canonical_name=Android】.os.Bundle;
import 【entity-android¦canonical_name=Android】.provider.Settings;
import 【entity-android¦canonical_name=Android】.view.ViewGroup;
import 【entity-android¦canonical_name=Android】.webkit.WebSettings;
import 【entity-android¦canonical_name=Android】.webkit.WebView;
import 【entity-android¦canonical_name=Android】.webkit.WebViewClient;
import 【entity-android¦canonical_name=Android】.widget.FrameLayout;

public class MainActivity extends Activity {
    private WebView webView;
    private static final int OVERLAY_REQ = 1234;

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

        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("https://wavelord-math-lab.netlify.app/");

        // Ask for overlay permission once
        checkOverlay();
    }

    private void checkOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_REQ);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // THIS IS THE MISSING CALL - collapse = start bubbles
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            startService(new Intent(this, FloatingService.class));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Back in app = hide bubbles
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
        stopService(new Intent(this, FloatingService.class));
        if (webView != null) webView.destroy();
    }
                         }
