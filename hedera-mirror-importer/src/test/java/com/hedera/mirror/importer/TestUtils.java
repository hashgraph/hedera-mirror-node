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
import lombok.experimental.UtilityClass;

import com.hedera.mirror.importer.domain.DigestAlgorithm;
import com.hedera.mirror.importer.domain.RecordFile;
import com.hedera.mirror.importer.util.Utility;

@UtilityClass
public class TestUtils {

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

    public Map<String, RecordFile> getRecordFilesMap() {
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
                .previousHash(digestAlgorithm.getEmptyHash())
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
                .consensusStart(1610402964063739000L)
                .consensusEnd(1610402964063739000L)
                .count(1L)
                .digestAlgorithm(digestAlgorithm)
                .endRunningHash("151bd3358db59fc7936eff15f1cb6734354e444cf85549a5643e55c9c929cb500be712abccd588cd8d20eb92ca55ff49")
                .fileHash("e8adaac05a62a655a3c476b43f1383f6c5f5bba4bfa6c7b087dc4ee3a9089e232b5d5977bde7fba858fd56987792ece3")
                .hapiVersionMajor(0)
                .hapiVersionMinor(9)
                .hapiVersionPatch(0)
                .metadataHash("ffe56840b99145f7b3370367fa5784cbe225278afd1c4c078dfe5b950fee22e2b9e9a04bde32023c3ba07c057cb54406")
                .name("2021-01-11T22_09_24.063739000Z.rcd")
                .previousHash(digestAlgorithm.getEmptyHash())
                .version(5)
                .build();
        RecordFile recordFileV5_2 = RecordFile.builder()
                .consensusStart(1610402974097416003L)
                .consensusEnd(1610402974097416003L)
                .count(1L)
                .digestAlgorithm(digestAlgorithm)
                .endRunningHash("514e361089074cb06f984e5a943a20fba2a0d601b766f8adb432d03214c48c3ff14898e6b78292520340f484e820ea84")
                .fileHash("06fb76873dcdc3a4fdb67202e64ed735feaf6a6bb80d4f57fd3511df49ef61fc69d7a2414315028b7d77e168169fad22")
                .hapiVersionMajor(0)
                .hapiVersionMinor(9)
                .hapiVersionPatch(0)
                .metadataHash("912869b5204ffbb7e437aaa6e7a09e9d53da98ead27942fdf7017e850827e857fadb1167e8877cfb8175883adcd74f7d")
                .name("2021-01-11T22_09_34.097416003Z.rcd")
                .previousHash(recordFileV5_1.getEndRunningHash())
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
