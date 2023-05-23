/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.monitor.health;

import com.hedera.mirror.monitor.publish.generator.TransactionGenerator;
import com.hedera.mirror.monitor.subscribe.MirrorSubscriber;
import com.hedera.mirror.monitor.subscribe.Scenario;
import jakarta.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.boot.actuate.health.Status;
import reactor.core.publisher.Mono;

@Named
@RequiredArgsConstructor
public class ClusterHealthIndicator implements ReactiveHealthIndicator {

    private static final Mono<Health> DOWN = health(Status.DOWN, "Subscribing is inactive");
    private static final Mono<Health> UNKNOWN = health(Status.UNKNOWN, "Publishing is inactive");
    private static final Mono<Health> UP = health(Status.UP, "");

    private final MirrorSubscriber mirrorSubscriber;
    private final TransactionGenerator transactionGenerator;

    private static Mono<Health> health(Status status, String reason) {
        Health.Builder health = Health.status(status);
        if (StringUtils.isNotBlank(reason)) {
            health.withDetail("reason", reason);
        }
        return Mono.just(health.build());
    }

    @Override
    public Mono<Health> health() {
        return publishing().switchIfEmpty(subscribing());
    }

    // Returns unknown if all publish scenarios aggregated rate has dropped to zero, otherwise returns an empty flux
    private Mono<Health> publishing() {
        return transactionGenerator
                .scenarios()
                .map(Scenario::getRate)
                .reduce(0.0, (c, n) -> c + n)
                .filter(sum -> sum <= 0)
                .flatMap(n -> UNKNOWN);
    }

    // Returns up if any subscription is running and its rate is above zero, otherwise returns down
    private Mono<Health> subscribing() {
        return mirrorSubscriber
                .getSubscriptions()
                .map(Scenario::getRate)
                .reduce(0.0, (cur, next) -> cur + next)
                .filter(sum -> sum > 0)
                .flatMap(n -> UP)
                .switchIfEmpty(UNKNOWN);
    }
}
