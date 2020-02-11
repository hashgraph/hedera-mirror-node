package com.hedera.mirror.test.e2e.acceptance.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import com.hedera.mirror.test.e2e.acceptance.client.AccountClient;
import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.SDKClient;
import com.hedera.mirror.test.e2e.acceptance.client.TopicClient;

@Configuration
@Log4j2
@RequiredArgsConstructor
public class ClientConfiguration {
    @Autowired
    private final AcceptanceTestProperties acceptanceTestProperties;

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public SDKClient sdkClient() {
        return new SDKClient(acceptanceTestProperties);
    }

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public MirrorNodeClient mirrorNodeClient() {
        return new MirrorNodeClient(acceptanceTestProperties);
    }

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public TopicClient topicClient() {
        return new TopicClient(sdkClient());
    }

    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public AccountClient accountClient() {
        return new AccountClient(sdkClient());
    }
}
