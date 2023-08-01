/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.evm.config;

import com.hedera.mirror.web3.evm.pricing.RatesAndFeesLoader;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.contract.EntityAddressSequencer;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.services.contracts.gascalculator.GasCalculatorHederaV22;
import com.hedera.services.fees.BasicHbarCentExchange;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calc.OverflowCheckingCalc;
import com.hedera.services.fees.calculation.BasicFcfsUsagePrices;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.fees.calculation.UsageBasedFeeCalculator;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.fees.calculation.crypto.queries.GetTxnRecordResourceUsage;
import com.hedera.services.fees.calculation.token.txns.TokenAssociateResourceUsage;
import com.hedera.services.fees.calculation.token.txns.TokenDeleteResourceUsage;
import com.hedera.services.fees.calculation.token.txns.TokenDissociateResourceUsage;
import com.hedera.services.fees.calculation.utils.AccessorBasedUsages;
import com.hedera.services.fees.calculation.utils.OpUsageCtxHelper;
import com.hedera.services.fees.calculation.utils.PricedUsageCalculator;
import com.hedera.services.fees.pricing.AssetsLoader;
import com.hedera.services.fees.usage.token.TokenOpsUsage;
import com.hedera.services.hapi.fees.usage.EstimatorFactory;
import com.hedera.services.hapi.fees.usage.TxnUsageEstimator;
import com.hedera.services.hapi.fees.usage.crypto.CryptoOpsUsage;
import com.hedera.services.hapi.utils.fees.CryptoFeeBuilder;
import com.hedera.services.ledger.TransferLogic;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.PrecompileMapper;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.TokenUpdateLogic;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.impl.ApprovePrecompile;
import com.hedera.services.store.contracts.precompile.impl.AssociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.BurnPrecompile;
import com.hedera.services.store.contracts.precompile.impl.DeleteTokenPrecompile;
import com.hedera.services.store.contracts.precompile.impl.DissociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.ERCTransferPrecompile;
import com.hedera.services.store.contracts.precompile.impl.FreezeTokenPrecompile;
import com.hedera.services.store.contracts.precompile.impl.GrantKycPrecompile;
import com.hedera.services.store.contracts.precompile.impl.MintPrecompile;
import com.hedera.services.store.contracts.precompile.impl.MultiAssociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.MultiDissociatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.PausePrecompile;
import com.hedera.services.store.contracts.precompile.impl.RevokeKycPrecompile;
import com.hedera.services.store.contracts.precompile.impl.SetApprovalForAllPrecompile;
import com.hedera.services.store.contracts.precompile.impl.TokenCreatePrecompile;
import com.hedera.services.store.contracts.precompile.impl.TransferPrecompile;
import com.hedera.services.store.contracts.precompile.impl.UnfreezeTokenPrecompile;
import com.hedera.services.store.contracts.precompile.impl.UnpausePrecompile;
import com.hedera.services.store.contracts.precompile.impl.WipeFungiblePrecompile;
import com.hedera.services.store.contracts.precompile.impl.WipeNonFungiblePrecompile;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txn.token.AssociateLogic;
import com.hedera.services.txn.token.BurnLogic;
import com.hedera.services.txn.token.CreateLogic;
import com.hedera.services.txn.token.DeleteLogic;
import com.hedera.services.txn.token.DissociateLogic;
import com.hedera.services.txn.token.FreezeLogic;
import com.hedera.services.txn.token.GrantKycLogic;
import com.hedera.services.txn.token.MintLogic;
import com.hedera.services.txn.token.PauseLogic;
import com.hedera.services.txn.token.RevokeKycLogic;
import com.hedera.services.txn.token.UnfreezeLogic;
import com.hedera.services.txn.token.UnpauseLogic;
import com.hedera.services.txn.token.WipeLogic;
import com.hedera.services.txns.crypto.ApproveAllowanceLogic;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.txns.crypto.DeleteAllowanceLogic;
import com.hedera.services.txns.crypto.validators.ApproveAllowanceChecks;
import com.hedera.services.txns.crypto.validators.DeleteAllowanceChecks;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for beans related to com.hedera.services components
 */
@Configuration
public class ServicesConfiguration {

