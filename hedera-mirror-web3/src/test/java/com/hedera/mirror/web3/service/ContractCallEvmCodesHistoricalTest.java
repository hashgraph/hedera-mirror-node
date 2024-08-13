/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.service;

import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.EVM_V_34_BLOCK;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.EVM_V_38_BLOCK;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.EVM_V_46_BLOCK;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.SENDER_ADDRESS_HISTORICAL;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.SENDER_ALIAS_HISTORICAL;
import static com.hedera.mirror.web3.service.ContractCallTestSetup.SENDER_PUBLIC_KEY_HISTORICAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.Range;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.TestWeb3jServiceState;
import com.hedera.mirror.web3.web3j.generated.EvmCodesHistorical;
import java.math.BigInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.web3j.protocol.core.DefaultBlockParameter;

public class ContractCallEvmCodesHistoricalTest extends AbstractContractCallServiceTest {

    private Entity senderHistorical;

    protected static RecordFile recordFileBeforeEvm34;

    protected static RecordFile recordFileAfterEvm34;

    protected static RecordFile recordFileEvm38;

    protected static RecordFile recordFileEvm46;

    @BeforeEach
    void beforeAll() {
        historicalBlocksPersist();
        setupTestWeb3jServiceState(TestWeb3jServiceState.AFTER_EVM_34_BLOCK);
        senderPersistHistorical();
    }

    @AfterEach
    void cleanup() {
        setupTestWeb3jServiceState(TestWeb3jServiceState.LATEST);
        testWeb3jService.setSender("");
    }

    @ParameterizedTest
    @CsvSource({
        // function getCodeHash with parameter hedera system accounts or accounts that do not exist
        // expected to revert with INVALID_SOLIDITY_ADDRESS
        "0000000000000000000000000000000000000000000000000000000000000167",
        "0000000000000000000000000000000000000000000000000000000000000168",
        "0000000000000000000000000000000000000000000000000000000000000169",
        "00000000000000000000000000000000000000000000000000000000000005ee",
        "00000000000000000000000000000000000000000000000000000000000005e4",
    })
    void testSystemContractCodeHashPreVersion38(String input) {
        final var contract = testWeb3jService.deploy(EvmCodesHistorical::deploy);
        contract.setDefaultBlockParameter(DefaultBlockParameter.valueOf(BigInteger.valueOf(EVM_V_34_BLOCK)));
        assertThatThrownBy(() -> contract.call_getCodeHash(input).send())
                .isInstanceOf(MirrorEvmTransactionException.class)
                .satisfies(ex -> assertEquals(INVALID_TRANSACTION.name(), ex.getMessage()));
    }

    private void setupTestWeb3jServiceState(TestWeb3jServiceState state) {
        switch (state) {
            case BEFORE_EVM_34_BLOCK -> {
                testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK - 1)));
                testWeb3jService.setHistoricalRange(Range.closedOpen(
                        recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd()));
            }
            case AFTER_EVM_34_BLOCK -> {
                testWeb3jService.setBlockType(BlockType.of(String.valueOf(EVM_V_34_BLOCK)));
                testWeb3jService.setHistoricalRange(Range.closedOpen(
                        recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd()));
            }
            case LATEST -> {
                testWeb3jService.setBlockType(BlockType.LATEST);
                testWeb3jService.setHistoricalRange(null);
            }
        }
    }

    void historicalBlocksPersist() {
        recordFileBeforeEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK - 1))
                .persist();

        recordFileAfterEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK))
                .persist();

        recordFileEvm38 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_38_BLOCK))
                .persist();

        recordFileEvm46 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_46_BLOCK))
                .persist();
    }

    private void senderPersistHistorical() {
        final var senderEntityId = entityIdFromEvmAddress(SENDER_ADDRESS_HISTORICAL);

        this.senderHistorical = domainBuilder
                .entity()
                .customize(e -> e.id(senderEntityId.getId())
                        .num(senderEntityId.getNum())
                        .evmAddress(SENDER_ALIAS_HISTORICAL.toArray())
                        .type(ACCOUNT)
                        .deleted(false)
                        .alias(SENDER_PUBLIC_KEY_HISTORICAL.toByteArray())
                        .balance(10000 * 100_000_000L)
                        .createdTimestamp(recordFileAfterEvm34.getConsensusStart())
                        .timestampRange(Range.closedOpen(
                                recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd())))
                .persist();

        testWeb3jService.setSender(toAddress(senderHistorical.toEntityId()).toHexString());
    }
}
