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

package com.hedera.services.utils;

import com.google.protobuf.ByteString;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import java.util.stream.Stream;

public class IdUtils {

    public static BalanceChange hbarChange(final AccountID account, final long amount) {
        return BalanceChange.changingHbar(adjustFrom(account, amount), null);
    }

    public static AccountAmount adjustFrom(AccountID account, long amount) {
        return AccountAmount.newBuilder()
                .setAccountID(account)
                .setAmount(amount)
                .build();
    }

    public static BalanceChange tokenChange(final Id token, final AccountID account, final long amount) {
        return BalanceChange.changingFtUnits(token, token.asGrpcToken(), adjustFrom(account, amount), null);
    }

    public static NftTransfer nftXfer(AccountID from, AccountID to, long serialNo) {
        return NftTransfer.newBuilder()
                .setSenderAccountID(from)
                .setReceiverAccountID(to)
                .setSerialNumber(serialNo)
                .build();
    }

    public static AccountID asAccount(String v) {
        long[] nativeParts = asDotDelimitedLongArray(v);
        return AccountID.newBuilder()
                .setShardNum(nativeParts[0])
                .setRealmNum(nativeParts[1])
                .setAccountNum(nativeParts[2])
                .build();
    }

    static long[] asDotDelimitedLongArray(String s) {
        String[] parts = s.split("[.]");
        return Stream.of(parts).mapToLong(Long::valueOf).toArray();
    }

    public static AccountID asAccountWithAlias(String alias) {
        return AccountID.newBuilder().setAlias(ByteString.copyFromUtf8(alias)).build();
    }
}
