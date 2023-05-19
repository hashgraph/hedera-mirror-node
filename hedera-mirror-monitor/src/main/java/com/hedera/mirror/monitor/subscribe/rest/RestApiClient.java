/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.monitor.subscribe.rest;

import com.hedera.mirror.monitor.MonitorProperties;
import com.hedera.mirror.rest.model.NetworkNode;
import com.hedera.mirror.rest.model.NetworkNodesResponse;
import jakarta.inject.Named;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@CustomLog
@Named
public class RestApiClient {

    private static final String PREFIX = "/api/v1";

    private final WebClient webClient;

    public RestApiClient(MonitorProperties monitorProperties, WebClient.Builder webClientBuilder) {
        String url = monitorProperties.getMirrorNode().getRest().getBaseUrl();
        webClient = webClientBuilder
                .baseUrl(url)
                .defaultHeaders(h -> h.setAccept(List.of(MediaType.APPLICATION_JSON)))
                .build();
        log.info("Connecting to mirror node {}", url);
    }

    public <T> Mono<T> retrieve(Class<T> responseClass, String uri, Object... parameters) {
        return webClient
                .get()
                .uri(uri.replace(PREFIX, StringUtils.EMPTY), parameters)
                .retrieve()
                .bodyToMono(responseClass)
                .onErrorResume(Mono::error) // Needed for some reason to avoid onErrorDropped
                .name("rest");
    }

    public Flux<NetworkNode> getNodes() {
        var next = new AtomicReference<>("/network/nodes?limit=25");

        return Flux.defer(() -> retrieve(NetworkNodesResponse.class, next.get())
                        .doOnNext(r ->
                                next.set(r.getLinks() != null ? r.getLinks().getNext() : null))
                        .flatMapIterable(NetworkNodesResponse::getNodes))
                .repeat(() -> StringUtils.isNotBlank(next.get()));
    }
}
