-- V8：题冒题（复合题/材料题）
-- 子题仍是 question 表中的普通题目，通过 parent_id 挂在题干下；
-- 题冒题本身只存材料与总分（= 子题分值合计），不参与直接判分。

ALTER TABLE question ADD COLUMN IF NOT EXISTS parent_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_question_parent ON question (parent_id);
