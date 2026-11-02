package cn.jicreate.jicreate_app.a2dde3930ff;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.PopupWindow;
import android.widget.TextView;

/**
 * 会话标题栏的交互控制：侧边栏入口、开启新对话、更多操作与底部反馈条。
 *
 * <p>左上角菜单与首页保持一致，直接滑出全局抽屉；只有右侧「更多」保留
 * 409 / 1080 宽的小弹层，其位置按参考稿换算。</p>
 */
public final class GptChatNavController {

    public interface Listener {
        /** 左上角菜单按钮：打开全局侧边栏（会话历史/搜索/导航），不是就地弹小菜单。 */
        void onOpenDrawer();

        /** 点击顶部铅笔按钮：开启一段新对话（不是编辑模式）。 */
        void onNewChatClick();

        void onSharePage();

        /** @return 是否真的删除了内容，用于选择对应的反馈文案。 */
        boolean onDeleteDraft();

        /** 当前会话可复制的链接。 */
        String pageLink();
    }

    private static final float DESIGN_WIDTH = 1080f;
    private static final float POPOVER_TOP = 171f;
    private static final float POPOVER_WIDTH = 408f;
    private static final float POPOVER_SIDE = 39f;
    private static final float BAR_HEIGHT = 193f;
    private static final float POPOVER_GAP = POPOVER_TOP - BAR_HEIGHT;

    private static final long TOAST_VISIBLE_MS = 1900L;

    private final Activity activity;
    private final GptChatNavBarView bar;
    private final Listener listener;
    private TextView toastView;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private PopupWindow morePopup;

    private final Runnable hideToast = () -> {
        if (toastView == null) {
            return;
        }
        toastView.animate().cancel();
        toastView.animate().alpha(0f)
                .translationY(dp(4.4f))
                .setDuration(200)
                .withEndAction(() -> toastView.setVisibility(View.GONE))
                .start();
    };

    public GptChatNavController(Activity activity, GptChatNavBarView bar, Listener listener) {
        this.activity = activity;
        this.bar = bar;
        this.listener = listener;
        this.toastView = activity.findViewById(R.id.nav_toast);
        bar.setListener(new GptChatNavBarView.Listener() {
            @Override
            public void onMenuClick() {
                // ChatGPT 会话页的菜单按钮与首页一致：滑出完整侧边栏。
                dismissPopups();
                if (listener != null) {
                    listener.onOpenDrawer();
                }
            }

            @Override
            public void onNewChatClick() {
                // ChatGPT 顶部铅笔是「开启新对话」，不是编辑模式开关。
                dismissPopups();
                if (listener != null) {
                    listener.onNewChatClick();
                }
            }

            @Override
            public void onMoreClick() {
                if (morePopup != null) {
                    dismissPopups();
                } else {
                    showMorePopup();
                }
            }
        });
    }

    public boolean dismissPopups() {
        boolean dismissed = false;
        if (morePopup != null) {
            PopupWindow popup = morePopup;
            morePopup = null;
            popup.dismiss();
            dismissed = true;
        }
        return dismissed;
    }

    public void release() {
        dismissPopups();
        handler.removeCallbacks(hideToast);
        if (toastView != null) {
            toastView.animate().cancel();
            toastView.setVisibility(View.GONE);
        }
    }

    private float unit() {
        int width = bar.getWidth();
        if (width <= 0) {
            width = activity.getResources().getDisplayMetrics().widthPixels;
        }
        return width / DESIGN_WIDTH;
    }

    private PopupWindow createPopup(View content, int widthPx) {
        PopupWindow popup = new PopupWindow(content, widthPx,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(10));
        return popup;
    }

    private void showMorePopup() {
        dismissPopups();
        float unit = unit();
        int width = Math.round(POPOVER_WIDTH * unit);
        View content = LayoutInflater.from(activity)
                .inflate(R.layout.popup_gpt_nav_more, null);
        content.findViewById(R.id.nav_share).setOnClickListener(v -> {
            dismissPopups();
            if (listener != null) {
                listener.onSharePage();
            }
        });
        content.findViewById(R.id.nav_copy).setOnClickListener(v -> {
            dismissPopups();
            copyPageLink();
        });
        content.findViewById(R.id.nav_delete).setOnClickListener(v -> {
            dismissPopups();
            boolean deleted = listener != null && listener.onDeleteDraft();
            showToast(activity.getString(deleted
                    ? R.string.gpt_nav_deleted : R.string.gpt_nav_no_draft));
        });

        final PopupWindow popup = createPopup(content, width);
        morePopup = popup;
        popup.setOnDismissListener(() -> {
            if (morePopup == popup) {
                morePopup = null;
            }
        });
        content.setPivotX(width);
        content.setPivotY(0f);
        content.setAlpha(0f);
        content.setScaleX(0.98f);
        content.setScaleY(0.98f);
        content.setTranslationY(dp(-2.2f));
        popup.showAsDropDown(bar,
                Math.round((DESIGN_WIDTH - POPOVER_SIDE - POPOVER_WIDTH) * unit),
                Math.round(POPOVER_GAP * unit));
        animateIn(content);
        content.post(() -> content.findViewById(R.id.nav_share).requestFocus());
    }

    private void animateIn(View content) {
        content.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(160)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void copyPageLink() {
        String link = listener == null ? null : listener.pageLink();
        if (link == null || link.isEmpty()) {
            link = "https://chatgpt.com/";
        }
        ClipboardManager manager =
                (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null) {
            showToast(activity.getString(R.string.gpt_nav_copy_failed));
            return;
        }
        manager.setPrimaryClip(ClipData.newPlainText(
                activity.getString(R.string.gpt_app_name), link));
        showToast(activity.getString(R.string.gpt_nav_link_copied));
    }

    public void showToast(String message) {
        if (toastView == null) {
            android.widget.Toast.makeText(activity, message,
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        handler.removeCallbacks(hideToast);
        toastView.animate().cancel();
        toastView.setText(message);
        toastView.setVisibility(View.VISIBLE);
        toastView.setAlpha(0f);
        toastView.setTranslationY(dp(4.4f));
        toastView.animate().alpha(1f).translationY(0f)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        handler.postDelayed(hideToast, TOAST_VISIBLE_MS);
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
