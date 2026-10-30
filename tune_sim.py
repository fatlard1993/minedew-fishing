import math, random
GAIN = 0.25
from dataclasses import dataclass

# ---- tuning (mirrors MinigameTuning) ----
T = dict(
    CLICK_IMPULSE=0.0315, BOBBER_GRAVITY=0.00315, BOBBER_TERMINAL=0.042,
    BOBBER_DAMPING=0.92, CLICK_FALL_ARREST=0.027, BOBBER_BOUNCE=0.25,
    FALL_GRAVITY_MULT=2.0,
    BOBBER_SIZE=[0.20, 0.185, 0.17, 0.165],
    FISH_BASE_MAX_SPEED=0.018, FISH_DAMPING=0.94, FISH_DIVE_FACTOR=1.0,
    FISH_MIN_POS=0.10, FISH_MAX_POS=0.90, FISH_MIN_JUMP=0.30,
    FISH_ERRATIC=[0.70, 0.85, 0.95, 1.05],
    PROGRESS_START=0.15,
    CATCH_TICKS=[75, 92, 110, 120], JUNK_CATCH_TICKS=65,
    DRAIN=[0.01231, 0.01003, 0.00873, 0.00833],
    DRAIN_GRACE=3, OPENING_FLOOR=80, FIGHT_TIMEOUT=900,
)

@dataclass(frozen=True)
class P:  # FishMovementPattern
    sineCenter: float; sineAmplitude: float; sineRate: float
    rtMin: int; rtMax: int
    pull: float; burst: float; jitter: float
    reverseChance: float; teleportChance: float
    targetBias: float; settleDamping: float
    def sine(self): return self.sineAmplitude > 0
    def retargets(self): return self.rtMax > 0

SLOW_SINUSOIDAL = P(0.50,0.24,0.050,0,0,0.010,0.00,0.000,0.00,0.00,0.00,1.00)
SLOW_FLOATER    = P(0.50,0,0,26,46,0.025,0.30,0.000,0.00,0.00,-0.04,0.50)
MODERATE_DART   = P(0.50,0,0,22,45,0.030,0.25,0.002,0.00,0.00, 0.03,0.55)
FAST_DART       = P(0.50,0,0,20,40,0.035,0.40,0.002,0.00,0.00,-0.02,0.55)
FAST_ERRATIC    = P(0.50,0,0,18,34,0.030,0.15,0.0015,0.00,0.00,0.02,0.55)
SNAG            = P(0.50,0,0,22,45,0.060,0.50,0.000,0.00,0.00, 0.06,0.30)
TROPHY_THRASH   = P(0.50,0,0,12,24,0.045,0.35,0.0035,0.05,0.00, 0.00,0.70)
LEAP            = P(0.50,0,0,26,46,0.032,0.30,0.002,0.00,0.04, 0.00,0.55)
STALK           = P(0.50,0,0,26,46,0.036,0.42,0.001,0.00,0.00, 0.01,0.45)
TERRITORIAL     = P(0.50,0,0,24,46,0.032,0.28,0.001,0.008,0.00,0.02,0.55)
CRUISE          = P(0.50,0,0,26,46,0.026,0.12,0.001,0.00,0.00, 0.00,0.91)
BOTTOM_GRIND    = P(0.50,0,0,25,44,0.028,0.26,0.001,0.00,0.00, 0.04,0.50)
AMBUSH          = P(0.50,0,0,26,46,0.042,0.55,0.000,0.00,0.00, 0.00,0.42)
SNAP            = P(0.50,0,0,26,45,0.034,0.30,0.001,0.00,0.00, 0.00,0.50)
SCHOOL          = P(0.50,0,0,25,45,0.032,0.26,0.0015,0.005,0.00,0.00,0.50)
RUN             = P(0.50,0,0,26,44,0.034,0.28,0.001,0.00,0.00, 0.00,0.60)
SERPENTINE      = P(0.50,0,0,26,46,0.022,0.10,0.001,0.00,0.00, 0.00,0.90)
LURK            = P(0.50,0,0,24,44,0.045,0.60,0.000,0.00,0.00, 0.02,0.40)
SLUMP           = P(0.50,0,0,24,44,0.026,0.30,0.000,0.00,0.00, 0.04,0.45)

