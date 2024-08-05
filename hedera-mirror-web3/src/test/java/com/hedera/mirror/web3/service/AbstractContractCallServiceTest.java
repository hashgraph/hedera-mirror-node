/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.google.protobuf.ByteString;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.service.model.CallServiceParameters.CallType;
import com.hedera.mirror.web3.service.model.ContractExecutionParameters;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.mirror.web3.web3j.TestWeb3jService;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.Key;
import jakarta.annotation.Resource;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.function.ToLongFunction;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;

public class AbstractContractCallServiceTest extends ContractCallTestSetup {

    public static final long TRANSACTION_GAS_LIMIT = 15_000_000L;
    public static final ToLongFunction<String> longValueOf =
            value -> Bytes.fromHexString(value).toLong();
    public static final String LEDGER_ID = "0x03";
    public static final String EMPTY_UNTRIMMED_ADDRESS =
            "0x0000000000000000000000000000000000000000000000000000000000000000";
    public static final byte[] KEY_PROTO = new byte[] {
        58, 33, -52, -44, -10, 81, 99, 100, 6, -8, -94, -87, -112, 42, 42, 96, 75, -31, -5, 72, 13, -70, 101, -111, -1,
        77, -103, 47, -118, 107, -58, -85, -63, 55, -57
    };
    public static final byte[] ECDSA_KEY = Arrays.copyOfRange(KEY_PROTO, 2, KEY_PROTO.length);
    public static final Key KEY_WITH_ECDSA_TYPE =
            Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(ECDSA_KEY)).build();
    public static final byte[] ED25519_KEY = Arrays.copyOfRange(KEY_PROTO, 2, KEY_PROTO.length);
    public static final Key KEY_WITH_ED_25519_TYPE =
            Key.newBuilder().setEd25519(ByteString.copyFrom(ED25519_KEY)).build();
    public static final ByteString SENDER_PUBLIC_KEY =
            ByteString.copyFrom(Hex.decode("3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d"));
    public static final Address SENDER_ALIAS = Address.wrap(
            Bytes.wrap(recoverAddressFromPubKey(SENDER_PUBLIC_KEY.substring(2).toByteArray())));

    public static final double GAS_ESTIMATE_MULTIPLIER_LOWER_RANGE = 1.05;
    public static final double GAS_ESTIMATE_MULTIPLIER_UPPER_RANGE = 1.2;

    public static final String ESTIMATE_GAS_ERROR_MESSAGE =
            "Expected gas usage to be within the expected range, but it was not. Estimate: %d, Actual: %d";

    @Resource
    protected TestWeb3jService testWeb3jService;

    /**
     * Checks if the *actual* gas usage is within 5-20% greater than the *expected* gas used from the initial call.
     *
     * @param estimatedGas The expected gas used from the initial call.
     * @param actualGas   The actual gas used.
     * @return {@code true} if the actual gas usage is within the expected range, otherwise {@code false}.
     */
    public static boolean isWithinExpectedGasRange(final long estimatedGas, final long actualGas) {
        return estimatedGas >= (actualGas * GAS_ESTIMATE_MULTIPLIER_LOWER_RANGE)
                && estimatedGas <= (actualGas * GAS_ESTIMATE_MULTIPLIER_UPPER_RANGE);
    }

    public static Key getKeyWithDelegatableContractId(final Contract contract) {
        final var contractAddress = Address.fromHexString(contract.getContractAddress());

        return Key.newBuilder()
                .setDelegatableContractId(EntityIdUtils.contractIdFromEvmAddress(contractAddress))
                .build();
    }

    public static Key getKeyWithContractId(final Contract contract) {
        final var contractAddress = Address.fromHexString(contract.getContractAddress());

        return Key.newBuilder()
                .setContractID(EntityIdUtils.contractIdFromEvmAddress(contractAddress))
                .build();
    }

    @BeforeEach
    void setup() {
        domainBuilder.recordFile().persist();
    }

    @AfterEach
    void cleanup() {
        testWeb3jService.setEstimateGas(false);
    }

    @SuppressWarnings("try")
    protected long gasUsedAfterExecution(final ContractExecutionParameters serviceParameters) {
        return ContractCallContext.run(ctx -> {
            ctx.initializeStackFrames(store.getStackedStateFrames());
            long result = processor
                    .execute(serviceParameters, serviceParameters.getGas())
                    .getGasUsed();

            assertThat(store.getStackedStateFrames().height()).isEqualTo(1);
            return result;
        });
    }

    protected ContractExecutionParameters getContractExecutionParameters(
            final RemoteFunctionCall<TransactionReceipt> functionCall, final Contract contract) {
        return ContractExecutionParameters.builder()
                .block(BlockType.LATEST)
                .callData(Bytes.fromHexString(functionCall.encodeFunctionCall()))
                .callType(CallType.ETH_CALL)
                .gas(TRANSACTION_GAS_LIMIT)
                .isEstimate(false)
                .isStatic(false)
                .receiver(Address.fromHexString(contract.getContractAddress()))
                .sender(new HederaEvmAccount(Address.wrap(Bytes.wrap(domainBuilder.evmAddress()))))
                .value(0L)
                .build();
    }

    protected void testEstimateGas(final RemoteFunctionCall<TransactionReceipt> functionCall, final Contract contract)
            throws Exception {
        testWeb3jService.setEstimateGas(true);

        functionCall.send();
        final var estimateGasUsedResult = longValueOf.applyAsLong(testWeb3jService.getOutput());

        final var actualGasUsed = gasUsedAfterExecution(getContractExecutionParameters(functionCall, contract));

        // Then
        assertThat(isWithinExpectedGasRange(estimateGasUsedResult, actualGasUsed))
                .withFailMessage(ESTIMATE_GAS_ERROR_MESSAGE, estimateGasUsedResult, actualGasUsed)
                .isTrue();

        testWeb3jService.setEstimateGas(false);
    }

    public enum KeyType {
        ADMIN_KEY(BigInteger.valueOf(1)),
        KYC_KEY(BigInteger.valueOf(2)),
        FREEZE_KEY(BigInteger.valueOf(4)),
        WIPE_KEY(BigInteger.valueOf(8)),
        SUPPLY_KEY(BigInteger.valueOf(16)),
        FEE_SCHEDULE_KEY(BigInteger.valueOf(32)),
        PAUSE_KEY(BigInteger.valueOf(64));
        final BigInteger keyTypeNumeric;

        KeyType(BigInteger keyTypeNumeric) {
            this.keyTypeNumeric = keyTypeNumeric;
        }

        public BigInteger getKeyTypeNumeric() {
            return keyTypeNumeric;
        }
    }
}
