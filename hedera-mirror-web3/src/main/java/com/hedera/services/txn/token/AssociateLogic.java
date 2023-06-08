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
import com.hedera.mirror.web3.evm.store.CachingStateFrame.Accessor;
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

    private final Accessor<Object, Account> accountAccessor;
    private final Accessor<Object, Token> tokenAccessor;
    private final Accessor<Object, TokenRelationship> tokenRelationshipAccessor;

    public AssociateLogic(
            final StackedStateFrames<Object> stackedStateFrames,
            final MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        this.stackedStateFrames = stackedStateFrames;
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
        accountAccessor = stackedStateFrames.top().getAccessor(Account.class);
        tokenAccessor = stackedStateFrames.top().getAccessor(Token.class);
        tokenRelationshipAccessor = stackedStateFrames.top().getAccessor(TokenRelationship.class);
    }

    public void associate(final Address accountAddress, final List<Address> tokensAddresses) {
        /* Load the models */
        final var account = loadAccount(accountAddress);
        final var tokens = tokensAddresses.stream().map(this::loadToken).toList();

        /* Associate and commit the changes */
        final var newTokenRelationships = associateWith(account, tokens);

        newTokenRelationships.forEach(
                relationship -> tokenRelationshipAccessor.set(keyFromRelationship(relationship), relationship));

        stackedStateFrames.top().commit();
    }

    private Account loadAccount(Address accountAddress) {
        return accountAccessor
                .get(accountAddress)
                .orElseThrow(() -> failAssociationException("account", accountAddress));
    }

    private Token loadToken(Address tokenAddress) {
        return tokenAccessor.get(tokenAddress).orElseThrow(() -> failAssociationException("token", tokenAddress));
    }

    private InvalidTransactionException failAssociationException(String type, Address address) {
        return new InvalidTransactionException(
                FAIL_INVALID, String.format("Association with %s %s failed", type, address), "");
    }

    private List<TokenRelationship> associateWith(final Account account, final List<Token> tokens) {
        int numAssociations = account.getNumAssociations();
        final var proposedTotalAssociations = tokens.size() + numAssociations;

        validateFalse(exceedsTokenAssociationLimit(proposedTotalAssociations), TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);

        Account updatedAccount =
                account.toBuilder().numAssociations(proposedTotalAssociations).build();

        final List<TokenRelationship> newModelRels = new ArrayList<>();
        for (final var token : tokens) {
            TokenRelationshipKey tokenRelationshipKey =
                    new TokenRelationshipKey(token.getId().asEvmAddress(), account.getAccountAddress());

            validateFalse(hasAssociation(tokenRelationshipKey), TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT);

            final var newRel = new TokenRelationship(token, updatedAccount);
            numAssociations++;
            newModelRels.add(newRel);
        }

        accountAccessor.set(updatedAccount.getAccountAddress(), updatedAccount);

        return newModelRels;
    }

    private boolean exceedsTokenAssociationLimit(int totalAssociations) {
        return mirrorNodeEvmProperties.isTokenAssociationsLimited()
                && totalAssociations > mirrorNodeEvmProperties.getMaxTokensPerAccount();
    }

    private boolean hasAssociation(TokenRelationshipKey tokenRelationshipKey) {
        return tokenRelationshipAccessor.get(tokenRelationshipKey).isPresent();
    }

    private TokenRelationshipKey keyFromRelationship(TokenRelationship tokenRelationship) {
        final var tokenAddress = tokenRelationship.getToken().getId().asEvmAddress();
        final var accountAddress = tokenRelationship.getAccount().getAccountAddress();
        return new TokenRelationshipKey(tokenAddress, accountAddress);
    }
}
