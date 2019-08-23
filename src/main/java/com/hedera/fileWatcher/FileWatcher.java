package com.hedera.fileWatcher;

import com.hedera.utilities.Utility;
import org.apache.logging.log4j.*;

import java.io.File;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public abstract class FileWatcher {
    protected final Logger log = LogManager.getLogger(getClass());
    private final File pathToWatch;

    public FileWatcher(File pathToWatch) {
        this.pathToWatch = pathToWatch;
        if (!this.pathToWatch.exists()) {
            this.pathToWatch.mkdirs();
        }
    }

    public void watch() {
        // Invoke on startup to check for any changed files while this process was down.
        onCreate();

        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            log.info("Watching directory for changes: {}", pathToWatch.getAbsoluteFile());
            Path path = pathToWatch.toPath();
            WatchKey rootKey = path.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
            boolean valid = rootKey.isValid();

            while (valid) {
                WatchKey key;
                try {
                    key = watcher.poll(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    continue;
                }

                if (Utility.checkStopFile()) {
                    log.info("Stop file found, stopping.");
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

                    onCreate();
                }

                valid = key.reset();
            }
        } catch (Exception e) {
            log.error("Error starting watch service", e);
        }
    }

    public abstract void onCreate();
}