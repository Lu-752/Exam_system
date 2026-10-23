-- V11：防作弊 —— 记录考试过程中的切屏 / 失焦次数
ALTER TABLE exam_attempt ADD COLUMN IF NOT EXISTS switch_count INT NOT NULL DEFAULT 0;
ALTER TABLE exam_attempt ADD COLUMN IF NOT EXISTS last_switch_at TIMESTAMP;
