package io.github.ddebree.pact.proxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;

@EnableZuulProxy
@SpringBootApplication
public class PactProxyApplication {

    public static void main(String[] args) {
        SpringApplication.run(PactProxyApplication.class, args);
    }

}
