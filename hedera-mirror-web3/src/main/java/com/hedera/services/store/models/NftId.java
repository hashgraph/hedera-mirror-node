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

package com.hedera.services.store.models;

import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.TokenID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Comparator;

/**
 * Copied NftId type from hedera-services.
 *
 * Represents the id of a UniqueToken model
 */
public record NftId(long shard, long realm, long num, long serialNo) implements Comparable<NftId> {
    private static final Comparator<NftId> NATURAL_ORDER = Comparator.comparingLong(NftId::num)
            .thenComparingLong(NftId::serialNo)
            .thenComparingLong(NftId::shard)
            .thenComparingLong(NftId::realm);

    public static NftId withDefaultShardRealm(final long num, final long serialNo) {
        return new NftId(0, 0, num, serialNo);
    }

    public TokenID tokenId() {
        return TokenID.newBuilder()
                .setShardNum(shard)
                .setRealmNum(realm)
                .setTokenNum(num)
                .build();
    }

    public static NftId fromGrpc(final TokenID tokenId, final long serialNo) {
        return new NftId(tokenId.getShardNum(), tokenId.getRealmNum(), tokenId.getTokenNum(), serialNo);
    }

    @Override
    public int compareTo(final @NonNull NftId that) {
        return NATURAL_ORDER.compare(this, that);
    }

    public static NftId fromGrpc(final NftID nftId) {
        return fromGrpc(nftId.getTokenID(), nftId.getSerialNumber());
    }
}
