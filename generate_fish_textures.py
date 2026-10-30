#!/usr/bin/env python3
"""Generate the named fish's skins: one texture and one Pandorical entity model each.

Pure-stdlib PNG writer, like generate_textures.py. Deterministic: the only
randomness is a hash of the fish's name and the pixel, so re-running produces
identical bytes.

Every skin is painted onto the UV layout of the vanilla body it borrows (cod,
salmon or pufferfish, all 32x32), worked out from the boxes those models are
built from rather than traced from vanilla's own textures: the pixels are ours,
only the geometry is shared. Each box unwraps the way every Minecraft cube does,

    (u+d, v)      top, w x d        (u+d+w, v)    belly, w x d
    (u, v+d)      side, d x h       (u+d, v+d)    front, w x h
    (u+d+w, v+d)  side, d x h       (u+2d+w, v+d) back, w x h

with the sides running the length of the fish and their rows top to belly.

    src/main/resources/assets/minedew-fishing/textures/entity/fish/<skin>.png
    src/main/resources/assets/minedew-fishing/pandorical/entity_models/fish/<skin>.json

The skin names are the ones Fish.java hands to Look.skin().
"""

import json
import os
import re
import struct
import zlib

ROOT = os.path.join(os.path.dirname(__file__), "src/main/resources/assets/minedew-fishing")
TEXTURES = os.path.join(ROOT, "textures/entity/fish")
MODELS = os.path.join(ROOT, "pandorical/entity_models/fish")
SIZE = 32

# (part, u, v, w, h, d). Bodies come last so they win where vanilla's layouts overlap.
COD = [
    ("fin", 22, 1, 2, 0, 2), ("fin", 22, 4, 2, 0, 2),
    ("fin", 22, 3, 0, 4, 4), ("fin", 20, -6, 0, 1, 6),
    ("nose", 0, 0, 2, 3, 1),
    ("head", 11, 0, 2, 4, 3),
    ("body", 0, 0, 2, 4, 7),
]
SALMON = [
    ("fin", 20, 10, 0, 5, 6), ("fin", 2, 1, 0, 2, 3), ("fin", 0, 2, 0, 2, 4),
    ("fin", -4, 0, 2, 0, 2), ("fin", 0, 0, 2, 0, 2),
    ("head", 22, 0, 2, 4, 3),
    ("body", 0, 0, 3, 5, 8), ("body", 0, 13, 3, 5, 8),
]
PUFFER = [
    # Spines and fins of all three puff states
    ("fin", -3, 0, 3, 0, 3), ("fin", 25, 0, 1, 0, 2),
    ("fin", 24, 0, 2, 0, 2), ("fin", 24, 3, 2, 0, 2),
    ("fin", 19, 17, 5, 1, 0), ("fin", 11, 17, 5, 1, 0), ("fin", 5, 17, 1, 5, 0), ("fin", 9, 17, 1, 5, 0),
    ("fin", 1, 17, 1, 5, 0), ("fin", 18, 20, 5, 1, 0), ("fin", 17, 19, 5, 1, 0),
    ("fin", 15, 17, 8, 1, 0), ("fin", 14, 16, 8, 1, 0), ("fin", 23, 18, 8, 1, 0), ("fin", 5, 17, 1, 8, 0),
    ("fin", 1, 17, 1, 8, 0), ("fin", 15, 20, 8, 1, 0), ("fin", 9, 17, 1, 8, 0),
    ("eye", 24, 6, 1, 1, 1), ("eye", 28, 6, 1, 1, 1),
    # Deflated, half puffed, fully puffed
    ("body", 0, 27, 3, 2, 3), ("body", 12, 22, 5, 5, 5), ("body", 0, 0, 8, 8, 8),
]
BODIES = {"cod": COD, "salmon": SALMON, "pufferfish": PUFFER}


def hexrgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def shade(rgb, amount):
    return tuple(max(0, min(255, round(c + amount))) for c in rgb)


def noise(seed, x, y):
    """A small, fixed wobble per pixel, so a flat colour reads as skin and not as paint."""
    h = zlib.crc32(f"{seed}:{x}:{y}".encode())
    return (h % 13) - 6