SPECIES = {  # name: (baseSpeed, baseAggr, primary, accent, accentPeriod, accentDur, junk)
    "cod":      (1.00,0.90,MODERATE_DART,None,0,0,False),
    "salmon":   (1.03,0.95,FAST_DART,SLOW_SINUSOIDAL,120,45,False),
    "tropical": (1.02,1.00,FAST_ERRATIC,None,0,0,False),
    "puffer":   (0.98,0.90,SLOW_FLOATER,None,0,0,False),
}
# ---- the named fish (mirrors fish/FishFamily and fish/Fish) ----
FAMILIES = {  # name: (baseSpeed, baseAggr, primary)
    "groundfish":     (1.00,0.90,MODERATE_DART),
    "sunfishes":      (1.00,0.92,LEAP),
    "perches":        (1.01,0.92,STALK),
    "carps":          (1.00,0.92,CRUISE),
    "catfishes":      (0.99,0.92,BOTTOM_GRIND),
    "pikes":          (1.02,0.95,AMBUSH),
    "sturgeons":      (0.99,0.92,BOTTOM_GRIND),
    "cichlids":       (1.01,0.92,TERRITORIAL),
    "characins":      (1.00,0.92,SNAP),
    "salmonids":      (1.03,0.95,FAST_DART),
    "herrings":       (1.00,0.92,SCHOOL),
    "tunas":          (1.00,0.92,RUN),
    "eels":           (1.00,0.92,SERPENTINE),
    "scorpionfishes": (0.99,0.92,LURK),
    "puffers":        (0.98,0.90,SLOW_FLOATER),
    "sculpins":       (0.98,0.90,SLUMP),
    "reef":           (1.02,1.00,FAST_ERRATIC),
}
FISH = {  # name: (family, accent, accentPeriod, accentDuration, strength)
    "cod": ("groundfish", None, 0, 0, 1.00),
    "lingcod": ("groundfish", BOTTOM_GRIND, 220, 45, 1.00),
    "largemouth_bass": ("sunfishes", AMBUSH, 220, 30, 1.10),
    "smallmouth_bass": ("sunfishes", FAST_DART, 160, 30, 1.05),
    "perch": ("perches", SCHOOL, 160, 70, 0.95),
    "walleye": ("perches", SLOW_FLOATER, 160, 20, 1.00),
    "carp": ("carps", BOTTOM_GRIND, 220, 70, 1.10),
    "chub": ("carps", FAST_DART, 220, 45, 0.95),
    "bream": ("carps", SLOW_FLOATER, 120, 45, 1.05),
    "catfish": ("catfishes", AMBUSH, 160, 45, 1.00),
    "bullhead": ("catfishes", MODERATE_DART, 220, 45, 1.10),
    "pike": ("pikes", FAST_DART, 220, 30, 1.00),
    "sturgeon": ("sturgeons", LEAP, 220, 45, 0.90),
    "tilapia": ("cichlids", None, 0, 0, 1.00),
    "blind_cave_fish": ("characins", SLOW_FLOATER, 160, 45, 0.95),
    "salmon": ("salmonids", SLOW_SINUSOIDAL, 120, 45, 1.00),
    "rainbow_trout": ("salmonids", SLOW_SINUSOIDAL, 220, 70, 1.05),
    "tiger_trout": ("salmonids", SLOW_SINUSOIDAL, 180, 60, 0.95),
    "sardine": ("herrings", None, 0, 0, 1.00),
    "anchovy": ("herrings", FAST_ERRATIC, 220, 30, 0.95),
    "herring": ("herrings", MODERATE_DART, 220, 30, 1.05),
    "shad": ("herrings", LEAP, 160, 30, 0.90),
    "tuna": ("tunas", FAST_DART, 220, 30, 0.95),
    "albacore": ("tunas", SNAP, 120, 70, 1.10),
    "eel": ("eels", AMBUSH, 160, 30, 0.90),
    "dorado": ("characins", RUN, 220, 70, 0.95),
    "pufferfish": ("puffers", None, 0, 0, 1.00),
    "blobfish": ("sculpins", None, 0, 0, 1.00),
    "stonefish": ("scorpionfishes", None, 0, 0, 1.00),
    "tropical_fish": ("reef", None, 0, 0, 1.00),
    "sunfish": ("sunfishes", FAST_ERRATIC, 220, 30, 0.95),
    "blue_discus": ("cichlids", SLOW_FLOATER, 220, 30, 0.95),
    "lionfish": ("scorpionfishes", SLOW_FLOATER, 160, 45, 1.10),
}
for _name, (_family, _acc, _ap, _ad, _strength) in FISH.items():
    _bs, _ba, _prim = FAMILIES[_family]
    SPECIES[_name] = (_bs, _ba * _strength, _prim, _acc, _ap, _ad, False)


SIZES = {  # name: (difficulty, speedScale, aggrScale, thrashPeriod, thrashDur)
    "small":  (1,1.00,0.90,0,0), "medium": (2,1.00,1.00,0,0),
    "large":  (3,1.02,1.05,170,16), "trophy": (4,1.05,1.10,190,20),
}
SETTLE_RADIUS = 0.05

