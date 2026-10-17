# Settings bottom sheet height

How the in-app settings sheet (`SettingsBottomSheet`) was kept from covering the whole screen, and
what didn't work. Read this before changing the sheet's layout or upgrading Material3.

## Symptom

The sheet opened from the status bar down to the navigation bar. No dimmed scrim showed above it, and
the drag handle sat right under the status bar, so the screen looked like a new page, not a sheet.

## Cause

The sheet uses `skipPartiallyExpanded = true` and a scrolling `Column`. Its content (General, Data,
About) is taller than a phone screen, so the sheet expands to its maximum height.

Measured on a 1080×2340 device with `adb shell uiautomator dump`:

- Sheet bounds `[0,95][1080,2205]`: it starts right below the status bar (95px) and ends above the
  navigation bar (2205px). The window it is laid out in excludes the navigation bar.
- The drag handle area adds about 135px on top of the content.

So capping the content at a fraction of the screen isn't enough on its own: the handle and the
navigation bar inset shift the sheet up again.

## What didn't work

| Attempt | Result |
| --- | --- |
| `heightIn(max = 85% of window)` on the content | Sheet still reached the status bar: 85% of the content + 135px handle ≈ the space above the navigation bar |
| `heightIn(max = …)` on `ModalBottomSheet(modifier = …)` | Sheet was pinned to the **top** of the screen with the scrim below it. Material3 places the sheet from its content's measured height, and capping the surface breaks that |

## Fix

Cap the **content**, not the sheet, at 75% of the window height:

```kotlin
val maxSheetHeight = with(LocalDensity.current) {
    (LocalWindowInfo.current.containerSize.height * MAX_SHEET_HEIGHT_FRACTION).toDp()
}

Column(
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = maxSheetHeight)
        .verticalScroll(rememberScrollState()),
)
```

75% leaves room for the handle and the navigation bar inset, so the scrim stays visible above the
sheet and the rest scrolls inside it. Shorter content (e.g. on tablets) still wraps its height.

## Checking a change

1. Open Dari → Settings on a phone.
2. A dimmed strip must show above the sheet, and the drag handle must sit on the sheet, not under
   the status bar.
3. For exact numbers, run `adb shell uiautomator dump` and read the bounds of the node that contains
   the drag handle (its description is localized, e.g. `드래그 핸들`). Its top must be well below
   the status bar.

Re-check after a Compose BOM upgrade: the Material3 sheet layout has changed between releases.
