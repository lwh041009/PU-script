from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math


ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "output" / "poster"
OUT_DIR.mkdir(parents=True, exist_ok=True)
OUT = OUT_DIR / "PU脚本_闲鱼宣传海报.png"
ICON = ROOT / "app" / "src" / "main" / "res" / "mipmap-xxxhdpi" / "ic_launcher.png"
W, H = 1242, 1660
FONT_REGULAR = "C:/Windows/Fonts/NotoSansSC-VF.ttf"
FONT_BOLD = "C:/Windows/Fonts/simhei.ttf"


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REGULAR, size)


def lerp(a, b, t):
    return int(a + (b - a) * t)


def gradient_bg(w, h):
    img = Image.new("RGB", (w, h), (255, 246, 238))
    px = img.load()
    top = (255, 237, 221)
    mid = (255, 249, 244)
    bot = (244, 249, 255)
    for y in range(h):
        t = y / (h - 1)
        if t < 0.55:
            k = t / 0.55
            c = tuple(lerp(top[i], mid[i], k) for i in range(3))
        else:
            k = (t - 0.55) / 0.45
            c = tuple(lerp(mid[i], bot[i], k) for i in range(3))
        for x in range(w):
            dx = (x - 150) / w
            dy = (y - 180) / h
            glow = max(0, 1 - math.sqrt(dx * dx + dy * dy) * 2.1)
            px[x, y] = tuple(min(255, int(c[i] + glow * (18 if i == 0 else 8))) for i in range(3))
    return img


def shadow(size, box, radius, blur=18, alpha=40):
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    draw.rounded_rectangle(box, radius=radius, fill=(0, 0, 0, alpha))
    return layer.filter(ImageFilter.GaussianBlur(blur))


