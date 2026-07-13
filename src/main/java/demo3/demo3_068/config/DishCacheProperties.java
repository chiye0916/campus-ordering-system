package demo3.demo3_068.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "dish.cache")
public class DishCacheProperties {

    private Duration listTtl = Duration.ofMinutes(30);
    private Duration emptyListTtl = Duration.ofMinutes(5);
}
