#!/usr/bin/env python3
"""Fast Python port of Engine + GreedyBot for tuning story.json. The Kotlin BalanceSimTest is the authority."""
import json, math, sys

S = json.load(open(__import__("os").path.join(__import__("os").path.dirname(__import__("os").path.abspath(__file__)), "..", "app", "src", "main", "assets", "story.json")))
EPS = 1e-9
CH = {c["number"]: c for c in S["chapters"]}
CHOICE = {c["id"]: c for c in S["choices"]}
UP = {u["id"]: u for u in S["upgrades"]}

class G:
    def __init__(s):
        s.chapter = 1; s.t = 0.0; s.res = dict(S["start"]); s.workers = {}; s.upgrades = set(); s.revealed = set()
        s.cd = {}; s.pending = None; s.progress = 0.0; s.choices = []; s.ending = None; s.taps = 0; s.totals = {}
        s.mods = {}
        s.checks()

    def recompute(s):
        m = {}
        effs = [e for u in s.upgrades for e in UP[u]["effects"]]
        for cid, oid in s.choices:
            effs += next(o for o in CHOICE[cid]["options"] if o["id"] == oid)["effects"]
        for e in effs:
            k = e["kind"] + "|" + e["target"]; m[k] = m.get(k, 1.0) * e["factor"]
        s.mods = m

    def f(s, kind, tgt): return s.mods.get(kind + "|" + tgt, 1.0)

    def gain(s, r, v):
        if v <= 0: return
        s.res[r] = s.res.get(r, 0) + v; s.totals[r] = s.totals.get(r, 0) + v
        if r == CH[s.chapter]["goalResource"]: s.progress += v

    def meets(s, at):
        for r, v in at.items():
            have = s.taps if r == "taps" else s.totals.get(r, 0)
            if have + EPS < v: return False
        return True

    def reveal(s):
        for a in S["actions"]:
            k = "action:" + a["id"]
            if k not in s.revealed and a["chapter"] <= s.chapter and s.meets(a.get("revealAt", {})): s.revealed.add(k)
        for w in S["workers"]:
            k = "worker:" + w["id"]
            if k not in s.revealed and w["chapter"] <= s.chapter and s.meets(w.get("revealAt", {})): s.revealed.add(k)
        for u in S["upgrades"]:
            k = "upgrade:" + u["id"]
            if k not in s.revealed and u["chapter"] <= s.chapter and all(q in s.upgrades for q in u.get("requires", [])) and s.meets(u.get("revealAt", {})): s.revealed.add(k)

    def checks(s):
        if s.ending: return
        s.reveal()
        if s.pending: return
        ch = CH[s.chapter]; goal = ch["goalAmount"]; made = {c for c, _ in s.choices}
        for t in ch["triggers"]:
            if t["choiceId"] in made: continue
            if s.progress + EPS >= t["at"] * goal:
                s.pending = t["choiceId"]; return
            break
        if s.progress + EPS < goal: return
        if any(t["choiceId"] not in made for t in ch["triggers"]): return
        if s.chapter == 6:
            s.ending = "done"
        else:
            s.chapter += 1; s.progress = 0.0; s.reveal()

    def step(s, dt):
        if s.ending: return
        for k in list(s.cd):
            s.cd[k] -= dt
            if s.cd[k] <= 0: del s.cd[k]
        s.t += dt
        if not s.pending:
            for w in S["workers"]:
                n = s.workers.get(w["id"], 0)
                if n <= 0: continue
                key = "worker:" + w["id"]; tp = s.f("throughput", key)
                ins = n * tp * s.f("thrift", key) * dt; fac = 1.0
                for r, rate in w.get("consumes", {}).items():
                    need = rate * ins
                    if need > 0: fac = min(fac, s.res.get(r, 0) / need)
                if fac <= 0: continue
                for r, rate in w.get("consumes", {}).items(): s.res[r] = max(0.0, s.res.get(r, 0) - rate * ins * fac)
                outs = n * tp * s.f("yield", key) * dt * fac
                for r, rate in w.get("produces", {}).items(): s.gain(r, rate * outs)
        s.checks()

    def net(s):
        out = {}
        for w in S["workers"]:
            n = s.workers.get(w["id"], 0)
            if not n: continue
            key = "worker:" + w["id"]; tp = s.f("throughput", key)
            for r, v in w.get("produces", {}).items(): out[r] = out.get(r, 0) + v * n * tp * s.f("yield", key)
            for r, v in w.get("consumes", {}).items(): out[r] = out.get(r, 0) - v * n * tp * s.f("thrift", key)
        return out

    def hire_cost(s, wid):
        w = next(x for x in S["workers"] if x["id"] == wid)
        sc = w["growth"] ** s.workers.get(wid, 0) * s.f("cost", "worker:" + wid)
        return {r: math.ceil(v * sc) for r, v in w["cost"].items()}

    def afford(s, cost): return all(s.res.get(r, 0) + EPS >= v for r, v in cost.items())

    def pay(s, cost):
        for r, v in cost.items(): s.res[r] = max(0.0, s.res.get(r, 0) - v)

