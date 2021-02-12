package com.hedera.mirror.importer.downloader.record;

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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import javax.inject.Named;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.domain.StreamFile;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.downloader.NodeSignatureVerifier;
import com.hedera.mirror.importer.downloader.StreamFileNotifier;
import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.reader.record.RecordFileReader;
import com.hedera.mirror.importer.reader.signature.SignatureFileReader;
import com.hedera.mirror.importer.repository.RecordFileRepository;

@Named
public class RecordFileDownloader extends Downloader<RecordFile> {

    private final Timer downloadLatencyMetric;
    private final Timer streamCloseMetric;

    public RecordFileDownloader(
            S3AsyncClient s3Client, AddressBookService addressBookService,
            RecordDownloaderProperties downloaderProperties,
            MeterRegistry meterRegistry, NodeSignatureVerifier nodeSignatureVerifier,
            SignatureFileReader signatureFileReader, RecordFileReader recordFileReader,
            StreamFileNotifier streamFileNotifier, RecordFileRepository recordFileRepository,
            MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor) {
        super(s3Client, addressBookService, downloaderProperties, meterRegistry,
                nodeSignatureVerifier, signatureFileReader, recordFileReader, streamFileNotifier, recordFileRepository,
                mirrorDateRangePropertiesProcessor);

        downloadLatencyMetric = Timer.builder("hedera.mirror.download.latency")
                .description("The difference in ms between the consensus time of the last transaction in the file " +
                        "and the time at which the file was downloaded and verified")
                .tag("type", downloaderProperties.getStreamType().toString())
                .register(meterRegistry);

        streamCloseMetric = Timer.builder("hedera.mirror.stream.close.latency")
                .description("The difference between the consensus time of the last and first transaction in the " +
                        "stream file")
                .tag("type", downloaderProperties.getStreamType().toString())
                .register(meterRegistry);
    }

    @Override
    @Leader
    @Scheduled(fixedDelayString = "${hedera.mirror.importer.downloader.record.frequency:500}")
    public void download() {
        downloadNextBatch();
    }

    @Override
    protected void onVerified(StreamFile streamFile) {
        RecordFile recordFile = (RecordFile) streamFile;

        Instant consensusEnd = Instant.ofEpochSecond(0, recordFile.getConsensusEnd());
        downloadLatencyMetric.record(Duration.between(consensusEnd, Instant.now()));

        long streamClose = recordFile.getConsensusEnd() - recordFile.getConsensusStart();
        streamCloseMetric.record(streamClose, TimeUnit.NANOSECONDS);
    }
}
