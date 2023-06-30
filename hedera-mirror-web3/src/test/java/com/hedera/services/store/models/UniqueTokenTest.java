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

package com.hedera.services.store.models;

import static com.hedera.services.utils.EntityIdUtils.asModelId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.services.state.submerkle.RichInstant;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UniqueTokenTest {
    private final Id id = new Id(1, 2, 3);
    private final byte[] metadata = new byte[] {111, 23, 85};
    private final Id tokenId = Id.DEFAULT;
    private final UniqueToken subj = new UniqueToken(tokenId, 1, RichInstant.MISSING_INSTANT, id, null, metadata);

    @Test
    void objectContractWorks() {
        assertEquals(RichInstant.MISSING_INSTANT, subj.getCreationTime());
        assertEquals(id, subj.getOwner());
        assertEquals(1, subj.getSerialNumber());
        assertEquals(Id.DEFAULT, subj.getTokenId());
        assertEquals(Id.DEFAULT.asEvmAddress(), subj.getAddress());
        assertEquals(metadata, subj.getMetadata());
        assertEquals(null, subj.getSpender());
        assertEquals(new NftId(tokenId.shard(), tokenId.realm(), tokenId.num(), 1), subj.getNftId());
    }

    @Test
    void toStringWorks() {
        final var token1 = asModelId("0.0.12345");
        final var owner1 = asModelId("0.0.12346");
        final var spender1 = asModelId("0.0.12347");
        final var meta1 = "aa".getBytes(StandardCharsets.UTF_8);
        final var subject = new UniqueToken(token1, 1L, RichInstant.MISSING_INSTANT, owner1, spender1, meta1);

        final var expected = "UniqueToken{tokenID=0.0.12345, serialNum=1, metadata=[97, 97],"
                + " creationTime=RichInstant{seconds=0, nanos=0}, owner=0.0.12346,"
                + " spender=0.0.12347}";

        assertEquals(expected, subject.toString());
    }

    @Test
    void testHashCode() {
        var rel = new UniqueToken(tokenId, 1, RichInstant.MISSING_INSTANT, id, null, metadata);
        assertEquals(rel.hashCode(), subj.hashCode());
    }

    @Test
    void testEquals() {
        var rel = new UniqueToken(tokenId, 1, RichInstant.MISSING_INSTANT, id, null, metadata);
        assertEquals(rel, subj);
        rel = new UniqueToken(tokenId, 2, RichInstant.MISSING_INSTANT, id, null, metadata);
        assertNotEquals(rel, subj);
    }
}
