# dmgbuild settings for the macOS installer window. Pass -D app=<Dari.app> -D background=<png>.
# The in-app updater mounts the DMG and expects Dari.app at its root.
import os.path

app = defines["app"]  # noqa: F821
files = [app]
symlinks = {"Applications": "/Applications"}
# LZFSE: smaller than UDZO, and decompressed in the kernel, so copying Dari out takes about
# a third of the time ULMO (LZMA) does, for a few MB more. Opens on macOS 10.11+.
format = "ULFO"

background = defines["background"]  # noqa: F821; background@2x.png next to it is picked up too
window_rect = ((200, 120), (660, 400))
default_view = "icon-view"
show_status_bar = False
show_tab_view = False
show_toolbar = False
show_pathbar = False
show_sidebar = False
icon_size = 144
text_size = 13
# Keep in sync with scripts/make-dmg-background.py.
icon_locations = {
    os.path.basename(app): (190, 180),
    "Applications": (470, 180),
}
