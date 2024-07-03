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

import com.hedera.mirror.common.domain.DomainWrapper;
import com.hedera.mirror.common.domain.DomainWrapperImpl;
import com.hedera.mirror.common.domain.token.NftTransfer;
import com.hedera.mirror.common.domain.transaction.Transaction;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.CollectionUtils;

@Named
class TransactionBuilder extends AbstractEntityBuilder {

    private static final long NETWORK_FEE = 1L;
    private static final long NODE_FEE = 2L;
    private static final long SERVICE_FEE = 4L;

    private static final Map<String, Function<Object, Object>> METHOD_PARAMETER_CONVERTERS = Map.of(
            "entityId", ENTITY_ID_CONVERTER,
//            "nftTransfer", NFT_TRANSFER_CONVERTER,
            "nodeAccountId", ENTITY_ID_CONVERTER,
            "payerAccountId", ENTITY_ID_CONVERTER,
            "recipientAccountId", ENTITY_ID_CONVERTER,
            "transactionHash", HEX_OR_BASE64_CONVERTER,
            "treasuryAccountId", ENTITY_ID_CONVERTER
    );

    private static final Map<String, String> ATTRIBUTE_NAME_MAP = Map.of(
            "valid_start_timestamp", "valid_start_ns"
    );

    private final NftTransferBuilder nftTransferBuilder;

    TransactionBuilder(NftTransferBuilder nftTransferBuilder, EntityManager entityManager, TransactionOperations transactionOperations) {
        super(entityManager, transactionOperations, METHOD_PARAMETER_CONVERTERS, ATTRIBUTE_NAME_MAP);
        this.nftTransferBuilder = nftTransferBuilder;
    }

    @Override
    protected void customizeAndPersistEntity(Map<String, Object> transaction) {
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
        customizeWithSpec(wrapper, transaction);

        // Check and finalize
        var entity = wrapper.get();

        // set valid_start_ns to consensus_timestamp - 1 if not set
        if (entity.getValidStartNs() == null) {
            builder.validStartNs(entity.getConsensusTimestamp() - 1L);
        }

        wrapper.persist();
    }

//    @SuppressWarnings("unchecked")
//    private final Function<Object, Object> NFT_TRANSFER_CONVERTER = value -> {
//        if (value instanceof List<?> nftTransferList && !CollectionUtils.isEmpty(nftTransferList)) {
//            var domainNftTransfers = new ArrayList<NftTransfer>(nftTransferList.size());
//            nftTransferList.forEach(transfer -> {
//                DomainWrapper<?,?> wrapper = nftTransferBuilder.customizeAndPersistEntity((Map<String, Object>)transfer);
//                domainNftTransfers.add(wrapper.get());
//            });
//            return domainNftTransfers;
//        }
//        return null;
//    };
}
