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

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import com.hedera.mirror.importer.util.ShutdownHelper;

@RequiredArgsConstructor
public abstract class FileWatcher {

    protected final Logger log = LogManager.getLogger(getClass());
    protected final ParserProperties parserProperties;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void watch() {
        Path path = parserProperties.getValidPath();

        if (!parserProperties.isEnabled()) {
            log.info("Skip watching directory: {}", path);
            return;
        }

        // Invoke on startup to check for any changed files while this process was down.
        parse();

        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            log.info("Watching directory for changes: {}", path);
            WatchKey rootKey = path
                    .register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
            boolean valid = rootKey.isValid();

            while (valid && parserProperties.isEnabled()) {
                WatchKey key = watcher.poll(100, TimeUnit.MILLISECONDS);

                if (ShutdownHelper.isStopping()) {
                    return;
                }

                if (key == null) {
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        log.error("File watching events may have been lost or discarded");
                        continue;
                    }

                    parse();
                }

                valid = key.reset();
            }
        } catch (InterruptedException e) {
            log.info("Watch thread halted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error starting watch service", e);
        }
    }

    public abstract void parse();
}