def rr(draw, box, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def wrap_text(draw, pos, text, fnt, fill, max_width, line_gap=8):
    lines = []
    cur = ""
    for ch in text:
        test = cur + ch
        if draw.textbbox((0, 0), test, font=fnt)[2] <= max_width or not cur:
            cur = test
        else:
            lines.append(cur)
            cur = ch
    if cur:
        lines.append(cur)
    x, y = pos
    for line in lines:
        draw.text((x, y), line, font=fnt, fill=fill)
        y = draw.textbbox((x, y), line, font=fnt)[3] + line_gap
    return y


def paste_round(base, img, box, radius):
    x, y, w, h = box
    img = img.resize((w, h), Image.LANCZOS).convert("RGBA")
    mask = Image.new("L", (w, h), 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle([0, 0, w, h], radius=radius, fill=255)
    clipped = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    clipped.paste(img, (0, 0), mask)
    base.alpha_composite(clipped, (x, y))


def chip(draw, x, y, text, fill, fg, size=27):
    fnt = font(size, True)
    bbox = draw.textbbox((0, 0), text, font=fnt)
    w = bbox[2] - bbox[0] + 36
    h = bbox[3] - bbox[1] + 18
    rr(draw, [x, y, x + w, y + h], h // 2, fill)
    draw.text((x + 18, y + 7), text, font=fnt, fill=fg)
    return w


img = gradient_bg(W, H).convert("RGBA")
d = ImageDraw.Draw(img)
orange = (255, 112, 24, 255)
text = (33, 33, 33, 255)
muted = (112, 112, 112, 255)

for i, (col, alpha) in enumerate([((255, 122, 26), 28), ((255, 180, 90), 22), ((62, 115, 255), 15)]):
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    for n in range(3):
        y = 170 + i * 70 + n * 390
        pts = [(x, y + int(math.sin((x + n * 80) / 170) * 34)) for x in range(-80, W + 81, 40)]
        ld.line(pts, fill=(*col, alpha), width=5)
    img.alpha_composite(layer.filter(ImageFilter.GaussianBlur(0.7)))

img.alpha_composite(shadow((W, H), (70, 74, 205, 209), 34, 18, 28))
rr(d, [70, 74, 205, 209], 34, (255, 255, 255, 245))
if ICON.exists():
    paste_round(img, Image.open(ICON), (92, 96, 91, 91), 24)
else:
    d.text((103, 110), "PU", font=font(42, True), fill=orange)
chip(d, 230, 92, "安卓本地版", (255, 236, 220, 255), (255, 105, 18, 255))
chip(d, 415, 92, "不依赖电脑服务器", (238, 245, 255, 255), (43, 95, 185, 255))

d.text((70, 245), "PU 脚本", font=font(88, True), fill=text)
d.text((70, 352), "二课活动查询 · 预约报名 · 分数查看", font=font(38, True), fill=orange)
wrap_text(
    d,
    (72, 417),
    "原生安卓工具，登录学校账号后直接访问 PU 官方接口。活动筛选、详情查看、预约执行反馈都在手机本地完成。",
    font(29),
    muted,
    610,
    12,
)

features = [
    ("活动筛选", "全部 / 可参加 / 可报名 / 类型筛选"),
    ("预约报名", "到点自动尝试提交，结果通知反馈"),
    ("本地账号", "多账号保存，敏感信息本地加密"),
    ("北京时间", "同步校准时间，辅助预约判断"),
]
for idx, (title, desc) in enumerate(features):
    x = 70 + (idx % 2) * 330
    y = 575 + (idx // 2) * 145
    img.alpha_composite(shadow((W, H), (x, y, x + 295, y + 112), 24, 15, 18))
    rr(d, [x, y, x + 295, y + 112], 24, (255, 255, 255, 238), outline=(255, 226, 206, 255), width=2)
    d.ellipse([x + 22, y + 24, x + 44, y + 46], fill=orange)
    d.text((x + 60, y + 20), title, font=font(29, True), fill=text)
    wrap_text(d, (x + 24, y + 62), desc, font(22), muted, 248, 4)

phone_x, phone_y, phone_w, phone_h = 720, 210, 430, 940
img.alpha_composite(shadow((W, H), (phone_x, phone_y, phone_x + phone_w, phone_y + phone_h), 48, 28, 60))
rr(d, [phone_x, phone_y, phone_x + phone_w, phone_y + phone_h], 48, (28, 29, 34, 255))
sx, sy, sw, sh = phone_x + 26, phone_y + 34, phone_w - 52, phone_h - 68
rr(d, [sx, sy, sx + sw, sy + sh], 30, (255, 247, 240, 255))
d.text((sx + 26, sy + 28), "活动详情", font=font(29, True), fill=text)
rr(d, [sx + 26, sy + 86, sx + 126, sy + 186], 18, (255, 255, 255, 255), outline=(242, 226, 214, 255), width=1)
if ICON.exists():
    paste_round(img, Image.open(ICON), (sx + 43, sy + 103, 66, 66), 18)
rr(d, [sx + 26, sy + 86, sx + 94, sy + 122], 18, (240, 82, 82, 255))
d.text((sx + 38, sy + 92), "未开始", font=font(16, True), fill=(255, 255, 255, 255))
wrap_text(d, (sx + 144, sy + 92), "【计算机学院】暑期三下乡经验分享会", font(24, True), text, 205, 3)
d.text((sx + 144, sy + 158), "分类：社会实践与志愿服务", font=font(17), fill=orange)

cx, cy = sx + 26, sy + 220
d.text((cx, cy + 12), "距离报名开始:", font=font(19), fill=text)
for i, val in enumerate(["0天", "01", "53", "23"]):
    bx = cx + 132 + i * 58
    rr(d, [bx, cy, bx + 48, cy + 42], 10, (255, 255, 255, 255))
    d.text((bx + 24, cy + 8), val, font=font(17, True), fill=text, anchor="ma")
    if i in [1, 2]:
        d.text((bx + 54, cy + 6), ":", font=font(24, True), fill=text)

cardx, cardy = sx + 24, sy + 288
rr(d, [cardx, cardy, cardx + sw - 48, cardy + 360], 22, (255, 255, 255, 250))
for i, (num, label) in enumerate([("300", "可参与人数"), ("271", "已报名"), ("1", "已签到"), ("1", "已签退")]):
    colw = (sw - 48) / 4
    tx = cardx + i * colw + colw / 2
    d.text((tx, cardy + 42), num, font=font(25), fill=text, anchor="ma")
    d.text((tx, cardy + 76), label, font=font(16), fill=(130, 130, 130, 255), anchor="ma")
for x in range(cardx + 20, cardx + sw - 68, 22):
    d.line([x, cardy + 130, x + 10, cardy + 130], fill=(230, 218, 210, 255), width=2)
for i, title in enumerate(["报名时间", "活动时间"]):
    tx = cardx + 34 + i * 170
    d.ellipse([tx, cardy + 168, tx + 18, cardy + 186], outline=orange, width=5)
    d.text((tx + 32, cardy + 160), title, font=font(20, True), fill=text)
    d.text((tx + 8, cardy + 216), "16:00", font=font(18), fill=text)
    d.text((tx + 8, cardy + 244), "2026.05.31", font=font(14), fill=(130, 130, 130, 255))
d.text((cardx + 28, cardy + 300), "活动地址：J13 蓝光报告厅", font=font(18), fill=text)
rr(d, [sx + 26, sy + sh - 82, sx + 178, sy + sh - 34], 14, (43, 43, 43, 255))
d.text((sx + 102, sy + sh - 70), "报名已开始", font=font(18), fill=(255, 255, 255, 255), anchor="ma")
rr(d, [sx + 196, sy + sh - 82, sx + sw - 26, sy + sh - 34], 14, orange)
d.text((sx + 287, sy + sh - 70), "报名", font=font(20, True), fill=(255, 255, 255, 255), anchor="ma")

panel_y = 1210
img.alpha_composite(shadow((W, H), (70, panel_y, 1172, 1518), 34, 22, 32))
rr(d, [70, panel_y, 1172, 1518], 34, (255, 255, 255, 242), outline=(255, 226, 206, 255), width=2)
d.text((110, panel_y + 46), "适合谁用？", font=font(40, True), fill=text)
for i, line in enumerate(["经常看二课活动的同学", "想提前预约、减少手动操作的人", "需要手机本地管理账号和预约记录的人"]):
    yy = panel_y + 120 + i * 55
    d.ellipse([112, yy + 6, 132, yy + 26], fill=orange)
    d.text((150, yy), line, font=font(27), fill=text)
rr(d, [110, panel_y + 258, 1132, panel_y + 292], 17, (246, 247, 249, 255))
d.text((621, panel_y + 263), "不提供账号｜不代报名｜不承诺成功｜仅作效率辅助工具", font=font(22), fill=(120, 120, 120, 255), anchor="ma")
rr(d, [70, 1548, 1172, 1612], 24, orange)
d.text((110, 1563), "PU 脚本 · APK 安装包 + 使用说明", font=font(28, True), fill=(255, 255, 255, 255))
d.text((1130, 1564), "安卓", font=font(28, True), fill=(255, 255, 255, 255), anchor="ra")

img.convert("RGB").save(OUT, quality=95)
print(OUT)
