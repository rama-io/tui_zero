package com.rama.tui_zero.pages;

import com.rama.tui_zero.objects.Themes;

/** One screen of the main activity. Pages are plain views, so no Fragment is needed. */
public interface Page {
    /** The page became visible. */
    void onShow();

    /** The page is about to be hidden. */
    void onHide();

    /** Called after the theme and zoom were applied to the whole tree, for pages that draw or color things themselves. */
    void refreshTheme(Themes.Palette palette);

    void destroy();
}
