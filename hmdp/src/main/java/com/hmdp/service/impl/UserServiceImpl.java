package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //检查手机格式
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("输入的手机号错误");
        }
        // 生成验证码
        String code = RandomUtil.randomNumbers(6);

        //todo: 发送验证码到用户手机中
        // 这里进行模拟
        log.info("发送成功：{}",code);

        // 保存到redis中
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        return Result.ok("success");
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 检验手机号
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();

        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);

        if (RegexUtils.isPhoneInvalid(phone)||!code.equals(cacheCode)){
            return Result.fail("手机号或者验证码不正确");
        }
        // 判断用户是否存在
        User user = query().eq("phone", phone).one();

        // 不存在创建新用户
        if(user == null){
            user = createUserWithPhone(phone);
        }

        // 存在保存在redis
        // 随机生成一个token
        String token = UUID.randomUUID().toString();

        Map<String, Object> userMap = BeanUtil.beanToMap(user, new HashMap<>(), CopyOptions.create().ignoreError().setFieldValueEditor((fieldKey, fieldValue) -> fieldValue.toString()));
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,userMap);

        // 设置过期时间
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL,TimeUnit.SECONDS);

        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));

        save(user);
        return user;
    }
}
