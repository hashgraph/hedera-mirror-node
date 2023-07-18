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

package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateTrue;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.BYTES32;
import static com.hedera.services.hapi.utils.contracts.ParsingConstants.INT;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils.GasCostType.DELETE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.services.store.contracts.precompile.AbiConstants;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.BodyParams;
import com.hedera.services.store.contracts.precompile.codec.DeleteWrapper;
import com.hedera.services.store.contracts.precompile.codec.EmptyRunResult;
import com.hedera.services.store.contracts.precompile.codec.RunResult;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.txn.token.DeleteLogic;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * This class is a modified copy of DeleteTokenPrecompile from hedera-services repo.
 * <p>
 * Differences with the original:
 * 1. Implements a modified {@link Precompile} interface
 * 2. Removed class fields and adapted constructors in order to achieve stateless behaviour
 * 3. Body method is modified to accept {@link BodyParams} argument in order to achieve stateless behaviour
 * 4. getMinimumFeeInTinybars method is modified to accept {@link BodyParams} argument in order to achieve stateless behaviour
 */
public class DeleteTokenPrecompile extends AbstractWritePrecompile {
    private static final Function DELETE_TOKEN_FUNCTION = new Function("deleteToken(address)", INT);
    private static final Bytes DELETE_TOKEN_SELECTOR = Bytes.wrap(DELETE_TOKEN_FUNCTION.selector());
    private static final ABIType<Tuple> DELETE_TOKEN_DECODER = TypeFactory.create(BYTES32);
    private final SyntheticTxnFactory syntheticTxnFactory;
    private final DeleteLogic deleteLogic;

    public DeleteTokenPrecompile(
            PrecompilePricingUtils precompilePricingUtils,
            SyntheticTxnFactory syntheticTxnFactory,
            DeleteLogic deleteLogic) {
        super(precompilePricingUtils);
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.deleteLogic = deleteLogic;
    }

    public static DeleteWrapper decodeDelete(final Bytes input) {
        final Tuple decodedArguments = decodeFunctionCall(input, DELETE_TOKEN_SELECTOR, DELETE_TOKEN_DECODER);
        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));

        return new DeleteWrapper(tokenID);
    }

    @Override
    public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver, BodyParams bodyParams) {
        final var deleteOp = decodeDelete(input);
        return syntheticTxnFactory.createDelete(deleteOp);
    }

    @Override
    public long getMinimumFeeInTinybars(Timestamp consensusTime, final TransactionBody transactionBody) {
        Objects.requireNonNull(transactionBody, "`body` method should be called before `getMinimumFeeInTinybars`");
        return pricingUtils.getMinimumPriceInTinybars(DELETE, consensusTime);
    }

    @Override
    public RunResult run(MessageFrame frame, Store store, TransactionBody transactionBody) {
        Objects.requireNonNull(transactionBody);
        final var tokenId = transactionBody.getTokenDeletion().getToken();

        /* --- Build the necessary infrastructure to execute the transaction --- */
        final var validity = deleteLogic.validate(transactionBody);
        validateTrue(validity == OK, validity);

        /* --- Execute the transaction and capture its results --- */
        deleteLogic.delete(tokenId, store);

        return new EmptyRunResult();
    }

    @Override
    public Set<Integer> getFunctionSelectors() {
        return Set.of(AbiConstants.ABI_ID_DELETE_TOKEN);
    }
}
