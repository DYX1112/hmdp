package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisClient;
import com.hmdp.utils.RedisData;
import com.sun.org.apache.bcel.internal.generic.RETURN;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisClient redisClient;

    private static final ExecutorService CACHE_BUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
//        Shop shop = queryWithPassThrough(id);
//        Shop shop = queryWithLogicExpire(id);
//        Shop shop = redisClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        Shop shop = redisClient.queryWithLogicExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);
        if(shop==null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

//    public Shop queryWithMutex(Long id) {
//        // 开始，从redis中去查
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//
//        // 存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            // 序列化成对象
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//
//        if(shopJson!=null){
//            return null;
//        }
//        // 获取锁
//        Shop shop = null;
//        try {
//            if(!tryLock(LOCK_SHOP_KEY)){
//                Thread.sleep(50);
//                return queryWithMutex(id);
//            }
//            //获取锁成功后，这时还需要去redis查看一下是否有数据
//            shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//
//            if (StrUtil.isNotBlank(shopJson)) {
//                // 序列化成对象
//                shop = JSONUtil.toBean(shopJson, Shop.class);
//                return shop;
//            }
//
//            // 不存在，则需要从数据库中查找
//            shop = getById(id);
//            // 模拟重建延时
//            Thread.sleep(200);
//            if(shop==null){
//                // 空值写会Redis
//                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,null,CACHE_NULL_TTL,TimeUnit.MINUTES);
//                return null;
//            }
//
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(shop));
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            unlock(LOCK_SHOP_KEY);
//        }
//
//        return shop;
//    }
//    public Shop queryWithLogicExpire(Long id) {
//        // 开始，从redis中去查
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//
//        // 不存在
//        if (StrUtil.isBlank(shopJson)) {
//            return null ;
//        }
//        // 存在
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        // 未过期
//        if(expireTime.isAfter(LocalDateTime.now())){
//            return shop;
//        }
//        //过期
//        //获取互斥锁
//        boolean flag = tryLock(LOCK_SHOP_KEY+id);
//        if(!flag){
//            return shop;
//        }else{
//            CACHE_BUILD_EXECUTOR.submit( () ->{
//                try {
//                    this.saveShop2Redis(shop.getId(),20L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    unlock(LOCK_SHOP_KEY+shop.getId());
//                }
//            });
//        }
//        return shop;
//    }

//    public Shop queryWithPassThrough(Long id) {
//        // 开始，从redis中去查
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//
//        // 存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            // 序列化成对象
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//
//        if(shopJson!=null){
//            return null;
//        }
//        // 不存在，则需要从数据库中查找
//        Shop shop = getById(id);
//
//        if(shop==null){
//            // 空值写会Redis
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,null,CACHE_NULL_TTL,TimeUnit.MINUTES);
//            return null;
//        }
//
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(shop));
//
//        // 设置超时时间
//        stringRedisTemplate.expire(CACHE_SHOP_KEY+id,CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        return shop;
//    }

//    public void saveShop2Redis(Long id, Long expire) throws InterruptedException {
//        Shop shop = getById(id);
//        Thread.sleep(200);
//        // 创建bean
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expire));
//        // 存入redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
//    }

//    // 获取锁
//    private boolean tryLock(String key){
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.MILLISECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    private void unlock(String key){
//        stringRedisTemplate.delete(key);
//    }

    @Override
    @Transactional
    public Result updateByShopId(Shop shop) {
        Long id = shop.getId();
        if(id==null){
            return Result.fail("店铺id不能为空");
        }
        // 更新数据库
        updateById(shop);

        // 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+ shop.getId());
        return Result.ok("更新成功");
    }

}
