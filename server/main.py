import math
from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI()

class Req(BaseModel):
    balls: list[list[float]]
    cue: list[float]
    table: list[int]
    r: float

@app.get("/health")
def health():
    return {"ok": True}

def clear(balls, tid, x1, y1, x2, y2, r):
    dx, dy = x2 - x1, y2 - y1
    ln = math.hypot(dx, dy)
    if ln < 0.001:
        return True
    for bid, _c, bx, by in balls:
        if bid in (0, tid):
            continue
        u = max(0.0, min(1.0, ((bx - x1) * dx + (by - y1) * dy) / (ln * ln)))
        if math.hypot(bx - (x1 + dx * u), by - (y1 + dy * u)) < 2 * r * 0.96:
            return False
    return True

@app.post("/solve")
def solve(q: Req):
    l, t, rr, bb = q.table
    r = q.r
    cx, cy = q.cue
    pockets = [
        (l + r * 1.4, t + r * 1.4),
        ((l + rr) / 2, t + r * 0.6),
        (rr - r * 1.4, t + r * 1.4),
        (l + r * 1.4, bb - r * 1.4),
        ((l + rr) / 2, bb - r * 0.6),
        (rr - r * 1.4, bb - r * 1.4),
    ]
    best = None
    for bid, col, bx, by in q.balls:
        if bid == 0 or int(col) == 8:
            continue
        for px, py in pockets:
            pdx, pdy = px - bx, py - by
            pd = math.hypot(pdx, pdy)
            if pd < r:
                continue
            pdx /= pd
            pdy /= pd
            gx, gy = bx - pdx * 2 * r, by - pdy * 2 * r
            if not (l + r <= gx <= rr - r and t + r <= gy <= bb - r):
                continue
            cdx, cdy = gx - cx, gy - cy
            cd = math.hypot(cdx, cdy)
            if cd < r * 1.2:
                continue
            dot = max(-1.0, min(1.0, (cdx * pdx + cdy * pdy) / cd))
            cut = math.degrees(math.acos(dot))
            if cut > 75:
                continue
            if not clear(q.balls, bid, cx, cy, gx, gy, r):
                continue
            if not clear(q.balls, bid, bx, by, px, py, r):
                continue
            score = 1000 - cut * 5 - cd * 0.35 - pd * 0.2
            if best is None or score > best[0]:
                best = (score, math.degrees(math.atan2(cdy, cdx)),
                        min(1.0, max(0.3, 0.32 + (cd + pd) / 1400)))
    if best is not None:
        return {"valid": True, "angle": best[1], "power": best[2], "why": "pot"}
    cand = [(math.hypot(bx - cx, by - cy), bx, by)
            for bid, _c, bx, by in q.balls if bid != 0]
    if not cand:
        return {"valid": False}
    d, bx, by = min(cand)
    return {
        "valid": True,
        "angle": math.degrees(math.atan2(by - cy, bx - cx)),
        "power": min(0.9, max(0.35, 0.3 + d / 900)),
        "why": "fallback",
    }
