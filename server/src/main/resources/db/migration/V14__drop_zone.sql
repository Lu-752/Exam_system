-- V14：下线「专区管理」域
-- 背景：专区（公告/资讯/指南）不属于考试主链路，产品确认删除，避免维护空心模块。
-- 处理：清菜单 + 清角色菜单关联 + 删表。V12 保持不动（已执行库的 Flyway 校验和不能变）。
DELETE FROM sys_role_menu
 WHERE menu_id IN (SELECT id FROM sys_menu WHERE path = '/system/zones' OR perm = 'zone:view');

DELETE FROM sys_menu
 WHERE path = '/system/zones' OR perm = 'zone:view';

DROP TABLE IF EXISTS sys_zone;
