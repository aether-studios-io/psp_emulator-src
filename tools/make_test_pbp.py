"""Wraps a homebrew PSP executable (ELF/PRX) into an EBOOT.PBP for on-device testing.

    python tools/make_test_pbp.py <program.prx|elf> <out/EBOOT.PBP> [--title "Cube Demo"]

Use only free/open test programs (e.g. PPSSPP's pspautotests demos). Never commercial data.
"""
import argparse
import struct
import zlib


def param_sfo(values):
    keys = sorted(values)
    key_table = b""
    key_offsets = []
    for k in keys:
        key_offsets.append(len(key_table))
        key_table += k.encode() + b"\0"
    while len(key_table) % 4:
        key_table += b"\0"
    data_table = b""
    entries = []
    for k in keys:
        v = values[k]
        if isinstance(v, int):
            raw, fmt, max_len = struct.pack("<I", v), 0x0404, 4
            length = 4
        else:
            raw = v.encode() + b"\0"
            fmt, length = 0x0204, len(raw)
            max_len = (len(raw) + 3) // 4 * 4
            raw = raw.ljust(max_len, b"\0")
        entries.append((len(data_table), length, max_len, fmt))
        data_table += raw
    header_size = 20 + 16 * len(keys)
    key_start = header_size
    data_start = key_start + len(key_table)
    out = b"\0PSF" + struct.pack("<IIII", 0x0101, key_start, data_start, len(keys))
    for i, k in enumerate(keys):
        off, length, max_len, fmt = entries[i]
        out += struct.pack("<HHIII", key_offsets[i], fmt, length, max_len, off)
    return out + key_table + data_table


def png(width=144, height=80, rgb=(0x00, 0x70, 0xD0)):
    row = b"\0" + bytes(rgb) * width
    raw = row * height

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("program")
    ap.add_argument("out")
    ap.add_argument("--title", default="Cube Demo")
    ap.add_argument("--disc-id", default="HBRW00001")
    a = ap.parse_args()
    data = open(a.program, "rb").read()
    sfo = param_sfo({
        "TITLE": a.title, "DISC_ID": a.disc_id, "CATEGORY": "MG", "DISC_VERSION": "1.00",
        "PSP_SYSTEM_VER": "1.00", "BOOTABLE": 1, "PARENTAL_LEVEL": 1, "REGION": 32768,
    })
    icon = png()
    off_sfo = 40
    off_icon = off_sfo + len(sfo)
    off_data = off_icon + len(icon)
    header = b"\0PBP" + struct.pack("<I", 0x10000) + struct.pack(
        "<8I", off_sfo, off_icon, off_data, off_data, off_data, off_data, off_data, off_data + len(data))
    with open(a.out, "wb") as f:
        f.write(header + sfo + icon + data)
    print(f"wrote {a.out} ({off_data + len(data)} bytes)")


if __name__ == "__main__":
    main()
