package demo3.demo3_068.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DishCacheProperties.class)
public class DishCacheConfig {
}