    @Bean
    GasCalculatorHederaV22 gasCalculatorHederaV22(
            BasicFcfsUsagePrices usagePricesProvider, BasicHbarCentExchange hbarCentExchange) {
        return new GasCalculatorHederaV22(usagePricesProvider, hbarCentExchange);
    }

    @Bean
    BasicFcfsUsagePrices basicFcfsUsagePrices(RatesAndFeesLoader ratesAndFeesLoader) {
        return new BasicFcfsUsagePrices(ratesAndFeesLoader);
    }

    @Bean
    OverflowCheckingCalc overflowCheckingCalc() {
        return new OverflowCheckingCalc();
    }

    @Bean
    PricedUsageCalculator pricedUsageCalculator(
            AccessorBasedUsages accessorBasedUsages, OverflowCheckingCalc overflowCheckingCalc) {
        return new PricedUsageCalculator(accessorBasedUsages, overflowCheckingCalc);
    }

    @Bean
    EstimatorFactory estimatorFactory() {
        return TxnUsageEstimator::new;
    }

    @Bean
    TokenAssociateResourceUsage tokenAssociateResourceUsage(final EstimatorFactory estimatorFactory) {
        return new TokenAssociateResourceUsage(estimatorFactory);
    }

    @Bean
    TokenDeleteResourceUsage tokenDeleteResourceUsage(final EstimatorFactory estimatorFactory) {
        return new TokenDeleteResourceUsage(estimatorFactory);
    }

    @Bean
    TokenDissociateResourceUsage tokenDissociateResourceUsage(final EstimatorFactory estimatorFactory) {
        return new TokenDissociateResourceUsage(estimatorFactory);
    }

    @Bean
    UsageBasedFeeCalculator usageBasedFeeCalculator(
            HbarCentExchange hbarCentExchange,
            UsagePricesProvider usagePricesProvider,
            PricedUsageCalculator pricedUsageCalculator,
            Set<QueryResourceUsageEstimator> queryResourceUsageEstimators,
            List<TxnResourceUsageEstimator> txnResourceUsageEstimators) {
        final Map<HederaFunctionality, List<TxnResourceUsageEstimator>> txnUsageEstimators =
                new EnumMap<>(HederaFunctionality.class);

        for (final var estimator : txnResourceUsageEstimators) {
            if (estimator.toString().contains("TokenAssociate")) {
                txnUsageEstimators.put(HederaFunctionality.TokenAssociateToAccount, List.of(estimator));
            }
            if (estimator.toString().contains("TokenDissociate")) {
                txnUsageEstimators.put(HederaFunctionality.TokenDissociateFromAccount, List.of(estimator));
            }
            if (estimator.toString().contains("TokenDelete")) {
                txnUsageEstimators.put(HederaFunctionality.TokenDelete, List.of(estimator));
            }
        }

        return new UsageBasedFeeCalculator(
                hbarCentExchange,
                usagePricesProvider,
                pricedUsageCalculator,
                queryResourceUsageEstimators,
                txnUsageEstimators);
    }

    @Bean
    ExpandHandleSpanMapAccessor expandHandleSpanMapAccessor() {
        return new ExpandHandleSpanMapAccessor();
    }

    @Bean
    AssetsLoader assetsLoader() {
        return new AssetsLoader();
    }

    @Bean
    AccessorFactory accessorFactory() {
        return new AccessorFactory();
    }

    @Bean
    PrecompilePricingUtils precompilePricingUtils(
            final AssetsLoader assetsLoader,
            final BasicHbarCentExchange exchange,
            final FeeCalculator feeCalculator,
            final BasicFcfsUsagePrices resourceCosts,
            final AccessorFactory accessorFactory) {
        return new PrecompilePricingUtils(assetsLoader, exchange, feeCalculator, resourceCosts, accessorFactory);
    }

    @Bean
    CryptoFeeBuilder cryptoFeeBuilder() {
        return new CryptoFeeBuilder();
    }

    @Bean
    GetTxnRecordResourceUsage getTxnRecordResourceUsage(CryptoFeeBuilder cryptoFeeBuilder) {
        return new GetTxnRecordResourceUsage(cryptoFeeBuilder);
    }

