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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hedera.mirror.web3.evm.store.accessor.model.TokenRelationshipKey;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import java.util.ArrayList;
import java.util.List;
import org.hyperledger.besu.datatypes.Address;

/**
 * Copied Logic type from hedera-services. Differences with the original:
 *  1. Removed validations performed in UsageLimits, since they check global node limits,
 *  while on Archive Node we are interested in transaction scope only
 *  2. Use abstraction for the state by introducing {@link Store} interface
 *  3. Use Mirror Node specific properties - {@link MirrorNodeEvmProperties}
 *  4. Use copied models from hedera-services which are enhanced with additional constructors and/or lombok generated builder for easier setup,
 *  those are {@link Account}, {@link Token}, {@link TokenRelationship}
 * */
public class AssociateLogic {
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    public AssociateLogic(final MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
    }

    public void associate(final Address accountAddress, final List<Address> tokensAddresses, final Store store) {
        /* Load the models */
        final var account = store.getAccount(accountAddress, OnMissing.THROW);
        final var tokens = tokensAddresses.stream()
                .map(t -> store.getFungibleToken(t, OnMissing.THROW))
                .toList();

        /* Associate and commit the changes */
        final var newTokenRelationships = associateWith(account, tokens, store);

        newTokenRelationships.forEach(store::updateTokenRelationship);
    }

    private List<TokenRelationship> associateWith(final Account account, final List<Token> tokens, final Store store) {
        int numAssociations = account.getNumAssociations();
        final var proposedTotalAssociations = tokens.size() + numAssociations;

        validateFalse(exceedsTokenAssociationLimit(proposedTotalAssociations), TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);

        final var updatedAccount = account.setNumAssociations(proposedTotalAssociations);

        final List<TokenRelationship> newModelRels = new ArrayList<>();
        for (final var token : tokens) {
            TokenRelationshipKey tokenRelationshipKey =
                    new TokenRelationshipKey(token.getId().asEvmAddress(), account.getAccountAddress());

            validateFalse(hasAssociation(tokenRelationshipKey, store), TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT);

            final var newRel = new TokenRelationship(token, updatedAccount);
            numAssociations++;
            newModelRels.add(newRel);
        }

        store.updateAccount(updatedAccount);

        return newModelRels;
    }

    private boolean exceedsTokenAssociationLimit(final int totalAssociations) {
        return totalAssociations > mirrorNodeEvmProperties.getMaxTokensPerAccount();
    }

    private boolean hasAssociation(final TokenRelationshipKey tokenRelationshipKey, final Store store) {
        return store.getTokenRelationship(tokenRelationshipKey, OnMissing.DONT_THROW)
                        .getAccount()
                        .getId()
                        .num()
                > 0;
    }
}
