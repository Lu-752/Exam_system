# -*- coding: utf-8 -*-
"""M16 烟测：大纲 ↔ 题库联动（题库关联大纲 / 大纲题库数 / 删除保护）"""
import json
import urllib.request
import urllib.error
from urllib.parse import quote

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


def fail(res, label, expect):
    if res["code"] == 0:
        raise SystemExit(f"[FAIL] {label}: 期望失败但成功了 -> {res}")
    print(f"  [{label}] 已按预期拦截：{res['message']}")
    if expect and expect not in (res.get("message") or ""):
        raise SystemExit(f"[FAIL] {label}: 提示语不含「{expect}」-> {res['message']}")


admin = ok(call("POST", "/auth/login", {"username": "admin", "password": "123456"}), "login admin")["token"]

print("== 0. 清理残留 ==")
for o in call("GET", "/admin/outlines?keyword=" + quote("烟测联动"), token=admin).get("data") or []:
    for b in call("GET", "/admin/question-banks", token=admin).get("data") or []:
        if b.get("outlineId") == o["id"]:
            call("DELETE", f"/admin/question-banks/{b['id']}", token=admin)
    call("DELETE", f"/admin/outlines/{o['id']}", token=admin)
for b in call("GET", "/admin/question-banks", token=admin).get("data") or []:
    if b["name"] == "烟测联动题库":
        call("DELETE", f"/admin/question-banks/{b['id']}", token=admin)
print("  已清理")

print("== 1. 建大纲 + 题库关联大纲 ==")
ol = ok(call("POST", "/admin/outlines", {
    "name": "烟测联动大纲", "category": "消防", "version": "V1.0", "status": "ACTIVE", "sortNo": 90
}, token=admin), "create outline")
bank = ok(call("POST", "/admin/question-banks", {
    "name": "烟测联动题库", "level": "初级", "outlineId": ol["id"]
}, token=admin), "create bank with outline")
assert bank.get("outlineId") == ol["id"], bank
print(f"  题库 {bank['name']} → 大纲 {bank.get('outlineName')}")

banks = ok(call("GET", "/admin/question-banks", token=admin), "banks list")
hit = next(b for b in banks if b["id"] == bank["id"])
assert hit.get("outlineName") == "烟测联动大纲", hit

print("== 2. 大纲列表带题库数 ==")
rows = ok(call("GET", "/admin/outlines", token=admin), "outlines")
row = next(r for r in rows if r["id"] == ol["id"])
assert row["bankCount"] == 1, row
print(f"  大纲 {row['name']} 关联题库 {row['bankCount']} 个")

print("== 3. 换绑 / 解绑 ==")
ol2 = ok(call("POST", "/admin/outlines", {
    "name": "烟测联动大纲B", "status": "ACTIVE", "sortNo": 91
}, token=admin), "create outline B")
ok(call("PUT", f"/admin/question-banks/{bank['id']}", {
    "name": "烟测联动题库", "level": "初级", "outlineId": ol2["id"]
}, token=admin), "rebind to B")
rows = ok(call("GET", "/admin/outlines", token=admin), "outlines after rebind")
assert next(r for r in rows if r["id"] == ol["id"])["bankCount"] == 0
assert next(r for r in rows if r["id"] == ol2["id"])["bankCount"] == 1
ok(call("PUT", f"/admin/question-banks/{bank['id']}", {
    "name": "烟测联动题库", "level": "初级", "outlineId": None
}, token=admin), "unbind")
rows = ok(call("GET", "/admin/outlines", token=admin), "outlines after unbind")
assert next(r for r in rows if r["id"] == ol2["id"])["bankCount"] == 0

print("== 4. 非法大纲 / 删除保护 ==")
fail(call("POST", "/admin/question-banks", {"name": "坏题库", "outlineId": 999999}, token=admin),
     "关联不存在的大纲", "不存在")
ok(call("PUT", f"/admin/question-banks/{bank['id']}", {
    "name": "烟测联动题库", "level": "初级", "outlineId": ol["id"]
}, token=admin), "rebind to A")
fail(call("DELETE", f"/admin/outlines/{ol['id']}", token=admin), "有关联题库禁删大纲", "关联")

print("== 5. 清理 ==")
ok(call("DELETE", f"/admin/question-banks/{bank['id']}", token=admin), "del bank")
ok(call("DELETE", f"/admin/outlines/{ol['id']}", token=admin), "del outline A")
ok(call("DELETE", f"/admin/outlines/{ol2['id']}", token=admin), "del outline B")

print("\n[PASS] M16 大纲↔题库联动全链路通过")
