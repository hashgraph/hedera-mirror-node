/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.importer.repository;

import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.importer.ImporterIntegrationTest;
import lombok.Builder;
import lombok.Data;

public abstract class RecordFileMigrationTest extends ImporterIntegrationTest {

    protected void persistRecordFile(RecordFile recordFile) {
        var migrationRecordFile = MigrationRecordFile.fromDomainRecordFile(recordFile);
        persistRecordFile(migrationRecordFile);
    }

    private void persistRecordFile(MigrationRecordFile migrationRecordFile) {
        jdbcOperations.update(
                """
                    insert into record_file (bytes, consensus_end, consensus_start, count, digest_algorithm, file_hash, gas_used, hapi_version_major, hapi_version_minor, hapi_version_patch, hash, index, load_end, load_start, logs_bloom, name, node_id, prev_hash, sidecar_count, size, version)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                migrationRecordFile.getBytes(),
                migrationRecordFile.getConsensusEnd(),
                migrationRecordFile.getConsensusStart(),
                migrationRecordFile.getCount(),
                migrationRecordFile.getDigestAlgorithm(),
                migrationRecordFile.getFileHash(),
                migrationRecordFile.getGasUsed(),
                migrationRecordFile.getHapiVersionMajor(),
                migrationRecordFile.getHapiVersionMinor(),
                migrationRecordFile.getHapiVersionPatch(),
                migrationRecordFile.getHash(),
                migrationRecordFile.getIndex(),
                migrationRecordFile.getLoadEnd(),
                migrationRecordFile.getLoadStart(),
                migrationRecordFile.getLogsBloom(),
                migrationRecordFile.getName(),
                migrationRecordFile.getNodeId(),
                migrationRecordFile.getPreviousHash(),
                migrationRecordFile.getSidecarCount(),
                migrationRecordFile.getSize(),
                migrationRecordFile.getVersion());
    }

    @Builder
    @Data
    protected static class MigrationRecordFile {
        private byte[] bytes;
        private long consensusStart;
        private long consensusEnd;
        private long count;
        private int digestAlgorithm;
        private String fileHash;
        private long gasUsed;
        private int hapiVersionMajor;
        private int hapiVersionMinor;
        private int hapiVersionPatch;
        private String hash;
        private long index;
        private long loadStart;
        private long loadEnd;
        private byte[] logsBloom;
        private String name;
        private long nodeId;
        private String previousHash;
        private int sidecarCount;
        private int size;
        private int version;

        public static MigrationRecordFile fromDomainRecordFile(RecordFile recordFile) {
            return MigrationRecordFile.builder()
                    .bytes(recordFile.getBytes())
                    .consensusStart(recordFile.getConsensusStart())
                    .consensusEnd(recordFile.getConsensusEnd())
                    .count(recordFile.getCount())
                    .digestAlgorithm(recordFile.getDigestAlgorithm().ordinal())
                    .fileHash(recordFile.getFileHash())
                    .gasUsed(recordFile.getGasUsed())
                    .hapiVersionMajor(recordFile.getHapiVersionMajor())
                    .hapiVersionMinor(recordFile.getHapiVersionMinor())
                    .hapiVersionPatch(recordFile.getHapiVersionPatch())
                    .hash(recordFile.getHash())
                    .index(recordFile.getIndex())
                    .loadStart(recordFile.getLoadStart())
                    .loadEnd(recordFile.getLoadEnd())
                    .logsBloom(recordFile.getLogsBloom())
                    .name(recordFile.getName())
                    .nodeId(recordFile.getNodeId())
                    .previousHash(recordFile.getPreviousHash())
                    .sidecarCount(recordFile.getSidecarCount())
                    .size(recordFile.getSize())
                    .version(recordFile.getVersion())
                    .build();
        }
    }
}