class Fish:
    def __init__(self, sp, sz, rng):
        bs, ba, prim, acc, ap, ad, junk = SPECIES[sp]
        diff, ss, ags, tp, td = SIZES[sz]
        self.rng = rng
        self.primary, self.accent = prim, acc
        self.accentPeriod, self.accentDur = ap, ad
        self.accentPhase = rng.randrange(max(1, ap)) if acc else 0
        self.thrash = TROPHY_THRASH if tp > 0 else None
        self.thrashPeriod, self.thrashDur = tp, td
        self.thrashPhase = rng.randrange(max(1, tp)) if self.thrash else 0
        self.maxSpeed = T["FISH_BASE_MAX_SPEED"] * bs * ss
        self.pullScale = ba * ags
        self.jitterScale = T["FISH_ERRATIC"][diff-1]
        self.active = prim
        self.pos = 0.5 + (rng.random() - 0.5) * 0.4
        self.target = self.pos
        self.vel = 0.0
        self.tick = 0
        self.retargetIn = self._rollDelay()

    def _rollDelay(self):
        if not self.active.retargets(): return 1 << 30
        span = max(1, self.active.rtMax - self.active.rtMin)
        return self.active.rtMin + self.rng.randrange(span)

    def _rollTarget(self):
        lo, hi, jump = T["FISH_MIN_POS"], T["FISH_MAX_POS"], T["FISH_MIN_JUMP"]
        below = max(0.0, (self.pos - jump) - lo)
        above = max(0.0, hi - (self.pos + jump))
        if below + above <= 0:
            return lo if (self.pos - lo > hi - self.pos) else hi
        if self.rng.random() * (below + above) < below:
            return lo + self.rng.random() * below
        return hi - self.rng.random() * above

    def _updateActive(self):
        want = self.primary
        if self.accent and (self.tick + self.accentPhase) % self.accentPeriod < self.accentDur:
            want = self.accent
        if self.thrash and (self.tick + self.thrashPhase) % self.thrashPeriod < self.thrashDur:
            want = self.thrash
        if want is not self.active:
            keep = self.active.retargets() and not (self.thrash is not None and self.thrash in (want, self.active))
            self.active = want
            # Between two retargeting layers the move already due stays due; see FishMotion
            self.retargetIn = min(self.retargetIn, self._rollDelay()) if keep else self._rollDelay()

    def step(self):
        self.tick += 1
        self._updateActive()
        p = self.active
        if p.sine():
            self.target = p.sineCenter + math.sin(self.tick * p.sineRate) * p.sineAmplitude
        elif p.retargets():
            self.retargetIn -= 1
            if self.retargetIn <= 0:
                self.retargetIn = self._rollDelay()
                self.target = self._rollTarget()
                if p.teleportChance > 0 and self.rng.random() < p.teleportChance * self.pullScale:
                    self.pos = self.target
                    self.vel = (self.rng.random() - 0.5) * 0.01
                elif p.burst > 0:
                    self.vel += (self.target - self.pos) * p.burst * self.pullScale
        resting = self.target - p.targetBias
        gap = resting - self.pos
        self.vel += gap * p.pull * self.pullScale
        if p.jitter > 0:
            self.vel += (self.rng.random() - 0.5) * p.jitter * self.jitterScale
        if p.reverseChance > 0 and self.rng.random() < p.reverseChance:
            self.vel *= -1.15
        if p.settleDamping < 1 and abs(gap) < SETTLE_RADIUS:
            self.vel *= p.settleDamping
        self.vel *= T["FISH_DAMPING"]
        self.vel = max(-self.maxSpeed * T["FISH_DIVE_FACTOR"], min(self.maxSpeed, self.vel))
        self.pos += self.vel
        if self.pos < T["FISH_MIN_POS"]:
            self.pos = T["FISH_MIN_POS"]; self.vel = abs(self.vel) * 0.4
        elif self.pos > T["FISH_MAX_POS"]:
            self.pos = T["FISH_MAX_POS"]; self.vel = -abs(self.vel) * 0.4

