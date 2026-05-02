#!/usr/bin/env python3
"""Generate app icons for Android and iOS featuring a 'U' letter on a deep-blue background."""

from pathlib import Path

try:
    from PIL import Image, ImageDraw
except ImportError:
    raise SystemExit("Install Pillow first: pip3 install Pillow")

BG_COLOR = (26, 35, 126)   # #1A237E deep indigo
FG_COLOR = (255, 255, 255)  # white letter

PROJECT_ROOT = Path(__file__).parent.parent


def make_icon(size: int, circle: bool = False) -> Image.Image:
    img = Image.new("RGB", (size, size), BG_COLOR)
    draw = ImageDraw.Draw(img)

    if circle:
        # Mask corners with transparent overlay to create circle shape
        mask = Image.new("L", (size, size), 0)
        mask_draw = ImageDraw.Draw(mask)
        mask_draw.ellipse([0, 0, size - 1, size - 1], fill=255)
        result = Image.new("RGB", (size, size), (0, 0, 0))
        result.paste(img, mask=mask)
        draw = ImageDraw.Draw(result)
        _draw_u(draw, size)
        return result

    _draw_u(draw, size)
    return img


def _draw_u(draw: ImageDraw.ImageDraw, size: int) -> None:
    """Draw a bold 'U' letter centred in the icon."""
    stroke = max(1, size // 10)
    pad = size * 0.22
    left = pad
    right = size - pad
    top = pad
    bottom = size - pad * 0.9

    arc_radius = (right - left) / 2
    arc_top = bottom - arc_radius * 2
    arc_bottom = bottom

    # Left vertical bar
    draw.rectangle(
        [left, top, left + stroke, arc_top + arc_radius],
        fill=FG_COLOR
    )
    # Right vertical bar
    draw.rectangle(
        [right - stroke, top, right, arc_top + arc_radius],
        fill=FG_COLOR
    )
    # Bottom arc (filled with a thick arc using multiple ellipses)
    arc_box = [left, arc_top, right, arc_bottom]
    for offset in range(int(stroke) + 1):
        adjusted = [
            arc_box[0] + offset,
            arc_box[1] + offset,
            arc_box[2] - offset,
            arc_box[3] - offset,
        ]
        draw.arc(adjusted, start=0, end=180, fill=FG_COLOR, width=stroke)


def save_png(img: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.convert("RGB").save(str(path), "PNG")
    print(f"  {path.relative_to(PROJECT_ROOT)}")


def generate_android() -> None:
    print("Android icons:")
    densities = {
        "mipmap-mdpi":     48,
        "mipmap-hdpi":     72,
        "mipmap-xhdpi":    96,
        "mipmap-xxhdpi":   144,
        "mipmap-xxxhdpi":  192,
    }
    res = PROJECT_ROOT / "androidApp" / "src" / "main" / "res"
    for dpi, px in densities.items():
        square = make_icon(px, circle=False)
        save_png(square, res / dpi / "ic_launcher.png")
        circle = make_icon(px, circle=True)
        save_png(circle, res / dpi / "ic_launcher_round.png")

    # Adaptive icon XML
    anydpi = res / "mipmap-anydpi-v26"
    anydpi.mkdir(parents=True, exist_ok=True)
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        (anydpi / name).write_text(
            '<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@drawable/ic_launcher_background"/>\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n'
            '</adaptive-icon>\n'
        )
        print(f"  androidApp/src/main/res/mipmap-anydpi-v26/{name}")

    drawable = res / "drawable"
    drawable.mkdir(parents=True, exist_ok=True)

    (drawable / "ic_launcher_background.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<shape xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <solid android:color="#1A237E"/>\n'
        '</shape>\n'
    )
    print("  androidApp/src/main/res/drawable/ic_launcher_background.xml")

    # Vector foreground: bold "U" path for adaptive icon (108dp canvas)
    (drawable / "ic_launcher_foreground.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp"\n'
        '    android:height="108dp"\n'
        '    android:viewportWidth="108"\n'
        '    android:viewportHeight="108">\n'
        '  <path\n'
        '      android:fillColor="#FFFFFF"\n'
        '      android:pathData="\n'
        '        M30,20 L30,68\n'
        '        Q30,88 54,88\n'
        '        Q78,88 78,68\n'
        '        L78,20\n'
        '        L68,20 L68,68\n'
        '        Q68,78 54,78\n'
        '        Q40,78 40,68\n'
        '        L40,20 Z"/>\n'
        '</vector>\n'
    )
    print("  androidApp/src/main/res/drawable/ic_launcher_foreground.xml")


def generate_ios() -> None:
    print("iOS icons:")
    sizes = [20, 29, 40, 58, 60, 76, 80, 87, 120, 152, 167, 180, 1024]
    appiconset = (
        PROJECT_ROOT / "iosApp" / "iosApp" / "Assets.xcassets" / "AppIcon.appiconset"
    )
    appiconset.mkdir(parents=True, exist_ok=True)

    images_json = []
    for s in sizes:
        img = make_icon(s, circle=False)
        filename = f"icon-{s}.png"
        save_png(img, appiconset / filename)
        images_json.append(
            f'    {{"filename": "{filename}", "idiom": "universal", "scale": "1x", "size": "{s}x{s}"}}'
        )

    contents = (
        '{\n'
        '  "images": [\n'
        + ',\n'.join(images_json) + '\n'
        '  ],\n'
        '  "info": {"author": "xcode", "version": 1}\n'
        '}\n'
    )
    (appiconset / "Contents.json").write_text(contents)
    print(f"  iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json")

    # Root Assets.xcassets Contents.json
    (appiconset.parent / "Contents.json").write_text(
        '{\n  "info": {"author": "xcode", "version": 1}\n}\n'
    )
    print("  iosApp/iosApp/Assets.xcassets/Contents.json")


if __name__ == "__main__":
    generate_android()
    generate_ios()
    print("Done.")
