package com.hedera.mirror.test.e2e.acceptance;

import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventHandler;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.TestRunFinished;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;

import com.hedera.mirror.test.e2e.acceptance.client.MirrorNodeClient;
import com.hedera.mirror.test.e2e.acceptance.client.TokenClient;
import com.hedera.mirror.test.e2e.acceptance.client.TopicClient;

@Log4j2
public class CucumberHooks implements ConcurrentEventListener {
    @Autowired
    private MirrorNodeClient mirrorClient;
    @Autowired
    private TokenClient tokenClient;
    @Autowired
    private TopicClient topicClient;

    private final EventHandler<TestRunFinished> afterAll = event -> {
        // close clients
        mirrorClient.close();
        tokenClient.close();
        topicClient.close();
    };

    @Override
    public void setEventPublisher(EventPublisher eventPublisher) {
        eventPublisher.registerHandlerFor(TestRunFinished.class, afterAll);
    }
}
