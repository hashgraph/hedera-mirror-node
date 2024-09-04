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

package com.hedera.mirror.web3.utils;

import com.hedera.mirror.web3.web3j.generated.NestedCalls;
import com.hedera.mirror.web3.web3j.generated.NestedCalls.KeyValue;
import java.math.BigInteger;

public class TokenKeyFactory {

    // Private constructor to prevent direct instantiation
    private TokenKeyFactory() {}

    public static Object getInstance(final Class classType, final Builder builder) {
        return builder.classType(classType).build();
    }

    public static class Builder {
        private BigInteger keyType;
        private KeyValue key;
        private Class classType;

        public Builder keyType(BigInteger keyType) {
            this.keyType = keyType;
            return this;
        }

        public Builder key(KeyValue key) {
            this.key = key;
            return this;
        }

        private Builder classType(Class<?> classType) {
            this.classType = classType;
            return this;
        }

        private Object build() {
            assert classType != null;
            if (classType.equals(NestedCalls.class)) {
                return new NestedCalls.TokenKey(this.keyType, this.key);
            }
            throw new RuntimeException("Class type not supported.");
        }
    }
}
