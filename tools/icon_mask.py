"""Applies a launcher-icon shape to a square 8-bit PNG and writes an RGBA PNG.

Usage: python3 -I tools/icon_mask.py IN.png OUT.png rounded|circle

`rounded` keeps a small transparent margin and rounds the corners (legacy square icon);
`circle` crops to a circle (legacy round icon). Edges are anti-aliased by 4x4 supersampling.
Standard library only, so it runs anywhere `python3` does.
"""
import math
import struct
import sys
import zlib


def read_png(path):
    data = open(path, "rb").read()
    pos, idat = 8, b""
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        kind, chunk = data[pos + 4:pos + 8], data[pos + 8:pos + 8 + length]
        if kind == b"IHDR":
            width, height, depth, color, _, _, interlace = struct.unpack(">IIBBBBB", chunk)
            if depth != 8 or color not in (2, 6) or interlace:
                sys.exit("expected an 8-bit, non-interlaced RGB or RGBA PNG")
        elif kind == b"IDAT":
            idat += chunk
        pos += 12 + length
    bpp = 4 if color == 6 else 3
    raw, stride = zlib.decompress(idat), width * bpp
    rows, prev = [], bytearray(stride)
    for y in range(height):
        start = y * (stride + 1)
        kind, line = raw[start], bytearray(raw[start + 1:start + 1 + stride])
        for i in range(stride):
            left = line[i - bpp] if i >= bpp else 0
            up, corner = prev[i], prev[i - bpp] if i >= bpp else 0
            if kind == 1:
                line[i] = (line[i] + left) & 255
            elif kind == 2:
                line[i] = (line[i] + up) & 255
            elif kind == 3:
                line[i] = (line[i] + (left + up) // 2) & 255
            elif kind == 4:
                guess = left + up - corner
                pa, pb, pc = abs(guess - left), abs(guess - up), abs(guess - corner)
                line[i] = (line[i] + (left if pa <= pb and pa <= pc else up if pb <= pc else corner)) & 255
        rows.append(line)
        prev = line
    return width, height, bpp, rows


def coverage(x, y, size, shape):
    """Fraction of pixel (x, y) inside the shape, from 4x4 subsamples."""
    inside = 0
    for sy in range(4):
        for sx in range(4):
            px, py = x + (sx + 0.5) / 4, y + (sy + 0.5) / 4
            if shape == "circle":
                r = size / 2
                inside += (px - r) ** 2 + (py - r) ** 2 <= r * r
            else:
                margin, radius = size / 24, size * 0.18
                lo, hi = margin + radius, size - margin - radius
                cx, cy = min(max(px, lo), hi), min(max(py, lo), hi)
                inside += (margin <= px <= size - margin and margin <= py <= size - margin
                           and math.hypot(px - cx, py - cy) <= radius)
    return inside / 16


def write_png(path, width, height, pixels):
    raw = b"".join(b"\x00" + bytes(row) for row in pixels)

    def chunk(kind, body):
        return (struct.pack(">I", len(body)) + kind + body
                + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF))

    with open(path, "wb") as out:
        out.write(b"\x89PNG\r\n\x1a\n")
        out.write(chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)))
        out.write(chunk(b"IDAT", zlib.compress(raw, 9)))
        out.write(chunk(b"IEND", b""))


def main():
    source, target, shape = sys.argv[1:4]
    if shape not in ("rounded", "circle"):
        sys.exit("shape must be rounded or circle")
    width, height, bpp, rows = read_png(source)
    if width != height:
        sys.exit("expected a square image")
    out = []
    for y in range(height):
        line = bytearray()
        for x in range(width):
            r, g, b = rows[y][x * bpp:x * bpp + 3]
            alpha = rows[y][x * bpp + 3] if bpp == 4 else 255
            line += bytes((r, g, b, round(alpha * coverage(x, y, width, shape))))
        out.append(line)
    write_png(target, width, height, out)


if __name__ == "__main__":
    main()
