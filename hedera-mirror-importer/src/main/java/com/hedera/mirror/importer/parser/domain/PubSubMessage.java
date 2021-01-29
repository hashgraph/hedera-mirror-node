package com.hedera.mirror.importer.parser.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import lombok.Value;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.parser.serializer.ProtoJsonSerializer;

@Value
public class PubSubMessage {
    private final Long consensusTimestamp;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final EntityId entity;

    private final int transactionType;

    private final Transaction transaction;

    @JsonSerialize(using = ProtoJsonSerializer.class)
    private final TransactionRecord transactionRecord;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSerialize(contentUsing = ProtoJsonSerializer.class)
    private final Iterable<AccountAmount> nonFeeTransfers;

    @Value
    // This is a pojo version of the Transaction proto, needed to get around protobuf serializing body as raw bytes
    public static class Transaction {
        @JsonSerialize(using = ProtoJsonSerializer.class)
        private final TransactionBody body;
        @JsonSerialize(using = ProtoJsonSerializer.class)
        private final SignatureMap sigMap;
    }
}
