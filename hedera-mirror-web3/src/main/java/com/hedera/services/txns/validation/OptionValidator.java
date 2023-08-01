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

import com.hedera.mirror.web3.evm.store.Store;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;

/**
 * Copied Logic type from hedera-services. Unnecessary methods are deleted.
 */
public interface OptionValidator {

    ResponseCodeEnum nftMetadataCheck(byte[] metadata);

    ResponseCodeEnum maxBatchSizeMintCheck(int length);

    ResponseCodeEnum maxBatchSizeBurnCheck(int length);

    boolean isValidExpiry(final Timestamp expiry);

    public ResponseCodeEnum expiryStatusGiven(final Store store, final AccountID id);

    ResponseCodeEnum memoCheck(String cand);

    ResponseCodeEnum rawMemoCheck(byte[] cand);

    ResponseCodeEnum rawMemoCheck(byte[] cand, boolean hasZeroByte);

    ResponseCodeEnum tokenNameCheck(String name);

    ResponseCodeEnum tokenSymbolCheck(String symbol);

    default boolean isValidAutoRenewPeriod(final long len) {
        return isValidAutoRenewPeriod(Duration.newBuilder().setSeconds(len).build());
    }

    boolean isValidAutoRenewPeriod(Duration autoRenewPeriod);
}
