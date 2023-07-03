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

package com.hedera.services.txns.validation;

import static com.hedera.services.utils.EntityIdUtils.asTypedEvmAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.store.Store;
import com.hedera.mirror.web3.evm.store.Store.OnMissing;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.time.Instant;

/**
 * Copied Logic type from hedera-services. Unnecessary methods are deleted.
 */
public class ContextOptionValidator implements OptionValidator {

    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    public ContextOptionValidator(final MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
    }

    public static ResponseCodeEnum batchSizeCheck(final int length, final int limit) {
        return lengthCheck(length, limit, ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED);
    }

    private static ResponseCodeEnum lengthCheck(final long length, final long limit, final ResponseCodeEnum onFailure) {
        if (length > limit) {
            return onFailure;
        }
        return OK;
    }

    @Override
    public ResponseCodeEnum maxBatchSizeBurnCheck(final int length) {
        return batchSizeCheck(length, mirrorNodeEvmProperties.getMaxBatchSizeBurn());
    }

    @Override
    public ResponseCodeEnum maxBatchSizeMintCheck(final int length) {
        return batchSizeCheck(length, mirrorNodeEvmProperties.getMaxBatchSizeMint());
    }

    @Override
    public ResponseCodeEnum nftMetadataCheck(final byte[] metadata) {
        return lengthCheck(
                metadata.length, mirrorNodeEvmProperties.getMaxNftMetadataBytes(), ResponseCodeEnum.METADATA_TOO_LONG);
    }

    @Override
    public boolean isValidExpiry(Timestamp expiry) {
        final var now = Instant.now();
        final var then = Instant.ofEpochSecond(expiry.getSeconds(), expiry.getNanos());
        return then.isAfter(now);
    }

    public ResponseCodeEnum expiryStatusGiven(final Store store, final AccountID id) {
        var account = store.getAccount(asTypedEvmAddress(id), OnMissing.THROW);
        if (!mirrorNodeEvmProperties.shouldAutoRenewSomeEntityType()) {
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
        return (isContract && !mirrorNodeEvmProperties.shouldAutoRenewContracts())
                || (!isContract && !mirrorNodeEvmProperties.shouldAutoRenewAccounts());
    }
}
