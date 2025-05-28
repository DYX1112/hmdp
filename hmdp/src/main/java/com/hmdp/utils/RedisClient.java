package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cglib.core.Local;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @Author: 杜宇翔
 * @CreateTime: 2025-05-27
 * @Description: redis缓存工具
 */
@Component
@Slf4j
public class RedisClient {

    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_BUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public RedisClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key , Object value, Long expire, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),expire,unit);
    }

    public void setWithLogicExpire(String key , Object value, Long expire, TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(expire)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> func,Long expire,TimeUnit timeUnit) {
        // 开始，从redis中去查
        String json =stringRedisTemplate.opsForValue().get(keyPrefix + id);

        // 存在
        if (StrUtil.isNotBlank(json)) {
            // 序列化成对象
            R r = JSONUtil.toBean(json, type);
            return r;
        }

        if(json!=null){
            return null;
        }
        // 不存在，则需要从数据库中查找
        R r = func.apply(id);

        if(r==null){
            // 空值写会Redis
            stringRedisTemplate.opsForValue().set(keyPrefix+id,"",expire,timeUnit);
            return null;
        }

        this.set(keyPrefix+id,r,expire,timeUnit);

        return r;
    }
    public <R,ID> R queryWithLogicExpire(String keyPrefix,ID id,Class<R> type,Function<ID,R> func,Long expire , TimeUnit timeUnit) {
        // 开始，从redis中去查
        String key = keyPrefix+id;
        String json = stringRedisTemplate.opsForValue().get(key);

        // 不存在
        if (StrUtil.isBlank(json)) {
            return null ;
        }
        // 存在
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 未过期
        if(expireTime.isAfter(LocalDateTime.now())){
            return r;
        }
        //过期
        //获取互斥锁
        boolean flag = tryLock(LOCK_SHOP_KEY+id);
        if(!flag){
            return r;
        }else{
            CACHE_BUILD_EXECUTOR.submit( () ->{
                try {
                    //查数据库
                    R r1 = func.apply(id);
                    //再存入redis
                    this.setWithLogicExpire(key,r1,expire,timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(LOCK_SHOP_KEY+id);
                }
            });
        }
        return r;
    }


    // 获取锁
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
