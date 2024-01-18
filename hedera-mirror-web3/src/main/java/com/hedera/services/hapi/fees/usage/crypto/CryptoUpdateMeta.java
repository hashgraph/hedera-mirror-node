/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.hapi.fees.usage.crypto;

import static com.hedera.services.hapi.utils.fees.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.INT_SIZE;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.LONG_SIZE;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.getAccountKeyStorageSize;

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class CryptoUpdateMeta {
    private final long keyBytesUsed;
    private final long msgBytesUsed;
    private final long memoSize;
    private final long effectiveNow;
    private final long expiry;
    private final boolean hasProxy;
    private final int maxAutomaticAssociations;
    private final boolean hasMaxAutomaticAssociations;

    public CryptoUpdateMeta(Builder builder) {
        keyBytesUsed = builder.keyBytesUsed;
        msgBytesUsed = builder.msgBytesUsed;
        memoSize = builder.memoSize;
        effectiveNow = builder.effectiveNow;
        expiry = builder.expiry;
        hasProxy = builder.hasProxy;
        maxAutomaticAssociations = builder.maxAutomaticAssociations;
        hasMaxAutomaticAssociations = builder.hasMaxAutomaticAssociations;
    }

    @SuppressWarnings("deprecation")
    public CryptoUpdateMeta(CryptoUpdateTransactionBody cryptoUpdateTxnBody, long transactionValidStartSecs) {
        keyBytesUsed = cryptoUpdateTxnBody.hasKey() ? getAccountKeyStorageSize(cryptoUpdateTxnBody.getKey()) : 0;
        msgBytesUsed = bytesUsedInTxn(cryptoUpdateTxnBody) + keyBytesUsed;
        memoSize = cryptoUpdateTxnBody.hasMemo()
                ? cryptoUpdateTxnBody.getMemo().getValueBytes().size()
                : 0;
        effectiveNow = transactionValidStartSecs;
        expiry = cryptoUpdateTxnBody.getExpirationTime().getSeconds();
        hasProxy = cryptoUpdateTxnBody.hasProxyAccountID();
        hasMaxAutomaticAssociations = cryptoUpdateTxnBody.hasMaxAutomaticTokenAssociations();
        maxAutomaticAssociations =
                cryptoUpdateTxnBody.getMaxAutomaticTokenAssociations().getValue();
    }

    public long getMsgBytesUsed() {
        return msgBytesUsed;
    }

    public long getKeyBytesUsed() {
        return keyBytesUsed;
    }

    public long getMemoSize() {
        return memoSize;
    }

    public long getEffectiveNow() {
        return effectiveNow;
    }

    public long getExpiry() {
        return expiry;
    }

    public boolean hasProxy() {
        return hasProxy;
    }

    public int getMaxAutomaticAssociations() {
        return maxAutomaticAssociations;
    }

    public boolean hasMaxAutomaticAssociations() {
        return hasMaxAutomaticAssociations;
    }

    @SuppressWarnings("deprecation")
    private int bytesUsedInTxn(CryptoUpdateTransactionBody op) {
        return BASIC_ENTITY_ID_SIZE
                + op.getMemo().getValueBytes().size()
                + (op.hasExpirationTime() ? LONG_SIZE : 0)
                + (op.hasAutoRenewPeriod() ? LONG_SIZE : 0)
                + (op.hasProxyAccountID() ? BASIC_ENTITY_ID_SIZE : 0)
                + (op.hasMaxAutomaticTokenAssociations() ? INT_SIZE : 0);
    }

    public static class Builder {
        private long keyBytesUsed;
        private long msgBytesUsed;
        private long memoSize;
        private long effectiveNow;
        private long expiry;
        private boolean hasProxy;
        private int maxAutomaticAssociations;
        private boolean hasMaxAutomaticAssociations;

        public Builder() {
            // empty here on purpose.
        }

        public Builder keyBytesUsed(long keyBytesUsed) {
            this.keyBytesUsed = keyBytesUsed;
            return this;
        }

        public Builder msgBytesUsed(long msgBytesUsed) {
            this.msgBytesUsed = msgBytesUsed;
            return this;
        }

        public Builder memoSize(long memoSize) {
            this.memoSize = memoSize;
            return this;
        }

        public Builder effectiveNow(long effectiveNow) {
            this.effectiveNow = effectiveNow;
            return this;
        }

        public Builder expiry(long expiry) {
            this.expiry = expiry;
            return this;
        }

        public Builder hasProxy(boolean hasProxy) {
            this.hasProxy = hasProxy;
            return this;
        }

        public Builder maxAutomaticAssociations(int maxAutomaticAssociations) {
            this.maxAutomaticAssociations = maxAutomaticAssociations;
            return this;
        }

        public Builder hasMaxAutomaticAssociations(boolean hasMaxAutomaticAssociations) {
            this.hasMaxAutomaticAssociations = hasMaxAutomaticAssociations;
            return this;
        }

        public CryptoUpdateMeta build() {
            return new CryptoUpdateMeta(this);
        }
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("keyBytesUsed", keyBytesUsed)
                .add("msgBytesUsed", msgBytesUsed)
                .add("memoSize", memoSize)
                .add("effectiveNow", effectiveNow)
                .add("expiry", expiry)
                .add("hasProxy", hasProxy)
                .add("maxAutomaticAssociations", maxAutomaticAssociations)
                .add("hasMaxAutomaticAssociations", hasMaxAutomaticAssociations)
                .toString();
    }
}
