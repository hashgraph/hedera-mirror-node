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

package com.hedera.mirror.web3.evm.contracts.operations;

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.common.util.DomainUtils.fromEvmAddress;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.ContractCallService;
import com.hedera.mirror.web3.utils.FunctionEncodeDecoder;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.nio.file.Path;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

public class SelfDestructOperationTest extends Web3IntegrationTest {

    private final ByteString SENDER_PUBLIC_KEY =
            ByteString.copyFrom(Hex.decode("3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d"));
    private final Address SENDER_ALIAS = Address.wrap(
            Bytes.wrap(recoverAddressFromPubKey(SENDER_PUBLIC_KEY.substring(2).toByteArray())));
    private static final Address SELF_DESTRUCT_CONTRACT_ADDRESS = toAddress(EntityId.of(0, 0, 1278));
    private static final Address SENDER_ADDRESS = toAddress(EntityId.of(0, 0, 1043));

    @Autowired
    protected ContractCallService contractCallService;

    @Autowired
    protected FunctionEncodeDecoder functionEncodeDecoder;

    protected RecordFile recordFileAfterEvm34;

    @Value("classpath:contracts/SelfDestructContract/SelfDestructContract.bin")
    protected Path SELF_DESTRUCT_CONTRACT_BYTES_PATH;

    @BeforeEach
    void setUp() {
        final var evmV34Block = 50L;
        recordFileAfterEvm34 =
                domainBuilder.recordFile().customize(f -> f.index(evmV34Block)).persist();
    }

    @Test
    void testSuccesfullExecute() {
        final var destroyContractInput = "0x9a0313ab000000000000000000000000" + SENDER_ALIAS.toUnprefixedHexString();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(destroyContractInput),
                SELF_DESTRUCT_CONTRACT_ADDRESS,
                ETH_CALL,
                0L,
                BlockType.LATEST,
                SENDER_ADDRESS);
        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo("0x");
    }

    @Test
    void testExecuteWithInvalidOwner() {
        final var systemAccountAddress = toAddress(EntityId.of(0, 0, 700));
        final var destroyContractInput =
                "0x9a0313ab000000000000000000000000" + systemAccountAddress.toUnprefixedHexString();
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(destroyContractInput),
                SELF_DESTRUCT_CONTRACT_ADDRESS,
                ETH_CALL,
                0L,
                BlockType.LATEST,
                SENDER_ADDRESS);

        assertEquals(
                INVALID_SOLIDITY_ADDRESS.name(),
                assertThrows(
                                MirrorEvmTransactionException.class,
                                () -> contractCallService.processCall(serviceParameters))
                        .getMessage());
    }

    protected void persistEntities() {
        selfDestructContractPersist();
        final var senderEntityId = fromEvmAddress(SENDER_ADDRESS.toArrayUnsafe());

        domainBuilder
                .entity()
                .customize(e -> e.id(senderEntityId.getId())
                        .num(senderEntityId.getNum())
                        .evmAddress(SENDER_ALIAS.toArray())
                        .deleted(false)
                        .alias(SENDER_PUBLIC_KEY.toByteArray())
                        .balance(10000 * 100_000_000L))
                .persist();
        exchangeRatesPersist();
        feeSchedulesPersist();
    }

    protected void feeSchedulesPersist() {
        CurrentAndNextFeeSchedule feeSchedules = CurrentAndNextFeeSchedule.newBuilder()
                .setCurrentFeeSchedule(FeeSchedule.newBuilder()
                        .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                                .setHederaFunctionality(EthereumTransaction)
                                .addFees(FeeData.newBuilder()
                                        .setServicedata(FeeComponents.newBuilder()
                                                .setGas(852000)
                                                .build()))))
                .build();
        final var feeScheduleEntityId = EntityId.of(0L, 0L, 111L);
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(feeSchedules.toByteArray())
                        .entityId(feeScheduleEntityId)
                        .consensusTimestamp(EXPIRY + 1))
                .persist();
    }

    protected void exchangeRatesPersist() {
        final ExchangeRateSet exchangeRatesSet = ExchangeRateSet.newBuilder()
                .setNextRate(ExchangeRate.newBuilder()
                        .setCentEquiv(15)
                        .setHbarEquiv(1)
                        .build())
                .build();
        final var exchangeRateEntityId = EntityId.of(0L, 0L, 112L);
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(exchangeRatesSet.toByteArray())
                        .entityId(exchangeRateEntityId)
                        .consensusTimestamp(EXPIRY))
                .persist();
    }

    private void selfDestructContractPersist() {
        final var evmCodesContractBytes = functionEncodeDecoder.getContractBytes(SELF_DESTRUCT_CONTRACT_BYTES_PATH);
        final var evmCodesContractEntityId = fromEvmAddress(SELF_DESTRUCT_CONTRACT_ADDRESS.toArrayUnsafe());
        final var evmCodesContractEvmAddress = toEvmAddress(evmCodesContractEntityId);

        domainBuilder
                .entity()
                .customize(e -> e.id(evmCodesContractEntityId.getId())
                        .num(evmCodesContractEntityId.getNum())
                        .evmAddress(evmCodesContractEvmAddress)
                        .type(CONTRACT)
                        .balance(1500L))
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(evmCodesContractEntityId.getId()).runtimeBytecode(evmCodesContractBytes))
                .persist();

        domainBuilder
                .recordFile()
                .customize(f -> f.bytes(evmCodesContractBytes))
                .persist();
    }
}
