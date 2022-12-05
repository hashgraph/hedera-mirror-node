package com.hedera.mirror.monitor;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

import io.fabric8.kubernetes.api.model.Condition;
import io.fabric8.kubernetes.api.model.ConditionBuilder;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@EnableKubernetesMockClient
@ExtendWith(SystemStubsExtension.class)
class HelmReleaseHealthIndicatorTest {

    private static final String HELM_RELEASE_PATH = "/apis/helm.toolkit.fluxcd.io/v2beta1/namespaces/test/helmreleases" +
            "/mirror";
    private static final String HOSTNAME = "test-pod";
    private static final String POD_REQUEST_PATH = "/api/v1/namespaces/test/pods/" + HOSTNAME;
    private static final Map<String, String> POD_LABELS = Map.of("app.kubernetes.io/instance", "mirror");

    @SystemStub
    private final EnvironmentVariables environmentVariables = new EnvironmentVariables("HOSTNAME", HOSTNAME);
    private final HelmReleaseHealthProperties properties = new HelmReleaseHealthProperties();

    private KubernetesMockServer server;
    private HelmReleaseHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        var client = server.createClient().inNamespace("test");
        healthIndicator = new HelmReleaseHealthIndicator(client, properties);
        properties.setCacheExpiry(Duration.ofSeconds(30));
        server.clearExpectations();
    }

    @Test
    void down() {
        // given
        server.expect()
                .withPath(POD_REQUEST_PATH)
                .andReturn(200, pod(POD_LABELS))
                .always();
        server.expect()
                .withPath(HELM_RELEASE_PATH)
                .andReturn(200, helmRelease(List.of(readyCondition("False"))))
                .always();

        // when
        var health = healthIndicator.getHealth(true).block();

        assertThat(health).returns(Status.DOWN, Health::getStatus);
    }

    @Test
    void up() {
        // given
        server.expect()
                .withPath(POD_REQUEST_PATH)
                .andReturn(200, pod(POD_LABELS))
                .always();
        server.expect()
                .withPath(HELM_RELEASE_PATH)
                .andReturn(200, helmRelease(List.of(readyCondition("True"))))
                .always();

        // when
        var health = healthIndicator.getHealth(true).block();

        // then
        assertThat(health).returns(Status.UP, Health::getStatus);
    }

    @Test
    void helmReleaseNotFound() {
        // given
        server.expect()
                .withPath(POD_REQUEST_PATH)
                .andReturn(200, pod(POD_LABELS))
                .always();
        server.expect()
                .withPath(HELM_RELEASE_PATH)
                .andReturn(404, null)
                .always();

        // when
        var health = healthIndicator.getHealth(true).block();

        // then
        assertThat(health).returns(Status.UNKNOWN, Health::getStatus);
    }

    @Test
    void helmReleaseWithoutReadyCondition() {
        // given
        server.expect()
                .withPath(POD_REQUEST_PATH)
                .andReturn(200, pod(POD_LABELS))
                .always();
        server.expect()
                .withPath(HELM_RELEASE_PATH)
                .andReturn(200, helmRelease(Collections.emptyList()))
                .always();

        // when
        var health = healthIndicator.getHealth(true).block();

        // then
        assertThat(health).returns(Status.DOWN, Health::getStatus);
    }

    @Test
    void podLabelNotFound() {
        // given
        server.expect()
                .withPath(POD_REQUEST_PATH)
                .andReturn(200, pod(Collections.emptyMap()))
                .always();

        // when
        var health = healthIndicator.getHealth(true).block();

        // then
        assertThat(health).returns(Status.UNKNOWN, Health::getStatus);
    }

    @Test
    void podNotFound() {
        // given
        server.expect()
                .withPath(POD_REQUEST_PATH)
                .andReturn(404, null)
                .always();

        // when
        var health = healthIndicator.getHealth(true).block();

        // then
        assertThat(health).returns(Status.UNKNOWN, Health::getStatus);
    }

    @Test
    void cacheHit() {
        // given
        properties.setCacheExpiry(Duration.ofMillis(500));
        server.expect()
                .withPath(POD_REQUEST_PATH)
                .andReturn(200, pod(POD_LABELS))
                .always();
        server.expect()
                .withPath(HELM_RELEASE_PATH)
                .andReturn(200, helmRelease(List.of(readyCondition("True"))))
                .once();
        server.expect()
                .withPath(HELM_RELEASE_PATH)
                .andReturn(200, helmRelease(List.of(readyCondition("False"))))
                .once();

        // when
        var first = healthIndicator.getHealth(true).block().getStatus();
        var second = healthIndicator.getHealth(true).block().getStatus();

        // then
        assertThat(List.of(first, second)).containsOnly(Status.UP);
    }

    @Test
    void cacheExpired() {
        // given
        properties.setCacheExpiry(Duration.ofMillis(500));
        server.expect()
                .withPath(POD_REQUEST_PATH)
                .andReturn(200, pod(POD_LABELS))
                .always();
        server.expect()
                .withPath(HELM_RELEASE_PATH)
                .andReturn(200, helmRelease(List.of(readyCondition("True"))))
                .once();

        // when
        var health = healthIndicator.getHealth(true).block();

        // then
        assertThat(health).returns(Status.UP, Health::getStatus);

        // given
        server.expect()
                .withPath(HELM_RELEASE_PATH)
                .andReturn(200, helmRelease(List.of(readyCondition("False"))))
                .once();

        // then
        await()
                .atLeast(Duration.ofMillis(100))
                .atMost(Duration.ofMillis(600))
                .pollInterval(Duration.ofMillis(50))
                .until(() -> healthIndicator.getHealth(true).block().getStatus(), equalTo(Status.DOWN));
    }

    @Test
    void sameMono() {
        var first = healthIndicator.getHealth(true);
        var second = healthIndicator.getHealth(true);
        assertEquals(first, second);
    }

    private GenericKubernetesResource helmRelease(List<Condition> conditions) {
        Map<String, Object> properties = Map.of("status", Map.of("conditions", conditions));
        return new GenericKubernetesResourceBuilder()
                .withKind("HelmRelease")
                .withAdditionalProperties(properties)
                .build();
    }

    private Pod pod(Map<String, String> labels) {
        return new PodBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withLabels(labels)
                        .withName(HOSTNAME)
                        .build())
                .build();
    }

    private Condition readyCondition(String status) {
        return new ConditionBuilder().withType("Ready").withStatus(status).build();
    }
}
