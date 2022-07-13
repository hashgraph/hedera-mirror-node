package com.hedera.mirror.monitor.subscribe.rest;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Named;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.hedera.mirror.monitor.MonitorProperties;
import com.hedera.mirror.rest.model.NetworkNode;
import com.hedera.mirror.rest.model.NetworkNodesResponse;

@CustomLog
@Named
public class RestApiClient {

    private static final String PREFIX = "/api/v1";

    private final WebClient webClient;

    public RestApiClient(MonitorProperties monitorProperties, WebClient.Builder webClientBuilder) {
        String url = monitorProperties.getMirrorNode().getRest().getBaseUrl();
        webClient = webClientBuilder.baseUrl(url)
                .defaultHeaders(h -> h.setAccept(List.of(MediaType.APPLICATION_JSON)))
                .build();
        log.info("Connecting to mirror node {}", url);
    }

    public <T> Mono<T> retrieve(Class<T> responseClass, String uri, String... parameters) {
        return webClient.get()
                .uri(uri.replace(PREFIX, StringUtils.EMPTY), parameters)
                .retrieve()
                .bodyToMono(responseClass)
                .name("rest")
                .metrics();
    }

    public Flux<NetworkNode> getNodes() {
        var next = new AtomicReference<>("/network/nodes?limit=25");

        return Flux.defer(() -> retrieve(NetworkNodesResponse.class, next.get())
                        .doOnNext(r -> next.set(r.getLinks() != null ? r.getLinks().getNext() : null))
                        .flatMapIterable(NetworkNodesResponse::getNodes))
                .repeat(() -> StringUtils.isNotBlank(next.get()));
    }
}
