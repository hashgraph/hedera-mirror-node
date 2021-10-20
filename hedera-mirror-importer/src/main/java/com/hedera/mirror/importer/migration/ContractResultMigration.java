package com.hedera.mirror.importer.migration;

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

import com.google.common.collect.Iterables;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Named;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
@RequiredArgsConstructor(onConstructor_ = {@Lazy})
public class ContractResultMigration extends MirrorBaseJavaMigration {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public String getDescription() {
        return "Parses the protobuf function_result field and normalize it into separate database fields";
    }

    @Override
    public MigrationVersion getVersion() {
        return MigrationVersion.fromVersion("1.46.6");
    }

    @Override
    protected void doMigrate() throws IOException {
        RowMapper<MigrationContractResult> rowMapper = new BeanPropertyRowMapper<>(MigrationContractResult.class);

        jdbcTemplate.setFetchSize(100);
        jdbcTemplate.query("select consensus_timestamp, function_result " +
                "from contract_result " +
                "order by consensus_timestamp asc", rs -> {
            MigrationContractResult contractResult = rowMapper.mapRow(rs, rs.getRow());
            process(contractResult);
        });
    }

    private void process(MigrationContractResult contractResult) {
        try {
            byte[] bytes = contractResult.getFunctionResult();

            if (bytes == null || bytes.length == 0) {
                log.warn("Contract result {} is missing function result data", contractResult.getConsensusTimestamp());
                return;
            }

            ContractFunctionResult contractFunctionResult = ContractFunctionResult.parseFrom(bytes);
            EntityId contractId = EntityId.of(contractFunctionResult.getContractID());
            String createdContractIds = contractFunctionResult
                    .getCreatedContractIDsList()
                    .stream()
                    .map(EntityId::of)
                    .map(EntityId::getId)
                    .map(String::valueOf)
                    .collect(Collectors.joining(",", "{", "}"));

            contractResult.setBloom(Utility.toBytes(contractFunctionResult.getBloom()));
            contractResult.setCallResult(Utility.toBytes(contractFunctionResult.getContractCallResult()));
            contractResult.setContractId(contractId.getId());
            contractResult.setCreatedContractIds(createdContractIds);
            contractResult.setErrorMessage(contractFunctionResult.getErrorMessage());
            update(contractResult);

            for (int index = 0; index < contractFunctionResult.getLogInfoCount(); ++index) {
                ContractLoginfo contractLoginfo = contractFunctionResult.getLogInfo(index);
                List<ByteString> topics = contractLoginfo.getTopicList();

                MigrationContractLog migrationContractLog = new MigrationContractLog();
                migrationContractLog.setBloom(Utility.toBytes(contractLoginfo.getBloom()));
                migrationContractLog.setConsensusTimestamp(contractResult.getConsensusTimestamp());
                migrationContractLog.setContractId(contractResult.getContractId());
                migrationContractLog.setData(Utility.toBytes(contractLoginfo.getData()));
                migrationContractLog.setIndex(index);
                migrationContractLog.setTopic0(getTopic(topics, 0));
                migrationContractLog.setTopic1(getTopic(topics, 1));
                migrationContractLog.setTopic2(getTopic(topics, 2));
                migrationContractLog.setTopic3(getTopic(topics, 3));

                insert(migrationContractLog);
            }
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    private String getTopic(List<ByteString> topics, int index) {
        ByteString byteString = Iterables.get(topics, index, null);

        if (byteString == null) {
            return null;
        }

        return Hex.encodeHexString(Utility.toBytes(byteString));
    }

    private void update(MigrationContractResult contractResult) {
        jdbcTemplate.update("update contract_result set bloom = ?, call_result = ?, contract_id = ?, " +
                        "created_contract_ids = ?, error_message = ? where consensus_timestamp = ?",
                contractResult.getBloom(),
                contractResult.getCallResult(),
                contractResult.getContractId(),
                contractResult.getCreatedContractIds(),
                contractResult.getErrorMessage(),
                contractResult.getConsensusTimestamp()
        );
    }

    private void insert(MigrationContractLog contractLog) {
        jdbcTemplate.update("insert into contract_log (bloom, consensus_timestamp, contract_id, data, index, topic0, " +
                        "topic1, topic2, topic3) values (?, ?, ?, ?, ?, ?)",
                contractLog.getBloom(),
                contractLog.getConsensusTimestamp(),
                contractLog.getContractId(),
                contractLog.getData(),
                contractLog.getIndex(),
                contractLog.getTopic0(),
                contractLog.getTopic1(),
                contractLog.getTopic2(),
                contractLog.getTopic3()
        );
    }

    @Data
    private class MigrationContractResult {
        private byte[] bloom;
        private byte[] callResult;
        private long consensusTimestamp;
        private Long contractId;
        private String createdContractIds;
        private byte[] functionResult;
        private String errorMessage;
    }

    @Data
    private class MigrationContractLog {
        private byte[] bloom;
        private long consensusTimestamp;
        private long contractId;
        private int index;
        private byte[] data;
        private String topic0;
        private String topic1;
        private String topic2;
        private String topic3;
    }
}
