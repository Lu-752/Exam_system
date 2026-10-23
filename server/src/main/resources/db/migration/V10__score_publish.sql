-- V10：成绩发布闸门（阅卷完成后由管理员显式发布，学员才可查分）
ALTER TABLE exam_plan ADD COLUMN IF NOT EXISTS score_published BOOLEAN NOT NULL DEFAULT TRUE;
