# -*- coding: utf-8 -*-
"""M14 烟测：防作弊切屏检测 —— 学员上报 → 累计 → 监考/交卷记录展示"""
import json
import urllib.request
import urllib.error

BASE = "http://localhost:8080/api/v1"


def call(method, path, body=None, token=None):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "ignore")
        try:
            return json.loads(raw)
        except Exception:
            return {"code": e.code, "message": raw, "data": None}


def ok(res, label):
    if res["code"] != 0:
        raise SystemExit(f"[FAIL] {label}: {res}")
    print(f"  [{label}] ok")
    return res.get("data")


admin = ok(call("POST", "/auth/login", {"username": "admin", "password": "123456"}), "login admin")["token"]
stu = ok(call("POST", "/auth/login", {"username": "student", "password": "123456"}), "login student")["token"]

print("== 1. 准备试卷与考试 ==")
bank = ok(call("GET", "/admin/question-banks", token=admin), "banks")[0]
qs = ok(call("GET", f"/admin/questions?bankId={bank['id']}&type=SC", token=admin), "list SC")
assert qs, "题库缺少单选题"
q = qs[0]
paper = ok(call("POST", "/admin/papers", {
    "name": "切屏烟测卷", "bankId": bank["id"], "passScore": 3,
    "items": [{"questionId": q["id"], "score": 5}]
}, token=admin), "create paper")
plan = ok(call("POST", "/admin/exam-plans", {
    "name": "切屏烟测考试", "paperId": paper["id"],
    "startAt": "2026-01-01 00:00:00", "endAt": "2030-12-31 23:59:59",
    "durationSec": 1800, "passScore": 3, "maxAttempts": 1
}, token=admin), "create plan")

started = ok(call("POST", f"/student/exams/{plan['id']}/start", {}, token=stu), "start exam")
attempt_id = started["attemptId"]
print(f"  attemptId={attempt_id}")

print("== 2. 上报 3 次切屏：计数递增、第 3 次触发预警 ==")
expect = [(1, False), (2, False), (3, True)]
for idx, (want_count, want_warn) in enumerate(expect, start=1):
    ack = ok(call("POST", f"/student/attempts/{attempt_id}/events",
                  {"type": "VISIBILITY", "count": 1}, token=stu), f"event #{idx}")
    print(f"    第 {idx} 次 → switchCount={ack['switchCount']} warned={ack['warned']} at={ack['lastSwitchAt']}")
    assert ack["switchCount"] == want_count, ack
    assert ack["warned"] is want_warn, ack

print("== 3. 单次上报批量 count=2 会累加，但被夹到上限内 ==")
ack = ok(call("POST", f"/student/attempts/{attempt_id}/events",
              {"type": "BLUR", "count": 2}, token=stu), "batch event")
print(f"    switchCount={ack['switchCount']}")
assert ack["switchCount"] == 5, ack
ack = ok(call("POST", f"/student/attempts/{attempt_id}/events",
              {"type": "BLUR", "count": 999}, token=stu), "overflow event")
print(f"    超大 count 夹紧后 switchCount={ack['switchCount']}")
assert ack["switchCount"] == 25, ack

print("== 4. 越权：他人答卷不可上报 ==")
res = call("POST", f"/student/attempts/{attempt_id}/events", {"type": "BLUR"}, token=admin)
print(f"    admin 上报 → code={res['code']} msg={res['message']}")
assert res["code"] == 403, res

print("== 5. 监考页读取到切屏次数并标记异常 ==")
rows = ok(call("GET", f"/admin/proctor/{plan['id']}", token=admin), "proctor rows")
row = next(r for r in rows if r["attemptId"] == attempt_id)
print(f"    {row['studentName']} switchCount={row['switchCount']} suspicious={row['suspicious']} last={row['lastSwitchAt']}")
assert row["switchCount"] == 25 and row["suspicious"] is True, row

print("== 6. 交卷记录同样带切屏次数 ==")
records = ok(call("GET", f"/admin/attempts?planId={plan['id']}", token=admin), "attempts")
rec = next(r for r in records if r["attemptId"] == attempt_id)
print(f"    switchCount={rec['switchCount']}")
assert rec["switchCount"] == 25, rec

print("== 7. 交卷后上报不再累计 ==")
ok(call("POST", f"/student/attempts/{attempt_id}/submit",
        {"answers": [{"questionId": q["id"], "answer": q["answer"]}]}, token=stu), "submit")
ack = ok(call("POST", f"/student/attempts/{attempt_id}/events",
              {"type": "VISIBILITY", "count": 5}, token=stu), "event after submit")
print(f"    switchCount={ack['switchCount']} warned={ack['warned']}")
assert ack["switchCount"] == 25, ack

print("\n[PASS] M14 防作弊切屏检测全链路通过")
