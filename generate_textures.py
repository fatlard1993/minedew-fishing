#!/usr/bin/env python3
"""Generate minedew-fishing's minigame HUD art.

Pure-stdlib PNG writer (zlib + struct) so it runs without Pillow; same
script-generated-texture approach as poopsmith's generate_textures.py.
Deterministic (no RNG at all here) so re-running produces identical bytes.

Every texture is drawn at exactly the pixel size the HUD blits it at, because
Pandorical's sprite component stretches a texture to the component bounds
unless it is in clip mode. The sizes below are the single source of truth and
are mirrored by the constants in hud/MinigameHud.java.

    track.png        26x150  the water column the fish and bobber live in
    gauge.png         8x150  the catch-progress gauge's empty frame
    gauge_fill.png    6x146  the gauge's fill, revealed upward via texture_v
    fish.png         12x12   the fish marker
    chest.png        10x10   the treasure chest, closed
    chest_open.png   10x10   the treasure chest, secured
"""

import os
import struct
import zlib

OUT = os.path.join(os.path.dirname(__file__),
                   "src/main/resources/assets/minedew-fishing/textures/gui")

CLEAR = (0, 0, 0, 0)

# Frame: weathered dock timber, dark enough to read over any sky
FRAME_DARK = (0x21, 0x18, 0x11, 0xFF)
FRAME_MID = (0x4A, 0x35, 0x20, 0xFF)
FRAME_LIGHT = (0x6E, 0x51, 0x31, 0xFF)

# Water column: deeper and colder toward the bottom of the track
WATER_TOP = (0x2A, 0x6D, 0x8C, 0xE6)
WATER_BOTTOM = (0x0E, 0x24, 0x3E, 0xE6)
RIPPLE = (0x4E, 0x9A, 0xB8, 0x40)

# Gauge: red at empty, amber through the middle, green at full
GAUGE_EMPTY = (0xC0, 0x36, 0x2C, 0xFF)
GAUGE_MID = (0xD8, 0xA5, 0x2B, 0xFF)
GAUGE_FULL = (0x4F, 0xC3, 0x4A, 0xFF)
GAUGE_SLOT = (0x14, 0x12, 0x10, 0xFF)

# Fish marker: warm orange against the cold water
FISH_BODY = (0xE8, 0x8B, 0x28, 0xFF)
FISH_DARK = (0xB4, 0x62, 0x14, 0xFF)
FISH_LIGHT = (0xF6, 0xC0, 0x62, 0xFF)
FISH_EYE = (0x1A, 0x10, 0x08, 0xFF)

# Treasure chest
CHEST_WOOD = (0x8A, 0x5A, 0x2C, 0xFF)
CHEST_DARK = (0x5A, 0x38, 0x18, 0xFF)
CHEST_GOLD = (0xE8, 0xC0, 0x4A, 0xFF)
CHEST_GLOW = (0xFF, 0xF0, 0xA8, 0xFF)


def write_png(path, pixels):
    """pixels: list of rows, each row a list of RGBA tuples."""
    height = len(pixels)
    width = len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(bytes(px) for px in row) for row in pixels)

    def chunk(tag, data):
        c = tag + data
        return struct.pack(">I", len(data)) + c + struct.pack(">I", zlib.crc32(c))

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print(f"wrote {path} ({width}x{height})")


def blend(a, b, t):
    """Linear blend between two RGBA tuples, t in 0..1."""
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(4))


def track(width=26, height=150, border=3):
    rows = [[CLEAR] * width for _ in range(height)]
    inner_h = height - border * 2
    for y in range(height):
        for x in range(width):
            edge = min(x, y, width - 1 - x, height - 1 - y)
            if edge == 0:
                rows[y][x] = FRAME_DARK
            elif edge == 1:
                rows[y][x] = FRAME_MID
            elif edge == 2:
                # Inner bevel: lit on the left/top, shaded on the right/bottom
                lit = x <= 2 or y <= 2
                rows[y][x] = FRAME_LIGHT if lit else FRAME_DARK
            else:
                t = (y - border) / max(1, inner_h - 1)
                rows[y][x] = blend(WATER_TOP, WATER_BOTTOM, t)

    # Ripple lines: faint horizontal bands so vertical motion has a reference
    for y in range(border + 6, height - border, 12):
        for x in range(border + 1, width - border - 1):
            base = rows[y][x]
            rows[y][x] = blend(base, RIPPLE, 0.55)
    return rows


def gauge(width=8, height=150):
    rows = [[CLEAR] * width for _ in range(height)]
    for y in range(height):
        for x in range(width):
            edge = min(x, y, width - 1 - x, height - 1 - y)
            if edge == 0:
                rows[y][x] = FRAME_DARK
            elif edge == 1:
                rows[y][x] = FRAME_MID
            else:
                rows[y][x] = GAUGE_SLOT
    return rows


def gauge_fill(width=6, height=146):
    """Bottom row is 'empty' red, top row is 'full' green: the HUD reveals this
    upward by moving the source origin (texture_v) in step with the height, so
    the visible slice is always the BOTTOM of this gradient."""
    rows = []
    for y in range(height):
        # y=0 is the top of the texture = the full end of the gauge
        t = y / max(1, height - 1)
        if t < 0.5:
            color = blend(GAUGE_FULL, GAUGE_MID, t / 0.5)
        else:
            color = blend(GAUGE_MID, GAUGE_EMPTY, (t - 0.5) / 0.5)
        row = []
        for x in range(width):
            # A one-pixel highlight down the left edge to keep it from reading flat
            row.append(blend(color, (0xFF, 0xFF, 0xFF, 0xFF), 0.22) if x == 0 else color)
        rows.append(row)
    return rows


