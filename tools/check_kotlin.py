import os, sys

def strip_literals(s: str) -> str:
    out = []
    i, n = 0, len(s)
    while i < n:
        c = s[i]
        if c == '"':
            i += 1
            while i < n and s[i] != '"':
                i += 2 if s[i] == '\\' else 1
            i += 1
            continue
        if c == "'":
            i += 1
            while i < n and s[i] != "'":
                i += 2 if s[i] == '\\' else 1
            i += 1
            continue
        if c == '/' and i + 1 < n and s[i + 1] == '/':
            while i < n and s[i] != '\n':
                i += 1
            continue
        if c == '/' and i + 1 < n and s[i + 1] == '*':
            i += 2
            while i + 1 < n and not (s[i] == '*' and s[i + 1] == '/'):
                i += 1
            i += 2
            continue
        out.append(c)
        i += 1
    return ''.join(out)

def check(path: str):
    raw = open(path, encoding='utf-8').read()
    src = strip_literals(raw)
    return (src.count('{') - src.count('}'), src.count('(') - src.count(')'), src.count('[') - src.count(']'), len(raw.splitlines()))

root = sys.argv[1]
bad = 0
files = []
for dp, dn, fn in os.walk(root):
    for f in sorted(fn):
        if f.endswith('.kt') or f.endswith('.kts'):
            files.append(os.path.join(dp, f))
for p in sorted(files):
    b, q, s, n = check(p)
    flag = ''
    if b or q or s:
        bad += 1
        flag = '  <== UNBALANCED'
    print('%+d %+d %+d %5d  %s%s' % (b, q, s, n, os.path.relpath(p, root), flag))
print('files=%d unbalanced=%d' % (len(files), bad))
