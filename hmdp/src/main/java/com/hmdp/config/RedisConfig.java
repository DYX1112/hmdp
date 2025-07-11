package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: 杜宇翔
 * @CreateTime: 2025-05-30
 * @Description: redis配置
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedissonClient getRedisson(){
        Config config = new Config();

        config.useSingleServer().setAddress("redis://127.0.0.1:6379").setPassword("851429dyx").setDatabase(8);

        return  Redisson.create(config);
    }
}
