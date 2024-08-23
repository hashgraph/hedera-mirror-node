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

import static com.google.common.collect.Range.closedOpen;
import static com.hedera.mirror.common.domain.entity.EntityType.ACCOUNT;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.entityIdFromEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.web3j.TestWeb3jServiceState;
import com.hedera.mirror.web3.web3j.generated.EvmCodesHistorical;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class ContractCallEvmCodesHistoricalTest extends AbstractContractCallHistoricalServiceTest {

    @BeforeEach
    void beforeAll() {
        historicalBlocksPersist();
        setupTestWeb3jServiceState(TestWeb3jServiceState.AFTER_EVM_34_BLOCK);
        senderPersistHistorical();
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
        assertThatThrownBy(() -> contract.call_getCodeHash(input).send())
                .isInstanceOf(MirrorEvmTransactionException.class)
                .satisfies(ex -> assertEquals(ex.getMessage(), INVALID_SOLIDITY_ADDRESS.name()));
    }

    private void senderPersistHistorical() {
        final var senderAddress = toAddress(1014);
        final var senderEntityId = entityIdFromEvmAddress(senderAddress);
        final var publicKeyHistorical = ByteString.copyFrom(
                Hex.decode("3a2102930a39a381a68d90afc8e8c82935bd93f89800e88ec29a18e8cc13d51947c6c8"));

        this.senderHistorical = domainBuilder
                .entity()
                .customize(e -> e.id(senderEntityId.getId())
                        .num(senderEntityId.getNum())
                        .evmAddress(senderAddress.toArray())
                        .type(ACCOUNT)
                        .deleted(false)
                        .alias(publicKeyHistorical.toByteArray())
                        .balance(10000 * 100_000_000L)
                        .createdTimestamp(recordFileAfterEvm34.getConsensusStart())
                        .timestampRange(closedOpen(
                                recordFileAfterEvm34.getConsensusStart(), recordFileAfterEvm34.getConsensusEnd())))
                .persist();

        testWeb3jService.setSender(toAddress(senderHistorical.toEntityId()).toHexString());
    }
}
