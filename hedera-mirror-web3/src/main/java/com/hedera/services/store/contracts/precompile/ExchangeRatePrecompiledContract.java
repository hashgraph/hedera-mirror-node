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

package com.hedera.services.store.contracts.precompile;

import com.esaulpaugh.headlong.abi.BigIntegerType;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.services.fees.HbarCentExchange;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.precompile.AbstractPrecompiledContract;

/**
 * System contract to interconvert tinybars and tinycents at the active exchange rate. The ABI
 * consists of 1 to 9 packed bytes where,
 *
 * <ol>
 *   <li>The first byte is either {@code 0xbb}, when converting to tinybars; or {@code 0xcc}, when
 *       converting to tinycents.
 *   <li>The remaining 0 to 8 bytes are (logically) left-padded with zeros to form an eight-byte
 *       big-endian representation of a {@code long} value.
 * </ol>
 *
 * <p>When the input {@code Bytes} take this form, <i>and</i> the given value can be converted to
 * the requested denomination without over-flowing an eight-byte value, the contract returns the
 * conversion result. Otherwise, it returns null.
 */
public class ExchangeRatePrecompiledContract extends AbstractPrecompiledContract {
    private static final String PRECOMPILE_NAME = "ExchangeRate";
    private static final BigIntegerType WORD_DECODER = TypeFactory.create("uint256");

    // tinycentsToTinybars(uint256)
    static final int TO_TINYBARS_SELECTOR = 0x2e3cff6a;
    // tinybarsToTinycents(uint256)
    static final int TO_TINYCENTS_SELECTOR = 0x43a88229;

    public static final String EXCHANGE_RATE_SYSTEM_CONTRACT_ADDRESS = "0x168";

    private final HbarCentExchange exchange;
    private final Instant consensusNow;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    public ExchangeRatePrecompiledContract(
            final GasCalculator gasCalculator,
            final HbarCentExchange exchange,
            final MirrorNodeEvmProperties mirrorNodeEvmProperties,
            final Instant consensusNow) {
        super(PRECOMPILE_NAME, gasCalculator);
        this.exchange = exchange;
        this.consensusNow = consensusNow;
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
    }

    @Override
    public long gasRequirement(Bytes bytes) {
        return mirrorNodeEvmProperties.exchangeRateGasReq();
    }

    @Override
    @Nonnull
    public PrecompileContractResult computePrecompile(final Bytes input, final @Nonnull MessageFrame frame) {
        try {
            final var selector = input.getInt(0);
            final var amount = biValueFrom(input);
            final var activeRate = exchange.activeRate(consensusNow);
            return switch (selector) {
                case TO_TINYBARS_SELECTOR -> padded(
                        fromAToB(amount, activeRate.getHbarEquiv(), activeRate.getCentEquiv()));
                case TO_TINYCENTS_SELECTOR -> padded(
                        fromAToB(amount, activeRate.getCentEquiv(), activeRate.getHbarEquiv()));
                default -> PrecompileContractResult.halt(null, Optional.of(ExceptionalHaltReason.NONE));
            };
        } catch (Exception ignore) {
            return PrecompileContractResult.halt(null, Optional.of(ExceptionalHaltReason.NONE));
        }
    }

    private BigInteger fromAToB(final BigInteger aAmount, final int bEquiv, final int aEquiv) {
        return aAmount.multiply(BigInteger.valueOf(bEquiv)).divide(BigInteger.valueOf(aEquiv));
    }

    private BigInteger biValueFrom(final Bytes input) {
        return WORD_DECODER.decode(input.slice(4).toArrayUnsafe());
    }

    private PrecompileContractResult padded(final BigInteger result) {
        return PrecompileContractResult.success(Bytes32.leftPad(Bytes.wrap(result.toByteArray())));
    }
}
