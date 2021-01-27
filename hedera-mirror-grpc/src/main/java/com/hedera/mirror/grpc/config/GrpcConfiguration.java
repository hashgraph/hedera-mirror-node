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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.services.HealthStatusManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import net.devh.boot.grpc.server.service.GrpcServiceDefinition;
import net.devh.boot.grpc.server.service.GrpcServiceDiscoverer;
import org.springframework.boot.actuate.health.CompositeHealthContributor;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hedera.mirror.grpc.GrpcProperties;

@Configuration
public class GrpcConfiguration {

    @Bean
    CompositeHealthContributor grpcServices(GrpcServiceDiscoverer grpcServiceDiscoverer,
                                            HealthStatusManager healthStatusManager) {

        Map<String, HealthIndicator> healthIndicators = new LinkedHashMap<>();

        for (GrpcServiceDefinition grpcService : grpcServiceDiscoverer.findGrpcServices()) {
            String serviceName = grpcService.getDefinition().getServiceDescriptor().getName();
            healthIndicators.put(serviceName, new GrpcHealthIndicator(healthStatusManager, serviceName));
        }

        return CompositeHealthContributor.fromMap(healthIndicators);
    }

    @Bean
    public GrpcServerConfigurer grpcServerConfigurer(GrpcProperties grpcProperties) {
        NettyProperties nettyProperties = grpcProperties.getNetty();
        Executor executor = new ThreadPoolExecutor(
                nettyProperties.getExecutorCoreThreadCount(),
                nettyProperties.getExecutorMaxThreadCount(),
                nettyProperties.getThreadKeepAliveTime().toSeconds(),
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new ThreadFactoryBuilder()
                        .setDaemon(true)
                        .setNameFormat("grpc-executor-%d")
                        .build());

        return serverBuilder -> ((NettyServerBuilder) serverBuilder)
                .executor(executor)
                .maxConnectionIdle(nettyProperties.getMaxConnectionIdle().toSeconds(), TimeUnit.SECONDS)
                .maxConcurrentCallsPerConnection(nettyProperties.getMaxConcurrentCallsPerConnection())
                .maxInboundMessageSize(nettyProperties.getMaxInboundMessageSize())
                .maxInboundMetadataSize(nettyProperties.getMaxInboundMetadataSize());
    }
}
