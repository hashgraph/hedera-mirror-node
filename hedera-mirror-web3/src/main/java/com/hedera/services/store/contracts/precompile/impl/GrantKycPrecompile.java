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

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.ADDRESS_PAIR_RAW_TYPE;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.INT;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GRANT_TOKEN_KYC;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.GRANT_KYC;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GrantRevokeKycWrapper;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.EmptyRunResult;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.store.models.Id;
import com.hedera.services.txn.token.GrantKycLogic;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * This class is a modified copy of GrantKycPrecompile from hedera-services repo.
 *
 * Differences with the original:
 *  1. Implements a modified {@link Precompile} interface
 *  2. Removed class fields and adapted constructors in order to achieve stateless behaviour
 *  3. Body method is modified to accept {@link BodyParams} argument in order to achieve stateless behaviour
 *  4. Run method accepts Store argument in order to achieve stateless behaviour and returns {@link RunResult}
 */
public class GrantKycPrecompile extends AbstractGrantRevokeKycPrecompile {
    private static final Function GRANT_TOKEN_KYC_FUNCTION = new Function("grantTokenKyc(address,address)", INT);
    private static final Bytes GRANT_TOKEN_KYC_FUNCTION_SELECTOR = Bytes.wrap(GRANT_TOKEN_KYC_FUNCTION.selector());
    private static final ABIType<Tuple> GRANT_TOKEN_KYC_FUNCTION_DECODER = TypeFactory.create(ADDRESS_PAIR_RAW_TYPE);

    private final GrantKycLogic grantKycLogic;
    private GrantRevokeKycWrapper<TokenID, AccountID> grantOp;

    public GrantKycPrecompile(
            final GrantKycLogic grantKycLogic,
            final SyntheticTxnFactory syntheticTxnFactory,
            final PrecompilePricingUtils pricingUtils) {
        super(syntheticTxnFactory, pricingUtils);
        this.grantKycLogic = grantKycLogic;
    }

    @Override
    public RunResult run(MessageFrame frame, Store store, TransactionBody transactionBody) {
        // --- Init ---

        requireNonNull(grantOp);
        final var tokenId = Id.fromGrpcToken(grantOp.token());
        final var accountId = Id.fromGrpcAccount(grantOp.account());

        // --- Execute the transaction and capture its results ---

        final var validity = grantKycLogic.validate(transactionBody);
        validateTrue(validity == OK, validity);

        grantKycLogic.grantKyc(tokenId, accountId, store);

        return new EmptyRunResult();
    }

    @Override
    public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver, BodyParams bodyParams) {
        grantOp = decodeGrantTokenKyc(input, aliasResolver);
        return syntheticTxnFactory.createGrantKyc(grantOp);
    }

    @Override
    public long getMinimumFeeInTinybars(Timestamp consensusTime, TransactionBody transactionBody) {
        requireNonNull(grantOp);
        return pricingUtils.getMinimumPriceInTinybars(GRANT_KYC, consensusTime);
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(ABI_ID_GRANT_TOKEN_KYC);
    }

    public static GrantRevokeKycWrapper<TokenID, AccountID> decodeGrantTokenKyc(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(input, GRANT_TOKEN_KYC_FUNCTION_SELECTOR, GRANT_TOKEN_KYC_FUNCTION_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var accountID = convertLeftPaddedAddressToAccountId(decodedArguments.get(1), aliasResolver);

        return new GrantRevokeKycWrapper<>(tokenID, accountID);
    }
}
