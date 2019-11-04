package com.hedera.utilities;

import lombok.extern.log4j.Log4j2;
import javax.inject.Named;

/**
 * ShutdownHelper helps in shutting down the threads cleanly when JVM is doing down.
 *
 * At some point when the mirror node process is going down, 'stopping' flag will be set to true. Since that point, all
 * threads will have fixed time (say 5 seconds), to stop gracefully. Therefore, long living and heavy lifting
 * threads should regularly probe for this flag.
 */
@Log4j2
@Named
public class ShutdownHelper {

    private static volatile boolean stopping;

    public ShutdownHelper() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::onExit));
    }

    public static boolean isStopping() {
        if (stopping) {
            log.info("Shutting down");
        }
        return stopping;
    }

    private void onExit() {
        stopping = true;
        log.info("Shutting down.......waiting 10s for internal processes to stop.");
        try {
            Thread.sleep(10 * 1000);
        } catch (InterruptedException e) {
            log.error("Interrupted when waiting for shutdown...", e);;
        }
    }
}
