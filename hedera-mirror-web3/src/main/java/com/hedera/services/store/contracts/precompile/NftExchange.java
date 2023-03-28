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
package com.hedera.services.store.contracts.precompile;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenID;

public class NftExchange {

    private final long serialNo;

    private final TokenID tokenType;
    private final AccountID sender;
    private final AccountID receiver;
    private final boolean isApproval;

    public NftExchange(
            final long serialNo, final TokenID tokenType, final AccountID sender, final AccountID receiver) {
        this(serialNo, tokenType, sender, receiver, false);
    }

    public static NftExchange fromApproval(
            final long serialNo, final TokenID tokenType, final AccountID sender, final AccountID receiver) {
        return new NftExchange(serialNo, tokenType, sender, receiver, true);
    }

    public NftExchange(
            final long serialNo,
            final TokenID tokenType,
            final AccountID sender,
            final AccountID receiver,
            final boolean isApproval) {
        this.serialNo = serialNo;
        this.tokenType = tokenType;
        this.sender = sender;
        this.receiver = receiver;
        this.isApproval = isApproval;
    }

    public NftTransfer asGrpc() {
        return NftTransfer.newBuilder()
                .setSenderAccountID(sender)
                .setReceiverAccountID(receiver)
                .setSerialNumber(serialNo)
                .setIsApproval(isApproval)
                .build();
    }

    public TokenID getTokenType() {
        return tokenType;
    }

    public long getSerialNo() {
        return serialNo;
    }

    public boolean isApproval() {
        return isApproval;
    }
}
