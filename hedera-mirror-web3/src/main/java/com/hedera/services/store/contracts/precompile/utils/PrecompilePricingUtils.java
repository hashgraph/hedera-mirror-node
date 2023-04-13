package com.hedera.services.store.contracts.precompile.utils;

import static com.hedera.services.fees.pricing.FeeSchedules.USD_TO_TINYCENTS;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoApproveAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDeleteAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDissociateFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGrantKycToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenPause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenRevokeKycFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnpause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUpdate;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;

import com.hedera.services.fees.BasicHbarCentExchange;

import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.calculation.BasicFcfsUsagePrices;

import com.hedera.services.fees.pricing.AssetsLoader;

import com.hedera.services.hapi.utils.fees.FeeBuilder;

import com.hedera.services.store.contracts.precompile.Precompile;

import com.hedera.services.utils.accessors.AccessorFactory;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionGetRecordQuery;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Provider;

public class PrecompilePricingUtils {
    static class CanonicalOperationsUnloadableException extends RuntimeException {
        public CanonicalOperationsUnloadableException(final Exception e) {
            super("Canonical prices for precompiles are not available", e);
        }
    }

    /**
     * If we lack an entry (because of a bad data load), return a value that cannot reasonably be
     * paid. In this case $1 Million Dollars.
     */
    static final long COST_PROHIBITIVE = 1_000_000L * 10_000_000_000L;

    private static final Query SYNTHETIC_REDIRECT_QUERY = Query.newBuilder()
            .setTransactionGetRecord(TransactionGetRecordQuery.newBuilder().build())
            .build();
    private final BasicHbarCentExchange exchange;
    private final Provider<FeeCalculator> feeCalculator;
    private final BasicFcfsUsagePrices resourceCosts;
    private final StateView currentView;
    private final AccessorFactory accessorFactory;
    Map<GasCostType, Long> canonicalOperationCostsInTinyCents;

    @Inject
    public PrecompilePricingUtils(
            final AssetsLoader assetsLoader,
            final BasicHbarCentExchange exchange,
            final Provider<FeeCalculator> feeCalculator,
            final BasicFcfsUsagePrices resourceCosts,
            final StateView currentView,
            final AccessorFactory accessorFactory) {
        this.exchange = exchange;
        this.feeCalculator = feeCalculator;
        this.resourceCosts = resourceCosts;
        this.currentView = currentView;
        this.accessorFactory = accessorFactory;

        canonicalOperationCostsInTinyCents = new EnumMap<>(GasCostType.class);
        final Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices;
        try {
            canonicalPrices = assetsLoader.loadCanonicalPrices();
        } catch (final IOException e) {
            throw new CanonicalOperationsUnloadableException(e);
        }
        for (final var costType : GasCostType.values()) {
            if (canonicalPrices.containsKey(costType.functionality)) {
                final BigDecimal costInUSD =
                        canonicalPrices.get(costType.functionality).get(costType.subtype);
                if (costInUSD != null) {
                    canonicalOperationCostsInTinyCents.put(
                            costType, costInUSD.multiply(USD_TO_TINYCENTS).longValue());
                }
            }
        }
    }

    public long getCanonicalPriceInTinyCents(final GasCostType gasCostType) {
        return canonicalOperationCostsInTinyCents.getOrDefault(gasCostType, COST_PROHIBITIVE);
    }

    public long getMinimumPriceInTinybars(final GasCostType gasCostType, final Timestamp timestamp) {
        return FeeBuilder.getTinybarsFromTinyCents(exchange.rate(timestamp), getCanonicalPriceInTinyCents(gasCostType));
    }

    public long gasFeeInTinybars(
            final TransactionBody.Builder txBody, final Instant consensusTime, final Precompile precompile) {
        final var signedTxn = SignedTransaction.newBuilder()
                .setBodyBytes(txBody.build().toByteString())
                .setSigMap(SignatureMap.getDefaultInstance())
                .build();
        final var txn = Transaction.newBuilder()
                .setSignedTransactionBytes(signedTxn.toByteString())
                .build();

        final var accessor = accessorFactory.uncheckedSpecializedAccessor(txn);
        precompile.addImplicitCostsIn(accessor);
        final var fees = feeCalculator.get().computeFee(accessor, EMPTY_KEY, currentView, consensusTime);
        return fees.getServiceFee() + fees.getNetworkFee() + fees.getNodeFee();
    }

