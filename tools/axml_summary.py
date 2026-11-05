#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
极简 AXML 解析器（无第三方依赖）。

用途：APK 的 res/ 目录被混淆 / 被打散（文件名乱码、路径丢失）时，仍可直接按
zip 条目读取二进制 AXML，还原元素层级与常见属性，用于分析布局与双窗口结构。

用法：
    python3 axml_summary.py <apk> [--min-size N] [--dump NAME] [--top K]
不带参数时列出所有 AXML 及其规模；--dump 输出指定条目名的完整 XML 文本。
"""
import sys
import zipfile

STRING_POOL = 0x0001
XML_START_NS = 0x0100
XML_END_NS = 0x0101
XML_START = 0x0102
XML_END = 0x0103
XML_CDATA = 0x0104
XML_RESOURCE_MAP = 0x0180

TYPE_REFERENCE = 0x01
TYPE_STRING = 0x03
TYPE_FLOAT = 0x04
TYPE_INT_DEC = 0x10
TYPE_INT_HEX = 0x11
TYPE_INT_BOOLEAN = 0x12
TYPE_INT_COLOR = 0x1C


def _u16(b, o):
    return b[o] | (b[o + 1] << 8)


def _u32(b, o):
    return b[o] | (b[o + 1] << 8) | (b[o + 2] << 16) | (b[o + 3] << 24)


def parse_string_pool(b, off, size):
    cnt = _u32(b, off + 8)
    flags = _u32(b, off + 16)
    str_start = _u32(b, off + 20)
    utf8 = (flags & 0x100) != 0
    offsets = [_u32(b, off + 28 + 4 * i) for i in range(cnt)]
    out = []
    base = off + str_start
    for so in offsets:
        p = base + so
        try:
            if utf8:
                # u8/u16 char count (high bit = 2-byte), then u8/u16 byte count
                n = b[p]
                p += 1
                if n & 0x80:
                    p += 1
                m = b[p]
                p += 1
                if m & 0x80:
                    bytes_len = ((m & 0x7F) << 8) | b[p]
                    p += 1
                else:
                    bytes_len = m
                out.append(b[p:p + bytes_len].decode("utf-8", "replace"))
            else:
                n = _u16(b, p)
                p += 2
                if n & 0x8000:
                    n = ((n & 0x7FFF) << 16) | _u16(b, p)
                    p += 2
                out.append(b[p:p + n * 2].decode("utf-16-le", "replace"))
        except Exception:
            out.append("")
    return out


def parse_axml(b):
    """返回 (strings, events)；events 为 (kind, name, attrs) 序列。"""
    strings = []
    events = []
    p = 8
    while p + 8 <= len(b):
        ctype = _u16(b, p)
        hsize = _u16(b, p + 2)
        size = _u32(b, p + 4)
        if size <= 0 or p + size > len(b):
            break
        if ctype == STRING_POOL:
            strings = parse_string_pool(b, p, size)
        elif ctype == XML_START:
            name_idx = _u32(b, p + 20)
            attr_count = _u16(b, p + 28)
            attr_start = _u16(b, p + 24)
            attrs = []
            ao = p + 16 + attr_start
            for i in range(attr_count):
                q = ao + i * 20
                an = _u32(b, q + 4)
                raw = _u32(b, q + 8)
                dtype = b[q + 15]
                data = _u32(b, q + 16)
                aname = strings[an] if an < len(strings) else "?"
                # 属性名可能带 android: 前缀（命名空间 URI 也在字符串池里，这里做简化还原）
                aname = aname.rsplit("/", 1)[-1]
                if aname == "android":
                    aname = "@android"
                if raw != 0xFFFFFFFF and raw < len(strings):
                    aval = strings[raw]
                elif dtype == TYPE_STRING and data < len(strings):
                    aval = strings[data]
                elif dtype in (TYPE_INT_DEC, TYPE_INT_HEX):
                    aval = str(data)
                elif dtype == TYPE_INT_BOOLEAN:
                    aval = "true" if data else "false"
                elif dtype == TYPE_REFERENCE:
                    aval = "@0x%08x" % data
                elif dtype == TYPE_FLOAT:
                    import struct
                    aval = str(struct.unpack("<f", struct.pack("<I", data))[0])
                elif dtype == TYPE_INT_COLOR:
                    aval = "#%08x" % data
                else:
                    aval = "?"
                attrs.append((aname, aval))
            events.append(("start", strings[name_idx] if name_idx < len(strings) else "?", attrs))
        elif ctype == XML_END:
            name_idx = _u32(b, p + 20)
            events.append(("end", strings[name_idx] if name_idx < len(strings) else "?", []))
        p += size
    return strings, events


def to_xml(events, indent="  "):
    lines = []
    depth = 0
    for kind, name, attrs in events:
        if kind == "end":
            depth = max(0, depth - 1)
            lines.append(indent * depth + "</%s>" % name)
        else:
            attr_txt = "".join(' %s="%s"' % (k, v) for k, v in attrs if k)
            lines.append(indent * depth + "<%s%s>" % (name, attr_txt))
            depth += 1
    return "\n".join(lines)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return
    apk = sys.argv[1]
    min_size = 0
    top = 12
    dump = None
    args = sys.argv[2:]
    i = 0
    while i < len(args):
        if args[i] == "--min-size":
            min_size = int(args[i + 1]); i += 2
        elif args[i] == "--top":
            top = int(args[i + 1]); i += 2
        elif args[i] == "--dump":
            dump = args[i + 1]; i += 2
        else:
            i += 1

    z = zipfile.ZipFile(apk)
    items = []
    for info in z.infolist():
        if info.file_size < 12:
            continue
        try:
            with z.open(info) as f:
                head = f.read(4)
        except Exception:
            continue
        if head == b"\x03\x00\x08\x00":
            items.append(info)

    print("AXML 条目总数：%d" % len(items))

    if dump is not None:
        for info in items:
            if info.filename == dump:
                with z.open(info) as f:
                    _, events = parse_axml(f.read())
                print(to_xml(events))
                return
        print("未找到：%s" % dump)
        return

    rows = []
    for info in sorted(items, key=lambda x: -x.file_size)[:top]:
        if info.file_size < min_size:
            continue
        with z.open(info) as f:
            _, events = parse_axml(f.read())
        counts = {}
        for kind, name, _ in events:
            if kind == "start":
                counts[name] = counts.get(name, 0) + 1
        rows.append((info.filename, info.file_size, len(events) // 2, counts))
    for fn, sz, nodes, counts in rows:
        hot = {k: v for k, v in counts.items() if v > 1}
        print("%-24s %7dB 节点%4d  多实例元素：%s" % (repr(fn)[:24], sz, nodes, hot))


if __name__ == "__main__":
    main()
