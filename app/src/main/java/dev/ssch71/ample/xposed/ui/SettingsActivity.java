/*
 * Copyright (c) 2026 ssch71
 */
package dev.ssch71.ample.xposed.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import dev.ssch71.ample.xposed.core.Setting;
import dev.ssch71.ample.xposed.core.Settings;
import io.github.libxposed.service.XposedService;

/** 의존성 없이 프레임워크 View만으로 만든 단순 설정 화면. 값은 RemotePreferences에 저장된다. */
public class SettingsActivity extends Activity implements App.ServiceStateListener {
    private TextView status;
    private final List<Switch> switches = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(24));

        TextView title = text("Ample Xposed", 24, true);
        root.addView(title);

        status = text("LSPosed 연결 확인 중…", 14, false);
        status.setPadding(0, dp(4), 0, dp(12));
        root.addView(status);

        String lastGroup = null;
        for (Setting setting : Settings.ALL) {
            if (!setting.group.equals(lastGroup)) {
                lastGroup = setting.group;
                TextView header = text(setting.group, 16, true);
                header.setPadding(0, dp(16), 0, dp(4));
                root.addView(header);
            }
            root.addView(row(setting));
        }

        TextView hint = text("설정은 대상 앱에 즉시 반영됩니다. 후크가 동작하지 않으면 "
                + "logcat에서 AmpleXposed 태그를 확인하세요.", 12, false);
        hint.setPadding(0, dp(24), 0, 0);
        root.addView(hint);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        // targetSdk 35+ 는 edge-to-edge 이므로 시스템 바 영역만큼 띄운다.
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);
    }

    @Override
    protected void onStart() {
        super.onStart();
        App.addListener(this);
    }

    @Override
    protected void onStop() {
        App.removeListener(this);
        super.onStop();
    }

    @Override
    public void onServiceStateChanged(XposedService service) {
        runOnUiThread(() -> {
            if (service == null) {
                status.setText("LSPosed에 연결되지 않았습니다. LSPosed에서 모듈을 활성화한 뒤 다시 열어 주세요.");
            } else {
                status.setText("연결됨 · " + service.getFrameworkName() + " " + service.getFrameworkVersion()
                        + " · API " + service.getApiVersion());
            }
            SharedPreferences prefs = service == null ? null : service.getRemotePreferences(Settings.PREFS_GROUP);
            for (Switch sw : switches) {
                Setting setting = (Setting) sw.getTag();
                sw.setOnCheckedChangeListener(null);
                sw.setEnabled(prefs != null);
                sw.setChecked(prefs == null ? setting.defaultValue
                        : prefs.getBoolean(setting.key, setting.defaultValue));
                if (prefs != null) {
                    sw.setOnCheckedChangeListener((button, checked) ->
                            prefs.edit().putBoolean(setting.key, checked).apply());
                }
            }
        });
    }

    private View row(Setting setting) {
        Switch sw = new Switch(this);
        sw.setText(setting.title);
        sw.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        sw.setTag(setting);
        sw.setEnabled(false);
        sw.setChecked(setting.defaultValue);
        switches.add(sw);

        TextView summary = text(setting.summary, 13, false);
        summary.setAlpha(0.7f);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, dp(8));
        box.addView(sw);
        box.addView(summary);
        return box;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setGravity(Gravity.START);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
