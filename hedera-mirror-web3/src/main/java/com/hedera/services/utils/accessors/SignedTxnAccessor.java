/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
import com.hedera.services.hapi.fees.usage.SigUsage;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SignedTxnAccessor implements TxnAccessor {

    private static final Logger log = LogManager.getLogger(SignedTxnAccessor.class);

    public static SignedTxnAccessor uncheckedFrom(final Transaction validSignedTxn) {
        try {
            return SignedTxnAccessor.from(validSignedTxn.toByteArray());
        } catch (final Exception illegal) {
            log.warn("Unexpected use of factory with invalid gRPC transaction", illegal);
            throw new IllegalArgumentException("Argument 'validSignedTxn' must be a valid signed txn");
        }
    }

    public static SignedTxnAccessor from(final byte[] signedTxnWrapperBytes) throws InvalidProtocolBufferException {
        return new SignedTxnAccessor(signedTxnWrapperBytes, null);
    }

    public static SignedTxnAccessor from(final byte[] signedTxnWrapperBytes, final Transaction signedTxnWrapper)
            throws InvalidProtocolBufferException {
        return new SignedTxnAccessor(signedTxnWrapperBytes, signedTxnWrapper);
    }

    protected SignedTxnAccessor(final byte[] signedTxnWrapperBytes, @Nullable final Transaction transaction) {}

    @Override
    public SubType getSubType() {
        return null;
    }

    @Override
    public AccountID getPayer() {
        return null;
    }

    @Override
    public TransactionID getTxnId() {
        return null;
    }

    @Override
    public HederaFunctionality getFunction() {
        return null;
    }

    @Override
    public SigUsage usageGiven(int numPayerKeys) {
        return null;
    }

    @Override
    public TransactionBody getTxn() {
        return null;
    }

    @Override
    public String getMemo() {
        return null;
    }

    @Override
    public byte[] getHash() {
        return new byte[0];
    }

    @Override
    public Transaction getSignedTxnWrapper() {
        return null;
    }
}
