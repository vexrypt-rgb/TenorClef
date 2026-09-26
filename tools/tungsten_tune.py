#!/usr/bin/env python3
"""Validation-gated tuner for Tungsten search knobs (SkillOpt-style loop, but over numbers).

Each round proposes one bounded edit to one knob, scores it with PathBench on the
train goals, and keeps it only if it also strictly beats the current best on the
held-out goals. Rejected edits are remembered so they are not retried.

  python3 tools/tungsten_tune.py --rounds 10
State lives in tools/tune_state.json (resumable); best knobs in tools/tune_best.json.
"""
import argparse, glob, json, os, random, subprocess, time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RUN = os.path.join(ROOT, "versions/1.16.1/run")
STATE = os.path.join(ROOT, "tools/tune_state.json")
BEST = os.path.join(ROOT, "tools/tune_best.json")

# name: (default, lo, hi)
KNOBS = {
    "xz": (1.3, 0.8, 2.5),
    "dist": (2.8, 1.0, 6.0),
    "bn": (40.0, 5.0, 120.0),
    "dedupe": (0.294, 0.1, 0.6),
    "drop": (1.5, 1.0, 3.5),
}
TRAIN = "0,2,4,6,9,11,13,15"   # half short (32), half long (96)
VAL = "1,3,5,7,8,10,12,14"
LIMIT = 1800


def score(csv_path):
    """Mean per-trial progress in [0,1.5]: fraction of distance closed, +0.5*speed for a GOAL."""
    rows = [l.strip().split(",") for l in open(csv_path)][1:]
    rows = [r for r in rows if len(r) >= 10]
    if not rows:
        return 0.0, 0
    total, goals = 0.0, 0
    for r in rows:
        dist, result, ticks, end = float(r[4]), r[6], int(r[7]), float(r[8])
        start = max(1.0, float(r[2]) ** 2 + float(r[3]) ** 2) ** 0.5
        if result == "GOAL":
            goals += 1
            total += 1.0 + 0.5 * max(0.0, 1 - ticks / LIMIT)
        else:
            total += max(0.0, min(1.0, (start - end) / start))
    return total / len(rows), goals


def run_bench(knobs, goals, timeout_s):
    subprocess.run(["pkill", "-9", "java"], stderr=subprocess.DEVNULL)
    time.sleep(3)
    before = set(glob.glob(os.path.join(RUN, "pathbench/pathbench_travel_tungsten_*.csv")))
    props = " ".join(f"-Dtungsten.tune.{k}={v:.4f}" for k, v in knobs.items())
    env = dict(os.environ)
    env["JAVA_TOOL_OPTIONS"] = (env.get("JAVA_TOOL_OPTIONS", "") + " -Dtenorclef.seed=12345 -Dtenorclef.pathbench.exit=true"
                                f" -Dtenorclef.pathbench.goals={goals} {props}")
    log = open("/tmp/tungsten_tune_game.log", "w")
    p = subprocess.Popen(["xvfb-run", "-a", "-s", "-screen 0 640x360x24", "./gradlew", "--offline", ":1.16.1:runClient"],
                         cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
    try:
        p.wait(timeout=timeout_s)
    except subprocess.TimeoutExpired:
        pass
    subprocess.run(["pkill", "-9", "java"], stderr=subprocess.DEVNULL)
    new = sorted(set(glob.glob(os.path.join(RUN, "pathbench/pathbench_travel_tungsten_*.csv"))) - before, key=os.path.getmtime)
    return score(new[-1]) if new else (0.0, 0)


def propose(best, rejected, lr, rng):
    for _ in range(50):
        k = rng.choice(list(KNOBS))
        _, lo, hi = KNOBS[k]
        step = (hi - lo) * lr * rng.choice([-1, 1]) * rng.uniform(0.5, 1.0)
        cand = dict(best)
        cand[k] = round(min(hi, max(lo, best[k] + step)), 3)
        key = json.dumps(cand, sort_keys=True)
        if cand[k] != best[k] and key not in rejected:
            return k, cand, key
    return None, None, None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--rounds", type=int, default=10)
    ap.add_argument("--lr", type=float, default=0.25, help="edit size as a fraction of each knob's range")
    ap.add_argument("--decay", type=float, default=0.85)
    ap.add_argument("--timeout", type=int, default=1500, help="seconds per bench run")
    a = ap.parse_args()
    rng = random.Random()
    if os.path.exists(STATE):
        st = json.load(open(STATE))
    else:
        base = {k: v[0] for k, v in KNOBS.items()}
        print("baseline...", flush=True)
        tr, tg = run_bench(base, TRAIN, a.timeout)
        va, vg = run_bench(base, VAL, a.timeout)
        st = {"best": base, "train": tr, "val": va, "lr": a.lr, "rejected": [], "history": [
            {"knobs": base, "train": tr, "val": va, "trainGoals": tg, "valGoals": vg, "accepted": True}]}
        json.dump(st, open(STATE, "w"), indent=1)
        print(f"baseline train={tr:.3f} ({tg}) val={va:.3f} ({vg})", flush=True)
    for i in range(a.rounds):
        k, cand, key = propose(st["best"], set(st["rejected"]), st["lr"], rng)
        if cand is None:
            print("no untried edits left at this lr"); break
        tr, tg = run_bench(cand, TRAIN, a.timeout)
        va, vg, ok = None, None, False
        if tr > st["train"]:
            va, vg = run_bench(cand, VAL, a.timeout)
            ok = va > st["val"]
        h = {"knobs": cand, "edit": k, "train": tr, "val": va, "trainGoals": tg, "valGoals": vg, "accepted": ok}
        st["history"].append(h)
        if ok:
            st.update(best=cand, train=tr, val=va)
            json.dump({"knobs": cand, "train": tr, "val": va}, open(BEST, "w"), indent=1)
        else:
            st["rejected"].append(key)
            st["lr"] = max(0.03, st["lr"] * a.decay)
        json.dump(st, open(STATE, "w"), indent=1)
        print(f"round {i}: {k}={cand[k]} train={tr:.3f}({tg}) val={va} -> {'ACCEPT' if ok else 'reject'}", flush=True)
    print("best", st["best"], "train", st["train"], "val", st["val"])


if __name__ == "__main__":
    main()