    public long computeViewFunctionGas(final Timestamp now, final long minimumTinybarCost) {
        final var calculator = feeCalculator.get();
        final var usagePrices = resourceCosts.defaultPricesGiven(TokenGetInfo, now);
        final var fees =
                calculator.estimatePayment(SYNTHETIC_REDIRECT_QUERY, usagePrices, currentView, now, ANSWER_ONLY);

        final long gasPriceInTinybars = calculator.estimatedGasPriceInTinybars(ContractCall, now);
        final long calculatedFeeInTinybars = fees.getNetworkFee() + fees.getNodeFee() + fees.getServiceFee();
        final long actualFeeInTinybars = Math.max(minimumTinybarCost, calculatedFeeInTinybars);

        // convert to gas cost
        final long baseGasCost = (actualFeeInTinybars + gasPriceInTinybars - 1L) / gasPriceInTinybars;

        // charge premium
        return baseGasCost + (baseGasCost / 5L);
    }

    public long computeGasRequirement(
            final long blockTimestamp, final Precompile precompile, final TransactionBody.Builder transactionBody) {
        final Timestamp timestamp =
                Timestamp.newBuilder().setSeconds(blockTimestamp).build();
        final long gasPriceInTinybars = feeCalculator.get().estimatedGasPriceInTinybars(ContractCall, timestamp);

        final long calculatedFeeInTinybars = gasFeeInTinybars(
                transactionBody.setTransactionID(TransactionID.newBuilder()
                        .setTransactionValidStart(timestamp)
                        .build()),
                Instant.ofEpochSecond(blockTimestamp),
                precompile);

        final long minimumFeeInTinybars = precompile.getMinimumFeeInTinybars(timestamp);
        final long actualFeeInTinybars = Math.max(minimumFeeInTinybars, calculatedFeeInTinybars);

        // convert to gas cost
        final long baseGasCost = (actualFeeInTinybars + gasPriceInTinybars - 1L) / gasPriceInTinybars;

        // charge premium
        return baseGasCost + (baseGasCost / 5L);
    }

    public enum GasCostType {
        UNRECOGNIZED(HederaFunctionality.UNRECOGNIZED, SubType.UNRECOGNIZED),
        CRYPTO_CREATE(CryptoCreate, DEFAULT),
        CRYPTO_UPDATE(CryptoUpdate, DEFAULT),
        TRANSFER_HBAR(CryptoTransfer, DEFAULT),
        TRANSFER_FUNGIBLE(CryptoTransfer, TOKEN_FUNGIBLE_COMMON),
        TRANSFER_NFT(CryptoTransfer, TOKEN_NON_FUNGIBLE_UNIQUE),
        TRANSFER_FUNGIBLE_CUSTOM_FEES(CryptoTransfer, TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES),
        TRANSFER_NFT_CUSTOM_FEES(CryptoTransfer, TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES),
        MINT_FUNGIBLE(TokenMint, TOKEN_FUNGIBLE_COMMON),
        MINT_NFT(TokenMint, TOKEN_NON_FUNGIBLE_UNIQUE),
        BURN_FUNGIBLE(TokenBurn, TOKEN_FUNGIBLE_COMMON),
        DELETE(TokenDelete, DEFAULT),
        BURN_NFT(TokenBurn, TOKEN_NON_FUNGIBLE_UNIQUE),
        ASSOCIATE(TokenAssociateToAccount, DEFAULT),
        DISSOCIATE(TokenDissociateFromAccount, DEFAULT),
        APPROVE(CryptoApproveAllowance, DEFAULT),
        DELETE_NFT_APPROVE(CryptoDeleteAllowance, DEFAULT),
        GRANT_KYC(TokenGrantKycToAccount, DEFAULT),
        REVOKE_KYC(TokenRevokeKycFromAccount, DEFAULT),
        PAUSE(TokenPause, DEFAULT),
        UNPAUSE(TokenUnpause, DEFAULT),
        FREEZE(TokenFreezeAccount, DEFAULT),
        UNFREEZE(TokenUnfreezeAccount, DEFAULT),
        WIPE_FUNGIBLE(TokenAccountWipe, TOKEN_FUNGIBLE_COMMON),
        WIPE_NFT(TokenAccountWipe, TOKEN_NON_FUNGIBLE_UNIQUE),
        UPDATE(TokenUpdate, DEFAULT),
        PRNG(HederaFunctionality.UtilPrng, DEFAULT);

        final HederaFunctionality functionality;
        final SubType subtype;

        GasCostType(final HederaFunctionality functionality, final SubType subtype) {
            this.functionality = functionality;
            this.subtype = subtype;
        }
    }
}
