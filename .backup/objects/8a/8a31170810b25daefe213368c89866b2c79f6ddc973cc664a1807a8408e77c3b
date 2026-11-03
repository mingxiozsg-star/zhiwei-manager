#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""跨文件符号粗检：对 object/类 的静态调用点（X.member）检查是否在同名源文件中有声明。
只做名字级匹配，目的是发现拼写错误/漏实现，不替代真正的编译。"""
import os
import re
import sys
from collections import defaultdict

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
SKIP = {"R", "Build", "Companion", "MaterialTheme", "Modifier", "Color", "File", "Uri",
        "Intent", "BuildConfig", "Dispatchers", "Log", "Toast", "AlertDialog", "Surface"}

decl_files = {}
members = defaultdict(set)
texts = {}

for dirpath, _dirs, files in os.walk(ROOT):
    for fn in files:
        if not fn.endswith(".kt"):
            continue
        p = os.path.join(dirpath, fn)
        src = open(p, encoding="utf-8").read()
        texts[p] = src
        for m in re.finditer(r"^\s*(?:private\s+|internal\s+|public\s+)?(?:open\s+|abstract\s+|data\s+|enum\s+|sealed\s+)*(object|class|interface)\s+([A-Za-z0-9_]+)", src, re.M):
            decl_files.setdefault(m.group(2), []).append(p)
        # 顶层 fun / val
        for m in re.finditer(r"^\s*(?:private\s+|internal\s+|public\s+)?(?:suspend\s+)?(?:inline\s+)?fun\s+(?:<[^>]*>\s*)?([A-Za-z0-9_]+)\s*\(", src, re.M):
            members[fn].add(m.group(1))
            members["__top__:" + os.path.basename(fn)].add(m.group(1))
        # 成员 fun / val（缩进 4+）
        for m in re.finditer(r"^\s{2,}(?:private\s+|internal\s+|public\s+|protected\s+)?(?:override\s+)?(?:suspend\s+)?(?:inline\s+)?fun\s+(?:<[^>]*>\s*)?([A-Za-z0-9_]+)\s*\(", src, re.M):
            members[os.path.basename(fn)].add(m.group(1))
        for m in re.finditer(r"^\s{2,}(?:private\s+|internal\s+|public\s+|protected\s+)?(?:override\s+)?(?:val|var)\s+([A-Za-z0-9_]+)", src, re.M):
            members[os.path.basename(fn)].add(m.group(1))

problems = []
for p, src in texts.items():
    for m in re.finditer(r"\b([A-Z][A-Za-z0-9_]*)\.([a-z][A-Za-z0-9_]*)\s*[(.]", src):
        cls, mem = m.group(1), m.group(2)
        if cls in SKIP or cls not in decl_files:
            continue
        if not any(mem in members.get(os.path.basename(f), set()) for f in decl_files[cls]):
            line = src[:m.start()].count("\n") + 1
            problems.append((p, line, cls, mem))

if problems:
    print("可能缺失的成员引用：%d" % len(problems))
    for p, line, cls, mem in problems:
        print("  %s:%d  %s.%s" % (os.path.relpath(p, ROOT), line, cls, mem))
else:
    print("跨文件静态引用检查通过：未发现缺失成员")