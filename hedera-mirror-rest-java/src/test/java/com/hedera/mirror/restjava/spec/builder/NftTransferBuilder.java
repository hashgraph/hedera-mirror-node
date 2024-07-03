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

package com.hedera.mirror.restjava.spec.builder;

import com.hedera.mirror.common.domain.DomainWrapperImpl;
import com.hedera.mirror.common.domain.token.NftTransfer;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.function.Function;
import org.springframework.transaction.support.TransactionOperations;

@Named
class NftTransferBuilder extends AbstractEntityBuilder {

    private static final Map<String, Function<Object, Object>> METHOD_PARAMETER_CONVERTERS = Map.of(
            "receiverAccountId", ENTITY_ID_CONVERTER,
            "senderAccountId", ENTITY_ID_CONVERTER,
            "tokenId", ENTITY_ID_CONVERTER
    );

    NftTransferBuilder(EntityManager entityManager, TransactionOperations transactionOperations) {
        super(entityManager, transactionOperations, METHOD_PARAMETER_CONVERTERS);
    }

    @Override
    protected void customizeAndPersistEntity(Map<String, Object> nftTransfer) {
        var builder = NftTransfer.builder();
        // Set defaults
        builder
                .isApproval(Boolean.FALSE)
                .serialNumber(1L);

        // Customize with spec setup definitions
        var wrapper = new DomainWrapperImpl<>(builder, builder::build, entityManager, transactionOperations);
        customizeWithSpec(wrapper, nftTransfer);
    }
}
