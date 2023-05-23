/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.contracts.gascalculator;

import static com.hedera.services.hapi.utils.fees.FeeBuilder.getTinybarsFromTinyCents;

import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;
import jakarta.inject.Inject;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;

/**
 * Temporary extracted class from services.
 * </p>
 * Provides Hedera adapted gas cost lookups and calculations used during transaction processing.
 * Maps the gas costs of the Smart Contract Service including and after 0.19.0 release
 */
@SuppressWarnings("java:S110")
public class GasCalculatorHederaV19 extends LondonGasCalculator {
    private static final long LOG_STORAGE_DURATION_SEC = 180L;

    private final UsagePricesProvider usagePrices;
    private final HbarCentExchange exchange;

    @Inject
    public GasCalculatorHederaV19(final UsagePricesProvider usagePrices, final HbarCentExchange exchange) {
        this.usagePrices = usagePrices;
        this.exchange = exchange;
    }

    @Override
    public long transactionIntrinsicGasCost(final Bytes payload, final boolean isContractCreate) {
        return 0L;
    }

    @Override
    public long codeDepositGasCost(final int codeSize) {
        return 0L;
    }

    @Override
    public long logOperationGasCost(
            final MessageFrame frame, final long dataOffset, final long dataLength, final int numTopics) {
        final var gasCost = storageGasNeeded(frame);
        return Math.max(super.logOperationGasCost(frame, dataOffset, dataLength, numTopics), gasCost);
    }

    private long storageGasNeeded(final MessageFrame frame) {
        long storageDuration = getLogStorageDuration();
        long gasPrice = frame.getGasPrice().toLong();
        long timestamp = frame.getBlockValues().getTimestamp();
        HederaFunctionality functionType = getFunctionType(frame);

        return calculateStorageGasNeeded(storageDuration, ramByteHoursTinyBarsGiven(timestamp, functionType), gasPrice);
    }

    private static HederaFunctionality getFunctionType(MessageFrame frame) {
        MessageFrame rootFrame = frame.getMessageFrameStack().getLast();
        return rootFrame.getContextVariable("HederaFunctionality");
    }

    private static long calculateStorageGasNeeded(long durationInSeconds, long byteHourCostInTinyBars, long gasPrice) {
        long storageCostTinyBars = (durationInSeconds * byteHourCostInTinyBars) / 3600;
        return Math.round((double) storageCostTinyBars / (double) gasPrice);
    }

    private long ramByteHoursTinyBarsGiven(long consensusTime, HederaFunctionality functionType) {
        final var timestamp = Timestamp.newBuilder().setSeconds(consensusTime).build();
        FeeData prices = usagePrices.defaultPricesGiven(functionType, timestamp);
        long feeInTinyCents = prices.getServicedata().getRbh() / 1000;
        long feeInTinyBars = getTinybarsFromTinyCents(exchange.rate(timestamp), feeInTinyCents);
        return Math.max(1L, feeInTinyBars);
    }

    long getLogStorageDuration() {
        return LOG_STORAGE_DURATION_SEC;
    }
}
