package com.hmdp.utils;

/**
 * @Author: 杜宇翔
 * @CreateTime: 2025-05-28
 * @Description: 分布式锁
 */
public interface ILock {

    public boolean tryLock(Long timeoutUnit);

    public void unLock();
}
