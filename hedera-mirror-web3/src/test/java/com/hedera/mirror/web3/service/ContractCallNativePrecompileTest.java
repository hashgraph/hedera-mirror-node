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

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.viewmodel.BlockType;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ContractCallNativePrecompileTest extends Web3IntegrationTest {
    private static final String GAS_METRICS = "hedera.mirror.web3.call.gas";
    // Account addresses
    private static final Address SENDER_ADDRESS = toAddress(EntityId.of(0, 0, 1043));
    private static final Address SENDER_ADDRESS_HISTORICAL = toAddress(EntityId.of(0, 0, 1014));

    // System addresses
    private static final EntityId FEE_SCHEDULE_ENTITY_ID = EntityId.of(0L, 0L, 111L);
    private static final EntityId EXCHANGE_RATE_ENTITY_ID = EntityId.of(0L, 0L, 112L);

    private static final long expiry = 1_234_567_890L;

    @Autowired
    private ContractCallService contractCallService;

    @BeforeEach
    void setup() {
        domainBuilder.recordFile().customize(f -> f.index(0L)).persist();
        // reset gas metrics
        meterRegistry.clear();
    }

    @Test
    void directCallToNativePrecompileECRecover() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var hash = "0x456e9aea5e197a1f1af7a3e85a3212fa4049a3ba34c2289b4c860fc0b0c64ef3";
        final var v = "000000000000000000000000000000000000000000000000000000000000001c";
        final var r = "9242685bf161793cc25603c231bc2f568eb630ea16aa137d2664ac8038825608";
        final var s = "4f8ae3bd7535248d0bd448298cc2e2071e56992d0774dc340c368ae950852ada";
        final var correctResult = "0x0000000000000000000000007156526fbd7a3c72969b54f64e42c10fbb768c8a";

        final var data = hash.concat(v).concat(r).concat(s);

        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(data), Address.ECREC, ETH_CALL, 0L, BlockType.LATEST);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(correctResult);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void directCallToNativePrecompileSHA2() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var data = "0xFF";
        final var correctResult = "0xa8100ae6aa1940d0b663bb31cd466142ebbdbd5187131b92d93818987832eb89";

        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(data), Address.SHA256, ETH_CALL, 0L, BlockType.LATEST);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(correctResult);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void directCallToNativePrecompileRIPEMD() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var data = "0xFF";
        final var correctResult = "0x0000000000000000000000002c0c45d3ecab80fe060e5f1d7057cd2f8de5e557";

        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(data), Address.RIPEMD160, ETH_CALL, 0L, BlockType.LATEST);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(correctResult);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void directCallToNativePrecompileIdentity() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var data = "0xFF";
        final var correctResult = "0xff";

        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(data), Address.ID, ETH_CALL, 0L, BlockType.LATEST);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(correctResult);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void directCallToNativePrecompileModexp() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var bsize = "0000000000000000000000000000000000000000000000000000000000000001";
        final var esize = "0000000000000000000000000000000000000000000000000000000000000001";
        final var msize = "0000000000000000000000000000000000000000000000000000000000000001";
        final var b = "08090A0000000000000000000000000000000000000000000000000000000000";
        final var data = bsize.concat(esize).concat(msize).concat(b);
        final var correctResult = "0x08";

        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(data), Address.MODEXP, ETH_CALL, 0L, BlockType.LATEST);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(correctResult);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void directCallToNativePrecompileEcAdd() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var x1 = "0x0000000000000000000000000000000000000000000000000000000000000001";
        final var y1 = "0000000000000000000000000000000000000000000000000000000000000002";
        final var x2 = "0000000000000000000000000000000000000000000000000000000000000001";
        final var y2 = "0000000000000000000000000000000000000000000000000000000000000002";
        final var data = x1.concat(y1).concat(x2).concat(y2);
        final var correctResult =
                "0x030644e72e131a029b85045b68181585d97816a916871ca8d3c208c16d87cfd315ed738c0e0a7c92e7845f96b2ae9c0a68a6a449e3538fc7ff3ebf7a5a18a2c4";

        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(data), Address.ALTBN128_ADD, ETH_CALL, 0L, BlockType.LATEST);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(correctResult);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void directCallToNativePrecompileEcMul() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var x1 = "0x0000000000000000000000000000000000000000000000000000000000000001";
        final var x2 = "0000000000000000000000000000000000000000000000000000000000000002";
        final var s = "0000000000000000000000000000000000000000000000000000000000000002";

        final var data = x1.concat(x2).concat(s);
        final var correctResult =
                "0x030644e72e131a029b85045b68181585d97816a916871ca8d3c208c16d87cfd315ed738c0e0a7c92e7845f96b2ae9c0a68a6a449e3538fc7ff3ebf7a5a18a2c4";

        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(data), Address.ALTBN128_MUL, ETH_CALL, 0L, BlockType.LATEST);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(correctResult);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void directCallToNativePrecompileEcPairing() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var x1 = "0x2cf44499d5d27bb186308b7af7af02ac5bc9eeb6a3d147c186b21fb1b76e18da";
        final var y1 = "2c0f001f52110ccfe69108924926e45f0b0c868df0e7bde1fe16d3242dc715f6";
        final var x2 = "1fb19bb476f6b9e44e2a32234da8212f61cd63919354bc06aef31e3cfaff3ebc";
        final var y2 = "22606845ff186793914e03e21df544c34ffe2f2f3504de8a79d9159eca2d98d9";
        final var x3 = "2bd368e28381e8eccb5fa81fc26cf3f048eea9abfdd85d7ed3ab3698d63e4f90";
        final var y3 = "2fe02e47887507adf0ff1743cbac6ba291e66f59be6bd763950bb16041a0a85e";
        final var x4 = "0000000000000000000000000000000000000000000000000000000000000001";
        final var y4 = "30644e72e131a029b85045b68181585d97816a916871ca8d3c208c16d87cfd45";
        final var x5 = "1971ff0471b09fa93caaf13cbf443c1aede09cc4328f5a62aad45f40ec133eb4";
        final var y5 = "091058a3141822985733cbdddfed0fd8d6c104e9e9eff40bf5abfef9ab163bc7";
        final var x6 = "2a23af9a5ce2ba2796c1f4e453a370eb0af8c212d9dc9acd8fc02c2e907baea2";
        final var y6 = "23a8eb0b0996252cb548a4487da97b02422ebc0e834613f954de6c7e0afdc1fc";

        final var data = x1.concat(y1)
                .concat(x2)
                .concat(y2)
                .concat(x3)
                .concat(y3)
                .concat(x4)
                .concat(y4)
                .concat(x5)
                .concat(y5)
                .concat(x6)
                .concat(y6);
        final var correctResult = "0x0000000000000000000000000000000000000000000000000000000000000001";
        System.out.println(data);
        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(data), Address.ALTBN128_PAIRING, ETH_CALL, 0L, BlockType.LATEST);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(correctResult);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    @Test
    void directCallToNativePrecompileBlake2f() {
        final var gasUsedBeforeExecution = getGasUsedBeforeExecution(ETH_CALL);

        final var rounds = "0x0000000c";
        final var h =
                "48c9bdf267e6096a3ba7ca8485ae67bb2bf894fe72f36e3cf1361d5f3af54fa5d182e6ad7f520e511f6c3e2b8c68059b6bbd41fbabd9831f79217e1319cde05b";
        final var m =
                "6162630000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
        final var t = "03000000000000000000000000000000";
        final var f = "01";

        final var data = rounds.concat(h).concat(m).concat(t).concat(f);
        final var correctResult =
                "0xba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6fdbffa2d17d87c5392aab792dc252d5de4533cc9518d38aa8dbf1925ab92386edd4009923";

        final var serviceParameters = serviceParametersForExecution(
                Bytes.fromHexString(data), Address.BLAKE2B_F_COMPRESSION, ETH_CALL, 0L, BlockType.LATEST);

        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(correctResult);

        assertGasUsedIsPositive(gasUsedBeforeExecution, ETH_CALL);
    }

    private CallServiceParameters serviceParametersForExecution(
            final Bytes callData,
            final Address contractAddress,
            final CallServiceParameters.CallType callType,
            final long value,
            final BlockType block) {
        HederaEvmAccount sender;
        if (block != BlockType.LATEST) {
            sender = new HederaEvmAccount(SENDER_ADDRESS_HISTORICAL);
        } else {
            sender = new HederaEvmAccount(SENDER_ADDRESS);
        }

        // persist needed entities
        exchangeRatesPersist();
        feeSchedulesPersist();

        return CallServiceParameters.builder()
                .sender(sender)
                .value(value)
                .receiver(contractAddress)
                .callData(callData)
                .gas(15_000_000L)
                .isStatic(false)
                .callType(callType)
                .isEstimate(ETH_ESTIMATE_GAS == callType)
                .block(block)
                .build();
    }

    private void feeSchedulesPersist() {
        CurrentAndNextFeeSchedule feeSchedules = CurrentAndNextFeeSchedule.newBuilder()
                .setCurrentFeeSchedule(FeeSchedule.newBuilder()
                        .setExpiryTime(TimestampSeconds.newBuilder().setSeconds(expiry))
                        .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                                .setHederaFunctionality(ContractCall)
                                .addFees(FeeData.newBuilder()
                                        .setServicedata(FeeComponents.newBuilder()
                                                .setGas(852000)
                                                .build())))
                        .addTransactionFeeSchedule(TransactionFeeSchedule.newBuilder()
                                .setHederaFunctionality(EthereumTransaction)
                                .addFees(FeeData.newBuilder()
                                        .setServicedata(FeeComponents.newBuilder()
                                                .setGas(852000)
                                                .build()))))
                .build();
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(feeSchedules.toByteArray())
                        .entityId(FEE_SCHEDULE_ENTITY_ID)
                        .consensusTimestamp(expiry + 1))
                .persist();
    }

    private void exchangeRatesPersist() {
        final ExchangeRateSet exchangeRatesSet = ExchangeRateSet.newBuilder()
                .setCurrentRate(ExchangeRate.newBuilder()
                        .setCentEquiv(12)
                        .setHbarEquiv(1)
                        .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(4_102_444_800L))
                        .build())
                .setNextRate(ExchangeRate.newBuilder()
                        .setCentEquiv(15)
                        .setHbarEquiv(1)
                        .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(4_102_444_800L))
                        .build())
                .build();
        domainBuilder
                .fileData()
                .customize(f -> f.fileData(exchangeRatesSet.toByteArray())
                        .entityId(EXCHANGE_RATE_ENTITY_ID)
                        .consensusTimestamp(expiry))
                .persist();
    }

    private double getGasUsedBeforeExecution(final CallServiceParameters.CallType callType) {
        final var callCounter = meterRegistry.find(GAS_METRICS).counters().stream()
                .filter(c -> callType.name().equals(c.getId().getTag("type")))
                .findFirst();

        var gasUsedBeforeExecution = 0d;
        if (callCounter.isPresent()) {
            gasUsedBeforeExecution = callCounter.get().count();
        }

        return gasUsedBeforeExecution;
    }

    private void assertGasUsedIsPositive(
            final double gasUsedBeforeExecution, final CallServiceParameters.CallType callType) {
        final var afterExecution = meterRegistry.find(GAS_METRICS).counters().stream()
                .filter(c -> callType.name().equals(c.getId().getTag("type")))
                .findFirst()
                .get();

        final var gasConsumed = afterExecution.count() - gasUsedBeforeExecution;
        assertThat(gasConsumed).isPositive();
    }
}
