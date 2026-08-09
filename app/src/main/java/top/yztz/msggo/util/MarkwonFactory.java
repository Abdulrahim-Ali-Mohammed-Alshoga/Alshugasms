/*
 * Copyright (C) 2026 yztz
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package top.yztz.msggo.util;

import android.content.Context;
import android.graphics.Color;

import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonSpansFactory;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.ext.tables.TablePlugin;
import top.yztz.msggo.R;

/**
 * 统一 Markwon 实例构建，确保深色/浅色主题下代码块等元素颜色与系统主题一致。
 */
public class MarkwonFactory {

    private MarkwonFactory() { }

    /**
     * 创建适配当前主题（深色/浅色）的 Markwon 实例。
     */
    public static Markwon create(@NonNull Context context) {
        // 从 Material 主题中读取颜色
        int colorSurface = MaterialColors.getColor(context, R.attr.colorSurface, Color.WHITE);
        int colorOnSurface = MaterialColors.getColor(context,R.attr.colorOnSurface, Color.BLACK);
        int colorSurfaceVariant = MaterialColors.getColor(context, R.attr.colorSurfaceVariant, 0xFFE0E0E0);
        int colorOnSurfaceVariant = MaterialColors.getColor(context, R.attr.colorOnSurfaceVariant, 0xFF444444);
        int colorOutlineVariant = MaterialColors.getColor(context, R.attr.colorOutlineVariant, 0xFFCCCCCC);
        int colorPrimary = MaterialColors.getColor(context, R.attr.colorPrimary, 0xFF256489);

        // 代码块背景：使用 surfaceVariant，和正文有适当区分
        int codeBackground = colorSurfaceVariant;
        // 代码文字颜色：使用 onSurfaceVariant
        int codeTextColor = colorOnSurfaceVariant;
        // 引用块左侧竖线颜色
        int blockQuoteColor = colorPrimary;
        // 分割线颜色
        int thematicBreakColor = colorOutlineVariant;

        return Markwon.builder(context)
                .usePlugin(TablePlugin.create(context))
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
                        builder
                                // 代码块（围栏式）背景
                                .codeBackgroundColor(codeBackground)
                                // 行内代码背景
                                .codeBackgroundColor(codeBackground)
                                // 代码文字颜色
                                .codeTextColor(codeTextColor)
                                // 引用块左侧竖线
                                .blockQuoteColor(blockQuoteColor)
                                // 分割线
                                .thematicBreakColor(thematicBreakColor);
                    }
                })
                .build();
    }
}
