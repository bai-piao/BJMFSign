# 生成 iOS 应用图标：python3 ios-app/scripts/make_icon.py ios-app/BJMFSign/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon.png（需要 Pillow）
import math, sys
from PIL import Image, ImageDraw, ImageFilter

S = 4096  # supersample
OUT = 1024

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

# diagonal gradient background
top = (92, 170, 255)
bottom = (38, 84, 235)
grad = Image.new("RGB", (256, 256))
px = grad.load()
for y in range(256):
    for x in range(256):
        t = (x * 0.35 + y * 0.65) / 255
        px[x, y] = lerp(top, bottom, min(1, max(0, t)))
bg = grad.resize((S, S), Image.BICUBIC).convert("RGBA")

# soft glow blobs for depth
glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
g = ImageDraw.Draw(glow)
g.ellipse((-S*0.2, -S*0.25, S*0.75, S*0.55), fill=(170, 220, 255, 110))
g.ellipse((S*0.45, S*0.55, S*1.25, S*1.25), fill=(90, 60, 230, 120))
glow = glow.filter(ImageFilter.GaussianBlur(S * 0.08))
bg = Image.alpha_composite(bg, glow)

# liquid-glass specular highlight across the top
hl = Image.new("RGBA", (S, S), (0, 0, 0, 0))
hd = ImageDraw.Draw(hl)
hd.ellipse((-S * 0.3, -S * 0.95, S * 1.3, S * 0.38), fill=(255, 255, 255, 40))
hl = hl.filter(ImageFilter.GaussianBlur(S * 0.06))
bg = Image.alpha_composite(bg, hl)

# pin geometry
cx, cy = S * 0.5, S * 0.42
r = S * 0.245
tip_y = S * 0.80

def pin_polygon(scale=1.0, dy=0.0):
    pts = []
    rr = r * scale
    ccy = cy + dy
    # tangent points from tip to circle
    d = (tip_y + dy) - ccy
    ang = math.acos(min(1, rr / d))
    start = math.pi / 2 + ang
    end = math.pi / 2 - ang + 2 * math.pi
    steps = 400
    for i in range(steps + 1):
        a = start + (end - start) * i / steps
        pts.append((cx + rr * math.cos(a), ccy + rr * math.sin(a)))
    pts.append((cx, tip_y + dy))
    return pts

# shadow
shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
sd = ImageDraw.Draw(shadow)
sd.polygon(pin_polygon(dy=S * 0.03), fill=(10, 30, 120, 120))
shadow = shadow.filter(ImageFilter.GaussianBlur(S * 0.025))
bg = Image.alpha_composite(bg, shadow)

# glass pin: translucent white body + bright rim
pin = Image.new("RGBA", (S, S), (0, 0, 0, 0))
pd = ImageDraw.Draw(pin)
pd.polygon(pin_polygon(), fill=(255, 255, 255, 255))
bg = Image.alpha_composite(bg, pin)

# check mark inside the pin head, in theme blue
check = Image.new("RGBA", (S, S), (0, 0, 0, 0))
cd = ImageDraw.Draw(check)
w = int(S * 0.055)
p1 = (cx - r * 0.48, cy + r * 0.02)
p2 = (cx - r * 0.12, cy + r * 0.38)
p3 = (cx + r * 0.52, cy - r * 0.34)
cd.line([p1, p2, p3], fill=(52, 130, 255, 255), width=w, joint="curve")
for p in (p1, p3):
    cd.ellipse((p[0] - w / 2, p[1] - w / 2, p[0] + w / 2, p[1] + w / 2), fill=(52, 130, 255, 255))
bg = Image.alpha_composite(bg, check)

out = bg.convert("RGB").resize((OUT, OUT), Image.LANCZOS)
out.save(sys.argv[1], "PNG")
print("saved", sys.argv[1], out.size)
