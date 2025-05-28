package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIDGenerator;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIDGenerator redisIDGenerator;

    @Override
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
        synchronized (UserHolder.getUser().getId().toString().intern()){
            // 获取动态代理对象使得事务生效
            IVoucherOrderService o = (IVoucherOrderService) AopContext.currentProxy();
            return o.createVoucherOrder(voucherId);
        }
    }

    @Transactional
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
    }
}
