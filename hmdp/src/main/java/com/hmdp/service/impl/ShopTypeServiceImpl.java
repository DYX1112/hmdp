package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_TYPE;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service

public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryByType() {
        // 从redis里面查
        String shops = stringRedisTemplate.opsForValue().get(SHOP_TYPE);
        if(StrUtil.isNotBlank(shops)||shops!=null){
            List<ShopType> collect = Arrays.stream(shops.split(";")).map(strJson -> JSONUtil.toBean(strJson, ShopType.class)).collect(Collectors.toList());
            return Result.ok(collect);
        }

        // 不存在，从数据库中查
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        if(shopTypes==null||shopTypes.isEmpty()){
            return Result.fail("不存在商铺分类");
        }

        //存入redis中
        List<String> collect = shopTypes.stream().map(shopType -> JSONUtil.toJsonStr(shopType)).collect(Collectors.toList());
        String str = String.join(";", collect);
        stringRedisTemplate.opsForValue().set(SHOP_TYPE,str);
        return Result.ok(shopTypes);
    }
}
