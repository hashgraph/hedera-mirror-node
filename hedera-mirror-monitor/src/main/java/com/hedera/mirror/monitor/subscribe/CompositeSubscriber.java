package com.hedera.mirror.monitor.subscribe;

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

import com.google.common.base.Suppliers;
import com.google.common.collect.Streams;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

import com.hedera.mirror.monitor.MonitorProperties;
import com.hedera.mirror.monitor.expression.ExpressionConverter;
import com.hedera.mirror.monitor.publish.PublishResponse;

@Named
@Primary
@RequiredArgsConstructor
public class CompositeSubscriber implements Subscriber {

    private final ExpressionConverter expressionConverter;
    private final MonitorProperties monitorProperties;
    private final SubscribeProperties subscribeProperties;
    private final MeterRegistry meterRegistry;
    private final WebClient.Builder webClientBuilder;
    final Supplier<List<Subscriber>> subscribers = Suppliers.memoize(this::subscribers);

    @Override
    @PreDestroy
    public void close() {
        subscribers.get().forEach(Subscriber::close);
    }

    @Override
    public void onPublish(PublishResponse response) {
        subscribers.get().forEach(s -> s.onPublish(response));
    }

    private List<Subscriber> subscribers() {
        if (!subscribeProperties.isEnabled()) {
            return Collections.emptyList();
        }

        return Streams.concat(
                subscribeProperties.getGrpc()
                        .stream()
                        .filter(AbstractSubscriberProperties::isEnabled)
                        .map(p -> new GrpcSubscriber(expressionConverter, meterRegistry, monitorProperties, p)),
                subscribeProperties.getRest()
                        .stream()
                        .filter(AbstractSubscriberProperties::isEnabled)
                        .map(p -> new RestSubscriber(meterRegistry, monitorProperties, p, webClientBuilder))
        ).collect(Collectors.toList());
    }
}
