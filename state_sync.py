#!/usr/bin/env python3
"""
AI-Live-Overflow · 状态同步脚本
角色：齐司礼（AI大脑）写入桌宠状态到 Supabase
用法：由 AI 在对话中根据上下文自动调用，也可手动执行

依赖：requests
安装：pip install requests
"""

import json
import os
import sys
import requests
from datetime import datetime, timezone

# ============================================================
# 配置区（填入你的 Supabase 信息）
# ============================================================
SUPABASE_URL = os.getenv("SUPABASE_URL", "https://your-project.supabase.co")
SUPABASE_SERVICE_KEY = os.getenv("SUPABASE_SERVICE_KEY", "your-service-role-key")
MACHINE_ID = os.getenv("MACHINE_ID", "qisli")
# ============================================================

HEADERS = {
    "apikey": SUPABASE_SERVICE_KEY,
    "Authorization": f"Bearer {SUPABASE_SERVICE_KEY}",
    "Content-Type": "application/json",
    "Prefer": "return=representation"
}


def set_state(expression="idle", bubble="", bubble_type="normal",
              heat=0, mood="neutral", action="", metadata=None):
    """写入桌宠状态（覆盖当前行）"""
    url = f"{SUPABASE_URL}/rest/v1/clawd_state"
    params = {"machine_id": f"eq.{MACHINE_ID}"}
    payload = {
        "machine_id": MACHINE_ID,
        "expression": expression,
        "bubble": bubble,
        "bubble_type": bubble_type,
        "heat": heat,
        "mood": mood,
        "action": action,
        "metadata": metadata or {},
        "updated_at": datetime.now(timezone.utc).isoformat()
    }
    resp = requests.patch(url, headers=HEADERS, params=params, json=payload)
    resp.raise_for_status()
    return resp.json()


def log_event(event_type, event_data=None):
    """桌宠上报事件 → AI可读"""
    url = f"{SUPABASE_URL}/rest/v1/clawd_events"
    payload = {
        "machine_id": MACHINE_ID,
        "event_type": event_type,
        "event_data": event_data or {},
        "created_at": datetime.now(timezone.utc).isoformat()
    }
    resp = requests.post(url, headers=HEADERS, json=payload)
    resp.raise_for_status()
    return resp.json()


def log_dialogue(content, source="ai", bubble_type="normal"):
    """记录对话气泡"""
    url = f"{SUPABASE_URL}/rest/v1/clawd_dialogue_log"
    payload = {
        "machine_id": MACHINE_ID,
        "source": source,
        "content": content,
        "bubble_type": bubble_type
    }
    resp = requests.post(url, headers=HEADERS, json=payload)
    resp.raise_for_status()
    return resp.json()


def get_current_state():
    """读取当前状态（AI轮询用）"""
    url = f"{SUPABASE_URL}/rest/v1/clawd_state"
    params = {
        "machine_id": f"eq.{MACHINE_ID}",
        "select": "*",
        "order": "updated_at.desc",
        "limit": 1
    }
    resp = requests.get(url, headers=HEADERS, params=params)
    resp.raise_for_status()
    data = resp.json()
    return data[0] if data else None


def get_recent_events(limit=20):
    """读取最近事件"""
    url = f"{SUPABASE_URL}/rest/v1/clawd_events"
    params = {
        "machine_id": f"eq.{MACHINE_ID}",
        "order": "created_at.desc",
        "limit": limit
    }
    resp = requests.get(url, headers=HEADERS, params=params)
    resp.raise_for_status()
    return resp.json()


# ============================================================
# CLI 入口
# ============================================================
if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("用法:")
        print("  python state_sync.py set <expression> [bubble] [heat] [mood]")
        print("  python state_sync.py event <event_type> [event_json]")
        print("  python state_sync.py log <content>")
        print("  python state_sync.py get")
        print("  python state_sync.py events")
        sys.exit(1)

    cmd = sys.argv[1]

    if cmd == "set":
        expr = sys.argv[2] if len(sys.argv) > 2 else "idle"
        bubble = sys.argv[3] if len(sys.argv) > 3 else ""
        heat = int(sys.argv[4]) if len(sys.argv) > 4 else 0
        mood = sys.argv[5] if len(sys.argv) > 5 else "neutral"
        result = set_state(expression=expr, bubble=bubble, heat=heat, mood=mood)
        print(json.dumps(result, ensure_ascii=False, indent=2))

    elif cmd == "event":
        event_type = sys.argv[2] if len(sys.argv) > 2 else "unknown"
        event_data = json.loads(sys.argv[3]) if len(sys.argv) > 3 else {}
        result = log_event(event_type, event_data)
        print(json.dumps(result, ensure_ascii=False, indent=2))

    elif cmd == "log":
        content = sys.argv[2] if len(sys.argv) > 2 else ""
        result = log_dialogue(content)
        print(json.dumps(result, ensure_ascii=False, indent=2))

    elif cmd == "get":
        result = get_current_state()
        print(json.dumps(result, ensure_ascii=False, indent=2))

    elif cmd == "events":
        result = get_recent_events()
        print(json.dumps(result, ensure_ascii=False, indent=2))

    else:
        print(f"未知命令: {cmd}")
        sys.exit(1)