import math, random
GAIN = 0.25
from dataclasses import dataclass

# ---- tuning (mirrors MinigameTuning) ----
T = dict(
    CLICK_IMPULSE=0.0210, BOBBER_GRAVITY=0.0021, BOBBER_TERMINAL=0.028,
    BOBBER_DAMPING=0.92, CLICK_FALL_ARREST=0.018, BOBBER_BOUNCE=0.25,
    BOBBER_HOLD=40,
    BOBBER_SIZE=[0.20, 0.185, 0.17, 0.165],
    FISH_BASE_MAX_SPEED=0.018, FISH_DAMPING=0.94,
    FISH_MIN_POS=0.10, FISH_MAX_POS=0.90, FISH_MIN_JUMP=0.30,
    FISH_ERRATIC=[0.70, 0.85, 0.95, 1.05],
    PROGRESS_START=0.15,
    CATCH_TICKS=[60, 72, 84, 88], JUNK_CATCH_TICKS=55,
    DRAIN=[0.0154, 0.0128, 0.0114, 0.0109],
    DRAIN_GRACE=3, OPENING_FLOOR=35, FIGHT_TIMEOUT=900,
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

SPECIES = {  # name: (baseSpeed, baseAggr, primary, accent, accentPeriod, accentDur, junk)
    "cod":      (1.00,0.90,MODERATE_DART,None,0,0,False),
    "salmon":   (1.03,0.95,FAST_DART,SLOW_SINUSOIDAL,120,45,False),
    "tropical": (1.02,1.00,FAST_ERRATIC,None,0,0,False),
    "puffer":   (0.98,0.90,SLOW_FLOATER,None,0,0,False),
}
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
            self.active = want
            self.retargetIn = self._rollDelay()

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
        self.vel = max(-self.maxSpeed, min(self.maxSpeed, self.vel))
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
        self.held = T["BOBBER_HOLD"]
        self.progress = T["PROGRESS_START"]
        self.gain = 1.0 / (T["JUNK_CATCH_TICKS"] if junk else T["CATCH_TICKS"][diff-1])
        self.drain = T["DRAIN"][diff-1]
        self.grace = 0
        self.ticks = 0
        self.inside = self.covers(self.fish.pos)

    def covers(self, p): return self.pos <= p <= self.pos + self.size

    def step(self, click):
        if self.held > 0 and not click:
            self.held -= 1
            self.fish.step()
            self.inside = self.covers(self.fish.pos)
            return
        self.held = 0
        self.ticks += 1
        self.fish.step()
        # bobber
        if click:
            self.vel = max(self.vel, -T["CLICK_FALL_ARREST"]) + T["CLICK_IMPULSE"]
        self.vel -= T["BOBBER_GRAVITY"]
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
        if park:
            want = 0.5 - f.size / 2
            err = want - f.pos
            vstar = max(-D / (1 - D) * G, min(T["BOBBER_TERMINAL"], K * err))
            r = 20.0 * (vstar * (1 - D) / D + G) / I
            credit += max(0.0, min(prof.cps if prof else 20.0, r)) / 20.0
            if credit >= 1.0:
                credit -= 1.0; click = True
        elif t >= prof.orient:
            i = max(0, len(hist) - 1 - prof.lag)
            seen = hist[i]
            fishvel = seen - hist[i - 1] if i >= 1 else 0.0
            want = seen - f.size / 2
            err = want - f.pos
            lo = -D / (1 - D) * G
            hi = min(T["BOBBER_TERMINAL"], D / (1 - D) * (I * prof.cps / 20.0 - G))
            vstar = max(lo, min(hi, K * err + fishvel))
            r = 20.0 * (vstar * (1 - D) / D + G) / I
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
