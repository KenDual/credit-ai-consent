package com.demo.credit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
public class Web3jConfig {
    @Bean
    public Web3j web3j(org.springframework.core.env.Environment env) {
        String rpc = env.getProperty("web3.rpc-url");
        return Web3j.build(new HttpService(rpc));
    }
}
