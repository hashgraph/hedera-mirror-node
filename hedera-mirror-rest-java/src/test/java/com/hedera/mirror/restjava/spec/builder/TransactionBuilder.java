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
import com.hedera.mirror.common.domain.transaction.Transaction;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;
import org.springframework.transaction.support.TransactionOperations;

@Named
class TransactionBuilder extends AbstractEntityBuilder {

    private static final long NETWORK_FEE = 1L;
    private static final long NODE_FEE = 2L;
    private static final long SERVICE_FEE = 4L;

    private static final Map<String, Function<Object, Object>> METHOD_PARAMETER_CONVERTERS = Map.of(
            "entityId", ENTITY_ID_CONVERTER,
            "nodeAccountId", ENTITY_ID_CONVERTER,
            "payerAccountId", ENTITY_ID_CONVERTER,
            "recipientAccountId", ENTITY_ID_CONVERTER,
            "treasuryAccountId", ENTITY_ID_CONVERTER
    );

    TransactionBuilder(EntityManager entityManager, TransactionOperations transactionOperations) {
        super(entityManager, transactionOperations, METHOD_PARAMETER_CONVERTERS);
    }

    @Override
    void customizeAndPersistEntity(Map<String, Object> account) {
        var builder = Transaction.builder();
        // Set defaults
        builder
                .chargedTxFee(NODE_FEE + NETWORK_FEE + SERVICE_FEE)
                .consensusTimestamp(null)
                .entityId(null)
                .index(1)
                .maxFee(33L)
                .nftTransfer(null)
                .nodeAccountId(null)
                .nonce(0)
                .parentConsensusTimestamp(null)
                .payerAccountId(null)
                .result(22)
                .scheduled(false)
                .transactionBytes("bytes".getBytes(StandardCharsets.UTF_8))
                .type(14)
                .validDurationSeconds(11L)
                .validStartNs(null);

        // Customize with spec setup definitions
        var wrapper = new DomainWrapperImpl<Transaction, Transaction.TransactionBuilder>(builder, builder::build, entityManager, transactionOperations);
        customizeWithSpec(wrapper, account);

        // Check and finalize
        var entity = wrapper.get();
        // Setup other entities and stuff, according to JS code.

        wrapper.persist();
    }
}
