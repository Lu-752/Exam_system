-- V9：组织机构落地（与人员管理打通）
-- sys_org 由 V1 建表（id / parent_id / name / sort_no / created_at），这里补齐业务字段
ALTER TABLE sys_org ADD COLUMN IF NOT EXISTS code       VARCHAR(64);
ALTER TABLE sys_org ADD COLUMN IF NOT EXISTS leader     VARCHAR(64);
ALTER TABLE sys_org ADD COLUMN IF NOT EXISTS phone      VARCHAR(32);
ALTER TABLE sys_org ADD COLUMN IF NOT EXISTS status     VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE sys_org ADD COLUMN IF NOT EXISTS remark     VARCHAR(255);
ALTER TABLE sys_org ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- 学员归属组织（保留 org_name 做冗余展示，org_id 作为唯一事实来源）
ALTER TABLE sys_student ADD COLUMN IF NOT EXISTS org_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_sys_org_parent ON sys_org (parent_id);
CREATE INDEX IF NOT EXISTS idx_sys_student_org_id ON sys_student (org_id);
