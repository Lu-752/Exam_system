-- V13：题库关联培训大纲（大纲 → 题库 → 试题 → 试卷 → 考试 追溯链）
ALTER TABLE question_bank ADD COLUMN IF NOT EXISTS outline_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_bank_outline ON question_bank (outline_id);
