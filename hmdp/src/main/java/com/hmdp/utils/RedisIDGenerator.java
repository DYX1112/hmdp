package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @Author: 杜宇翔
 * @CreateTime: 2025-05-28
 * @Description: 全局ID生成器
 */
@Component
public class RedisIDGenerator {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final long BEGIN_TIME_STAMP = 1735660800L;
    private static final int COUNT_BIT = 32;

    public long nextId(String keyPrefix){
        // 生成时间戳
        LocalDateTime currentTimeStamp = LocalDateTime.now();

        long current = currentTimeStamp.toEpochSecond(ZoneOffset.UTC);

        long timeStamp = current - BEGIN_TIME_STAMP;

        //生成序列号
        String date = currentTimeStamp.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        return timeStamp<<COUNT_BIT | count;
    }
}
