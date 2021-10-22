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

import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterables;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Named;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;
import org.flywaydb.core.api.MigrationVersion;
import org.postgresql.jdbc.PgArray;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.util.Utility;

@Log4j2
@Named
@RequiredArgsConstructor(onConstructor_ = {@Lazy})
public class ContractResultMigration extends MirrorBaseJavaMigration {

    static final DataClassRowMapper<MigrationContractResult> resultRowMapper;

    static {
        DefaultConversionService defaultConversionService = new DefaultConversionService();
        defaultConversionService.addConverter(PgArray.class, Long[].class, ContractResultMigration::convert);
        resultRowMapper = new DataClassRowMapper<>(MigrationContractResult.class);
        resultRowMapper.setConversionService(defaultConversionService);
    }

    private final JdbcTemplate jdbcTemplate;

    @SneakyThrows
    private static Long[] convert(PgArray s) {
        return (Long[]) s.getArray();
    }

    @Override
    public String getDescription() {
        return "Parses the protobuf function_result field and normalize it into separate database fields";
    }

    @Override
    public MigrationVersion getVersion() {
        return MigrationVersion.fromVersion("1.46.8");
    }

    @Override
    protected void doMigrate() throws IOException {
        AtomicLong count = new AtomicLong(0L);
        Stopwatch stopwatch = Stopwatch.createStarted();

        jdbcTemplate.setFetchSize(100);
        jdbcTemplate.query("select consensus_timestamp, function_result from contract_result " +
                "order by consensus_timestamp asc", rs -> {

            MigrationContractResult contractResult = resultRowMapper.mapRow(rs, rs.getRow());
            if (process(contractResult)) {
                count.incrementAndGet();
            }
        });

        log.info("Updated {} contract results in {}", count, stopwatch);
    }

    private boolean process(MigrationContractResult contractResult) {
        long consensusTimestamp = contractResult.getConsensusTimestamp();

        try {
            byte[] functionResult = contractResult.getFunctionResult();
            if (functionResult == null || functionResult.length == 0) {
                return false;
            }

            ContractFunctionResult contractFunctionResult = ContractFunctionResult.parseFrom(functionResult);
            Long[] createdContractIds = new Long[contractFunctionResult.getCreatedContractIDsCount()];

            for (int i = 0; i < createdContractIds.length; ++i) {
                createdContractIds[i] = getContractId(contractFunctionResult.getCreatedContractIDs(i));
            }

            contractResult.setBloom(Utility.toBytes(contractFunctionResult.getBloom()));
            contractResult.setCallResult(Utility.toBytes(contractFunctionResult.getContractCallResult()));
            contractResult.setContractId(getContractId(contractFunctionResult.getContractID()));
            contractResult.setCreatedContractIds(createdContractIds);
            contractResult.setErrorMessage(contractFunctionResult.getErrorMessage());
            update(contractResult);

            for (int index = 0; index < contractFunctionResult.getLogInfoCount(); ++index) {
                ContractLoginfo contractLoginfo = contractFunctionResult.getLogInfo(index);
                List<ByteString> topics = contractLoginfo.getTopicList();

                MigrationContractLog migrationContractLog = new MigrationContractLog();
                migrationContractLog.setBloom(Utility.toBytes(contractLoginfo.getBloom()));
                migrationContractLog.setConsensusTimestamp(consensusTimestamp);
                migrationContractLog.setContractId(getContractId(contractLoginfo.getContractID()));
                migrationContractLog.setData(Utility.toBytes(contractLoginfo.getData()));
                migrationContractLog.setIndex(index);
                migrationContractLog.setTopic0(getTopic(topics, 0));
                migrationContractLog.setTopic1(getTopic(topics, 1));
                migrationContractLog.setTopic2(getTopic(topics, 2));
                migrationContractLog.setTopic3(getTopic(topics, 3));

                insert(migrationContractLog);
            }

            return true;
        } catch (Exception e) {
            log.warn("Unable to parse {} as ContractFunctionResult", consensusTimestamp, e);
        }

        return false;
    }

    private Long getContractId(ContractID contractID) {
        EntityId entityId = EntityId.of(contractID);
        return !EntityId.isEmpty(entityId) ? entityId.getId() : null;
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
                        "topic1, topic2, topic3) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
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
    static class MigrationContractResult {
        private byte[] bloom;
        private byte[] callResult;
        private long consensusTimestamp;
        private Long contractId;
        private Long[] createdContractIds;
        private String errorMessage;
        private byte[] functionParameters;
        private byte[] functionResult;
    }

    @Data
    static class MigrationContractLog {
        private byte[] bloom;
        private long consensusTimestamp;
        private long contractId;
        private byte[] data;
        private int index;
        private String topic0;
        private String topic1;
        private String topic2;
        private String topic3;
    }
}
