package demo3.demo3_068.config;

import demo3.demo3_068.interceptor.JwtTokenInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtTokenInterceptor jwtTokenInterceptor;
    private final boolean mockPaymentEnabled;

    public WebMvcConfig(JwtTokenInterceptor jwtTokenInterceptor,
                        @Value("${payment.mock.enabled:false}") boolean mockPaymentEnabled) {
        this.jwtTokenInterceptor = jwtTokenInterceptor;
        this.mockPaymentEnabled = mockPaymentEnabled;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        var registration = registry.addInterceptor(jwtTokenInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/",
                        "/index.html",
                        "/styles.css",
                        "/app.js",
                        "/favicon.ico",
                        "/assets/**",
                        "/actuator/**",
                        "/user/email/code",
                        "/user/register",
                        "/user/login"
                );
        if (mockPaymentEnabled) {
            registration.excludePathPatterns("/payment/mock/callback");
        }
    }
}
