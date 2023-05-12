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

package com.hedera.mirror.monitor.health;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.ResourceDefinitionContext;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.inject.Named;
import lombok.AccessLevel;
import lombok.CustomLog;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.cloud.CloudPlatform;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
@CustomLog
@Named
@RequiredArgsConstructor
public class ReleaseHealthIndicator implements ReactiveHealthIndicator {

    static final String DEPENDENCY_NOT_READY = "DependencyNotReady";
    private static final Mono<Health> DOWN = Mono.just(Health.down().build());
    private static final Mono<Health> UNKNOWN = Mono.just(Health.unknown().build());
    private static final Mono<Health> UP = Mono.just(Health.up().build());
    private static final String INSTANCE_LABEL = "app.kubernetes.io/instance";
    private static final ResourceDefinitionContext RESOURCE_DEFINITION_CONTEXT = new ResourceDefinitionContext.Builder()
            .withGroup("helm.toolkit.fluxcd.io")
            .withKind("HelmRelease")
            .withNamespaced(true)
            .withPlural("helmreleases")
            .withVersion("v2beta1")
            .build();
    private final KubernetesClient client;
    private final ReleaseHealthProperties properties;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final Mono<Health> health = createHelmReleaseHealth();

    @Override
    public Mono<Health> health() {
        return properties.isEnabled() ? getHealth() : UP;
    }

    private Mono<Health> createHelmReleaseHealth() {
        return Mono.fromCallable(this::getHelmRelease)
                .cacheInvalidateIf(v -> false)
                .doOnError(e -> log.error("Unable to get helm release", e))
                .onErrorComplete()
                .flatMap(this::getHelmReleaseReadyStatus)
                .doOnError(e -> log.error("Unable to get helm release ready status", e))
                .onErrorComplete()
                .switchIfEmpty(UNKNOWN)
                .cache(properties.getCacheExpiry(), Schedulers.newSingle("helmrelease-health-cache"));
    }

    private String getHelmRelease() {
        var hostname = System.getenv("HOSTNAME");
        var labels = client.pods().withName(hostname).get().getMetadata().getLabels();
        return Objects.requireNonNull(labels.get(INSTANCE_LABEL), "No " + INSTANCE_LABEL + " label");
    }

    @SuppressWarnings("unchecked")
    private Mono<Health> getHelmReleaseReadyStatus(String release) {
        var resource = client.genericKubernetesResources(RESOURCE_DEFINITION_CONTEXT)
                .withName(release)
                .get();
        var status = (Map<String, Object>) resource.getAdditionalProperties().get("status");
        var conditions = (List<Map<String, String>>) status.get("conditions");
        return conditions.stream()
                .filter(condition -> StringUtils.equals(condition.get("type"), "Ready"))
                .findFirst()
                .map(this::mapStatus)
                .orElse(DOWN);
    }

    private Mono<Health> mapStatus(Map<String, String> condition) {
        var status = condition.get("status");
        if ("True".equals(status)) {
            return UP;
        }

        var reason = condition.get("reason");
        if (DEPENDENCY_NOT_READY.equals(reason)) {
            return UNKNOWN;
        }

        return DOWN;
    }
}
