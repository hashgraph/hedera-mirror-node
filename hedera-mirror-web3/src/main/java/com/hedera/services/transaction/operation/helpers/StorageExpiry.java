/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.transaction.operation.helpers;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Helper to efficiently get the expiration time for new storage being allocated in a {@link
 * MessageFrame}.
 *
 * <p>Note the allocating contract is given by {@link MessageFrame#getRecipientAddress()}. Now there
 * are two cases: either the contract allocating the storage <i>already existed</i> at the start of
 * the EVM transaction; or we have created the allocating contract in the ongoing EVM transaction.
 *
 * <ol>
 *   <li>In the first case, the storage expiry is just the same as the (well-known) expiry of the
 *       allocating contract---which must be available in the accounts {@code MerkleMap}.
 *   <li>In the second case, the expiry of the newly-created, allocating contract is determined by
 *       its sponsor contract.
 * </ol>
 *
 * Note we say "sponsor <i>chain</i>"---and not simply "sponsor"---because the sponsor of the
 * allocating contract might have itself been created in the ongoing EVM transaction! So we have to
 * walk up the {@code MessageFrame} stack until we find a {@code recipient} contract that already
 * existed; this will be the source of expiries for the whole sponsor chain.
 *
 * <p><b>IMPORTANT:</b> if our stack walk never finds a {@code recipient} that already exists, the
 * of the sponsor chain must begin with a contract that is <i>itself</i> being created this EVM
 * transaction. In this case, the HAPI {@code cryptoCreate} should be the ultimate source of the
 * storage expiry. So we need to option to configure the oracle with a {@code
 * fallbackExpiryFromHapi}.
 */
@Singleton
public class StorageExpiry {
    private static final long UNAVAILABLE_EXPIRY = 0;

    private final AliasManager aliasManager;

    @Inject
    public StorageExpiry(final AliasManager aliasManager) {
        this.aliasManager = aliasManager;
    }

    public class Oracle {
        private final long fallbackExpiry;

        private Oracle(long fallbackExpiryFromHapi) {
            this.fallbackExpiry = fallbackExpiryFromHapi;
        }

        /**
         * Returns the effective expiry for storage being allocated in the current frame.
         *
         * @param frame the active message frame
         * @return the effective expiry for allocated storage
         */
        public long storageExpiryIn(final MessageFrame frame) {
            return 0;
        }
    }

    private class UnusableStaticOracle extends Oracle {
        public UnusableStaticOracle() {
            super(UNAVAILABLE_EXPIRY);
        }

        @Override
        public long storageExpiryIn(final MessageFrame frame) {
            return UNAVAILABLE_EXPIRY;
        }
    }
}
