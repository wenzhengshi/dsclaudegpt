package cn.jicreate.jicreate_app.a2dde3930ff;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * 默认桌面图标的入口。
 *
 * 应用支持切换桌面图标（manifest 里每个样式一个 activity-alias），因此主界面
 * MainActivity 本身不再声明 LAUNCHER。为了让桌面/自动化工具仍能解析到一个真实
 * 的启动 Activity，这里保留一个默认样式、无界面的中转页：打开后立即启动
 * MainActivity 并结束自身，用户不会看到额外页面。
 */
public class LaunchEntryActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
        overridePendingTransition(0, 0);
    }
}
