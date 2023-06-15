/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.utils.accessors;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.Transaction;

public class AccessorFactory {

    /**
     * Given a gRPC {@link Transaction}, returns a {@link SignedTxnAccessor} specialized to handle the transaction's
     * logical operation.
     *
     * @param transaction the gRPC transaction
     * @return a specialized accessor
     */
    public SignedTxnAccessor constructSpecializedAccessor(final Transaction transaction)
            throws InvalidProtocolBufferException {
        return internalSpecializedConstruction(transaction.toByteArray(), transaction);
    }

    private SignedTxnAccessor internalSpecializedConstruction(
            final byte[] transactionBytes, final Transaction transaction) throws InvalidProtocolBufferException {
        return SignedTxnAccessor.from(transactionBytes, transaction);
    }

    public TxnAccessor uncheckedSpecializedAccessor(final Transaction transaction) {
        try {
            return constructSpecializedAccessor(transaction);
        } catch (final InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Not a valid signed transaction");
        }
    }
}
