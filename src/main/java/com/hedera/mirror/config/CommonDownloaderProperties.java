package com.hedera.mirror.config;

public interface CommonDownloaderProperties {
    int getMaxThreads();
    int getTaskQueueSize();
    int getListObjectsMaxKeys();
    int getMaxDownloadItems();
    int getCoreThreads();
}
