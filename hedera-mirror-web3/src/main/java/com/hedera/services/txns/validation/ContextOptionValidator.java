/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.txns.validation;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.StackedStateFrames;
import com.hedera.services.store.models.Account;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ContextOptionValidator {
    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Inject
    public ContextOptionValidator(final MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
    }

    public ResponseCodeEnum expiryStatusGiven(final StackedStateFrames<Object> stackedStateFrames, final AccountID id) {
        final var topFrame = stackedStateFrames.top();
        final var accountAccessor = topFrame.getAccessor(Account.class);
        final var account = accountAccessor.get(id).orElseThrow(); // TODO: change?
        if (!mirrorNodeEvmProperties.isAtLeastOneAutoRenewTargetType()) {
            return OK;
        }
        final var balance = account.getBalance();
        if (balance > 0) {
            return OK;
        }
        final var isDetached = (account.getExpiry() < System.currentTimeMillis() / 1000);
        if (!isDetached) {
            return OK;
        }
        final var isContract = account.isSmartContract();
        return expiryStatusForNominallyDetached(isContract);
    }

    public ResponseCodeEnum expiryStatusGiven(final long balance, final boolean isDetached, final boolean isContract) {
        if (balance > 0 || !isDetached) {
            return OK;
        }
        return expiryStatusForNominallyDetached(isContract);
    }

    private ResponseCodeEnum expiryStatusForNominallyDetached(final boolean isContract) {
        if (isExpiryDisabled(isContract)) {
            return OK;
        }
        return isContract ? CONTRACT_EXPIRED_AND_PENDING_REMOVAL : ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
    }

    private boolean isExpiryDisabled(final boolean isContract) {
        return (isContract && !mirrorNodeEvmProperties.isExpireContracts())
                || (!isContract && !mirrorNodeEvmProperties.isExpireAccounts());
    }
}