    @Bean
    BasicHbarCentExchange basicHbarCentExchange(RatesAndFeesLoader ratesAndFeesLoader) {
        return new BasicHbarCentExchange(ratesAndFeesLoader);
    }

    @Bean
    AssociatePrecompile associatePrecompile(
            final PrecompilePricingUtils precompilePricingUtils,
            final SyntheticTxnFactory syntheticTxnFactory,
            final AssociateLogic associateLogic) {
        return new AssociatePrecompile(precompilePricingUtils, syntheticTxnFactory, associateLogic);
    }

    @Bean
    MultiAssociatePrecompile multiAssociatePrecompile(
            final PrecompilePricingUtils precompilePricingUtils,
            final SyntheticTxnFactory syntheticTxnFactory,
            final AssociateLogic associateLogic) {
        return new MultiAssociatePrecompile(precompilePricingUtils, syntheticTxnFactory, associateLogic);
    }

    @Bean
    PrecompileMapper precompileMapper(final Set<Precompile> precompiles) {
        return new PrecompileMapper(precompiles);
    }

    @Bean
    OptionValidator optionValidator(final MirrorNodeEvmProperties properties) {
        return new ContextOptionValidator(properties);
    }

    @Bean
    EncodingFacade encodingFacade() {
        return new EncodingFacade();
    }

    @Bean
    SyntheticTxnFactory syntheticTxnFactory() {
        return new SyntheticTxnFactory();
    }

    @Bean
    DeleteAllowanceLogic deleteAllowanceLogic() {
        return new DeleteAllowanceLogic();
    }

    @Bean
    AssociateLogic associateLogic(MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        return new AssociateLogic(mirrorNodeEvmProperties);
    }

    @Bean
    ApproveAllowanceLogic approveAllowanceLogic() {
        return new ApproveAllowanceLogic();
    }

    @Bean
    AutoCreationLogic autocreationLogic(
            FeeCalculator feeCalculator, EvmProperties evmProperties, SyntheticTxnFactory syntheticTxnFactory) {
        return new AutoCreationLogic(feeCalculator, evmProperties, syntheticTxnFactory);
    }

    @Bean
    MintPrecompile mintPrecompile(
            PrecompilePricingUtils precompilePricingUtils,
            EncodingFacade encodingFacade,
            SyntheticTxnFactory syntheticTxnFactory,
            MintLogic mintLogic) {
        return new MintPrecompile(precompilePricingUtils, encodingFacade, syntheticTxnFactory, mintLogic);
    }

    @Bean
    MintLogic mintLogic(OptionValidator optionValidator) {
        return new MintLogic(optionValidator);
    }

    @Bean
    BurnLogic burnLogic(OptionValidator optionValidator) {
        return new BurnLogic(optionValidator);
    }

    @Bean
    DissociateLogic dissociateLogic() {
        return new DissociateLogic();
    }

    @Bean
    TransferLogic transferLogic(AutoCreationLogic autoCreationLogic) {
        return new TransferLogic(autoCreationLogic);
    }

    @Bean
    TransferPrecompile transferPrecompile(
            final PrecompilePricingUtils pricingUtils,
            final MirrorNodeEvmProperties mirrorNodeEvmProperties,
            final TransferLogic transferLogic,
            final ContextOptionValidator contextOptionValidator,
            final AutoCreationLogic autoCreationLogic,
            final SyntheticTxnFactory syntheticTxnFactory,
            final EntityAddressSequencer entityAddressSequencer) {
        return new TransferPrecompile(
                pricingUtils,
                mirrorNodeEvmProperties,
                transferLogic,
                contextOptionValidator,
                autoCreationLogic,
                syntheticTxnFactory,
                entityAddressSequencer);
    }

    @Bean
    ERCTransferPrecompile ercTransferPrecompile(
            final PrecompilePricingUtils pricingUtils,
            final MirrorNodeEvmProperties mirrorNodeEvmProperties,
            final TransferLogic transferLogic,
            final ContextOptionValidator contextOptionValidator,
            final AutoCreationLogic autoCreationLogic,
            final SyntheticTxnFactory syntheticTxnFactory,
            final EncodingFacade encoder,
            final EntityAddressSequencer entityAddressSequencer) {
        return new ERCTransferPrecompile(
                pricingUtils,
                mirrorNodeEvmProperties,
                transferLogic,
                contextOptionValidator,
                autoCreationLogic,
                syntheticTxnFactory,
                entityAddressSequencer,
                encoder);
    }

