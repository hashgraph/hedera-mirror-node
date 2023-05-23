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

package com.hedera.mirror.importer.util;

import jakarta.inject.Named;
import lombok.extern.log4j.Log4j2;

/**
 * ShutdownHelper helps in shutting down the threads cleanly when JVM is doing down.
 * <p>
 * At some point when the mirror node process is going down, 'stopping' flag will be set to true. Since that point, all
 * threads will have fixed time (say 5 seconds), to stop gracefully. Therefore, long living and heavy lifting threads
 * should regularly probe for this flag.
 */
@Log4j2
@Named
public class ShutdownHelper {

    private static volatile boolean stopping;

    private ShutdownHelper() {
        Runtime.getRuntime().addShutdownHook(new Thread(ShutdownHelper::onExit));
    }

    public static boolean isStopping() {
        return stopping;
    }

    private static void onExit() {
        stopping = true;
        log.info("Shutting down.......waiting 10s for internal processes to stop.");
        try {
            Thread.sleep(10L * 1000L);
        } catch (InterruptedException e) {
            log.warn("Interrupted when waiting for shutdown...", e);
            Thread.currentThread().interrupt();
        }
    }
}
