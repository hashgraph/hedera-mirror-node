/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.PrngSystemPrecompiledContract.PSEUDORANDOM_SEED_GENERATOR_SELECTOR;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.PRNG;
import static org.hyperledger.besu.datatypes.Address.ALTBN128_ADD;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INVALID_OPERATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.google.common.primitives.Longs;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.node.app.service.evm.contracts.execution.HederaBlockValues;
import com.hedera.node.app.service.mono.contracts.execution.LivePricesSource;
import com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf;
import com.hedera.node.app.service.mono.txns.util.PrngLogic;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.utility.CommonUtils;
import java.time.Instant;
import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrngSystemPrecompiledContractTest {
    private static final Hash WELL_KNOWN_HASH = new Hash(CommonUtils.unhex(
            "65386630386164632d356537632d343964342d623437372d62636134346538386338373133633038316162372d616300"));

    @Mock
    private MessageFrame frame;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private MirrorNodeEvmProperties dynamicProperties;

    @Mock
    private RecordsRunningHashLeaf runningHashLeaf;

    @Mock
    private PrecompilePricingUtils pricingUtils;

    private final Instant consensusNow = Instant.ofEpochSecond(123456789L);

    @Mock
    private LivePricesSource livePricesSource;

    private PrngSystemPrecompiledContract subject;
    private final Random r = new Random();

    @BeforeEach
    void setUp() {
        //        final var logic = new PrngLogic(dynamicProperties, () -> runningHashLeaf, sideEffectsTracker);
        //        subject = new PrngSystemPrecompiledContract(
        //                gasCalculator, logic, creator, recordsHistorian, pricingUtils, livePricesSource,
        // dynamicProperties);
    }

    @Test
    void generatesRandom256BitNumber() throws InterruptedException {
        //       given(runningHashLeaf.nMinusThreeRunningHash()).willReturn(new Hash(TxnUtils.randomUtf8Bytes(48)));
        final var result = subject.generatePseudoRandomData(random256BitGeneratorInput());
        assertEquals(32, result.toArray().length);
    }

    @Test
    void hasExpectedGasRequirement() {
        assertEquals(0, subject.gasRequirement(argOf(123)));

        subject.setGasRequirement(100);
        assertEquals(100, subject.gasRequirement(argOf(123)));
    }

    @Test
    void calculatesGasCorrectly() {
        given(pricingUtils.getCanonicalPriceInTinyCents(PRNG)).willReturn(100000000L);
        //        given(livePricesSource.currentGasPriceInTinycents(consensusNow, HederaFunctionality.ContractCall))
        //                .willReturn(800L);
        assertEquals(100000000L / 800L, subject.calculateGas(consensusNow));
    }

    @Test
    void insufficientGasRecordStillIncludesUtilPrngBody() {}

    @Test
    void happyPathWithRandomSeedGeneratedWorks() throws InterruptedException {}

    @Test
    void unknownExceptionFailsTheCall() {
        final var input = random256BitGeneratorInput();
        initialSetUp();
        given(frame.getBlockValues()).willReturn(new HederaBlockValues(10L, 123L, consensusNow));
        final var logic = mock(PrngLogic.class);
        //        subject = new PrngSystemPrecompiledContract(
        //                gasCalculator, logic, creator, recordsHistorian, pricingUtils, livePricesSource,
        // dynamicProperties);
        given(logic.getNMinus3RunningHashBytes()).willThrow(IndexOutOfBoundsException.class);

        final var response = subject.computePrngResult(10L, input, frame);
        assertEquals(INVALID_OPERATION, response.getLeft().getHaltReason().get());

        final var result = subject.computePrecompile(input, frame);
        assertNull(result.getOutput());
    }

    @Test
    void selectorMustBeRecognized() {
        final var fragmentSelector = Bytes.of((byte) 0xab, (byte) 0xab, (byte) 0xab, (byte) 0xab);
        final var input = Bytes.concatenate(fragmentSelector, Bytes32.ZERO);
        assertNull(subject.generatePseudoRandomData(input));
    }

    @Test
    void invalidHashReturnsSentinelOutputs() throws InterruptedException {
        given(runningHashLeaf.nMinusThreeRunningHash()).willReturn(new Hash(TxnUtils.randomUtf8Bytes(48)));

        var result = subject.generatePseudoRandomData(random256BitGeneratorInput());
        assertEquals(32, result.toArray().length);

        // hash is null
        given(runningHashLeaf.nMinusThreeRunningHash()).willReturn(null);

        result = subject.generatePseudoRandomData(random256BitGeneratorInput());
        assertNull(result);
    }

    @Test
    void interruptedExceptionReturnsNull() throws InterruptedException {
        final var runningHash = mock(Hash.class);
        given(runningHashLeaf.nMinusThreeRunningHash()).willReturn(runningHash);

        final var result = subject.generatePseudoRandomData(random256BitGeneratorInput());
        assertNull(result);
    }

    @Test
    void parentUpdaterMissingFails() throws InterruptedException {}

    private static Bytes random256BitGeneratorInput() {
        return input(PSEUDORANDOM_SEED_GENERATOR_SELECTOR, Bytes.EMPTY);
    }

    private static Bytes defaultInput() {
        return input(0x34676789, Bytes.EMPTY);
    }

    private static Bytes input(final int selector, final Bytes wordInput) {
        return Bytes.concatenate(Bytes.ofUnsignedInt(selector & 0xffffffffL), wordInput);
    }

    private static Bytes argOf(final long amount) {
        return Bytes.wrap(Longs.toByteArray(amount));
    }

    private void initialSetUp() {
        given(frame.getSenderAddress()).willReturn(ALTBN128_ADD);
        given(frame.getWorldUpdater()).willReturn(updater);
        given(updater.permissivelyUnaliased(frame.getSenderAddress().toArray())).willReturn(ALTBN128_ADD.toArray());
        given(pricingUtils.getCanonicalPriceInTinyCents(PRNG)).willReturn(100000000L);
        given(livePricesSource.currentGasPriceInTinycents(consensusNow, HederaFunctionality.ContractCall))
                .willReturn(830L);
        given(frame.getRemainingGas()).willReturn(400_000L);
    }
}
