package com.hedera.mirror.importer.reader.record;

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

import static java.lang.String.format;
import static org.apache.commons.io.output.NullOutputStream.NULL_OUTPUT_STREAM;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.util.Version;
import reactor.core.publisher.Flux;

import com.hedera.mirror.common.domain.DigestAlgorithm;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.exception.StreamFileReaderException;
import com.hedera.services.stream.proto.HashAlgorithm;
import com.hedera.services.stream.proto.RecordStreamFile;

@Log4j2
@Named
public class ProtoRecordFileReader implements RecordFileReader {

    public static final int VERSION = 6;

    @Override
    public RecordFile read(StreamFileData streamFileData) {
        var filename = streamFileData.getFilename();
        var loadStart = Instant.now().getEpochSecond();

        try (var inputStream = streamFileData.getInputStream()) {
            var recordStreamFile = readRecordStreamFile(filename, inputStream);
            var startObjectRunningHash = recordStreamFile.getStartObjectRunningHash();
            var endObjectRunningHash = recordStreamFile.getEndObjectRunningHash();
            var startHashAlgorithm = startObjectRunningHash.getAlgorithm();
            var endHashAlgorithm = endObjectRunningHash.getAlgorithm();
            if (!startHashAlgorithm.equals(endHashAlgorithm)) {
                log.warn("{} has mismatch start object running hash algorithm {} and end object running" +
                        "hash algorithm {}", filename, startHashAlgorithm, endHashAlgorithm);
            }

            var items = readItems(filename, recordStreamFile);
            int count = items.size();
            var digestAlgorithm = getDigestAlgorithm(filename, startHashAlgorithm, endHashAlgorithm);
            var hapiProtoVersion = recordStreamFile.getHapiProtoVersion();

            var bytes = streamFileData.getBytes();
            return RecordFile.builder()
                    .bytes(bytes)
                    .consensusStart(items.get(0).getConsensusTimestamp())
                    .consensusEnd(items.get(count - 1).getConsensusTimestamp())
                    .count((long) count)
                    .digestAlgorithm(digestAlgorithm)
                    .fileHash(getFileHash(digestAlgorithm, streamFileData.getDecompressedBytes()))
                    .hapiVersionMajor(hapiProtoVersion.getMajor())
                    .hapiVersionMinor(hapiProtoVersion.getMinor())
                    .hapiVersionPatch(hapiProtoVersion.getPatch())
                    .hash(DomainUtils.bytesToHex(DomainUtils.getHashBytes(endObjectRunningHash)))
                    .index(recordStreamFile.getBlockNumber())
                    .items(Flux.fromIterable(items))
                    .loadStart(loadStart)
                    .metadataHash(getMetadataHash(digestAlgorithm, recordStreamFile))
                    .name(filename)
                    .previousHash(DomainUtils.bytesToHex(DomainUtils.getHashBytes(startObjectRunningHash)))
                    .size(bytes.length)
                    .version(VERSION)
                    .build();
        } catch (IOException e) {
            throw new InvalidStreamFileException("Error reading record file " + filename, e);
        }
    }

    private MessageDigest createMessageDigest(DigestAlgorithm digestAlgorithm) {
        try {
            return MessageDigest.getInstance(digestAlgorithm.getName());
        } catch (NoSuchAlgorithmException e) {
            throw new StreamFileReaderException(e);
        }
    }

    private DigestAlgorithm getDigestAlgorithm(String filename, HashAlgorithm start, HashAlgorithm end) {
        return Stream.of(start, end)
                .map(hashAlgorithm -> {
                    try {
                        return DigestAlgorithm.valueOf(hashAlgorithm.toString());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> {
                    var message = format("%s has unsupported start running object hash algorithm %s and " +
                            "end running object hash algorithm %s", filename, start, end);
                    return new InvalidStreamFileException(message);
                });
    }

    private String getFileHash(DigestAlgorithm algorithm, byte[] fileData) {
        var messageDigest = createMessageDigest(algorithm);
        return DomainUtils.bytesToHex(messageDigest.digest(fileData));
    }

    private String getMetadataHash(DigestAlgorithm algorithm, RecordStreamFile recordStreamFile) throws IOException {
        try (var digestOutputStream = new DigestOutputStream(NULL_OUTPUT_STREAM, createMessageDigest(algorithm));
             var dataOutputStream = new DataOutputStream(digestOutputStream)) {
            var hapiProtoVersion = recordStreamFile.getHapiProtoVersion();
            dataOutputStream.writeInt(VERSION);
            dataOutputStream.writeInt(hapiProtoVersion.getMajor());
            dataOutputStream.writeInt(hapiProtoVersion.getMinor());
            dataOutputStream.writeInt(hapiProtoVersion.getPatch());
            dataOutputStream.write(DomainUtils.getHashBytes(recordStreamFile.getStartObjectRunningHash()));
            dataOutputStream.write(DomainUtils.getHashBytes(recordStreamFile.getEndObjectRunningHash()));
            dataOutputStream.writeLong(recordStreamFile.getBlockNumber());

            return DomainUtils.bytesToHex(digestOutputStream.getMessageDigest().digest());
        }
    }

    private List<RecordItem> readItems(String filename, RecordStreamFile recordStreamFile) {
        int count = recordStreamFile.getRecordStreamItemsCount();
        if (count == 0) {
            throw new InvalidStreamFileException("No record stream objects in record file " + filename);
        }

        var hapiProtoVersion = recordStreamFile.getHapiProtoVersion();
        var hapiVersion = new Version(hapiProtoVersion.getMajor(), hapiProtoVersion.getMinor(),
                hapiProtoVersion.getPatch());
        var items = new ArrayList<RecordItem>(count);
        RecordItem previousItem = null;
        for (var recordStreamItem : recordStreamFile.getRecordStreamItemsList()) {
            var recordItem = RecordItem.builder()
                    .hapiVersion(hapiVersion)
                    .previous(previousItem)
                    .recordBytes(recordStreamItem.getRecord().toByteArray())
                    .transactionBytes(recordStreamItem.getTransaction().toByteArray())
                    .transactionIndex(items.size())
                    .build();
            items.add(recordItem);
            previousItem = recordItem;
        }

        return items;
    }

    private RecordStreamFile readRecordStreamFile(String filename, InputStream inputStream) throws IOException {
        try (var dataInputStream = new DataInputStream(inputStream)) {
            int version = dataInputStream.readInt();
            if (version != VERSION) {
                throw new InvalidStreamFileException(format("Expected file %s with version %d, got %d.", filename,
                        VERSION, version));
            }

            return RecordStreamFile.parseFrom(dataInputStream);
        }
    }
}
