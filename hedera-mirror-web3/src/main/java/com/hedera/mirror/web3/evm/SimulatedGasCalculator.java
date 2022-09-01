package com.hedera.mirror.web3.evm;

import javax.inject.Named;

import com.hedera.mirror.web3.evm.utils.GasCalculatorUtils;

import lombok.AllArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;

@Named
@AllArgsConstructor
public class SimulatedGasCalculator extends LondonGasCalculator {

    private static final long TX_DATA_ZERO_COST = 4L;
    private static final long ISTANBUL_TX_DATA_NON_ZERO_COST = 16L;
    private static final long TX_BASE_COST = 21_000L;

    private final UsagePricesProvider usagePrices;
    private final HbarCentExchange exchange;

    @Override
    public long transactionIntrinsicGasCost(final Bytes payload, final boolean isContractCreation) {
        int zeros = 0;
        for (int i = 0; i < payload.size(); i++) {
            if (payload.get(i) == 0) {
                ++zeros;
            }
        }
        final int nonZeros = payload.size() - zeros;

        long cost =
                TX_BASE_COST
                        + TX_DATA_ZERO_COST * zeros
                        + ISTANBUL_TX_DATA_NON_ZERO_COST * nonZeros;

        return isContractCreation ? (cost + txCreateExtraGasCost()) : cost;
    }

    @Override
    public long codeDepositGasCost(final int codeSize) {
        return 0L;
    }

    @Override
    public long logOperationGasCost(
            final MessageFrame frame,
            final long dataOffset,
            final long dataLength,
            final int numTopics) {
        final var gasCost =
                GasCalculatorUtils.logOperationGasCost(
                        usagePrices,
                        exchange,
                        frame,
                        getLogStorageDuration(),
                        dataOffset,
                        dataLength,
                        numTopics);
        return Math.max(
                super.logOperationGasCost(frame, dataOffset, dataLength, numTopics), gasCost);
    }

    long getLogStorageDuration() {
        return 100L;
    }
}