    @Bean
    DissociatePrecompile dissociatePrecompile(
            final PrecompilePricingUtils precompilePricingUtils,
            final SyntheticTxnFactory syntheticTxnFactory,
            final DissociateLogic dissociateLogic) {
        return new DissociatePrecompile(precompilePricingUtils, syntheticTxnFactory, dissociateLogic);
    }

    @Bean
    MultiDissociatePrecompile multiDissociatePrecompile(
            final PrecompilePricingUtils precompilePricingUtils,
            final SyntheticTxnFactory syntheticTxnFactory,
            final DissociateLogic dissociateLogic) {
        return new MultiDissociatePrecompile(precompilePricingUtils, syntheticTxnFactory, dissociateLogic);
    }

    @Bean
    CreateLogic createLogic(final MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        return new CreateLogic(mirrorNodeEvmProperties);
    }

    @Bean
    GrantKycLogic grantKycLogic() {
        return new GrantKycLogic();
    }

    @Bean
    GrantKycPrecompile grantKycPrecompile(
            final GrantKycLogic grantKycLogic,
            final SyntheticTxnFactory syntheticTxnFactory,
            final PrecompilePricingUtils pricingUtils) {
        return new GrantKycPrecompile(grantKycLogic, syntheticTxnFactory, pricingUtils);
    }

    @Bean
    BurnPrecompile burnPrecompile(
            final PrecompilePricingUtils pricingUtils,
            final EncodingFacade encoder,
            final SyntheticTxnFactory syntheticTxnFactory,
            final BurnLogic burnLogic) {
        return new BurnPrecompile(pricingUtils, encoder, syntheticTxnFactory, burnLogic);
    }

    @Bean
    ApproveAllowanceChecks approveAllowanceChecks() {
        return new ApproveAllowanceChecks();
    }

    @Bean
    DeleteAllowanceChecks deleteAllowanceChecks() {
        return new DeleteAllowanceChecks();
    }

    @Bean
    ApprovePrecompile approvePrecompile(
            final EncodingFacade encoder,
            final SyntheticTxnFactory syntheticTxnFactory,
            final PrecompilePricingUtils pricingUtils,
            final ApproveAllowanceLogic approveAllowanceLogic,
            final DeleteAllowanceLogic deleteAllowanceLogic,
            final ApproveAllowanceChecks approveAllowanceChecks,
            final DeleteAllowanceChecks deleteAllowanceChecks) {
        return new ApprovePrecompile(
                encoder,
                syntheticTxnFactory,
                pricingUtils,
                approveAllowanceLogic,
                deleteAllowanceLogic,
                approveAllowanceChecks,
                deleteAllowanceChecks);
    }

    @Bean
    TokenOpsUsage tokenOpsUsage() {
        return new TokenOpsUsage();
    }

    @Bean
    CryptoOpsUsage cryptoOpsUsage() {
        return new CryptoOpsUsage();
    }

    @Bean
    OpUsageCtxHelper opUsageCtxHelper() {
        return new OpUsageCtxHelper();
    }

    @Bean
    AccessorBasedUsages accessorBasedUsages(
            final TokenOpsUsage tokenOpsUsage,
            final CryptoOpsUsage cryptoOpsUsage,
            final OpUsageCtxHelper opUsageCtxHelper) {
        return new AccessorBasedUsages(tokenOpsUsage, cryptoOpsUsage, opUsageCtxHelper);
    }

    @Bean
    WipeLogic wipeLogic(MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        return new WipeLogic(mirrorNodeEvmProperties);
    }

    @Bean
    WipeFungiblePrecompile wipeFungiblePrecompile(
            PrecompilePricingUtils precompilePricingUtils,
            SyntheticTxnFactory syntheticTxnFactory,
            WipeLogic wipeLogic) {
        return new WipeFungiblePrecompile(precompilePricingUtils, syntheticTxnFactory, wipeLogic);
    }

