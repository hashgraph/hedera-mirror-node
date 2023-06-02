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

package com.hedera.services.store.contracts.precompile;

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;

public class HbarTransfer {

    protected final long amount;
    protected final AccountID sender;
    protected final AccountID receiver;
    protected final boolean isApproval;

    public HbarTransfer(final long amount, final boolean isApproval, final AccountID sender, final AccountID receiver) {
        this.amount = amount;
        this.isApproval = isApproval;
        this.sender = sender;
        this.receiver = receiver;
    }

    public AccountAmount senderAdjustment() {
        return AccountAmount.newBuilder()
                .setAccountID(sender)
                .setAmount(-amount)
                .setIsApproval(isApproval)
                .build();
    }

    public AccountAmount receiverAdjustment() {
        return AccountAmount.newBuilder()
                .setAccountID(receiver)
                .setAmount(+amount)
                .setIsApproval(isApproval)
                .build();
    }

    public AccountID sender() {
        return sender;
    }

    public AccountID receiver() {
        return receiver;
    }

    public long amount() {
        return amount;
    }

    public boolean isApproval() {
        return isApproval;
    }
}
