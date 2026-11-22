package com.rama.tui_zero.activities;

import android.app.Activity;
import android.view.View;
import android.view.WindowManager;

import com.rama.tui_zero.R;
import com.rama.tui_zero.helpers.SystemBars;
import com.rama.tui_zero.managers.PrefsManager;
import com.rama.tui_zero.managers.ZoomManager;

public abstract class BaseActivity extends Activity {
    @Override
    protected void onResume() {
        super.onResume();
        View root = findViewById(R.id.root);
        if (root != null) {
            ZoomManager.apply(this, root);
        }
        if (PrefsManager.getInstance(this).isKeepScreenAwake()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            SystemBars.setVisible(this, PrefsManager.getInstance(this).isSystemBarVisible());
        }
    }
}
