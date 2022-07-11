package com.hedera.mirror.importer.downloader.record;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import com.google.common.collect.ArrayListMultimap;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.inject.Named;
import lombok.Value;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.SidecarFile;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.addressbook.AddressBookService;
import com.hedera.mirror.importer.config.MirrorDateRangePropertiesProcessor;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.domain.StreamFilename;
import com.hedera.mirror.importer.downloader.Downloader;
import com.hedera.mirror.importer.downloader.NodeSignatureVerifier;
import com.hedera.mirror.importer.downloader.PendingDownload;
import com.hedera.mirror.importer.downloader.StreamFileNotifier;
import com.hedera.mirror.importer.exception.FileOperationException;
import com.hedera.mirror.importer.exception.HashMismatchException;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.leader.Leader;
import com.hedera.mirror.importer.parser.record.sidecar.SidecarProperties;
import com.hedera.mirror.importer.reader.record.ProtoRecordFileReader;
import com.hedera.mirror.importer.reader.record.RecordFileReader;
import com.hedera.mirror.importer.reader.record.sidecar.SidecarFileReader;
import com.hedera.mirror.importer.reader.signature.SignatureFileReader;
import com.hedera.services.stream.proto.SidecarType;
import com.hedera.services.stream.proto.TransactionSidecarRecord;

@Named
public class RecordFileDownloader extends Downloader<RecordFile> {

    private static final String SIDECAR_FOLDER = "sidecar";

    private final SidecarFileReader sidecarFileReader;

    private final SidecarProperties sidecarProperties;

    public RecordFileDownloader(AddressBookService addressBookService, RecordDownloaderProperties downloaderProperties,
                                MeterRegistry meterRegistry,
                                MirrorDateRangePropertiesProcessor mirrorDateRangePropertiesProcessor,
                                NodeSignatureVerifier nodeSignatureVerifier, S3AsyncClient s3Client,
                                SidecarProperties sidecarProperties, SignatureFileReader signatureFileReader,
                                RecordFileReader recordFileReader, SidecarFileReader sidecarFileReader,
                                StreamFileNotifier streamFileNotifier) {
        super(addressBookService, downloaderProperties, meterRegistry, mirrorDateRangePropertiesProcessor,
                nodeSignatureVerifier, s3Client, signatureFileReader, recordFileReader, streamFileNotifier);
        this.sidecarFileReader = sidecarFileReader;
        this.sidecarProperties = sidecarProperties;
    }

    @Override
    @Leader
    @Scheduled(fixedDelayString = "#{@recordDownloaderProperties.getFrequency().toMillis()}")
    public void download() {
        downloadNextBatch();
    }

    @Override
    protected void onVerified(PendingDownload pendingDownload,
                              RecordFile recordFile) throws ExecutionException, InterruptedException {
        downloadAndReadSidecars(recordFile);
        super.onVerified(pendingDownload, recordFile);
    }

    @Override
    protected void setStreamFileIndex(RecordFile recordFile) {
        // Starting from the record stream file v6, the record file index is externalized as the block_number field of
        // the protobuf RecordStreamFile, so only set the record file index to be last + 1 if it's pre-v6.
        if (recordFile.getVersion() < ProtoRecordFileReader.VERSION) {
            super.setStreamFileIndex(recordFile);
        }
    }

    private void downloadAndReadSidecars(RecordFile recordFile) throws InterruptedException, ExecutionException {
        if (!sidecarProperties.isEnabled() || recordFile.getSidecars().isEmpty()) {
            return;
        }

        var acceptedTypes = sidecarProperties.getTypes().stream().map(Enum::ordinal).collect(Collectors.toSet());
        var pendingSidecarDownloads = new ArrayList<PendingSidecarDownload>();
        var s3Prefix = getSidecarS3Prefix(recordFile.getNodeAccountId().toString());

        // First pass, create sidecar pending downloads to download the files async
        for (var sidecar : recordFile.getSidecars()) {
            if (sidecar.getTypes().stream().noneMatch(acceptedTypes::contains)) {
                log.info("Skipping sidecar file {} based on the sidecar type filter", sidecar.getName());
                continue;
            }

            var pendingDownload = pendingDownload(new StreamFilename(sidecar.getName()), s3Prefix);
            pendingSidecarDownloads.add(new PendingSidecarDownload(pendingDownload, sidecar));
        }

        // Second pass, read the downloaded sidecar files
        ArrayListMultimap<Long, TransactionSidecarRecord> records = ArrayListMultimap.create();
        for (var pendingSidecarDownload : pendingSidecarDownloads) {
            var pendingDownload = pendingSidecarDownload.getPendingDownload();
            var sidecar = pendingSidecarDownload.getSidecarFile();
            if (!pendingDownload.waitForCompletion()) {
                throw new FileOperationException("Failed to download sidecar file " + sidecar.getName());
            }

            var streamFileData = new StreamFileData(pendingDownload.getStreamFilename(), pendingDownload.getBytes());
            sidecarFileReader.read(sidecar, streamFileData);
            if (!Arrays.equals(sidecar.getHash(), sidecar.getActualHash())) {
                throw new HashMismatchException(sidecar.getName(), sidecar.getHash(), sidecar.getActualHash(),
                        sidecar.getHashAlgorithm().getName());
            }

            if (!downloaderProperties.isPersistBytes()) {
                sidecar.setBytes(null);
            }

            for (var record : sidecar.getRecords()) {
                int type = getSidecarType(record);
                if (acceptedTypes.contains(type)) {
                    records.put(DomainUtils.timestampInNanosMax(record.getConsensusTimestamp()), record);
                }
            }
        }

        recordFile.getItems()
                .doOnNext(recordItem -> recordItem.setSidecarRecords(records.get(recordItem.getConsensusTimestamp())))
                .blockLast();
    }

    private String getSidecarS3Prefix(String nodeAccountId) {
        return getS3Prefix(nodeAccountId) + SIDECAR_FOLDER + "/";
    }

    private int getSidecarType(TransactionSidecarRecord record) {
        return switch (record.getSidecarRecordsCase()) {
            case ACTIONS -> SidecarType.CONTRACT_ACTION_VALUE;
            case BYTECODE -> SidecarType.CONTRACT_BYTECODE_VALUE;
            case STATE_CHANGES -> SidecarType.CONTRACT_STATE_CHANGE_VALUE;
            default -> throw new InvalidDatasetException(
                    "Unknown sidecar transaction record type " + record.getSidecarRecordsCase());
        };
    }

    @Value
    private static class PendingSidecarDownload {
        private PendingDownload pendingDownload;
        private SidecarFile sidecarFile;
    }
}
