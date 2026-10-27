-- V15：下线「学习中心」域（学员管理 / 成绩统计 / 成绩分析）
-- 背景：产品确认学习中心从后台移除；学员账号数据（sys_user/sys_student）保留，
--      学员登录、报名、考试不受影响。处理方式同 V14（V1~V14 保持不动防校验和漂移）。
-- 注意：「发布成绩」按钮（perm=score:publish）属于「考试计划」发布成绩能力（M7 闸门），
--      与学习中心无关，必须保留：把它从学习中心迁到「考试计划」菜单下，
--      其权限行与角色分配（sys_role_menu）一律不动，避免考试发布成绩被 403。

-- 1) 把「发布成绩」按钮从学习中心迁到「考试计划」下（保留考试发布成绩能力）
UPDATE sys_menu
   SET parent_id = (SELECT id FROM sys_menu WHERE path = '/admin/exam-plans')
 WHERE perm = 'score:publish'
   AND parent_id IN (SELECT id FROM sys_menu WHERE name = '学习中心');

-- 2) 删除学习中心域（DIR + 学员管理/成绩统计/成绩分析）及其权限分配
DELETE FROM sys_role_menu
 WHERE menu_id IN (SELECT id FROM sys_menu
                    WHERE path IN ('/admin/students', '/admin/scores', '/admin/analysis')
                       OR name IN ('学习中心', '学员管理', '成绩统计', '成绩分析'));

DELETE FROM sys_menu
 WHERE path IN ('/admin/students', '/admin/scores', '/admin/analysis')
    OR name IN ('学习中心', '学员管理', '成绩统计', '成绩分析');
