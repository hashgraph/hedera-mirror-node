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

package com.hedera.mirror.importer.parser.record.transactionhandler;

import com.hedera.mirror.common.domain.transaction.TransactionType;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Named
public class TransactionHandlerFactory {

    private final Map<TransactionType, TransactionHandler> transactionHandlers;
    private final TransactionHandler defaultTransactionHandler;

    TransactionHandlerFactory(List<TransactionHandler> transactionHandlers) {
        this.transactionHandlers = transactionHandlers.stream()
                .collect(Collectors.toUnmodifiableMap(TransactionHandler::getType, Function.identity()));
        this.defaultTransactionHandler = this.transactionHandlers.get(TransactionType.UNKNOWN);
    }

    public TransactionHandler get(TransactionType transactionType) {
        return transactionHandlers.getOrDefault(transactionType, defaultTransactionHandler);
    }
}
