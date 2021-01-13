package com.hedera.mirror.importer;

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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.experimental.UtilityClass;

import com.hedera.mirror.importer.domain.DigestAlgorithm;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.util.Utility;

@UtilityClass
public class TestUtils {

    @Getter
    private final Map<String, RecordFile> recordFilesMap = createRecordFilesMap();

    public AccountID toAccountId(String accountId) {
        var parts = accountId.split("\\.");
        return AccountID.newBuilder().setShardNum(Long.parseLong(parts[0])).setRealmNum(Long.parseLong(parts[1]))
                .setAccountNum(Long.parseLong(parts[2])).build();
    }

    public TransactionID toTransactionId(String transactionId) {
        var parts = transactionId.split("-");
        return TransactionID.newBuilder().setAccountID(toAccountId(parts[0]))
                .setTransactionValidStart(toTimestamp(Long.valueOf(parts[1]))).build();
    }

    public Timestamp toTimestamp(Long nanosecondsSinceEpoch) {
        if (nanosecondsSinceEpoch == null) {
            return null;
        }
        return Utility.instantToTimestamp(Instant.ofEpochSecond(0, nanosecondsSinceEpoch));
    }

    public Timestamp toTimestamp(long seconds, long nanoseconds) {
        return Timestamp.newBuilder().setSeconds(seconds).setNanos((int) nanoseconds).build();
    }

    public byte[] toByteArray(Key key) {
        return (null == key) ? null : key.toByteArray();
    }

    private Map<String, RecordFile> createRecordFilesMap() {
        DigestAlgorithm digestAlgorithm = DigestAlgorithm.SHA384;

        RecordFile recordFileV1_1 = RecordFile.builder()
                .consensusStart(1561990380317763000L)
                .consensusEnd(1561990399074934000L)
                .count(15L)
                .digestAlgorithm(digestAlgorithm)
                .fileHash("333d6940254659533fd6b939033e59c57fe8f4ff78375d1e687c032918aa0b7b8179c7fd403754274a8c91e0b6c0195a")
                .name("2019-07-01T14:13:00.317763Z.rcd")
                .previousHash("f423447a3d5a531a07426070e511555283daae063706242590949116f717a0524e4dd18f9d64e66c73982d475401db04")
                .version(1)
                .build();
        RecordFile recordFileV1_2 = RecordFile.builder()
                .consensusStart(1561991340302068000L)
                .consensusEnd(1561991353226225001L)
                .count(69L)
                .digestAlgorithm(digestAlgorithm)
                .fileHash("1faf198f8fdbefa59bde191f214d73acdc4f5c0f434677a7edf9591b129e21aea90a5b3119d2802cee522e7be6bc8830")
                .name("2019-07-01T14:29:00.302068Z.rcd")
                .previousHash(recordFileV1_1.getFileHash())
                .version(1)
                .build();
        RecordFile recordFileV2_1 = RecordFile.builder()
                .consensusStart(1567188600419072000L)
                .consensusEnd(1567188604906443001L)
                .count(19L)
                .digestAlgorithm(digestAlgorithm)
                .fileHash("591558e059bd1629ee386c4e35a6875b4c67a096718f5d225772a651042715189414df7db5588495efb2a85dc4a0ffda")
                .name("2019-08-30T18_10_00.419072Z.rcd")
                .previousHash(Utility.EMPTY_HASH)
                .version(2)
                .build();
        RecordFile recordFileV2_2 = RecordFile.builder()
                .consensusStart(1567188605249678000L)
                .consensusEnd(1567188609705382001L)
                .count(15L)
                .digestAlgorithm(digestAlgorithm)
                .fileHash("5ed51baeff204eb6a2a68b76bbaadcb9b6e7074676c1746b99681d075bef009e8d57699baaa6342feec4e83726582d36")
                .name("2019-08-30T18_10_05.249678Z.rcd")
                .previousHash(recordFileV2_1.getFileHash())
                .version(2)
                .build();
        RecordFile recordFileV5_1 = RecordFile.builder()
                .consensusStart(1609254295576899000L)
                .consensusEnd(1609254299991811050L)
                .count(244L)
                .digestAlgorithm(digestAlgorithm)
                .fileHash("e074430a185ec74d4bc13478d364b71c174e2bae8f33669ac617004c541c2182242290a4fb5782e47408405cfcf23f5d")
                .hash("719c0fdb673bd87e9ed1e6006a908e573d20ee0eb01fdeb811fc385d9ea9eb4d234cf3314126979e949e7352bf940b5a")
                .hapiVersionMajor(0)
                .hapiVersionMinor(9)
                .hapiVersionPatch(0)
                .metadataHash("34c3f643e57beea0ec78a8d39528ebcbe4fae7ecec4d021e3b19b2165c6a259d8879d66c28ad4c65694400159ae87ad0")
                .name("2020-12-29T15_04_55.576899000Z.rcd")
                .previousHash(Utility.EMPTY_HASH)
                .version(5)
                .build();
        RecordFile recordFileV5_2 = RecordFile.builder()
                .consensusStart(1609254300243029005L)
                .consensusEnd(1609254304995886047L)
                .count(4501L)
                .digestAlgorithm(digestAlgorithm)
                .fileHash("2abf8a264ea103e02b49ffb4d3858361751006553bbd6a2d832756679e0afb6006dab2d7f69005961457749927168cd9")
                .hash("2695d43a749bdf7b972ae2da3cb1ab17cf2bc33446442f73ef675c606821341babee78bcf9ca0d2ea7cbdad458f48e98")
                .hapiVersionMajor(0)
                .hapiVersionMinor(9)
                .hapiVersionPatch(0)
                .metadataHash("a430ba7f3532a53ffaf3e85ff13ab2cf97d346b37c4083b7dff0e9aa4504ae5d852fc7ab0b1e727af60bbc20508b06b1")
                .name("2020-12-29T15_05_00.243029005Z.rcd")
                .previousHash(recordFileV5_1.getHash())
                .version(5)
                .build();
        List<RecordFile> allFiles = List.of(recordFileV1_1, recordFileV1_2,
                recordFileV2_1, recordFileV2_2,
                recordFileV5_1, recordFileV5_2);
        return Collections.unmodifiableMap(allFiles.stream().collect(Collectors.toMap(RecordFile::getName, rf -> rf)));
    }

    public byte[] generateRandomByteArray(int size) {
        byte[] hashBytes = new byte[size];
        new SecureRandom().nextBytes(hashBytes);
        return hashBytes;
    }
}
