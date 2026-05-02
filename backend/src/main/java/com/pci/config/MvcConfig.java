package com.pci.config;

import com.pci.interceptor.LoginInterceptor;
import com.pci.interceptor.RateLimitInterceptor;
import com.pci.interceptor.RefreshTokenInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0);
        registry.addInterceptor(new RateLimitInterceptor(stringRedisTemplate)).order(1);
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/user/login", "/user/code",
                        "/", "/login", "/home", "/course", "/memo",
                        "/notify", "/chat", "/settings", "/travel", "/guide",
                        "/index.html", "/assets/**", "/*.js", "/*.css", "/*.ico", "/*.svg"
                )
                .order(2);
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOrigin("*");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
