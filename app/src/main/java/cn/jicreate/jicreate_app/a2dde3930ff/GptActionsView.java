package cn.jicreate.jicreate_app.a2dde3930ff;

import android.content.Context;
import android.util.AttributeSet;

/**
 * ChatGPT 消息操作行的混合渲染容器。
 *
 * <p>与 DeepSeek 的 {@link DsActionsView} 共用 WebView 容器、JS 桥和反馈 / 朗读状态同步，
 * 仅把图标页换成 {@code assets/gpt_actions.html}。该页复用 {@code ds_actions.html}
 * 中逐像素还原的 copy / up / down / vol / share 图标路径，并按 ChatGPT 的操作栏
 * 补一枚横向“更多”三点；行高 34dp、触控格 36dp、视觉图标 18dp 与原布局保持一致。</p>
 */
public class GptActionsView extends DsActionsView {

    public GptActionsView(Context context) {
        super(context);
    }

    public GptActionsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public GptActionsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected String pageAsset() {
        return "gpt_actions.html";
    }
}
