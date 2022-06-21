package com.hedera.mirror.importer;

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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

import com.hedera.mirror.common.domain.DigestAlgorithm;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.entity.EntityType;
import com.hedera.mirror.common.domain.transaction.RecordFile;

@UtilityClass
public class TestRecordFiles {

    public Map<String, RecordFile> getAll() {
        DigestAlgorithm digestAlgorithm = DigestAlgorithm.SHA384;

        RecordFile recordFileV1_1 = RecordFile.builder()
                .consensusStart(1561990380317763000L)
                .consensusEnd(1561990399074934000L)
                .count(15L)
                .digestAlgorithm(digestAlgorithm)
                .fileHash(
                        "333d6940254659533fd6b939033e59c57fe8f4ff78375d1e687c032918aa0b7b8179c7fd403754274a8c91e0b6c0195a")
                .hash("333d6940254659533fd6b939033e59c57fe8f4ff78375d1e687c032918aa0b7b8179c7fd403754274a8c91e0b6c0195a")
                .name("2019-07-01T14_13_00.317763Z.rcd")
                .previousHash(
                        "f423447a3d5a531a07426070e511555283daae063706242590949116f717a0524e4dd18f9d64e66c73982d475401db04")
                .size(4898)
                .version(1)
                .build();
        RecordFile recordFileV1_2 = RecordFile.builder()
                .consensusStart(1561991340302068000L)
                .consensusEnd(1561991353226225001L)
                .count(69L)
                .digestAlgorithm(digestAlgorithm)
                .fileHash(
                        "1faf198f8fdbefa59bde191f214d73acdc4f5c0f434677a7edf9591b129e21aea90a5b3119d2802cee522e7be6bc8830")
                .hash("1faf198f8fdbefa59bde191f214d73acdc4f5c0f434677a7edf9591b129e21aea90a5b3119d2802cee522e7be6bc8830")
                .name("2019-07-01T14_29_00.302068Z.rcd")
                .previousHash(recordFileV1_1.getFileHash())
                .size(22347)
                .version(1)
                .build();
        RecordFile recordFileV2_1 = RecordFile.builder()
                .consensusStart(1567188600419072000L)
                .consensusEnd(1567188604906443001L)
                .count(19L)
                .digestAlgorithm(digestAlgorithm)
                .fileHash(
                        "591558e059bd1629ee386c4e35a6875b4c67a096718f5d225772a651042715189414df7db5588495efb2a85dc4a0ffda")
                .hash("591558e059bd1629ee386c4e35a6875b4c67a096718f5d225772a651042715189414df7db5588495efb2a85dc4a0ffda")
                .name("2019-08-30T18_10_00.419072Z.rcd")
                .previousHash(digestAlgorithm.getEmptyHash())
                .size(8515)
                .version(2)
                .build();
        RecordFile recordFileV2_2 = RecordFile.builder()
                .consensusStart(1567188605249678000L)
                .consensusEnd(1567188609705382001L)
                .count(15L)
                .digestAlgorithm(digestAlgorithm)
                .fileHash(
                        "5ed51baeff204eb6a2a68b76bbaadcb9b6e7074676c1746b99681d075bef009e8d57699baaa6342feec4e83726582d36")
                .hash("5ed51baeff204eb6a2a68b76bbaadcb9b6e7074676c1746b99681d075bef009e8d57699baaa6342feec4e83726582d36")
                .name("2019-08-30T18_10_05.249678Z.rcd")
                .previousHash(recordFileV2_1.getFileHash())
                .size(6649)
                .version(2)
                .build();
        RecordFile recordFileV5_1 = RecordFile.builder()
                .consensusStart(1610402964063739000L)
                .consensusEnd(1610402964063739000L)
                .count(1L)
                .digestAlgorithm(digestAlgorithm)
                .fileHash(
                        "e8adaac05a62a655a3c476b43f1383f6c5f5bba4bfa6c7b087dc4ee3a9089e232b5d5977bde7fba858fd56987792ece3")
                .hapiVersionMajor(0)
                .hapiVersionMinor(9)
                .hapiVersionPatch(0)
                .hash("151bd3358db59fc7936eff15f1cb6734354e444cf85549a5643e55c9c929cb500be712abccd588cd8d20eb92ca55ff49")
                .metadataHash(
                        "ffe56840b99145f7b3370367fa5784cbe225278afd1c4c078dfe5b950fee22e2b9e9a04bde32023c3ba07c057cb54406")
                .name("2021-01-11T22_09_24.063739000Z.rcd")
                .previousHash(digestAlgorithm.getEmptyHash())
                .size(498)
                .version(5)
                .build();
        RecordFile recordFileV5_2 = RecordFile.builder()
                .consensusStart(1610402974097416003L)
                .consensusEnd(1610402974097416003L)
                .count(1L)
                .digestAlgorithm(digestAlgorithm)
                .fileHash(
                        "06fb76873dcdc3a4fdb67202e64ed735feaf6a6bb80d4f57fd3511df49ef61fc69d7a2414315028b7d77e168169fad22")
                .hapiVersionMajor(0)
                .hapiVersionMinor(9)
                .hapiVersionPatch(0)
                .hash("514e361089074cb06f984e5a943a20fba2a0d601b766f8adb432d03214c48c3ff14898e6b78292520340f484e820ea84")
                .metadataHash(
                        "912869b5204ffbb7e437aaa6e7a09e9d53da98ead27942fdf7017e850827e857fadb1167e8877cfb8175883adcd74f7d")
                .name("2021-01-11T22_09_34.097416003Z.rcd")
                .previousHash(recordFileV5_1.getHash())
                .size(498)
                .version(5)
                .build();

        RecordFile recordFileV6_1 = RecordFile.builder()
                .consensusStart(1655374717496046001L)
                .consensusEnd(1655374717496046003L)
                .count(3L)
                .digestAlgorithm(digestAlgorithm)
                .fileHash(
                        "3e546619bd9c59fe0ee03be25ff2371718ff206f31323868207bb621bc85212669eb66b8a1556d4e686c8fbcd14f9e97")
                .hapiVersionMajor(0)
                .hapiVersionMinor(27)
                .hapiVersionPatch(0)
                .hash("53b645e288b9f2fe6cc7d80813fe0e21446d379cd501e750c0948c1b7d49a451695dd1c2474521694ee162ee4e1478e8")
                .index(-9223372036854775807L)
                .metadataHash(
                        "01a43036a6ce80082680fa64b4702aecb659b8ddd02a06201c6c83d2b71def7b2ee1b5ac09bb7eb9b2f04dad5547e8e9")
                .name("2022-06-16T10_18_37.496046001Z.rcd.gz")
                .previousHash(
                        "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")
                .size(1124)
                .version(6)
                .build();
        RecordFile recordFileV6_2 = RecordFile.builder()
                .consensusStart(1655374720720801003L)
                .consensusEnd(1655374720720801003L)
                .count(1L)
                .digestAlgorithm(digestAlgorithm)
                .fileHash(
                        "b5947d59a5661f591c0950e8dd0c6a4d42f49dcf7454bc05c911fef67aedead41fb00f01ac32b1de9ac2b3130fb14346")
                .hapiVersionMajor(0)
                .hapiVersionMinor(27)
                .hapiVersionPatch(0)
                .hash("3bade250e5b68e427df2c6ed1b427ed057303bf92a5a497b432ea22250ce03e33740d60fd7fe70a8883f7bd57babcb0b")
                .index(-9223372036854775806L)
                .metadataHash(
                        "c8766bf8449e2d4598a4b7cc41c7a54f16dc869bd9cf81af2536ba1a8ec8c86d1e33134995d45f67ce26a79249d96666")
                .name("2022-06-16T10_18_40.720801003Z.rcd.gz")
                .previousHash(recordFileV6_1.getHash())
                .size(792)
                .version(6)
                .build();

        List<RecordFile> allFiles = List.of(recordFileV1_1, recordFileV1_2,
                recordFileV2_1, recordFileV2_2,
                recordFileV5_1, recordFileV5_2,
                recordFileV6_1, recordFileV6_2);
        return Collections.unmodifiableMap(allFiles.stream().collect(Collectors.toMap(RecordFile::getName, rf -> rf)));
    }

