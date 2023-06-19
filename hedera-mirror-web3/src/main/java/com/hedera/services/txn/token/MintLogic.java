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

package com.hedera.services.txn.token;

import static com.hedera.services.state.submerkle.RichInstant.fromJava;
import static com.hedera.services.txn.token.TokenOpsValidator.validateTokenOpsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;

import com.google.protobuf.ByteString;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.node.app.service.evm.store.tokens.TokenType;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.OwnershipTracker;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.List;

/**
 * Copied Logic type from hedera-services.
 *
 * Differences with the original:
 *  1. Removed validations performed in UsageLimits, since they check global node limits,
 *  while on Archive Node we are interested in transaction scope only
 *  2. Use abstraction for the state by introducing {@link Store} interface
 *  3. Use Mirror Node specific properties - {@link MirrorNodeEvmProperties}
 *  4. Use copied models from hedera-services which are enhanced with additional constructors for easier setup,
 *  those are {@link Account}, {@link Token}, {@link TokenRelationship}
 *  5. validateSyntax method uses default value of true for areNftsEnabled property
 * */
public class MintLogic {

    private final OptionValidator validator;

    public MintLogic(final OptionValidator validator) {
        this.validator = validator;
    }

    public void mint(
            final Id targetId,
            final long amount,
            final List<ByteString> metaDataList,
            final Instant consensusTime,
            final Store store) {

        /* --- Load the model objects --- */
        final var token = store.getToken(targetId.asEvmAddress(), OnMissing.THROW);

        final var tokenRelationshipKey = new TokenRelationshipKey(
                token.getId().asEvmAddress(), token.getTreasury().getId().asEvmAddress());
        final var treasuryRel = store.getTokenRelationship(tokenRelationshipKey, OnMissing.THROW);

        /* --- Instantiate change trackers --- */
        final var ownershipTracker = new OwnershipTracker();

        /* --- Do the business logic --- */
        if (token.getType() == TokenType.FUNGIBLE_COMMON) {
            token.mint(treasuryRel, amount, false);
        } else {
            token.mint(ownershipTracker, treasuryRel, metaDataList, fromJava(consensusTime));
        }

        /* --- Persist the updated models --- */
        store.updateToken(token);
        store.updateTokenRelationship(treasuryRel);
        store.updateAccount(token.getTreasury());
    }

    public ResponseCodeEnum validateSyntax(final TransactionBody txn) {
        TokenMintTransactionBody op = txn.getTokenMint();

        if (!op.hasToken()) {
            return INVALID_TOKEN_ID;
        }

        return validateTokenOpsWith(
                op.getMetadataCount(),
                op.getAmount(),
                true,
                INVALID_TOKEN_MINT_AMOUNT,
                op.getMetadataList(),
                validator::maxBatchSizeMintCheck,
                validator::nftMetadataCheck);
    }
}
