/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.grpc.config;

import jakarta.inject.Named;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.devh.boot.grpc.server.event.GrpcServerShutdownEvent;
import net.devh.boot.grpc.server.event.GrpcServerStartedEvent;
import net.devh.boot.grpc.server.event.GrpcServerTerminatedEvent;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.event.EventListener;

@Log4j2
@Named
@RequiredArgsConstructor
public class GrpcHealthIndicator implements HealthIndicator {

    private final AtomicReference<Status> status = new AtomicReference<>(Status.UNKNOWN);

    @Override
    public Health health() {
        return Health.status(status.get()).build();
    }

    @EventListener
    public void onStart(GrpcServerStartedEvent event) {
        log.info("Started gRPC server on {}:{}", event.getAddress(), event.getPort());
        status.set(Status.UP);
    }

    @EventListener
    public void onStop(GrpcServerShutdownEvent event) {
        log.info("Stopping gRPC server");
        status.set(Status.OUT_OF_SERVICE);
    }

    @EventListener
    public void onTermination(GrpcServerTerminatedEvent event) {
        log.info("Stopped gRPC server");
        status.set(Status.DOWN);
    }
}
