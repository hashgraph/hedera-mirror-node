package com.hedera.mirror.monitor.subscribe.rest;

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

import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;

import com.hedera.mirror.monitor.subscribe.AbstractSubscription;
import com.hedera.mirror.monitor.subscribe.SubscriberProtocol;
import com.hedera.mirror.monitor.subscribe.rest.response.MirrorTransaction;

class RestSubscription extends AbstractSubscription<RestSubscriberProperties, MirrorTransaction> {

    RestSubscription(int id, RestSubscriberProperties properties) {
        super(id, properties);
    }

    @Override
    public SubscriberProtocol getProtocol() {
        return SubscriberProtocol.REST;
    }

    @Override
    public void onError(Throwable t) {
        log.warn("Error subscribing to REST API: {}", t.getMessage());
        String error = t.getClass().getSimpleName();

        if (Exceptions.isRetryExhausted(t) && t.getCause() != null) {
            t = t.getCause();
        }

        if (t instanceof WebClientResponseException) {
            error = String.valueOf(((WebClientResponseException) t).getStatusCode().value());
        }

        errors.add(error);
    }
}
