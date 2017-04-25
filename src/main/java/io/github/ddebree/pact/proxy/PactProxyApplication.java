package io.github.ddebree.pact.proxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.context.annotation.Bean;
import io.github.ddebree.pact.proxy.filters.PactRecorderFilter;

@EnableZuulProxy
@SpringBootApplication
public class PactProxyApplication {

    public static void main(String[] args) {
        SpringApplication.run(PactProxyApplication.class, args);
    }

    @Bean
    public PactRecorderFilter simpleFilter() {
        return new PactRecorderFilter();
    }

}
