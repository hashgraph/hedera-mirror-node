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

package com.hedera.services.hapi.fees.usage.file;

import static com.hedera.services.hapi.fees.usage.file.FileOpsUsage.asKey;
import static com.hedera.services.hapi.utils.fees.FeeBuilder.getAccountKeyStorageSize;

import com.hederahashgraph.api.proto.java.KeyList;
import java.nio.charset.StandardCharsets;

public class ExtantFileContext {
    private final long currentSize;
    private final long currentExpiry;
    private final String currentMemo;
    private final KeyList currentWacl;

    private ExtantFileContext(final ExtantFileContext.Builder builder) {
        currentMemo = builder.currentMemo;
        currentSize = builder.currentSize;
        currentExpiry = builder.currentExpiry;
        currentWacl = builder.currentWacl;
    }

    public long currentNonBaseSb() {
        return currentSize
                + currentMemo.getBytes(StandardCharsets.UTF_8).length
                + getAccountKeyStorageSize(asKey(currentWacl));
    }

    public long currentSize() {
        return currentSize;
    }

    public long currentExpiry() {
        return currentExpiry;
    }

    public String currentMemo() {
        return currentMemo;
    }

    public KeyList currentWacl() {
        return currentWacl;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private static final int SIZE_MASK = 1 << 0;
        private static final int EXPIRY_MASK = 1 << 1;
        private static final int MEMO_MASK = 1 << 2;
        private static final int WACL_MASK = 1 << 3;

        private static final int ALL_FIELDS_MASK = SIZE_MASK | EXPIRY_MASK | MEMO_MASK | WACL_MASK;

        private int mask = 0;
        private long currentSize;
        private long currentExpiry;
        private String currentMemo;
        private KeyList currentWacl;

        private Builder() {}

        public ExtantFileContext build() {
            if (mask != ALL_FIELDS_MASK) {
                throw new IllegalStateException(String.format("Field mask is %d, not %d!", mask, ALL_FIELDS_MASK));
            }
            return new ExtantFileContext(this);
        }

        public ExtantFileContext.Builder setCurrentSize(final long currentSize) {
            this.currentSize = currentSize;
            mask |= SIZE_MASK;
            return this;
        }

        public ExtantFileContext.Builder setCurrentExpiry(final long currentExpiry) {
            this.currentExpiry = currentExpiry;
            mask |= EXPIRY_MASK;
            return this;
        }

        public ExtantFileContext.Builder setCurrentMemo(final String currentMemo) {
            this.currentMemo = currentMemo;
            mask |= MEMO_MASK;
            return this;
        }

        public ExtantFileContext.Builder setCurrentWacl(final KeyList currentWacl) {
            this.currentWacl = currentWacl;
            mask |= WACL_MASK;
            return this;
        }
    }
}
