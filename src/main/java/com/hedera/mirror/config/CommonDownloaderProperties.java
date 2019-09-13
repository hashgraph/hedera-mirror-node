package com.hedera.mirror.config;

public interface CommonDownloaderProperties {
    int getMaxThreads();
    int getTaskQueueSize();
    int getBatchSize();
    int getCoreThreads();
}
