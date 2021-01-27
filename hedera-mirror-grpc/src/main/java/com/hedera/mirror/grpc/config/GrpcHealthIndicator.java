package com.hedera.mirror.grpc.config;

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

import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.services.HealthStatusManager;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

@RequiredArgsConstructor
public class GrpcHealthIndicator implements HealthIndicator {

    private final HealthStatusManager healthStatusManager;
    private final String serviceName;

    @Override
    public Health health() {
        HealthGrpc.HealthImplBase healthService = (HealthGrpc.HealthImplBase) healthStatusManager
                .getHealthService();
        HealthCheckRequest healthcheckRequest = HealthCheckRequest.newBuilder().setService(serviceName).build();
        HealthStreamObserver healthStreamObserver = new HealthStreamObserver();
        healthService.check(healthcheckRequest, healthStreamObserver);
        return healthStreamObserver.getHealth();
    }

    private class HealthStreamObserver implements StreamObserver<HealthCheckResponse> {

        private Health.Builder health = Health.unknown();

        public Health getHealth() {
            return health.build();
        }

        @Override
        public void onNext(HealthCheckResponse healthCheckResponse) {
            if (healthCheckResponse.getStatus() == HealthCheckResponse.ServingStatus.SERVING) {
                health = health.up();
            } else {
                health = health.down();
            }
        }

        @Override
        public void onError(Throwable t) {
            health = health.down(t);
        }

        @Override
        public void onCompleted() {
        }
    }
}
