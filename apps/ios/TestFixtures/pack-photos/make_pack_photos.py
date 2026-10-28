"""
Draws the two pack photos the iOS walk-through of Add a packaged food uses on
the simulator, which has no camera: the front of a granola bar's pack and its
US Nutrition Facts panel. Both are drawn, not photographed, so they carry no
one's product or likeness; they are tilted, shaded and grained so the label
reader sees something closer to a phone photo than a clean render.

The label is saved sideways with an EXIF orientation of 6 (rotate 90 degrees
clockwise to view), so it only reads upright if the app applies the photo's
orientation before it shrinks and sends it.

Run with python3 -I (Pillow), then add both to a booted simulator:

    xcrun simctl addmedia booted pack-front.jpg nutrition-label.jpg
"""
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).resolve().parent
FONT = "/System/Library/Fonts/Helvetica.ttc"
random.seed(7)


def font(size, bold=False):
    # Helvetica.ttc: index 1 is Bold.
    return ImageFont.truetype(FONT, size, index=1 if bold else 0)


def photo_finish(panel, size, background, angle):
    """Sets the panel on a surface, tilts it, and adds grain and a little blur."""
    canvas = Image.new("RGB", size, background)
    tilted = panel.rotate(angle, resample=Image.BICUBIC, expand=True, fillcolor=background)
    x = (size[0] - tilted.width) // 2
    y = (size[1] - tilted.height) // 2
    canvas.paste(tilted, (x, y))
    # A soft shadow from the top left, as from a window.
    shade = Image.new("L", size, 0)
    ImageDraw.Draw(shade).rectangle([0, 0, size[0], size[1] // 3], fill=28)
    canvas = Image.composite(Image.new("RGB", size, (0, 0, 0)), canvas, shade.filter(ImageFilter.GaussianBlur(120)))
    grain = Image.effect_noise(size, 12).convert("RGB")
    canvas = Image.blend(canvas, grain, 0.06)
    return canvas.filter(ImageFilter.GaussianBlur(0.6))


def nutrition_label():
    w, h = 900, 1500
    panel = Image.new("RGB", (w, h), (252, 251, 247))
    d = ImageDraw.Draw(panel)
    d.rectangle([12, 12, w - 12, h - 12], outline=(20, 20, 20), width=6)
    x, y = 40, 36
    d.text((x, y), "Nutrition Facts", font=font(92, bold=True), fill=(15, 15, 15))
    y += 112
    d.line([x, y, w - 40, y], fill=(15, 15, 15), width=3)
    y += 14
    d.text((x, y), "6 servings per container", font=font(38), fill=(15, 15, 15))
    y += 50
    d.text((x, y), "Serving size", font=font(44, bold=True), fill=(15, 15, 15))
    d.text((w - 330, y), "1 bar (40g)", font=font(44, bold=True), fill=(15, 15, 15))
    y += 66
    d.rectangle([x, y, w - 40, y + 22], fill=(15, 15, 15))
    y += 34
    d.text((x, y), "Amount per serving", font=font(32, bold=True), fill=(15, 15, 15))
    y += 40
    d.text((x, y), "Calories", font=font(76, bold=True), fill=(15, 15, 15))
    d.text((w - 190, y - 6), "190", font=font(88, bold=True), fill=(15, 15, 15))
    y += 100
    d.rectangle([x, y, w - 40, y + 12], fill=(15, 15, 15))
    y += 20
    d.text((w - 250, y), "% Daily Value*", font=font(32, bold=True), fill=(15, 15, 15))
    y += 44
    rows = [
        ("Total Fat 7g", "9%", True, 0),
        ("Saturated Fat 1g", "5%", False, 1),
        ("Trans Fat 0g", "", False, 1),
        ("Cholesterol 0mg", "0%", True, 0),
        ("Sodium 150mg", "7%", True, 0),
        ("Total Carbohydrate 29g", "11%", True, 0),
        ("Dietary Fiber 3g", "11%", False, 1),
        ("Total Sugars 12g", "", False, 1),
        ("Includes 10g Added Sugars", "20%", False, 2),
        ("Protein 4g", "", True, 0),
    ]
    for label, dv, bold, indent in rows:
        d.line([x, y, w - 40, y], fill=(15, 15, 15), width=2)
        y += 10
        d.text((x + indent * 40, y), label, font=font(40, bold=bold), fill=(15, 15, 15))
        if dv:
            d.text((w - 140, y), dv, font=font(40, bold=True), fill=(15, 15, 15))
        y += 56
    d.rectangle([x, y, w - 40, y + 22], fill=(15, 15, 15))
    y += 34
    for label, dv in [("Vitamin D 0mcg", "0%"), ("Calcium 20mg", "2%"), ("Iron 1.1mg", "6%"), ("Potassium 115mg", "2%")]:
        d.text((x, y), label, font=font(38), fill=(15, 15, 15))
        d.text((w - 140, y), dv, font=font(38), fill=(15, 15, 15))
        y += 50
        d.line([x, y, w - 40, y], fill=(15, 15, 15), width=1)
        y += 8
    y += 10
    d.multiline_text(
        (x, y),
        "* The % Daily Value (DV) tells you how much a nutrient in\na serving of food contributes to a daily diet. 2,000 calories\na day is used for general nutrition advice.",
        font=font(26),
        fill=(30, 30, 30),
        spacing=6,
    )
    return panel


def pack_front():
    w, h = 1300, 700
    panel = Image.new("RGB", (w, h), (196, 120, 38))
    d = ImageDraw.Draw(panel)
    d.rectangle([0, 0, w, 120], fill=(92, 50, 18))
    d.text((40, 26), "HILLCREST", font=font(70, bold=True), fill=(250, 236, 200))
    d.ellipse([820, 170, 1220, 570], fill=(232, 196, 120), outline=(120, 70, 20), width=8)
    for _ in range(140):
        cx, cy = random.randint(860, 1180), random.randint(210, 530)
        d.ellipse([cx - 9, cy - 6, cx + 9, cy + 6], fill=(170, 120, 60))
    d.text((40, 190), "Oat & Honey", font=font(110, bold=True), fill=(255, 250, 238))
    d.text((40, 320), "Granola Bar", font=font(110, bold=True), fill=(255, 250, 238))
    d.text((40, 470), "Whole grain oats, wildflower honey", font=font(40), fill=(255, 238, 205))
    d.text((40, 590), "6 BARS  NET WT 8.5 OZ (240g)", font=font(38, bold=True), fill=(92, 50, 18))
    return panel


def main():
    front = photo_finish(pack_front(), (1600, 1200), (214, 210, 202), angle=-4)
    front.save(HERE / "pack-front.jpg", quality=88)

    upright = photo_finish(nutrition_label(), (1200, 1700), (226, 222, 214), angle=2.5)
    # Stored sideways; EXIF orientation 6 says "rotate 90 clockwise to view".
    sideways = upright.transpose(Image.Transpose.ROTATE_90)
    exif = Image.Exif()
    exif[0x0112] = 6
    sideways.save(HERE / "nutrition-label.jpg", quality=88, exif=exif.tobytes())
    print("wrote", HERE / "pack-front.jpg", HERE / "nutrition-label.jpg")


if __name__ == "__main__":
    main()