def chance(seed, x, y, salt, odds):
    return zlib.crc32(f"{seed}:{salt}:{x}:{y}".encode()) % 1000 < odds * 1000


class Skin:
    """Paints one fish. Colours are hex strings; marks are (kind, colour, ...) tuples."""

    def __init__(self, name, body, back, flank, belly, fin, eye="#14100c", marks=(), transforms=None):
        self.name, self.body = name, body
        self.back, self.flank, self.belly = hexrgb(back), hexrgb(flank), hexrgb(belly)
        self.fin, self.eye = hexrgb(fin), hexrgb(eye)
        self.marks, self.transforms = marks, transforms

    def side(self, i, j, length, height):
        """A pixel on the flank, i along the fish, j down from the top."""
        f = j / max(1, height - 1)
        rgb = self.back if f < 0.3 else self.belly if f > 0.75 else self.flank
        for mark in self.marks:
            kind, colour = mark[0], hexrgb(mark[1]) if mark[1] else None
            if kind == "bars" and i % mark[2] == mark[3] and f < 0.8:
                rgb = colour
            elif kind == "stripe" and j == round(mark[2] * (height - 1)):
                rgb = colour
            elif kind == "band" and mark[2] <= f <= mark[3]:
                rgb = colour
            elif kind == "spots" and f < mark[3] and chance(self.name, i, j, kind + mark[1], mark[2]):
                rgb = colour
            elif kind == "blotch" and chance(self.name, i // 2, j // 2, kind + mark[1], mark[2]):
                rgb = colour
            elif kind == "worms" and f < 0.8 and (i + (j // 2) * 2) % 3 == 0 and (i * 7 + j * 3) % 5 != 0:
                rgb = colour
            elif kind == "scales" and (i + j) % 2 == 0:
                rgb = shade(rgb, mark[2])
            elif kind == "shoulder" and i == mark[2] and 1 <= j <= max(1, height // 2):
                rgb = colour
        return rgb

    def top(self, i, j):
        rgb = self.back
        for mark in self.marks:
            if mark[0] in ("spots", "blotch") and chance(self.name, i, j, "top" + mark[1], mark[2]):
                rgb = hexrgb(mark[1])
        return rgb

    def paint(self):
        pixels = [[(0, 0, 0, 0)] * SIZE for _ in range(SIZE)]

        def put(x, y, rgb, wobble=True):
            if 0 <= x < SIZE and 0 <= y < SIZE:
                if wobble:
                    rgb = shade(rgb, noise(self.name, x, y))
                pixels[y][x] = (*rgb, 255)

        for part, u, v, w, h, d in BODIES[self.body]:
            if part == "fin":
                for x in range(u, u + 2 * (d + w)):
                    for y in range(v, v + d + h):
                        if (y < v + d and u + d <= x < u + d + 2 * w) or (y >= v + d):
                            edge = y == v + d + h - 1 or x == u + 2 * (d + w) - 1
                            put(x, y, shade(self.fin, -18 if edge else 0))
                continue
            if part == "eye":
                for x in range(u, u + 2 * (d + w)):
                    for y in range(v, v + d + h):
                        put(x, y, self.eye, wobble=False)
                continue
            # The two caps
            for i in range(w):
                for j in range(d):
                    put(u + d + i, v + j, self.top(i, j))
                    put(u + d + w + i, v + j, self.belly)
            # The two flanks, then the two ends
            for i in range(d):
                for j in range(h):
                    put(u + i, v + d + j, self.side(i, j, d, h))
                    put(u + d + w + i, v + d + j, self.side(d - 1 - i, j, d, h))
            for i in range(w):
                for j in range(h):
                    put(u + d + i, v + d + j, self.flank if j < h - 1 else self.belly)
                    put(u + 2 * d + w + i, v + d + j, shade(self.flank, -12))
            if part == "head" and h >= 3 and self.eye is not None:
                # One eye a side, high and forward
                put(u + 1, v + d + 1, self.eye, wobble=False)
                put(u + d + w + 1, v + d + 1, self.eye, wobble=False)
            if self.body == "pufferfish" and part == "body" and w >= 5:
                # Eyes on the face of the two puffed bodies
                put(u + d + 1, v + d + 1, self.eye, wobble=False)
                put(u + d + w - 2, v + d + 1, self.eye, wobble=False)
        return pixels


def write_png(path, pixels):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *p) for p in row) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as out:
        out.write(png)


# Stretches, in the vanilla models' own part names. Pandorical scales a part's pivot along with
# the part, so each is worked out about the part's own pivot (the vanilla ones below) and then moved.
PIVOTS = {
    "cod": {"body": (0, 22, 0), "head": (0, 22, 0), "nose": (0, 22, -3), "tail_fin": (0, 22, 7), "top_fin": (0, 20, 0)},
    "salmon": {"body_front": (0, 20, -7.2), "body_back": (0, 20, 0.8), "head": (0, 20, -7.2)},
}


def number(value):
    value = round(value, 2)
    return int(value) if value == int(value) else value


def stretch(vanilla, **parts):
    """parts: name=(scale xyz, move xyz); the scale is about the part's own pivot."""
    out = {}
    for name, (scale, move) in parts.items():
        pivot = PIVOTS[vanilla][name]
        entry = {}
        if scale != (1, 1, 1):
            entry["scale"] = list(scale)
        offset = [number(p * (1 - k) + m) for p, k, m in zip(pivot, scale, move)]
        if any(offset):
            entry["offset"] = offset
        out[name] = entry
    return out


def long_cod(k, head):
    """A cod's body is 7 long from its pivot, with the tail and the dorsal fin pinned behind it."""
    tail = 7 * (k - 1)
    return dict(body=((1, 1, k), (0, 0, 0)), tail_fin=((1, 1, 1), (0, 0, tail)),
                top_fin=((1, 1, 1), (0, 0, tail)), head=((1, 1, head), (0, 0, 0)),
                nose=((1, 1, 1), (0, 0, -3 * (head - 1))))


SKINS = [
    # Whitefish, on the cod
    Skin("lingcod", "cod", "#5b5a3c", "#7c7552", "#c9c2a0", "#4d4a33",
         marks=[("blotch", "#3d3a26", 0.35), ("spots", "#a59a6b", 0.15, 0.7)]),
    Skin("largemouth_bass", "cod", "#3f5a2a", "#7d9a4c", "#e4e2c4", "#5d7340",
         marks=[("stripe", "#283a1a", 0.5)]),
    Skin("smallmouth_bass", "cod", "#6b5530", "#9c7c45", "#e2d4ac", "#7a6034", eye="#b22a1a",
         marks=[("bars", "#4a3a1e", 2, 0)]),
    Skin("perch", "cod", "#5c6a22", "#d6b83c", "#efe6c0", "#e0782c",
         marks=[("bars", "#3b4416", 2, 1)]),
    Skin("walleye", "cod", "#5a5428", "#a89548", "#ece8d4", "#8b7a3a", eye="#e8ecd8",
         marks=[("blotch", "#4a4320", 0.25)]),
    Skin("carp", "cod", "#6a5420", "#b8902c", "#e8d08a", "#a8582c",
         marks=[("scales", "", -16)]),
    Skin("chub", "cod", "#4e5258", "#a8adb2", "#e6e8ea", "#c27a6a",
         marks=[("scales", "", -12)]),
    Skin("bream", "cod", "#4a4228", "#86763e", "#c8b884", "#5a4e30",
         marks=[("scales", "", -8)]),
    Skin("catfish", "cod", "#3e4a58", "#6a7a8a", "#d4d8dc", "#3a444e",
         marks=[("spots", "#252c34", 0.12, 0.7)]),
    Skin("bullhead", "cod", "#2c2418", "#4a3c26", "#c8b05a", "#2a2216"),
    Skin("pike", "cod", "#3a4a22", "#5e7a34", "#e8e4b8", "#6a6a2c",
         marks=[("spots", "#d8d68a", 0.3, 0.75)],
         transforms=stretch("cod", **long_cod(1.4, head=1.3))),
    Skin("sturgeon", "cod", "#4a463e", "#7a756a", "#d8d2c0", "#5a554c",
         marks=[("stripe", "#c8c2ae", 0.0), ("stripe", "#c8c2ae", 0.5)],
         transforms=stretch("cod", **{**long_cod(1.5, head=1.4), "body": ((0.9, 0.9, 1.5), (0, 0, 0))})),
    Skin("tilapia", "cod", "#4a5866", "#8a98a4", "#dce0e2", "#b04a3a",
         marks=[("bars", "#5e6c78", 3, 1)]),
    Skin("blind_cave_fish", "cod", "#e2b8b0", "#f0d2cc", "#faeae6", "#e8c4bc", eye="#f0d2cc"),

    # Oily fish, on the salmon
    Skin("rainbow_trout", "salmon", "#4a5a2c", "#a8b07a", "#f0eee4", "#8a8a5a",
         marks=[("band", "#d8687a", 0.4, 0.6), ("spots", "#22281a", 0.25, 0.45)]),
    Skin("tiger_trout", "salmon", "#5a4a1e", "#c8a446", "#efe2b4", "#b88a3a",
         marks=[("worms", "#3e3214")]),
    Skin("sardine", "salmon", "#2a5a6a", "#b8c8cc", "#f0f4f4", "#7a9aa2",
         marks=[("stripe", "#244a56", 0.3), ("spots", "#1e3a44", 0.12, 0.5)]),
    Skin("anchovy", "salmon", "#3a6a6a", "#a8bcb8", "#e4eceb", "#86a4a0",
         marks=[("stripe", "#d8e6e8", 0.5)]),
    Skin("herring", "salmon", "#28507a", "#c4ccd4", "#f2f4f6", "#8a9cae"),
    Skin("shad", "salmon", "#3a6064", "#c0c8c4", "#eef0ec", "#8aa09c",
         marks=[("shoulder", "#22282a", 1), ("spots", "#3a4446", 0.08, 0.5)]),
    Skin("tuna", "salmon", "#1c2a48", "#7a8aa4", "#e8ecf0", "#e8c43a",
         marks=[("band", "#3a5a8a", 0.25, 0.4)],
         transforms=stretch("salmon", body_front=((1.2, 1.15, 1), (0, 0, 0)), body_back=((1.1, 1.1, 1), (0, 0, 0)))),
    Skin("albacore", "salmon", "#1a2a52", "#5a74a8", "#eef0f4", "#3a4a72",
         marks=[("band", "#6a9ad8", 0.35, 0.5)]),
    Skin("eel", "salmon", "#3e3a1e", "#6a6232", "#c8b866", "#4a4422",
         # The front half is 8 long; at half again it ends 4 further back, and the back half with it
         transforms=stretch("salmon", body_front=((0.7, 0.7, 1.5), (0, 0, 0)),
                            body_back=((0.7, 0.7, 1.5), (0, 0, 4)), head=((0.8, 0.8, 1), (0, 0, 0)))),
    Skin("dorado", "salmon", "#b8781a", "#e8b030", "#f6e2a0", "#d8482a",
         marks=[("spots", "#7a4a10", 0.2, 0.7)]),

    # Pufferfish bodies
    Skin("blobfish", "pufferfish", "#d89a9a", "#e8b2b0", "#f2cccc", "#d08a8a", eye="#3a2a2a"),
    Skin("stonefish", "pufferfish", "#5a4a3a", "#7a6448", "#9a8a6a", "#a0603a", eye="#c8a03a",
         marks=[("blotch", "#3e3226", 0.35), ("spots", "#b87a4a", 0.15, 1.0)]),
]


def main():
    os.makedirs(TEXTURES, exist_ok=True)
    os.makedirs(MODELS, exist_ok=True)
    for skin in SKINS:
        write_png(os.path.join(TEXTURES, skin.name + ".png"), skin.paint())
        model = {"texture": f"minedew-fishing:textures/entity/fish/{skin.name}.png"}
        if skin.transforms:
            model["transforms"] = skin.transforms
        text = json.dumps(model, indent="\t")
        text = re.sub(r"\[\s*([-\d.,\s]+?)\s*\]", lambda m: "[" + ", ".join(m.group(1).split()).replace(",,", ",") + "]", text)
        with open(os.path.join(MODELS, skin.name + ".json"), "w") as out:
            out.write(text + "\n")
    print(f"{len(SKINS)} skins written")


if __name__ == "__main__":
    main()
