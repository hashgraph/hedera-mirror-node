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

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.services.contracts.execution.LivePricesSource;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txns.util.PrngLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.time.Instant;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.AbstractPrecompiledContract;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.jetbrains.annotations.NotNull;

/**
 * This is a modified copy of the PRNGSystemPrecompiledContract class from the hedera-services repository.
 *
 * The main differences from the original version are as follows:
 * 1. The seed is generated based on the running hash of the latest record file retrieved from the database.
 * 2. The childRecord logic has been removed.
 */
public class PrngSystemPrecompiledContract extends AbstractPrecompiledContract {
    private static final Logger log = LogManager.getLogger(PrngSystemPrecompiledContract.class);
    private static final String PRECOMPILE_NAME = "PRNG";

    // random256BitGenerator(uint256)
    public static final int PSEUDORANDOM_SEED_GENERATOR_SELECTOR = 0xd83bf9a1;
    public static final String PRNG_PRECOMPILE_ADDRESS = "0x169";

    private final LivePricesSource livePricesSource;
    private final PrecompilePricingUtils pricingUtils;
    private final PrngLogic prngLogic;

    private long gasRequirement;

    public PrngSystemPrecompiledContract(
            final GasCalculator gasCalculator,
            final PrngLogic prngLogic,
            final LivePricesSource livePricesSource,
            final PrecompilePricingUtils pricingUtils) {
        super(PRECOMPILE_NAME, gasCalculator);
        this.livePricesSource = livePricesSource;
        this.prngLogic = prngLogic;
        this.pricingUtils = pricingUtils;
    }

    @Override
    public long gasRequirement(final Bytes bytes) {
        return gasRequirement;
    }

    @Override
    @NotNull
    public PrecompileContractResult computePrecompile(final Bytes input, final MessageFrame frame) {
        gasRequirement =
                calculateGas(Instant.ofEpochSecond(frame.getBlockValues().getTimestamp()));
        final var result = computePrngResult(gasRequirement, input, frame);
        return result.getLeft();
    }

    public Pair<PrecompileContractResult, ResponseCodeEnum> computePrngResult(
            final long gasNeeded, final Bytes input, final MessageFrame frame) {
        try {
            validateTrue(frame.getRemainingGas() >= gasNeeded, INSUFFICIENT_GAS);
            final var randomNum = generatePseudoRandomData(input);
            return Pair.of(PrecompiledContract.PrecompileContractResult.success(randomNum), null);
        } catch (final InvalidTransactionException e) {
            return Pair.of(
                    PrecompiledContract.PrecompileContractResult.halt(
                            null, Optional.ofNullable(ExceptionalHaltReason.INVALID_OPERATION)),
                    e.getResponseCode());
        } catch (final Exception e) {
            log.warn("Internal precompile failure", e);
            return Pair.of(
                    PrecompiledContract.PrecompileContractResult.halt(
                            null, Optional.ofNullable(ExceptionalHaltReason.INVALID_OPERATION)),
                    FAIL_INVALID);
        }
    }

    public Bytes generatePseudoRandomData(final Bytes input) {
        final var selector = input.getInt(0);
        return selector == PSEUDORANDOM_SEED_GENERATOR_SELECTOR ? random256BitGenerator() : null;
    }

    public long calculateGas(final Instant now) {
        final var feesInTinyCents = pricingUtils.getCanonicalPriceInTinyCents(PRNG);
        final var currentGasPriceInTinyCents = livePricesSource.currentGasPriceInTinycents(now, ContractCall);
        return feesInTinyCents / currentGasPriceInTinyCents;
    }

    private Bytes random256BitGenerator() {
        final var hashBytes = prngLogic.getLatestRecordRunningHashBytes();
        if (isEmptyOrNull(hashBytes)) {
            return null;
        }
        return Bytes.wrap(hashBytes, 0, 32);
    }

    private boolean isEmptyOrNull(final byte[] hashBytes) {
        return hashBytes == null || hashBytes.length == 0;
    }
}
