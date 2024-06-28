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

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.protobuf.ByteString;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.service.ContractCallTestSetup;
import com.hedera.mirror.web3.service.ContractExecutionService;
import com.hedera.mirror.web3.service.model.ContractExecutionParameters;
import com.hedera.mirror.web3.utils.FunctionEncodeDecoder;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

@RequiredArgsConstructor
class SelfDestructOperationTest extends ContractCallTestSetup {

    private final ContractExecutionService contractCallService;

    private final FunctionEncodeDecoder functionEncodeDecoder;

    @Value("classpath:contracts/SelfDestructContract/SelfDestructContract.bin")
    private Path selfDestructContractPath;

    @BeforeEach
    void setUp() {
        exchangeRatesPersist();
        feeSchedulesPersist();
    }

    @Test
    void testSuccessfulExecute() {
        final var senderAddress = toAddress(EntityId.of(1043));
        final var senderPublicKey = ByteString.copyFrom(
                Hex.decode("3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d"));
        final var senderAlias = Address.wrap(
                Bytes.wrap(recoverAddressFromPubKey(senderPublicKey.substring(2).toByteArray())));
        domainBuilder
                .entity()
                .customize(e -> e.evmAddress(senderAlias.toArray()))
                .persist();
        final var contractAddress = toAddress(selfDestructContractPersist());
        final var destroyContractInput = "0x9a0313ab000000000000000000000000" + senderAlias.toUnprefixedHexString();
        final var serviceParameters = serviceParametersForExecution()
                .sender(new HederaEvmAccount(senderAddress))
                .callData(Bytes.fromHexString(destroyContractInput))
                .receiver(contractAddress)
                .build();
        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo("0x");
    }

    @Test
    void testExecuteWithInvalidOwner() {
        final var contractAddress = toAddress(selfDestructContractPersist());
        final var senderAddress = toAddress(EntityId.of(1043));
        final var systemAccountAddress = toAddress(EntityId.of(700));
        final var destroyContractInput =
                "0x9a0313ab000000000000000000000000" + systemAccountAddress.toUnprefixedHexString();
        final var serviceParameters = serviceParametersForExecution()
                .sender(new HederaEvmAccount(senderAddress))
                .receiver(contractAddress)
                .callData(Bytes.fromHexString(destroyContractInput))
                .build();

        assertEquals(
                INVALID_SOLIDITY_ADDRESS.name(),
                assertThrows(
                                MirrorEvmTransactionException.class,
                                () -> contractCallService.processCall(serviceParameters))
                        .getMessage());
    }

    private EntityId selfDestructContractPersist() {
        final var selfDestructContractBytes = functionEncodeDecoder.getContractBytes(selfDestructContractPath);
        final var selfDestructContractEntity = domainBuilder.entity().persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(selfDestructContractEntity.toEntityId().getId())
                        .runtimeBytecode(selfDestructContractBytes))
                .persist();

        domainBuilder
                .recordFile()
                .customize(f -> f.bytes(selfDestructContractBytes))
                .persist();

        return selfDestructContractEntity.toEntityId();
    }

    private ContractExecutionParameters.ContractExecutionParametersBuilder serviceParametersForExecution() {
        return ContractExecutionParameters.builder()
                .value(0L)
                .gas(15_000_000L)
                .isStatic(false)
                .callType(ETH_CALL)
                .isEstimate(false)
                .block(BlockType.LATEST);
    }
}