    @Bean
    WipeNonFungiblePrecompile wipeNonFungiblePrecompile(
            PrecompilePricingUtils precompilePricingUtils,
            SyntheticTxnFactory syntheticTxnFactory,
            WipeLogic wipeLogic) {
        return new WipeNonFungiblePrecompile(precompilePricingUtils, syntheticTxnFactory, wipeLogic);
    }

    @Bean
    RevokeKycLogic revokeKycLogic() {
        return new RevokeKycLogic();
    }

    @Bean
    RevokeKycPrecompile revokeKycPrecompile(
            final RevokeKycLogic revokeKycLogic,
            final SyntheticTxnFactory syntheticTxnFactory,
            final PrecompilePricingUtils precompilePricingUtils) {
        return new RevokeKycPrecompile(revokeKycLogic, syntheticTxnFactory, precompilePricingUtils);
    }

    @Bean
    TokenCreatePrecompile tokenCreatePrecompile(
            PrecompilePricingUtils precompilePricingUtils,
            EncodingFacade encodingFacade,
            SyntheticTxnFactory syntheticTxnFactory,
            OptionValidator validator,
            CreateLogic createLogic) {
        return new TokenCreatePrecompile(
                precompilePricingUtils, encodingFacade, syntheticTxnFactory, validator, createLogic);
    }

    @Bean
    SetApprovalForAllPrecompile setApprovalForAllPrecompile(
            final SyntheticTxnFactory syntheticTxnFactory,
            final PrecompilePricingUtils pricingUtils,
            final ApproveAllowanceChecks approveAllowanceChecks,
            final ApproveAllowanceLogic approveAllowanceLogic) {
        return new SetApprovalForAllPrecompile(
                syntheticTxnFactory, pricingUtils, approveAllowanceChecks, approveAllowanceLogic);
    }

    @Bean
    DeleteLogic deleteLogic() {
        return new DeleteLogic();
    }

    @Bean
    DeleteTokenPrecompile deleteTokenPrecompile(
            PrecompilePricingUtils precompilePricingUtils,
            SyntheticTxnFactory syntheticTxnFactory,
            DeleteLogic deleteLogic) {
        return new DeleteTokenPrecompile(precompilePricingUtils, syntheticTxnFactory, deleteLogic);
    }

    @Bean
    UnpauseLogic unpauseLogic() {
        return new UnpauseLogic();
    }

    @Bean
    UnpausePrecompile unpausePrecompile(
            final UnpauseLogic unpauseLogic,
            final SyntheticTxnFactory syntheticTxnFactory,
            final PrecompilePricingUtils pricingUtils) {
        return new UnpausePrecompile(pricingUtils, syntheticTxnFactory, unpauseLogic);
    }

    @Bean
    FreezeLogic freezeLogic() {
        return new FreezeLogic();
    }

    @Bean
    FreezeTokenPrecompile freezeTokenPrecompile(
            PrecompilePricingUtils precompilePricingUtils,
            SyntheticTxnFactory syntheticTxnFactory,
            FreezeLogic freezeLogic) {
        return new FreezeTokenPrecompile(precompilePricingUtils, syntheticTxnFactory, freezeLogic);
    }

    @Bean
    UnfreezeLogic unfreezeLogic() {
        return new UnfreezeLogic();
    }

    @Bean
    UnfreezeTokenPrecompile unfreezeTokenPrecompile(
            PrecompilePricingUtils precompilePricingUtils,
            SyntheticTxnFactory syntheticTxnFactory,
            UnfreezeLogic unfreezeLogic) {
        return new UnfreezeTokenPrecompile(precompilePricingUtils, syntheticTxnFactory, unfreezeLogic);
    }

    @Bean
    PauseLogic pauseLogic() {
        return new PauseLogic();
    }

    @Bean
    PausePrecompile pausePrecompile(
            PrecompilePricingUtils precompilePricingUtils,
            SyntheticTxnFactory syntheticTxnFactory,
            PauseLogic pauseLogic) {
        return new PausePrecompile(precompilePricingUtils, syntheticTxnFactory, pauseLogic);
    }

    @Bean
    TokenUpdateLogic tokenUpdateLogic(MirrorNodeEvmProperties mirrorNodeEvmProperties, OptionValidator validator) {
        return new TokenUpdateLogic(mirrorNodeEvmProperties, validator);
    }
}
