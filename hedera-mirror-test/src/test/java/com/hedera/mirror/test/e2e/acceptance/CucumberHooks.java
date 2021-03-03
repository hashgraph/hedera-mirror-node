package com.hedera.mirror.test.e2e.acceptance;

import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.TestRunFinished;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.test.e2e.acceptance.client.AbstractNetworkClient;

@Log4j2
@RequiredArgsConstructor
public class CucumberHooks implements ConcurrentEventListener {
    private final Collection<AbstractNetworkClient> clients;

    public CucumberHooks() {
        clients = null;
    }

    @Override
    public void setEventPublisher(EventPublisher eventPublisher) {
        eventPublisher.registerHandlerFor(TestRunFinished.class, this::afterAll);
    }

    private void afterAll(TestRunFinished event) {
        clients.forEach(AbstractNetworkClient::close);
    }
}
