package com.hedera.mirror.importer.leader;
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

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.log4j.Log4j2;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.integration.leader.event.OnGrantedEvent;
import org.springframework.integration.leader.event.OnRevokedEvent;

/**
 * This class uses Spring Cloud Kubernetes Leader to atomically elect a leader using Kubernetes primitives. This class
 * tracks those leader events and either allows or disallows the execution of a method annotated with @Leader based upon
 * whether this pod is currently leader or not.
 */
@Aspect
@Log4j2
@Order(1)
public class LeaderAspect {

    private final AtomicBoolean leader = new AtomicBoolean(false);

    public LeaderAspect() {
        log.info("Starting as follower");
    }

    @Around("execution(@com.hedera.mirror.importer.leader.Leader * *(..)) && @annotation(leaderAnnotation)")
    public Object leader(ProceedingJoinPoint joinPoint, Leader leaderAnnotation) throws Throwable {
        String targetClass = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        log.trace("Verifying leadership status before invoking");

        if (!leader.get()) {
            log.debug("Not the leader. Skipping invocation of {}.{}()", targetClass, methodName);
            return null;
        }

        log.debug("Currently the leader, proceeding to invoke: {}.{}()", targetClass, methodName);
        return joinPoint.proceed();
    }

    @EventListener
    public void granted(OnGrantedEvent event) {
        if (leader.compareAndSet(false, true)) {
            log.info("Transitioned to leader: {}", event);
        }
    }

    @EventListener
    public void revoked(OnRevokedEvent event) {
        if (leader.compareAndSet(true, false)) {
            log.info("Transitioned to follower: {}", event);
        }
    }
}
