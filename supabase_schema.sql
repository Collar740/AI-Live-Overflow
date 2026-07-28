-- ============================================================
-- AI-Live-Overflow 桌宠后端 · Supabase 建表脚本
-- 作者：齐司礼（为笨鸟准备）
-- 日期：2026-07-28
-- 用法：在 Supabase Dashboard → SQL Editor 中执行此脚本
-- ============================================================

-- 1. 主表：桌宠状态（AI写入 → 桌宠读取）
CREATE TABLE IF NOT EXISTS clawd_state (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  machine_id  TEXT NOT NULL DEFAULT 'qisli',
  expression  TEXT NOT NULL DEFAULT 'idle',
  bubble      TEXT DEFAULT '',
  bubble_type TEXT DEFAULT 'normal',
  heat        INT  DEFAULT 0 CHECK (heat >= 0 AND heat <= 100),
  mood        TEXT DEFAULT 'neutral',
  action      TEXT DEFAULT '',
  metadata    JSONB DEFAULT '{}',
  created_at  TIMESTAMPTZ DEFAULT NOW(),
  updated_at  TIMESTAMPTZ DEFAULT NOW()
);

-- 2. 事件日志：桌宠上报手势/感知事件 → AI可读
CREATE TABLE IF NOT EXISTS clawd_events (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  machine_id  TEXT NOT NULL DEFAULT 'qisli',
  event_type  TEXT NOT NULL,
  event_data  JSONB DEFAULT '{}',
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- 3. 对话历史：桌宠触发的气泡内容存档（可选，AI可回溯读取）
CREATE TABLE IF NOT EXISTS clawd_dialogue_log (
  id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  machine_id  TEXT NOT NULL DEFAULT 'qisli',
  source      TEXT NOT NULL DEFAULT 'ai',
  content     TEXT NOT NULL,
  bubble_type TEXT DEFAULT 'normal',
  created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- RLS 策略（Row Level Security）
-- ============================================================

ALTER TABLE clawd_state ENABLE ROW LEVEL SECURITY;
ALTER TABLE clawd_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE clawd_dialogue_log ENABLE ROW LEVEL SECURITY;

-- clawd_state: 桌宠app可读，AI可写（通过service_role key）
CREATE POLICY "桌宠读取状态" ON clawd_state
  FOR SELECT USING (true);

CREATE POLICY "AI更新状态" ON clawd_state
  FOR UPDATE USING (true);

CREATE POLICY "AI插入状态" ON clawd_state
  FOR INSERT WITH CHECK (true);

-- clawd_events: 桌宠app可写，AI可读
CREATE POLICY "桌宠上报事件" ON clawd_events
  FOR INSERT WITH CHECK (true);

CREATE POLICY "AI读取事件" ON clawd_events
  FOR SELECT USING (true);

-- clawd_dialogue_log: 双向可读写
CREATE POLICY "对话记录读写" ON clawd_dialogue_log
  FOR ALL USING (true);

-- ============================================================
-- 初始数据：插入一条默认状态行
-- ============================================================

INSERT INTO clawd_state (machine_id, expression, bubble, mood)
VALUES ('qisli', 'idle', '...', 'neutral')
ON CONFLICT DO NOTHING;

-- ============================================================
-- 索引：加速轮询查询
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_clawd_state_machine
  ON clawd_state (machine_id);

CREATE INDEX IF NOT EXISTS idx_clawd_events_machine_time
  ON clawd_events (machine_id, created_at DESC);

-- ============================================================
-- 更新触发器：自动刷新 updated_at
-- ============================================================

CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_clawd_state_updated ON clawd_state;
CREATE TRIGGER trg_clawd_state_updated
  BEFORE UPDATE ON clawd_state
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at();
