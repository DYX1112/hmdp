package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIDGenerator;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * @Author: 杜宇翔
 * @CreateTime: 2025-05-27
 * @Description:
 */
@SpringBootTest
public class hmdpTest {
    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisClient redisClient;

    @Resource
    private RedisIDGenerator redisIDGenerator;

    private static final ExecutorService es = Executors.newFixedThreadPool(500);

//    @Test
//    void save2RedisTest() throws InterruptedException {
//        shopService.saveShop2Redis(1L,20L);
//    }

    @Test
    void testNextId() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(300);

        Runnable task = () ->{
            for(int i = 0 ; i < 100 ; i++){
                long id = redisIDGenerator.nextId("order");
                System.out.println("id="+id);
            }
            countDownLatch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        countDownLatch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = "+(end-begin));
    }

    @Test
    void testSaveShop(){
        Shop shop = shopService.getById(1L);

        redisClient.setWithLogicExpire(CACHE_SHOP_KEY+1L,shop, CACHE_SHOP_TTL, TimeUnit.SECONDS);
    }

}
