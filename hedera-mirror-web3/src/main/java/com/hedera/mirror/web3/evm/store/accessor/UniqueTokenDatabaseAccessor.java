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

package com.hedera.mirror.web3.evm.store.accessor;

import static com.hedera.services.utils.EntityIdUtils.idFromEntityId;

import com.hedera.mirror.common.domain.token.Nft;
import com.hedera.mirror.common.domain.token.NftId;
import com.hedera.mirror.web3.repository.NftRepository;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.UniqueToken;
import java.time.Instant;
import java.util.Optional;
import javax.inject.Named;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class UniqueTokenDatabaseAccessor extends DatabaseAccessor<NftId, UniqueToken> {
    private final NftRepository nftRepository;

    @Override
    public @NonNull Optional<UniqueToken> get(@NonNull NftId nftId) {
        return nftRepository
                .findActiveById(nftId.getTokenId().getId(), nftId.getSerialNumber())
                .map(this::mapNftToUniqueToken);
    }

    private UniqueToken mapNftToUniqueToken(Nft nft) {
        Id tokenId = idFromEntityId(nft.getId().getTokenId());
        return new UniqueToken(
                tokenId,
                nft.getId().getSerialNumber(),
                mapNanosToRichInstant(nft.getCreatedTimestamp()),
                idFromEntityId(nft.getAccountId()),
                idFromEntityId(nft.getSpender()),
                nft.getMetadata());
    }

    private RichInstant mapNanosToRichInstant(Long nanos) {
        if (nanos == null) {
            return RichInstant.MISSING_INSTANT;
        }

        return RichInstant.fromJava(Instant.ofEpochSecond(0, nanos));
    }
}
