/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.state;

import static com.hedera.services.utils.EntityIdUtils.toEntityId;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.repository.EntityRepository;
import jakarta.annotation.Nonnull;
import jakarta.inject.Named;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class CommonEntityAccessor {
    private final EntityRepository entityRepository;

    public @Nonnull Optional<Entity> get(@Nonnull AccountID accountID, final Optional<Long> timestamp) {
        if (accountID.hasAccountNum()) {
            return getEntityByMirrorAddressAndTimestamp(toEntityId(accountID), timestamp);
        } else {
            return getEntityByEvmAddressAndTimestamp(accountID.alias().toByteArray(), timestamp);
        }
    }

    private Optional<Entity> getEntityByMirrorAddressAndTimestamp(EntityId entityId, final Optional<Long> timestamp) {
        return timestamp
                .map(t -> entityRepository.findActiveByIdAndTimestamp(entityId.getId(), t))
                .orElseGet(() -> entityRepository.findByIdAndDeletedIsFalse(entityId.getId()));
    }

    public Optional<Entity> getEntityByEvmAddressAndTimestamp(byte[] addressBytes, final Optional<Long> timestamp) {
        return timestamp
                .map(t -> entityRepository.findActiveByEvmAddressAndTimestamp(addressBytes, t))
                .orElseGet(() -> entityRepository.findByEvmAddressAndDeletedIsFalse(addressBytes));
    }

    public Optional<Entity> getEntityByEvmAddressOrAliasAndTimestamp(byte[] alias, final Optional<Long> timestamp) {
        return timestamp
                .map(t -> entityRepository.findActiveByEvmAddressOrAliasAndTimestamp(alias, t))
                .orElseGet(() -> entityRepository.findByEvmAddressOrAlias(alias));
    }
}
