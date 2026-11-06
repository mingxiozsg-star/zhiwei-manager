#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
知微管理器图标生成器（API 24/25 用的传统 PNG，API 26+ 走自适应矢量图标）。

设计：
  底色   靛蓝 #4F46E5 → 青 #06B6D4 对角渐变（与主题同源）
  前景   两个白色圆角窗格（左=文件行，右=「…」三点），像素级与 drawable/ic_launcher_foreground.xml 对齐
  遮蔽   标准图标=圆角方；圆形图标=正圆

只依赖标准库（zlib + struct），不装 Pillow/ImageMagick，避免污染构建环境。
用法：python3 tools/make_icons.py <res 目录>
"""
import os
import struct
import sys
import zlib

# —— 108×108 逻辑画布（与矢量图标 viewport 完全一致）——
CANVAS = 108.0
BG_FROM = (0x4F, 0x46, 0xE5)
BG_TO = (0x06, 0xB6, 0xD4)


def rounded_rect(x, y, x0, y0, x1, y1, r):
    if x < x0 or x >= x1 or y < y0 or y >= y1:
        return False
    cx = min(max(x, x0 + r), x1 - r)
    cy = min(max(y, y0 + r), y1 - r)
    dx, dy = x - cx, y - cy
    return dx * dx + dy * dy <= r * r


def rect(x, y, x0, y0, x1, y1):
    return x0 <= x < x1 and y0 <= y < y1


def in_circle(x, y, cx, cy, r):
    return (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def foreground(x, y):
    """返回 (是否命中白色前景)。"""
    left = rounded_rect(x, y, 18, 32, 52, 80, 5)
    right = rounded_rect(x, y, 56, 32, 90, 80, 5)
    if not (left or right):
        return False
    # 左窗格：三条「文件行」挖空
    if left and (rect(x, y, 25, 41, 45, 45) or rect(x, y, 25, 51, 45, 55) or rect(x, y, 25, 61, 37, 65)):
        return False
    # 右窗格：「…」三个点挖空
    if right and (rect(x, y, 63, 53, 69, 59) or rect(x, y, 71.5, 53, 77.5, 59) or rect(x, y, 80, 53, 86, 59)):
        return False
    return True


def mask_round_square(x, y):
    return rounded_rect(x, y, 0, 0, CANVAS, CANVAS, CANVAS * 0.22)


def mask_circle(x, y):
    return in_circle(x, y, CANVAS / 2, CANVAS / 2, CANVAS / 2)


def render(size, round_icon):
    supersample = 3
    mask = mask_circle if round_icon else mask_round_square
    rows = []
    step = CANVAS / size
    for py in range(size):
        row = bytearray()
        for px in range(size):
            r_acc = g_acc = b_acc = a_acc = 0
            for sy in range(supersample):
                for sx in range(supersample):
                    lx = (px + (sx + 0.5) / supersample) * step
                    ly = (py + (sy + 0.5) / supersample) * step
                    if not mask(lx, ly):
                        continue
                    a_acc += 255
                    if foreground(lx, ly):
                        r_acc += 255
                        g_acc += 255
                        b_acc += 255
                    else:
                        t = (lx + ly) / (2 * CANVAS)
                        r_acc += round(BG_FROM[0] + (BG_TO[0] - BG_FROM[0]) * t)
                        g_acc += round(BG_FROM[1] + (BG_TO[1] - BG_FROM[1]) * t)
                        b_acc += round(BG_FROM[2] + (BG_TO[2] - BG_FROM[2]) * t)
            n = supersample * supersample
            row += bytes((r_acc // n, g_acc // n, b_acc // n, a_acc // n))
        rows.append(bytes(row))
    return rows


def write_png(path, rows, size):
    raw = b"".join(b"\x00" + r for r in rows)

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def main():
    res = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res"
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for density, size in sizes.items():
        out = os.path.join(res, "mipmap-" + density)
        os.makedirs(out, exist_ok=True)
        for name, round_icon in (("ic_launcher", False), ("ic_launcher_round", True)):
            p = os.path.join(out, name + ".png")
            write_png(p, render(size, round_icon), size)
            print("wrote", p, size)


if __name__ == "__main__":
    main()
