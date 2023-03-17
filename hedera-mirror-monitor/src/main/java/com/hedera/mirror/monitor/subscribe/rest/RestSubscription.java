package com.hedera.mirror.monitor.subscribe.rest;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import lombok.Getter;
import lombok.Value;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.core.publisher.Sinks;

import com.hedera.mirror.monitor.AbstractScenario;
import com.hedera.mirror.monitor.ScenarioProtocol;
import com.hedera.mirror.monitor.publish.PublishResponse;
import com.hedera.mirror.rest.model.TransactionByIdResponse;

@Getter
@Value
class RestSubscription extends AbstractScenario<RestSubscriberProperties, TransactionByIdResponse> {

    private final Sinks.Many<PublishResponse> sink;

    RestSubscription(int id, RestSubscriberProperties properties) {
        super(id, properties);
        sink = Sinks.many().multicast().directBestEffort();
    }

    @Override
    public ScenarioProtocol getProtocol() {
        return ScenarioProtocol.REST;
    }

    @Override
    public void onError(Throwable t) {
        String message = t.getMessage();

        if (Exceptions.isRetryExhausted(t) && t.getCause() != null) {
            t = t.getCause();
            message += " " + t.getMessage();
        }

        String error = t.getClass().getSimpleName();

        if (t instanceof WebClientResponseException webClientResponseException) {
            error = String.valueOf(webClientResponseException.getStatusCode().value());
        }

        log.warn("Subscription {} failed: {}", this, message);
        errors.add(error);
    }

    @Override
    public String toString() {
        String name = getName();
        return getProperties().getSubscribers() <= 1 ? name : name + " #" + getId();
    }
}
