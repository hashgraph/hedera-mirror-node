package com.hedera.mirror.downloader;

import com.hedera.mirror.downloader.balance.AccountBalancesDownloader;
import com.hedera.mirror.downloader.event.EventStreamFileDownloader;

import com.hedera.mirror.downloader.record.RecordFileDownloader;

import javax.annotation.PostConstruct;
import javax.inject.Named;

// This pattern to kick off downloaders using separate initializer class is based on example here:
// https://docs.spring.io/spring/docs/5.2.x/spring-framework-reference/integration.html#scheduling-annotation-support-async
@Named
public class DownloaderInitializer {
    private final AccountBalancesDownloader accountBalancesDownloader;
    private final RecordFileDownloader recordFileDownloader;
    private final EventStreamFileDownloader eventStreamFileDownloader;

    public DownloaderInitializer(AccountBalancesDownloader accountBalancesDownloader,
                                 RecordFileDownloader recordFileDownloader,
                                 EventStreamFileDownloader eventStreamFileDownloader) {
        this.accountBalancesDownloader = accountBalancesDownloader;
        this.recordFileDownloader = recordFileDownloader;
        this.eventStreamFileDownloader = eventStreamFileDownloader;
    }

    @PostConstruct
    public void initialize() {
        accountBalancesDownloader.run();
        recordFileDownloader.run();
        eventStreamFileDownloader.run();
    }
}
