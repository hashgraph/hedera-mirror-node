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

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.PRNG;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.contracts.execution.LivePricesSource;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.store.contracts.AbstractLedgerWorldUpdater;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.node.app.service.mono.txns.util.PrngLogic;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.time.Instant;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompiledContract.PrecompileContractResult;

/**
 * System contract to generate random numbers. This will generate 256-bit pseudorandom number when
 * no range is provided using n-3 record's running hash. If 32-bit integer "range" and 256-bit
 * "seed" is provided returns a pseudorandom 32-bit integer X belonging to [0, range).
 */
public class PrngSystemPrecompiledContract {
    private static final Logger log = LogManager.getLogger(PrngSystemPrecompiledContract.class);
    private static final String PRECOMPILE_NAME = "PRNG";
    // random256BitGenerator(uint256)

    static final int PSEUDORANDOM_SEED_GENERATOR_SELECTOR = 0xd83bf9a1;
    public static final String PRNG_PRECOMPILE_ADDRESS = "0x169";
    private final PrngLogic prngLogic;
    private final EntityCreator creator;
    private final LivePricesSource livePricesSource;
    private final PrecompilePricingUtils pricingUtils;
    private final MirrorNodeEvmProperties evmProperties;
    private long gasRequirement;

    public PrngSystemPrecompiledContract(
            final MirrorNodeEvmProperties evmProperties,
            final PrngLogic prngLogic,
            final LivePricesSource livePricesSource,
            final PrecompilePricingUtils pricingUtils) {
        this.evmProperties = evmProperties;
        this.prngLogic = prngLogic;
        this.livePricesSource = livePricesSource;
        this.pricingUtils = pricingUtils;
    }

    @Override
    public long gasRequirement(final Bytes bytes) {
        return gasRequirement;
    }

    @Override
    public PrecompileContractResult computePrecompile(final Bytes input, final MessageFrame frame) {
        final var gasNeeded =
                calculateGas(Instant.ofEpochSecond(frame.getBlockValues().getTimestamp()));
        final var result = computePrngResult(gasNeeded, input, frame);

        if (frame.isStatic()) {
            final var proxyUpdater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
            if (!proxyUpdater.isInTransaction()) {
                // This thread is answering a ContractCallLocal query; don't create a record or
                // change
                // instance fields, just return the gas required and output for the given input
                return result.getLeft();
            }
        }
        final var randomNum = result.getLeft().getOutput();

        final var parentUpdater = updater.parentUpdater();
        if (parentUpdater.isPresent()) {
            final var parent = (AbstractLedgerWorldUpdater) parentUpdater.get();
            parent.manageInProgressRecord(recordsHistorian, childRecord, synthBody());
        } else {
            throw new InvalidTransactionException("PRNG precompile frame had no parent updater", FAIL_INVALID);
        }

        return result.getLeft();
    }

    Pair<PrecompileContractResult, ResponseCodeEnum> computePrngResult(
            final long gasNeeded, final Bytes input, final MessageFrame frame) {
        try {
            validateTrue(frame.getRemainingGas() >= gasNeeded, INSUFFICIENT_GAS);
            final var randomNum = generatePseudoRandomData(input);
            return Pair.of(PrecompileContractResult.success(randomNum), null);
        } catch (final InvalidTransactionException e) {
            return Pair.of(
                    PrecompileContractResult.halt(null, Optional.ofNullable(ExceptionalHaltReason.INVALID_OPERATION)),
                    e.getResponseCode());
        } catch (final Exception e) {
            log.warn("Internal precompile failure", e);
            return Pair.of(
                    PrecompileContractResult.halt(null, Optional.ofNullable(ExceptionalHaltReason.INVALID_OPERATION)),
                    FAIL_INVALID);
        }
    }

    @VisibleForTesting
    Bytes generatePseudoRandomData(final Bytes input) {
        final var selector = input.getInt(0);
        return switch (selector) {
            case PSEUDORANDOM_SEED_GENERATOR_SELECTOR -> random256BitGenerator();
            default -> null;
        };
    }

    private Bytes random256BitGenerator() {
        final var hashBytes = prngLogic.getNMinus3RunningHashBytes();
        if (isEmptyOrNull(hashBytes)) {
            return null;
        }
        return Bytes.wrap(hashBytes, 0, 32);
    }

    private boolean isEmptyOrNull(final byte[] hashBytes) {
        return hashBytes == null || hashBytes.length == 0;
    }

    @VisibleForTesting
    long calculateGas(final Instant now) {
        final var feesInTinyCents = pricingUtils.getCanonicalPriceInTinyCents(PRNG);
        final var currentGasPriceInTinyCents = livePricesSource.currentGasPriceInTinycents(now, ContractCall);
        return feesInTinyCents / currentGasPriceInTinyCents;
    }

    private void trackPrngOutput(final Bytes input, final Bytes randomNum) {
        final var selector = input.getInt(0);
        if (randomNum == null) {
            return;
        }
        if (selector == PSEUDORANDOM_SEED_GENERATOR_SELECTOR) {
            effectsTracker.trackRandomBytes(randomNum.toArray());
        }
    }

    //    @VisibleForTesting
    //    TransactionBody.Builder synthBody() {
    //        return TransactionBody.newBuilder().setUtilPrng(UtilPrngTransactionBody.newBuilder());
    //    }

    @VisibleForTesting
    public void setGasRequirement(final long gasRequirement) {
        this.gasRequirement = gasRequirement;
    }
}
