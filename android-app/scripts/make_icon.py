# 生成 Android 自适应图标图层（与 iOS 图标同款：定位图钉 + 对勾）。
# 用法：python3 android-app/scripts/make_icon.py android-app/app/src/main/res （需要 Pillow）
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

S = 1728  # 108dp 画布的超采样尺寸
DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
BLUE = (52, 130, 255, 255)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def background():
    top, bottom = (92, 170, 255), (38, 84, 235)
    grad = Image.new("RGB", (256, 256))
    px = grad.load()
    for y in range(256):
        for x in range(256):
            t = (x * 0.35 + y * 0.65) / 255
            px[x, y] = lerp(top, bottom, min(1, max(0, t)))
    bg = grad.resize((S, S), Image.BICUBIC).convert("RGBA")
    glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    g = ImageDraw.Draw(glow)
    g.ellipse((-S * 0.2, -S * 0.25, S * 0.75, S * 0.55), fill=(170, 220, 255, 110))
    g.ellipse((S * 0.45, S * 0.55, S * 1.25, S * 1.25), fill=(90, 60, 230, 120))
    bg = Image.alpha_composite(bg, glow.filter(ImageFilter.GaussianBlur(S * 0.08)))
    hl = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(hl).ellipse((-S * 0.3, -S * 0.95, S * 1.3, S * 0.38), fill=(255, 255, 255, 40))
    return Image.alpha_composite(bg, hl.filter(ImageFilter.GaussianBlur(S * 0.06)))


# 图钉几何：缩放到自适应图标 66dp 安全区内（画布中心 61% 直径）
SCALE = 0.68
CX = S * 0.5
CY = S * (0.5 + (0.42 - 0.5) * SCALE)
R = S * 0.245 * SCALE
TIP_Y = S * (0.5 + (0.80 - 0.5) * SCALE)


def pin_polygon(dy=0.0):
    ccy = CY + dy
    d = (TIP_Y + dy) - ccy
    ang = math.acos(min(1, R / d))
    start, end = math.pi / 2 + ang, math.pi / 2 - ang + 2 * math.pi
    pts = [(CX + R * math.cos(start + (end - start) * i / 400), ccy + R * math.sin(start + (end - start) * i / 400)) for i in range(401)]
    pts.append((CX, TIP_Y + dy))
    return pts


def draw_check(draw, fill):
    w = int(S * 0.055 * SCALE)
    p1 = (CX - R * 0.48, CY + R * 0.02)
    p2 = (CX - R * 0.12, CY + R * 0.38)
    p3 = (CX + R * 0.52, CY - R * 0.34)
    draw.line([p1, p2, p3], fill=fill, width=w, joint="curve")
    for p in (p1, p3):
        draw.ellipse((p[0] - w / 2, p[1] - w / 2, p[0] + w / 2, p[1] + w / 2), fill=fill)


def foreground():
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).polygon(pin_polygon(dy=S * 0.025), fill=(10, 30, 120, 120))
    layer = Image.alpha_composite(layer, shadow.filter(ImageFilter.GaussianBlur(S * 0.02)))
    d = ImageDraw.Draw(layer)
    d.polygon(pin_polygon(), fill=(255, 255, 255, 255))
    draw_check(d, BLUE)
    return layer


def monochrome():
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.polygon(pin_polygon(), fill=(255, 255, 255, 255))
    draw_check(d, (0, 0, 0, 0))  # 对勾镂空
    return layer


def main(res_dir):
    layers = {
        "ic_launcher_background": background().convert("RGB"),
        "ic_launcher_foreground": foreground(),
        "ic_launcher_monochrome": monochrome(),
    }
    for density, size in DENSITIES.items():
        folder = os.path.join(res_dir, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)
        for name, image in layers.items():
            image.resize((size, size), Image.LANCZOS).save(os.path.join(folder, f"{name}.png"), optimize=True)
    # 预览：前景叠加背景
    preview = Image.alpha_composite(layers["ic_launcher_background"].convert("RGBA"), layers["ic_launcher_foreground"])
    preview.resize((432, 432), Image.LANCZOS).save(os.path.join(res_dir, "..", "ic_launcher-preview.png"))
    print("done")


if __name__ == "__main__":
    main(sys.argv[1])
