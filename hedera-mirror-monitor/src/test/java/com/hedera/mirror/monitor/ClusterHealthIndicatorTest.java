package com.hedera.mirror.monitor;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import reactor.core.publisher.Flux;

import com.hedera.mirror.monitor.publish.PublishScenario;
import com.hedera.mirror.monitor.publish.PublishScenarioProperties;
import com.hedera.mirror.monitor.publish.generator.TransactionGenerator;
import com.hedera.mirror.monitor.subscribe.MirrorSubscriber;
import com.hedera.mirror.monitor.subscribe.Scenario;
import com.hedera.mirror.monitor.subscribe.TestScenario;

@ExtendWith(MockitoExtension.class)
class ClusterHealthIndicatorTest {

    @Mock
    private MirrorSubscriber mirrorSubscriber;

    @Mock
    private TransactionGenerator transactionGenerator;

    @InjectMocks
    private ClusterHealthIndicator clusterHealthIndicator;

    @Test
    void healthy() {
        when(transactionGenerator.scenarios()).thenReturn(Flux.just(publishScenario(1.0)));
        when(mirrorSubscriber.getSubscriptions()).thenReturn(Flux.just(subscribeScenario(1.0)));
        assertThat(clusterHealthIndicator.health().block()).isEqualTo(Health.up().build());
    }

    @Test
    void publishingInactive() {
        when(transactionGenerator.scenarios()).thenReturn(Flux.just(publishScenario(0.0)));
        when(mirrorSubscriber.getSubscriptions()).thenReturn(Flux.just(subscribeScenario(1.0)));
        assertThat(clusterHealthIndicator.health().block()).extracting(Health::getStatus).isEqualTo(Status.UNKNOWN);
    }

    @Test
    void subscribingInactive() {
        when(transactionGenerator.scenarios()).thenReturn(Flux.just(publishScenario(1.0)));
        when(mirrorSubscriber.getSubscriptions()).thenReturn(Flux.just(subscribeScenario(0.0)));
        assertThat(clusterHealthIndicator.health().block()).extracting(Health::getStatus).isEqualTo(Status.DOWN);
    }

    @Test
    void bothInactive() {
        when(transactionGenerator.scenarios()).thenReturn(Flux.just(publishScenario(0.0)));
        when(mirrorSubscriber.getSubscriptions()).thenReturn(Flux.just(subscribeScenario(0.0)));
        assertThat(clusterHealthIndicator.health().block()).extracting(Health::getStatus).isEqualTo(Status.UNKNOWN);
    }

    private PublishScenario publishScenario(double rate) {
        return new TestPublishScenario(rate);
    }

    private Scenario<?, ?> subscribeScenario(double rate) {
        TestScenario testScenario = new TestScenario();
        testScenario.setRate(rate);
        return testScenario;
    }

    @Getter
    private class TestPublishScenario extends PublishScenario {

        private final double rate;

        private TestPublishScenario(double rate) {
            super(new PublishScenarioProperties());
            this.rate = rate;
        }
    }
}
