/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.config;

import jakarta.inject.Named;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.api.callback.BaseCallback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

@Named
@RequiredArgsConstructor
public class MigrationHealthIndicator extends BaseCallback implements HealthIndicator {

    private static final Health DOWN = Health.down().build();
    private static final Health UP = Health.up().build();

    private final AtomicBoolean complete = new AtomicBoolean(false);

    @Override
    public Health health() {
        return complete.get() ? UP : DOWN;
    }

    @Override
    public void handle(Event event, Context context) {
        complete.set(true);
    }

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.AFTER_MIGRATE;
    }
}
