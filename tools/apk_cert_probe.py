#!/usr/bin/env python3
# APK 签名证书探针：直接从 APK Signing Block 取 v2/v3 证书，算 SHA-1 / SHA-256。
# 不依赖 apksigner / androguard / aapt2，纯标准库。用于判定「新旧包签名是否同一把密钥」。
# 用法: python3 tools/apk_cert_probe.py <a.apk> [b.apk ...]

import hashlib
import os
import struct
import sys

V2 = 0x7109871A
V3 = 0xF05368C0
V31 = 0x1B93AD61
MAGIC = b"APK Sig Block 42"

SCHEME = {V2: "v2", V3: "v3", V31: "v3.1"}


def u32(b, o):
    return struct.unpack_from("<I", b, o)[0]


def u64(b, o):
    return struct.unpack_from("<Q", b, o)[0]


def find_eocd(data):
    tail = min(len(data), 65557)
    start = len(data) - tail
    for i in range(len(data) - 22, start - 1, -1):
        if data[i:i + 4] == b"PK\x05\x06":
            return i
    return -1


def signing_block(data):
    """返回 (block_bytes 或 None, 说明)"""
    eocd = find_eocd(data)
    if eocd < 0:
        return None, "未找到 EOCD（不是合法 zip）"
    try:
        cd_off = u32(data, eocd + 16)
    except Exception as e:
        return None, "EOCD 解析失败: %s" % e
    if cd_off < 32 or cd_off > len(data):
        return None, "Central Directory 偏移异常 (%d)" % cd_off
    if data[cd_off - 16:cd_off] != MAGIC:
        return None, "Central Directory 前 16 字节不是 APK Sig Block 42 → 无 sig block"
    size = u64(data, cd_off - 24)
    start = cd_off - 8 - size
    if start < 0:
        return None, "sig block size 越界 (%d)" % size
    return data[start:cd_off - 8], "ok"


def id_values(block):
    """遍历 sig block 里的 id-value 对"""
    out = []
    if len(block) < 8:
        return out
    end = len(block) - 8  # 末尾还有一个 size 字段
    off = 8  # 开头第一个 size 字段
    while off + 8 <= end:
        ln = u64(block, off)
        off += 8
        if ln < 4 or off + ln > end:
            break
        pair_id = u32(block, off)
        out.append((pair_id, block[off + 4: off + ln]))
        off += ln
    return out


def lp(buf, off):
    """读一个 length-prefixed 字段，返回 (内容, 下一个偏移)"""
    n = u32(buf, off)
    s = off + 4
    e = s + n
    if e > len(buf):
        raise ValueError("length-prefixed 越界")
    return buf[s:e], e


def certs_of(value, scheme):
    """从某个 scheme 的 value 里取出所有证书 DER"""
    certs = []
    signers, _ = lp(value, 0)
    off = 0
    while off < len(signers):
        signer, off = lp(signers, off)
        signed_data, _ = lp(signer, 0)
        # signed data: [digests][certificates][...]
        _, p = lp(signed_data, 0)
        cert_seq, _ = lp(signed_data, p)
        q = 0
        while q < len(cert_seq):
            der, q = lp(cert_seq, q)
            certs.append(der)
    return certs


def report(path):
    print("================ %s" % path)
    with open(path, "rb") as f:
        data = f.read()
    print("  size            : %d" % len(data))
    block, msg = signing_block(data)
    if block is None:
        print("  sig block       : %s" % msg)
        return None
    print("  sig block       : %s (%d bytes)" % (msg, len(block)))
    pairs = id_values(block)
    found = []
    for pid, val in pairs:
        name = SCHEME.get(pid, "0x%08x" % pid)
        print("  block id        : %s  value=%d bytes  sha256=%s"
              % (name, len(val), hashlib.sha256(val).hexdigest()[:32]))
        if pid in (V2, V3, V31):
            found.append((pid, val))
    if not found:
        print("  -> 未发现 v2/v3 签名块")
        return None
    result = []
    for pid, val in found:
        try:
            certs = certs_of(val, pid)
        except Exception as e:
            print("  %s 证书解析失败: %s" % (SCHEME[pid], e))
            continue
        if not certs:
            print("  %s 未解析出证书" % SCHEME[pid])
            continue
        for i, der in enumerate(certs):
            sha256 = hashlib.sha256(der).hexdigest()
            sha1 = hashlib.sha1(der).hexdigest()
            print("  %s cert[%d] SHA-256: %s" % (SCHEME[pid], i, sha256))
            print("  %s cert[%d] SHA-1  : %s" % (SCHEME[pid], i, sha1))
            subject = "?"
            try:
                subject = pretty_subject(der)
            except Exception:
                pass
            print("  %s cert[%d] 名称    : %s" % (SCHEME[pid], i, subject))
            result.append(sha256)
            dump = os.environ.get("CERT_DUMP_DIR")
            if dump:
                os.makedirs(dump, exist_ok=True)
                base = os.path.basename(path).replace(".apk", "")
                fp = os.path.join(dump, "%s_%s_%d.der" % (base, SCHEME[pid], i))
                with open(fp, "wb") as w:
                    w.write(der)
                print("  %s cert[%d] 已导出  : %s" % (SCHEME[pid], i, fp))
    return result


def pretty_subject(der):
    """极简 DER 扫描，抽 CN= / OU= / O= 可打印串"""
    want = {b"\x06\x03\x55\x04\x03": "CN", b"\x06\x03\x55\x04\x0b": "OU",
            b"\x06\x03\x55\x04\x0a": "O", b"\x06\x03\x55\x04\x06": "C"}
    out = []
    for tag, label in want.items():
        idx = der.find(tag)
        while idx >= 0:
            p = idx + len(tag)
            if p < len(der) and der[p] == 0x0C:
                n = der[p + 1]
                if n & 0x80:
                    nb = n & 0x7F
                    n = int.from_bytes(der[p + 2:p + 2 + nb], "big")
                    p += 2 + nb
                else:
                    p += 2
                try:
                    out.append("%s=%s" % (label, der[p:p + n].decode("utf-8", "replace")))
                except Exception:
                    pass
            idx = der.find(tag, idx + 1)
    return ", ".join(out)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("用法: python3 apk_cert_probe.py <a.apk> [b.apk ...]")
        sys.exit(2)
    allres = {}
    for p in sys.argv[1:]:
        try:
            allres[p] = report(p)
        except Exception as e:
            print("  !! 异常: %s" % e)
            allres[p] = None
    if len(allres) >= 2:
        sets = {k: sorted(set(v)) for k, v in allres.items() if v}
        print("\n================ 结论")
        for k, v in sets.items():
            print("  %s -> %s" % (k, v))
        vals = list(sets.values())
        if len(vals) >= 2:
            same = all(v == vals[0] for v in vals[1:])
            print("  签名证书 %s" % ("【完全一致】" if same else "【不一致 —— 这就是覆盖安装冲突的根因】"))