def bot_act(g, strategy):
    if g.pending:
        opts = CHOICE[g.pending]["options"]
        g.choices.append((g.pending, opts[min(strategy, len(opts) - 1)]["id"])); g.pending = None; g.recompute(); g.checks()
    for a in S["actions"]:
        k = "action:" + a["id"]
        if k not in g.revealed or g.pending or g.ending: continue
        if a.get("costs", {}).get("pennies", 0) > 0 and all(g.res.get(r, 0) > 20 for r in a["gains"]): continue
        if g.cd.get(a["id"], 0) > 0: continue
        key = "action:" + a["id"]; tp = g.f("throughput", key)
        cost = {r: v * tp for r, v in a.get("costs", {}).items()}
        if not g.afford(cost): continue
        g.pay(cost)
        for r, v in a["gains"].items(): g.gain(r, v * tp * g.f("yield", key))
        g.cd[a["id"]] = a["cooldown"] * g.f("cooldown", key); g.taps += 1; g.checks()
    for _ in range(200):
        if g.pending or g.ending: return
        opts = []
        for u in S["upgrades"]:
            if u["id"] in g.upgrades or "upgrade:" + u["id"] not in g.revealed: continue
            c = {r: math.ceil(v) for r, v in u["cost"].items()}
            opts.append((c.get("pennies", 0), u["chapter"], "u", u["id"], c))
        for w in S["workers"]:
            if "worker:" + w["id"] not in g.revealed: continue
            c = g.hire_cost(w["id"])
            opts.append((c.get("pennies", 0), w["chapter"], "w", w["id"], c))
        rates = g.net()
        short = {r for r, v in rates.items() if v < 0 and g.res.get(r, 0) < -60 * v}
        feeders = {w["id"] for w in S["workers"] if short & set(w.get("produces", {}))}
        buyable = [o for o in opts if g.afford(o[4])]
        if not buyable: return
        _, _, kind, id, c = min(buyable, key=lambda o: (not (o[2] == "w" and o[3] in feeders), o[0]))
        g.pay(c)
        if kind == "u": g.upgrades.add(id); g.recompute()
        else: g.workers[id] = g.workers.get(id, 0) + 1
        g.checks()

SAVE = 1e18

def play(strategy, limit=20 * 3600, trace=False, every=10):
    g = G(); durs = []; start = 0; since = every
    while g.t < limit and not g.ending:
        if since >= every: bot_act(g, strategy); since = 0
        before = g.chapter
        g.step(1.0); since += 1
        if g.chapter != before or g.ending: durs.append(g.t - start); start = g.t
        if trace and int(g.t) % 1200 == 0:
            print(f"t={int(g.t/60)}m p/s={g.net().get('pennies',0):.1f} ch{g.chapter} {g.progress:.0f}/{CH[g.chapter]['goalAmount']} up={len(g.upgrades)} | " +
                  " ".join(f"{k}={v:.0f}" for k, v in g.res.items()) + " | " + " ".join(f"{k}={v}" for k, v in g.workers.items()))
    return durs, g.t

if __name__ == "__main__":
    trace = "-t" in sys.argv
    for st in ([0] if trace else [0, 1, 2]):
        d, t = play(st, trace=trace)
        print(f"strategy {st}: " + ", ".join(f"{x/60:.1f}" for x in d) + f" min; total {t/3600:.2f} h")
