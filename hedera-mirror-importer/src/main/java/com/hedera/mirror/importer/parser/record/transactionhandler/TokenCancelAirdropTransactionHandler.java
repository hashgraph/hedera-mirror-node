/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record.transactionhandler;

import com.hedera.mirror.common.domain.token.TokenAirdropStateEnum;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.entity.EntityListener;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;
import com.hederahashgraph.api.proto.java.PendingAirdropId;
import jakarta.inject.Named;
import java.util.List;
import java.util.function.Function;

@Named
class TokenCancelAirdropTransactionHandler extends AbstractTokenUpdateAirdropTransactionHandler {

    private static final Function<RecordItem, List<PendingAirdropId>> airdropExtractor =
            r -> r.getTransactionBody().getTokenCancelAirdrop().getPendingAirdropsList();

    public TokenCancelAirdropTransactionHandler(
            EntityIdService entityIdService, EntityListener entityListener, EntityProperties entityProperties) {
        super(
                entityIdService,
                entityListener,
                entityProperties,
                airdropExtractor,
                TokenAirdropStateEnum.CANCELLED,
                TransactionType.TOKENCANCELAIRDROP);
    }
}
