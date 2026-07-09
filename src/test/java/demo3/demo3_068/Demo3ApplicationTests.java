package demo3.demo3_068;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class Demo3ApplicationTests {

	@Test
	void contextLoads() {
	}

}
