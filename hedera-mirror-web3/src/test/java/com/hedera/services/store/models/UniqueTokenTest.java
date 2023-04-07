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

import static com.hedera.services.utils.EntityIdUtils.asModelId;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import com.hedera.services.state.submerkle.RichInstant;

class UniqueTokenTest {
    @Test
    void objectContractWorks() {
        var subj = UniqueToken.builder().tokenId(Id.DEFAULT).serialNumber(1).build();
        assertEquals(1, subj.getSerialNumber());
        assertEquals(Id.DEFAULT, subj.getTokenId());
        var metadata = new byte[] {111, 23, 85};
        var id = new Id(1, 2, 3);
        subj = UniqueToken.builder().tokenId(Id.DEFAULT).serialNumber(1).creationTime(RichInstant.MISSING_INSTANT)
                .owner(id)
                .metadata(metadata).build();
        assertEquals(RichInstant.MISSING_INSTANT, subj.getCreationTime());
        assertEquals(id, subj.getOwner());
        assertEquals(1, subj.getSerialNumber());
        assertEquals(Id.DEFAULT, subj.getTokenId());
        assertEquals(RichInstant.MISSING_INSTANT, subj.getCreationTime());
    }

    @Test
    void toStringWorks() {
        final var token1 = asModelId("0.0.12345");
        final var owner1 = asModelId("0.0.12346");
        final var spender1 = asModelId("0.0.12347");
        final var meta1 = "aa".getBytes(StandardCharsets.UTF_8);
        final var subject = UniqueToken.builder().tokenId(token1).serialNumber(1L).owner(owner1).spender(spender1)
                .metadata(meta1).creationTime(RichInstant.MISSING_INSTANT).build();

        final var expected = "UniqueToken{tokenID=0.0.12345, serialNum=1, metadata=[97, 97],"
                + " creationTime=RichInstant{seconds=0, nanos=0}, owner=0.0.12346,"
                + " spender=0.0.12347}";

        assertEquals(expected, subject.toString());
    }
}
