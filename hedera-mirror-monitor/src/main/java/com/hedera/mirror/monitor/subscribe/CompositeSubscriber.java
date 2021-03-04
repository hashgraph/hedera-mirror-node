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
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

import com.hedera.mirror.monitor.MonitorProperties;
import com.hedera.mirror.monitor.expression.ExpressionConverter;
import com.hedera.mirror.monitor.publish.PublishResponse;

@Log4j2
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
                        .map(p -> {
                            try {
                                return new GrpcSubscriber(expressionConverter, meterRegistry, monitorProperties, p);
                            } catch (InterruptedException e) {
                                log.warn("Unable to retrieve mirror grpc subscriber to {}: ", monitorProperties
                                        .getMirrorNode().getGrpc().getEndpoint());
                            }
                            return null;
                        })
                        .filter(Objects::nonNull),
                subscribeProperties.getRest()
                        .stream()
                        .filter(AbstractSubscriberProperties::isEnabled)
                        .map(p -> new RestSubscriber(meterRegistry, monitorProperties, p, webClientBuilder))
        ).collect(Collectors.toList());
    }
}
