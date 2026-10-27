package com.exam.system;

/**
 * 系统默认密码常量：种子账号、用户管理新建/重置密码共用一处，避免多处硬编码不一致。
 */
public final class DefaultPassword {

    /** 历史上用过的默认密码，仅用于存量账号迁移 */
    public static final String LEGACY = "123456";

    /** 当前默认密码 */
    public static final String CURRENT = "Mima3700%";

    private DefaultPassword() {
    }
}
