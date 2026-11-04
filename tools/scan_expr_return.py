#!/usr/bin/env python3
"""粗筛：表达式体函数（fun ... = ...）内部出现 return，Kotlin 会直接报错。"""
import re
import pathlib
import sys


def main() -> int:
    root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "app/src/main/java")
    fun_re = re.compile(r"\bfun\s+(?:[A-Za-z_][\w.]*\.)?\w+\s*\(")
    expr_re = re.compile(r"\)\s*(?::[^=]+)?=\s*")
    ret_re = re.compile(r"(^|[^A-Za-z0-9_.])return(\b|$)")
    bad = []
    for f in sorted(root.rglob("*.kt")):
        expr_body = False
        for i, ln in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
            if fun_re.search(ln):
                expr_body = bool(expr_re.search(ln))
                continue
            if expr_body and ret_re.search(ln):
                bad.append(f"{f}:{i}: {ln.strip()}")
    print("\n".join(bad) if bad else "clean")
    print("total", len(bad))
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())
