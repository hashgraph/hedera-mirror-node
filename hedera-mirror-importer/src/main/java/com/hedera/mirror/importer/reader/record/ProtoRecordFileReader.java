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

import com.hederahashgraph.api.proto.java.SemanticVersion;
import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import javax.inject.Named;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.output.NullOutputStream;
import reactor.core.publisher.Flux;

import com.hedera.mirror.common.domain.DigestAlgorithm;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.exception.StreamFileReaderException;
import com.hedera.services.stream.proto.RecordStreamFile;

@Named
public class ProtoRecordFileReader implements RecordFileReader {

    private static final DigestAlgorithm DIGEST_ALGORITHM = DigestAlgorithm.SHA384;

    private static final int VERSION = 6;

    @Override
    public RecordFile read(StreamFileData streamFileData) {
        try {
            var messageDigestFile = createMessageDigest(DIGEST_ALGORITHM);

            var recordStreamFile = readRecordStreamFile(streamFileData.getInputStream(), messageDigestFile);

            var recordFile = RecordFile.builder()
                    .bytes(streamFileData.getBytes())
                    .digestAlgorithm(DIGEST_ALGORITHM)
                    .loadStart(Instant.now().getEpochSecond())
                    .name(streamFileData.getFilename())
                    .build();

            readHeader(recordFile, recordStreamFile);
            readBody(recordFile, recordStreamFile);

            recordFile.setFileHash(Hex.encodeHexString(messageDigestFile.digest()));
            MessageDigest messageDigestMetadata = computeMetadataHash(DIGEST_ALGORITHM, recordStreamFile);
            recordFile.setMetadataHash(Hex.encodeHexString(messageDigestMetadata.digest()));

            return recordFile;
        } catch (IOException e) {
            throw new StreamFileReaderException("Error reading record file " + streamFileData.getFilename(), e);
        }
    }

    private RecordStreamFile readRecordStreamFile(InputStream inputStream, MessageDigest messageDigestFile) {
        try (var digestInputStream = new DigestInputStream(inputStream, messageDigestFile);
             var bufferedInputStream = new BufferedInputStream(digestInputStream)) {
            int recordFileVersion = ByteBuffer.wrap(digestInputStream.readNBytes(4)).getInt();
            if (recordFileVersion != VERSION) {
                throw new InvalidStreamFileException(
                        format("Expected file with version %d, given %d.", VERSION, recordFileVersion));
            }

            return RecordStreamFile.parseFrom(bufferedInputStream);
        } catch (IOException e) {
            throw new InvalidStreamFileException(e);
        }
    }

    private void readHeader(RecordFile recordFile, RecordStreamFile recordStreamFile) {
        SemanticVersion hapiProtoVersion = recordStreamFile.getHapiProtoVersion();

        recordFile.setHapiVersionMajor(hapiProtoVersion.getMajor());
        recordFile.setHapiVersionMinor(hapiProtoVersion.getMinor());
        recordFile.setHapiVersionPatch(hapiProtoVersion.getPatch());
        recordFile.setVersion(VERSION);
    }

    private void readBody(RecordFile recordFile, RecordStreamFile recordStreamFile) {
        if(recordStreamFile.getRecordStreamItemsCount() == 0){
            throw new InvalidStreamFileException("No record stream objects in record file " + recordFile.getName());
        }

        var items = new ArrayList<RecordItem>(recordStreamFile.getRecordStreamItemsCount());
        RecordItem previousItem = null;
        int itemCounter = 0;
        for(var recordStreamItem : recordStreamFile.getRecordStreamItemsList()){
            var recordItem = RecordItem.builder()
                    .hapiVersion(recordFile.getHapiVersion())
                    .previous(previousItem)
                    .recordBytes(recordStreamItem.getRecord().toByteArray())
                    .transactionIndex(itemCounter++)
                    .transactionBytes(recordStreamItem.getTransaction().toByteArray())
                    .build();
            items.add(recordItem);
            previousItem = recordItem;
        }

        recordFile.setCount((long)itemCounter);
        recordFile.setItems(Flux.fromIterable(items));

        recordFile.setConsensusStart(items.get(0).getConsensusTimestamp());
        recordFile.setConsensusEnd(previousItem.getConsensusTimestamp());

        recordFile.setPreviousHash(Hex.encodeHexString(recordStreamFile.getStartObjectRunningHash().getHash().toByteArray()));
        recordFile.setHash(Hex.encodeHexString(recordStreamFile.getEndObjectRunningHash().getHash().toByteArray()));
    }

    private MessageDigest computeMetadataHash(DigestAlgorithm algorithm, RecordStreamFile recordStreamFile)
            throws IOException {
        var digestOutputStream = new DigestOutputStream(NullOutputStream.NULL_OUTPUT_STREAM,
                createMessageDigest(algorithm));
        var dataOutputStream = new DataOutputStream(digestOutputStream);

        var hapiProtoVersion = recordStreamFile.getHapiProtoVersion();
        dataOutputStream.writeInt(VERSION);
        dataOutputStream.writeInt(hapiProtoVersion.getMajor());
        dataOutputStream.writeInt(hapiProtoVersion.getMinor());
        dataOutputStream.writeInt(hapiProtoVersion.getPatch());

        dataOutputStream.write(recordStreamFile.getStartObjectRunningHash().getHash().toByteArray());
        dataOutputStream.write(recordStreamFile.getEndObjectRunningHash().getHash().toByteArray());

        dataOutputStream.writeLong(recordStreamFile.getBlockNumber());

        dataOutputStream.flush();
        dataOutputStream.close();

        return digestOutputStream.getMessageDigest();
    }

    private MessageDigest createMessageDigest(DigestAlgorithm digestAlgorithm) {
        try {
            return MessageDigest.getInstance(digestAlgorithm.getName());
        } catch (NoSuchAlgorithmException e) {
            throw new StreamFileReaderException(e);
        }
    }
}
