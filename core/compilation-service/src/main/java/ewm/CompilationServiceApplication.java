package ewm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@EnableFeignClients(basePackages = "ewm.feign.client")
@SpringBootApplication
@ComponentScan(value = {"ewm", "client"})
public class CompilationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CompilationServiceApplication.class, args);
    }
}
