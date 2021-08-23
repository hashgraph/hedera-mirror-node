package com.hedera.mirror.importer.parser;

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

import java.sql.Connection;
import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

@Log4j2
@UtilityClass
public class DbConnectionUtils {

    public Future<Void> scheduleAbort(Connection connection, ScheduledExecutorService executor, Duration timeout) {
        return executor.schedule(() -> {
            log.warn("Attempt to abort the db connection upon timeout in {}", timeout);
            connection.abort(executor);
            return null;
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void cancelAbortFuture(Future<Void> abortFuture) {
        if (abortFuture != null) {
            abortFuture.cancel(true);
        }
    }
}
