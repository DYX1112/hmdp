package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.config.RedisConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIDGenerator;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIDGenerator redisIDGenerator;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    //创建线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    //获取代理对象
    private IVoucherOrderService proxy;

    //再类初始化后就应该进行开启执行任务
    @PostConstruct
    public void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    public Result addSecKillOrder(Long voucherId){
        // 执行lua脚本
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIDGenerator.nextId("order");
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString(),String.valueOf(orderId));
        //判断结果是否为0，否就返回异常信息
        int r = result.intValue();
        if(r!=0){
            return Result.fail(r==1?"库存不足":"用户已下单");
        }

        //如果是0，将优惠卷id，用户id和订单id都放入阻塞队列中

        //异步下单
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }

    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while(true){
                // 订阅消息队列中的元素
                try {
                    List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)), StreamOffset.create("stream.orders", ReadOffset.lastConsumed()));

                    if(records==null||records.isEmpty()){
                        continue;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = records.get(0);

                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    handleVoucherOrder(voucherOrder);
                    stringRedisTemplate.opsForStream().acknowledge("stream.orders","g1",record.getId());
                } catch (Exception e) {
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while(true){
                // 取出pending中的元素
                try {
                    List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1), StreamOffset.create("stream.orders", ReadOffset.from("0")));

                    // 没有读到消息，说明pending中没有要处理的消息
                    if(records==null||records.isEmpty()){
                        break;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = records.get(0);

                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    handleVoucherOrder(voucherOrder);
                    stringRedisTemplate.opsForStream().acknowledge("stream.orders","g1",record.getId());
                } catch (Exception e) {
                    log.error("订单处理异常");
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }

        }
    }

    /*public Result addSecKillOrder(Long voucherId){
        // 执行lua脚本
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
        //判断结果是否为0，否就返回异常信息
        int r = result.intValue();
        if(r!=0){
            return Result.fail(r==1?"库存不足":"用户已下单");
        }
        long orderId = redisIDGenerator.nextId("order");
        //如果是0，将优惠卷id，用户id和订单id都放入阻塞队列中
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        voucherOrderBlockingQueue.add(voucherOrder);
        //异步下单

        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }*/

    // 创建任务进行执行
    /*private BlockingQueue<VoucherOrder> voucherOrderBlockingQueue = new ArrayBlockingQueue<>(1024*1024);

    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while(true){
                // 获取阻塞队列的元素
                try {
                    VoucherOrder task = voucherOrderBlockingQueue.take();
                    handleVoucherOrder(task);
                } catch (InterruptedException e) {
                   log.error("处理异常",e);
                }
            }
        }
        }*/


    public void handleVoucherOrder(VoucherOrder voucherOrder){
        Long id = voucherOrder.getUserId();
        //SimpleRedisLock lock = new SimpleRedisLock("order:" + id, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + id);
        boolean l = lock.tryLock();
        if(!l){
            log.error("你已经购买过该商品");
            return;
        }
        try {
            // 抢购成功将信息推入进阻塞队列中，并额外开启一个线程去异步执行
             proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder){
        // 由于开启了子线程，无法从threadLocal中获取了
        Long userId = voucherOrder.getUserId();
        Long count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if(count>0){
            log.error("你已经购买过该商品");
            return;
        }
        boolean success = seckillVoucherService.update().setSql("stock=stock-1").eq("voucher_id", voucherOrder.getVoucherId()).gt("stock",0).update();

        if(!success){
            log.error("库存不足");
            return;
        }
        save(voucherOrder);
        log.info("秒杀卷抢购成功");
    }

/*    @Override
    public Result addSecKillOrder(Long voucherId) {
        // 获取秒杀卷信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);

        // 判断秒杀是否开始
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        if(LocalDateTime.now().isBefore(beginTime)){
            return Result.fail("秒杀还未开始");
        }

        if(LocalDateTime.now().isAfter(seckillVoucher.getEndTime())){
            return Result.fail("秒杀活动已结束");
        }

        //判断库存是否充足
        Integer stock = seckillVoucher.getStock();
        if(stock<1){
            return Result.fail("库存不足");
        }
        // 为什么要加锁？原因：第一我们需要保证一人一单的情况下，这里我们只是去查询，并没有修改数据库的值，这也就导致我们无法使用乐观锁；只有使用悲观锁串行执行，这里的锁是针对每个用户单独加，意思是每个用户都可以享用自己
        // 的一把锁，保证同一用户的线程咨只允许串行执行，不同用户的线程可以并行执行，大大提高效率。这里使用了事务，我们要使事务生效就不能仅仅调用createVoucherOrder这个函数，这样spring的事务管理无法生效，我们需要动态
        // 代理对象去调用，使得事务生效。
//        synchronized (UserHolder.getUser().getId().toString().intern()){
//            // 获取动态代理对象使得事务生效
//            IVoucherOrderService o = (IVoucherOrderService) AopContext.currentProxy();
//            return o.createVoucherOrder(voucherId);
//        }
        Long id = UserHolder.getUser().getId();
        //SimpleRedisLock lock = new SimpleRedisLock("order:" + id, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + id);
        boolean l = lock.tryLock();
        if(!l){
            return Result.fail("你以及购买过该商品");
        }
        try {
            IVoucherOrderService o = (IVoucherOrderService) AopContext.currentProxy();
            // 抢购成功将信息推入进阻塞队列中，并额外开启一个线程去异步执行
            return o.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }
    }*/

/*    @Transactional
    public Result createVoucherOrder(Long voucherId){

        Long userId = UserHolder.getUser().getId();

        Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();

        if(count>0){
            return Result.fail("你已经购买过改商品");
        }

        boolean success = seckillVoucherService.update().setSql("stock=stock-1").eq("voucher_id", voucherId).gt("stock",0).update();

        if(!success){
            return Result.fail("库存不足");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(redisIDGenerator.nextId("order"));
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);

        save(voucherOrder);
        return Result.ok("秒杀卷抢购成功");
    }*/
}
