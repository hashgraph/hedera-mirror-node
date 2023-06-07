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

import static com.hedera.node.app.service.evm.utils.ValidationUtils.validateFalse;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.CachingStateFrame;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import java.util.ArrayList;
import java.util.List;
import org.hyperledger.besu.datatypes.Address;

public class AssociateLogic {
    private final StackedStateFrames<Object> stackedStateFrames;
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    public AssociateLogic(
            final StackedStateFrames<Object> stackedStateFrames,
            final MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        this.stackedStateFrames = stackedStateFrames;
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
    }

    public void associate(final Address accountAddress, final List<Address> tokensAddresses) {
        final var frame = stackedStateFrames.top();

        /* Load the models */
        final var account = loadAccount(frame, accountAddress);
        final var tokens = tokensAddresses.stream()
                .map(tokenAddress -> loadToken(frame, tokenAddress))
                .toList();

        /* Associate and commit the changes */
        final var newTokenRelationships = associateWith(account, tokens, false);

        final var relationshipAccessor = frame.getAccessor(TokenRelationship.class);
        newTokenRelationships.forEach(
                relationship -> relationshipAccessor.set(keyFromRelationship(relationship), relationship));

        frame.commit();
    }

    private Account loadAccount(CachingStateFrame<Object> frame, Address accountAddress) {
        return frame.getAccessor(Account.class)
                .get(accountAddress)
                .orElseThrow(() -> failAssociationException("account", accountAddress));
    }

    private Token loadToken(CachingStateFrame<Object> frame, Address tokenAddress) {
        return frame.getAccessor(Token.class)
                .get(tokenAddress)
                .orElseThrow(() -> failAssociationException("token", tokenAddress));
    }

    private InvalidTransactionException failAssociationException(String type, Address address) {
        return new InvalidTransactionException(
                FAIL_INVALID, String.format("Association with %s %s failed", type, address), "");
    }

    public List<TokenRelationship> associateWith(
            final Account account, final List<Token> tokens, final boolean shouldEnableRelationship) {
        int numAssociations = account.getNumAssociations();
        final var proposedTotalAssociations = tokens.size() + numAssociations;

        validateFalse(exceedsTokenAssociationLimit(proposedTotalAssociations), TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);

        final List<TokenRelationship> newModelRels = new ArrayList<>();
        for (final var token : tokens) {
            TokenRelationshipKey tokenRelationshipKey = new TokenRelationshipKey(token, account);

            validateFalse(hasAssociation(tokenRelationshipKey), TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT);

            final var newRel = shouldEnableRelationship
                    ? newEnabledRelationship(token, account)
                    : newRelationshipWith(token, account, false);

            numAssociations++;
            newModelRels.add(newRel);
        }

        Account updatedAccount =
                account.modificationBuilder().numAssociations(numAssociations).build();
        stackedStateFrames.top().getAccessor(Account.class).set(updatedAccount.getAccountAddress(), updatedAccount);

        return newModelRels;
    }

    private boolean hasAssociation(TokenRelationshipKey tokenRelationshipKey) {
        return stackedStateFrames
                .top()
                .getAccessor(TokenRelationship.class)
                .get(tokenRelationshipKey)
                .isPresent();
    }

    public TokenRelationship newEnabledRelationship(Token token, Account account) {
        return new TokenRelationship(token, account, 0, false, true, false, true, true, 0);
    }

    public TokenRelationship newRelationshipWith(Token token, Account account, boolean automaticAssociation) {
        boolean frozen = token.hasFreezeKey() && token.isFrozenByDefault();
        return new TokenRelationship(
                token, account, 0, frozen, !token.hasKycKey(), false, true, automaticAssociation, 0);
    }

    private boolean exceedsTokenAssociationLimit(int totalAssociations) {
        return mirrorNodeEvmProperties.isTokenAssociationsLimited()
                && totalAssociations > mirrorNodeEvmProperties.getMaxTokensPerAccount();
    }

    private TokenRelationshipKey keyFromRelationship(TokenRelationship tokenRelationship) {
        final var tokenAddress = tokenRelationship.getToken().getId().asEvmAddress();
        final var accountAddress = tokenRelationship.getAccount().getAccountAddress();
        return new TokenRelationshipKey(tokenAddress, accountAddress);
    }
}
