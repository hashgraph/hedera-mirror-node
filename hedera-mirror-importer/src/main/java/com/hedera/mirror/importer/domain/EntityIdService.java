package com.hedera.mirror.importer.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.importer.exception.AliasNotFoundException;

/**
 * This service is used to centralize the conversion logic from protobuf-based Hedera entities to its internal EntityId
 * representation. Lookup methods encapsulate caching and alias resolution.
 */
public interface EntityIdService {

    /**
     * Converts a protobuf AccountID to an EntityID, resolving any aliases that may be present.
     *
     * @param accountId The protobuf account ID
     * @return The converted EntityId
     * @throws AliasNotFoundException if it fails to resolve the alias
     */
    EntityId lookup(AccountID accountId);

    /**
     * Specialized form of lookup(AccountID) that returns the first account ID parameter that resolves to a non-empty
     * EntityId.
     *
     * @param accountIds The protobuf account IDs
     * @return The converted EntityId or EntityId.EMPTY if none can be resolved
     */
    default EntityId lookup(AccountID... accountIds) {
        return lookup(AliasNotFoundAction.FALLTHROUGH, accountIds);
    }

    /**
     * Specialized form of lookup(AccountID) that returns the first account ID parameter that resolves to a non-empty
     * EntityId. If the {@code action} is {@link AliasNotFoundAction#ERROR}, the method throws
     * {@link AliasNotFoundException} as soon as resolving an alias fails.
     *
     * @param action     The action when looking up an alias results in {@link AliasNotFoundException}
     * @param accountIds The protobuf account IDs
     * @return The converted EntityId or EntityId.EMPTY if none can be resolved
     * @throws AliasNotFoundException
     */
    EntityId lookup(AliasNotFoundAction action, AccountID... accountIds);

    /**
     * Converts a protobuf ContractID to an EntityID, resolving any EVM addresses that may be present.
     *
     * @param contractId The protobuf contract ID
     * @return The converted EntityId
     * @throws com.hedera.mirror.importer.exception.AliasNotFoundException Throws exception if alias not found
     */
    EntityId lookup(ContractID contractId);

    /**
     * Specialized form of lookup(ContractID) that returns the first contract ID parameter that resolves to a non-empty
     * EntityId.
     *
     * @param contractIds The protobuf contract IDs
     * @return The converted EntityId or EntityId.EMPTY if none can be resolved
     */
    default EntityId lookup(ContractID... contractIds) {
        return lookup(AliasNotFoundAction.FALLTHROUGH, contractIds);
    }

    /**
     * Specialized form of lookup(ContractID) that returns the first contract ID parameter that resolves to a non-empty
     * EntityId. If the {@code action} is {@link AliasNotFoundAction#ERROR}, the method throws
     * {@link AliasNotFoundException} as soon as resolving an evm address fails.
     *
     * @param contractIds The protobuf contract IDs
     * @return The converted EntityId or EntityId.EMPTY if none can be resolved
     * @throws AliasNotFoundException
     */
    EntityId lookup(AliasNotFoundAction action, ContractID... contractIds);

    /**
     * Used to notify the system of new aliases for potential use in future lookups.
     *
     * @param aliasable Represents a mapping of alias to entity ID.
     */
    void notify(Entity entity);
}