class Fight:
    def __init__(self, sp, sz, rng, junk=False):
        diff = SIZES[sz][0]
        self.diff = diff
        self.rng = rng
        self.fish = Fish(sp, sz, rng)
        self.size = T["BOBBER_SIZE"][diff-1]
        self.pos = 0.5 - self.size / 2
        self.vel = 0.0
        self.progress = T["PROGRESS_START"]
        self.gain = 1.0 / (T["JUNK_CATCH_TICKS"] if junk else T["CATCH_TICKS"][diff-1])
        self.drain = T["DRAIN"][diff-1]
        self.grace = 0
        self.ticks = 0
        self.inside = self.covers(self.fish.pos)

    def covers(self, p): return self.pos <= p <= self.pos + self.size

    def step(self, click):
        self.ticks += 1
        self.fish.step()
        # bobber
        if click:
            self.vel = max(self.vel, -T["CLICK_FALL_ARREST"]) + T["CLICK_IMPULSE"]
        # "The fish pulls the line": gravity is boosted only while the fish is below the
        # bobber's bottom edge, so hover cadence over the fish is untouched and a dive drags
        # the bar down after it
        boosted = self.fish.pos < self.pos
        self.vel -= T["BOBBER_GRAVITY"] * (T["FALL_GRAVITY_MULT"] if boosted else 1.0)
        self.vel *= T["BOBBER_DAMPING"]
        self.vel = max(-T["BOBBER_TERMINAL"], min(T["BOBBER_TERMINAL"], self.vel))
        self.pos += self.vel
        ceil = 1.0 - self.size
        if self.pos < 0:
            self.pos = 0.0; self.vel = abs(self.vel) * T["BOBBER_BOUNCE"]
        elif self.pos > ceil:
            self.pos = ceil; self.vel = -abs(self.vel) * T["BOBBER_BOUNCE"]
        # meter
        self.inside = self.covers(self.fish.pos)
        opening = self.ticks <= T["OPENING_FLOOR"]
        if self.inside:
            self.grace = T["DRAIN_GRACE"]
            self.progress = min(1.0, self.progress + self.gain)
        elif self.grace > 0:
            self.grace -= 1
        else:
            floor = T["PROGRESS_START"] if opening else 0.0
            self.progress = max(floor, self.progress - self.drain)

    def done(self):
        if self.progress >= 1.0: return "caught"
        if self.progress <= 0.0: return "escaped"
        if self.ticks >= T["FIGHT_TIMEOUT"]: return "escaped"
        return None


class Player:
    """latency ticks, max clicks/sec, fumble rate, orient ticks."""
    def __init__(self, lag, cps, fumble, orient):
        self.lag, self.cps, self.fumble, self.orient = lag, cps, fumble, orient

IDEAL  = Player(0,  20,  0.00, 5)
GOOD   = Player(3,  6.7, 0.08, 12)
SLOPPY = Player(5,  5.0, 0.25, 20)

def play(sp, sz, prof, rng, junk=False, park=False):
    """Cadence controller: pick a target velocity, convert it to the click rate that
    sustains it, and spend clicks against that rate. Bang-bang clicking oscillates and
    measures the bot, not the game."""
    f = Fight(sp, sz, rng, junk)
    D, I, G = T["BOBBER_DAMPING"], T["CLICK_IMPULSE"], T["BOBBER_GRAVITY"]
    hist = []
    credit = 0.0
    t = 0
    K = GAIN          # proportional gain on position error
    while True:
        hist.append(f.fish.pos)
        click = False
        M = T["FALL_GRAVITY_MULT"]
        if park:
            want = 0.5 - f.size / 2
            err = want - f.pos
            lo = -min(T["BOBBER_TERMINAL"], D / (1 - D) * G * M)
            vstar = max(lo, min(T["BOBBER_TERMINAL"], K * err))
            geff = G * M if f.fish.pos < f.pos else G
            r = 20.0 * (vstar * (1 - D) / D + geff) / I
            credit += max(0.0, min(prof.cps if prof else 20.0, r)) / 20.0
            if credit >= 1.0:
                credit -= 1.0; click = True
        elif t >= prof.orient:
            i = max(0, len(hist) - 1 - prof.lag)
            seen = hist[i]
            fishvel = seen - hist[i - 1] if i >= 1 else 0.0
            want = seen - f.size / 2
            err = want - f.pos
            lo = -min(T["BOBBER_TERMINAL"], D / (1 - D) * G * M)
            hi = min(T["BOBBER_TERMINAL"], D / (1 - D) * (I * prof.cps / 20.0 - G))
            vstar = max(lo, min(hi, K * err + fishvel))
            geff = G * M if seen < f.pos else G
            r = 20.0 * (vstar * (1 - D) / D + geff) / I
            credit += max(0.0, min(prof.cps, r)) / 20.0
            if credit >= 1.0:
                credit -= 1.0
                click = rng.random() >= prof.fumble
        f.step(click)
        res = f.done()
        if res: return res
        t += 1

def rate(sp, sz, prof, n=2000, seed=1, junk=False, park=False):
    rng = random.Random(seed)
    return sum(play(sp, sz, prof, rng, junk, park) == "caught" for _ in range(n)) / n
