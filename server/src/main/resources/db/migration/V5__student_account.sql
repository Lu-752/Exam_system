-- V5：学员账号绑定
ALTER TABLE sys_student ADD COLUMN IF NOT EXISTS user_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_sys_student_user ON sys_student (user_id);