def from_map(art, palette):
    """Build pixel rows from a character map. '.' is always transparent."""
    return [[CLEAR if ch == "." else palette[ch] for ch in row] for row in art]


# Left-facing fish, body centered on row 6 of 12 so the marker's vertical
# center is the sprite's center.
FISH_ART = [
    "............",
    "............",
    "............",
    ".....dd.....",
    "...LLLLL..f.",
    "..LXXXXXX.ff",
    ".oXXXXXXXfff",
    "..XXXXXXX.ff",
    "...ddddd..f.",
    ".....dd.....",
    "............",
    "............",
]

CHEST_ART = [
    "..........",
    "..dddddd..",
    ".dLLLLLLd.",
    ".dLLLLLLd.",
    ".dGGGGGGd.",
    ".dWWWGWWd.",
    ".dWWWWWWd.",
    ".dWWWWWWd.",
    "..dddddd..",
    "..........",
]

CHEST_OPEN_ART = [
    "..........",
    "..gggggg..",
    ".gGGGGGGg.",
    ".gGGGGGGg.",
    ".dGGGGGGd.",
    ".dWWWgWWd.",
    ".dWWWWWWd.",
    ".dWWWWWWd.",
    "..dddddd..",
    "..........",
]


def fish():
    return from_map(FISH_ART, {
        "X": FISH_BODY,
        "L": FISH_LIGHT,
        "d": FISH_DARK,
        "f": FISH_DARK,
        "o": FISH_EYE,
    })


def chest(open_lid=False):
    palette = {
        "W": CHEST_WOOD,
        "L": blend(CHEST_WOOD, CHEST_GLOW, 0.25),
        "d": CHEST_DARK,
        "G": CHEST_GOLD,
        "g": CHEST_GLOW,
    }
    return from_map(CHEST_OPEN_ART if open_lid else CHEST_ART, palette)


ITEM_OUT = os.path.join(os.path.dirname(__file__),
                        "src/main/resources/assets/minedew-fishing/textures/item")

# Fillet palettes: raw cod is pale off-white, raw salmon pink-orange, and the
# cooked pair are browner and darker with a seared crust.
FILLET_PALETTES = {
    "cod_fillet": {
        "flesh": (0xE8, 0xE0, 0xCC, 0xFF),
        "light": (0xF6, 0xF1, 0xE4, 0xFF),
        "line": (0xC6, 0xBA, 0xA0, 0xFF),
        "edge": (0x9C, 0x90, 0x78, 0xFF),
        "skin": (0x7E, 0x8A, 0x8E, 0xFF),
    },
    "salmon_fillet": {
        "flesh": (0xE8, 0x8A, 0x52, 0xFF),
        "light": (0xF7, 0xAE, 0x7C, 0xFF),
        "line": (0xF3, 0xD2, 0xB4, 0xFF),
        "edge": (0xB5, 0x5F, 0x30, 0xFF),
        "skin": (0x7A, 0x84, 0x8C, 0xFF),
    },
    "cooked_cod_fillet": {
        "flesh": (0xC9, 0xA5, 0x6E, 0xFF),
        "light": (0xE0, 0xC4, 0x92, 0xFF),
        "line": (0xA3, 0x7C, 0x4A, 0xFF),
        "edge": (0x74, 0x53, 0x2C, 0xFF),
        "skin": (0x5E, 0x46, 0x28, 0xFF),
    },
    "cooked_salmon_fillet": {
        "flesh": (0xC4, 0x6A, 0x3A, 0xFF),
        "light": (0xDC, 0x8B, 0x55, 0xFF),
        "line": (0xE7, 0xB8, 0x8E, 0xFF),
        "edge": (0x82, 0x40, 0x1E, 0xFF),
        "skin": (0x55, 0x3A, 0x22, 0xFF),
    },
}

# A thick wedge of meat seen flat on: skin strip down the left, muscle
# striations running across it, thicker at the top and tapering to the tail end.
# Deliberately nothing like vanilla's whole-fish silhouette.
FILLET_ART = [
    "................",
    "................",
    "...eeeee........",
    "..esfffee.......",
    "..esfLffee......",
    ".esflllfffe.....",
    ".esfffLfffee....",
    ".esflllffffee...",
    ".esfffLffffffe..",
    ".esflllfffffee..",
    "..esfffLffffee..",
    "..esflllfffee...",
    "...esfffffee....",
    "....esfffee.....",
    ".....eeeee......",
    "................",
]


def fillet(name):
    palette = FILLET_PALETTES[name]
    return from_map(FILLET_ART, {
        "f": palette["flesh"],
        "L": palette["line"],
        "l": palette["light"],
        "e": palette["edge"],
        "s": palette["skin"],
    })


def main():
    write_png(os.path.join(OUT, "track.png"), track())
    write_png(os.path.join(OUT, "gauge.png"), gauge())
    write_png(os.path.join(OUT, "gauge_fill.png"), gauge_fill())
    write_png(os.path.join(OUT, "fish.png"), fish())
    write_png(os.path.join(OUT, "chest.png"), chest())
    write_png(os.path.join(OUT, "chest_open.png"), chest(open_lid=True))

    for name in FILLET_PALETTES:
        write_png(os.path.join(ITEM_OUT, name + ".png"), fillet(name))


if __name__ == "__main__":
    main()
