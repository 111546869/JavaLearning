package com.example.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import jakarta.servlet.Filter;
import java.time.Duration;

@Configuration
public class WebConfig {

    @Bean
    public Filter characterEncodingFilterCustom() {
        CharacterEncodingFilter filter = new CharacterEncodingFilter();
        filter.setEncoding("UTF-8");
        filter.setForceRequestEncoding(true);
        filter.setForceResponseEncoding(true);
        return filter;
    }

    // 注入 WebClient.Builder，配置底层 Netty 的连接池机制
    @Bean
    public WebClient.Builder webClientBuilder() {
        // 1. 配置连接池，复用 TCP 连接，减免 TLS 握手时间
        ConnectionProvider provider = ConnectionProvider.builder("dify-pool")
                .maxConnections(50)
                .maxIdleTime(Duration.ofSeconds(60)) // 连接最大空闲时间
                .maxLifeTime(Duration.ofMinutes(5))
                .build();

        // 2. 配置 HttpClient，开启 Keep-Alive
        HttpClient httpClient = HttpClient.create(provider)
                .keepAlive(true);

        // 3. 将定制的 HttpClient 塞给 WebClient
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}