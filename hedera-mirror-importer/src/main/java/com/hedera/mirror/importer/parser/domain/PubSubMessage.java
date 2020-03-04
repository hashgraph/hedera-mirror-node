package com.hedera.mirror.importer.parser.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.List;
import lombok.Data;

import com.hedera.mirror.importer.domain.EntityId;
import com.hedera.mirror.importer.parser.serializer.ProtoJsonSerializer;

@Data
public class PubSubMessage {
    Long consensusTimestamp;
    EntityId entity;
    @JsonSerialize(using = ProtoJsonSerializer.class)
    Transaction transaction;
    @JsonSerialize(using = ProtoJsonSerializer.class)
    TransactionRecord transactionRecord;
    /*
    Transaction transaction;
    // Transaction.body is deprecated, and using Transaction.bodyBytes would require de-serializing again. Hence,
    // making TransactionBody a field here despite it being part of Transaction itself.
    TransactionBody transactionBody;
    TransactionRecord transactionRecord;
    */
    // TODO: it might be better to change other places to List<> too? Returning an object with type way up the hierarchy
    //   limits the way it can used. While returning very specific types are also not good, List<> is good generic
    //   return type and is most commonly used. What do you guys say?
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSerialize(contentUsing = ProtoJsonSerializer.class)
    List<AccountAmount> nonFeeTransfers;
}
