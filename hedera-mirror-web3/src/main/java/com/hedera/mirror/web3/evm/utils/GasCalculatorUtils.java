package com.hedera.mirror.web3.evm.utils;

import com.hedera.mirror.web3.evm.HbarCentExchange;
import com.hedera.mirror.web3.evm.UsagePricesProvider;

import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class GasCalculatorUtils {
    private static final int LOG_CONTRACT_ID_SIZE = 24;
    private static final int LOG_TOPIC_SIZE = 32;
    private static final int LOG_BLOOM_SIZE = 256;

    private GasCalculatorUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static long ramByteHoursTinyBarsGiven(
            final UsagePricesProvider usagePrices,
            final HbarCentExchange exchange,
            long consensusTime,
            HederaFunctionality functionType) {
        final var timestamp = Timestamp.newBuilder().setSeconds(consensusTime).build();
        FeeData prices = usagePrices.defaultPricesGiven(functionType, timestamp);
        long feeInTinyCents = prices.getServicedata().getRbh() / 1000;
        long feeInTinyBars =
                FeeBuilder.getTinybarsFromTinyCents(exchange.rate(timestamp), feeInTinyCents);
        return Math.max(1L, feeInTinyBars);
    }

    public static long calculateLogSize(int numberOfTopics, long dataSize) {
        return LOG_CONTRACT_ID_SIZE
                + LOG_BLOOM_SIZE
                + LOG_TOPIC_SIZE * (long) numberOfTopics
                + dataSize;
    }

    @SuppressWarnings("unused")
    public static long calculateStorageGasNeeded(
            long numberOfBytes,
            long durationInSeconds,
            long byteHourCostInTinyBars,
            long gasPrice) {
        long storageCostTinyBars = (durationInSeconds * byteHourCostInTinyBars) / 3600;
        return Math.round((double) storageCostTinyBars / (double) gasPrice);
    }

    public static HederaFunctionality getFunctionType(MessageFrame frame) {
        MessageFrame rootFrame = frame.getMessageFrameStack().getLast();
        return rootFrame.getContextVariable("HederaFunctionality");
    }

    @SuppressWarnings("unused")
    public static long logOperationGasCost(
            final UsagePricesProvider usagePrices,
            final HbarCentExchange exchange,
            final MessageFrame frame,
            final long storageDuration,
            final long dataOffset,
            final long dataLength,
            final int numTopics) {
        long gasPrice = frame.getGasPrice().toLong();
        long timestamp = frame.getBlockValues().getTimestamp();
        long logStorageTotalSize = GasCalculatorUtils.calculateLogSize(numTopics, dataLength);
        HederaFunctionality functionType = GasCalculatorUtils.getFunctionType(frame);

        return GasCalculatorUtils.calculateStorageGasNeeded(
                logStorageTotalSize,
                storageDuration,
                GasCalculatorUtils.ramByteHoursTinyBarsGiven(
                        usagePrices, exchange, timestamp, functionType),
                gasPrice);
    }
}
