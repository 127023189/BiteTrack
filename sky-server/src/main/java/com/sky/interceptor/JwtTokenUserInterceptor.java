package com.sky.interceptor;

import com.sky.constant.JwtClaimsConstant;
import com.sky.context.BaseContext;
import com.sky.properties.JwtProperties;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.HandlerMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
@Slf4j
public class JwtTokenUserInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;

    public boolean preHandle(HttpServletRequest request,HttpServletResponse response, Object handler) throws Exception{

        // 判断当前拦截到的是Controller的方法还是其他资源
        if(!(handler instanceof HandlerMethod)){
            // 当前拦截到的不是动态方法，直接放行
            return true;
        }

        // 从请求中获取token
        String token = request.getHeader(jwtProperties.getUserTokenName());

        Claims claims = null;
        try {
            // 校验token
            claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
            // 将当前登录用户的id存入ThreadLocal
            Long userId = Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString());
            log.info("当前用户id：{}", userId);
            BaseContext.setCurrentId(userId);
            // 放行
            return true;
        } catch (Exception ex) {
            response.setStatus(401);
            return false;
        }
    }

}