    public List<RecordFile> getV2V5Files() {
        EntityId nodeAccountId = EntityId.of(0, 0, 3, EntityType.ACCOUNT);
        RecordFile recordFileV2 = RecordFile.builder()
                .consensusStart(1611188151568507001L)
                .consensusEnd(1611188151568507001L)
                .count(1L)
                .digestAlgorithm(DigestAlgorithm.SHA384)
                .fileHash(
                        "e7d9e71efd239bde3adcad8eb0571c38f91f77ae76a4af69bb44f19b2785ad3594ac1d265351a592ab14301da9bb1950")
                .hash("e7d9e71efd239bde3adcad8eb0571c38f91f77ae76a4af69bb44f19b2785ad3594ac1d265351a592ab14301da9bb1950")
                .name("2021-01-21T00_15_51.568507001Z.rcd")
                .nodeAccountId(nodeAccountId)
                .previousHash(
                        "d27ba83c736bfa2ffc9a6f062b27ea4856800bbbe820b77b32e08faf3d7475d81ef5a16f90ce065d35eefa999677edaa")
                .size(389)
                .version(2)
                .build();
        RecordFile recordFileV5 = RecordFile.builder()
                .consensusStart(1611188383558496000L)
                .consensusEnd(1611188383558496000L)
                .count(1L)
                .digestAlgorithm(DigestAlgorithm.SHA384)
                .fileHash(
                        "42717bae0e538bac34563784b08b5a5b50a9964c9435452c93134bf13355c9778a1c64cfdc30f33fe52dd7f76dbdda70")
                .hapiVersionMajor(0)
                .hapiVersionMinor(11)
                .hapiVersionPatch(0)
                .hash("e6c1d7bfe956b6b2c8061bee5c43e512111cbccb21099bb0c49e2a7c74cf617cf5b6bf65070f29eb43a80d9cef2d8242")
                .metadataHash(
                        "1d83206a166a06c8579f9de637cf50a565341928b55bfbdc774ce85ac2169b46c23db42729723e7c39e5a042bd9e3b98")
                .name("2021-01-21T00_19_43.558496000Z.rcd")
                .nodeAccountId(nodeAccountId)
                .previousHash(recordFileV2.getHash())
                .size(495)
                .version(5)
                .build();
        return List.of(recordFileV2, recordFileV5);
    }

    public List<RecordFile> getV5V6Files() {
        EntityId nodeAccountId = EntityId.of(0, 0, 3, EntityType.ACCOUNT);
        RecordFile recordFileV5 = RecordFile.builder()
                .nodeAccountId(nodeAccountId)
                .name("2022-06-16T14_18_05.036741003Z.rcd")
                .digestAlgorithm(DigestAlgorithm.SHA384)
                .size(488)
                .version(5)
                .build();

        RecordFile recordFileV6 = RecordFile.builder()
                .name("2022-06-16T14_20_42.665292003Z.rcd.gz")
                .nodeAccountId(nodeAccountId)
                .digestAlgorithm(DigestAlgorithm.SHA384)
                .index(-9223372036854775800L)
                .size(791)
                .version(6)
                .build();

        return List.of(recordFileV5, recordFileV6);
    }
}
