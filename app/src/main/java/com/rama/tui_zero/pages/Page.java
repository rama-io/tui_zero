package com.rama.tui_zero.pages;

import com.rama.tui_zero.objects.Themes;

public interface Page {
    void onShow();

    void onHide();

    void refreshTheme(Themes.Palette palette);

    void destroy();
}
