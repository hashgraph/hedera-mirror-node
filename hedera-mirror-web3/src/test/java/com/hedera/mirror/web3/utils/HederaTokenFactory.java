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

import com.hedera.mirror.web3.web3j.generated.ModificationPrecompileTestContract;
import com.hedera.mirror.web3.web3j.generated.NestedCalls;
import com.hedera.mirror.web3.web3j.generated.PrecompileTestContract;
import java.math.BigInteger;
import java.util.List;

public class HederaTokenFactory {

    // Private constructor to prevent direct instantiation
    private HederaTokenFactory() {}

    public static Object getInstance(final Class classType, final Builder builder) {
        return builder.classType(classType).build();
    }

    public static class Builder {
        private String name;
        private String symbol;
        private String treasury;
        private String memo;
        private Boolean tokenSupplyType;
        private BigInteger maxSupply;
        private Boolean freezeDefault;
        private List<Object> tokenKeys;
        private Object expiry;
        private Class classType;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder treasury(String treasury) {
            this.treasury = treasury;
            return this;
        }

        public Builder memo(String memo) {
            this.memo = memo;
            return this;
        }

        public Builder tokenSupplyType(Boolean tokenSupplyType) {
            this.tokenSupplyType = tokenSupplyType;
            return this;
        }

        public Builder maxSupply(BigInteger maxSupply) {
            this.maxSupply = maxSupply;
            return this;
        }

        public Builder freezeDefault(Boolean freezeDefault) {
            this.freezeDefault = freezeDefault;
            return this;
        }

        public Builder tokenKeys(List<Object> tokenKeys) {
            this.tokenKeys = tokenKeys;
            return this;
        }

        public Builder expiry(Object expiry) {
            this.expiry = expiry;
            return this;
        }

        private Builder classType(Class classType) {
            this.classType = classType;
            return this;
        }

        private Object build() {
            assert classType != null;
            if (classType.equals(NestedCalls.class)) {
                final var convertedKeys = this.tokenKeys.stream()
                        .map(key -> (NestedCalls.TokenKey) key)
                        .toList();
                return new NestedCalls.HederaToken(
                        this.name,
                        this.symbol,
                        this.treasury,
                        this.memo,
                        this.tokenSupplyType,
                        this.maxSupply,
                        this.freezeDefault,
                        convertedKeys,
                        (NestedCalls.Expiry) this.expiry);
            } else if (classType.equals(PrecompileTestContract.class)) {
                final var convertedKeys = this.tokenKeys.stream()
                        .map(key -> (PrecompileTestContract.TokenKey) key)
                        .toList();
                return new PrecompileTestContract.HederaToken(
                        this.name,
                        this.symbol,
                        this.treasury,
                        this.memo,
                        this.tokenSupplyType,
                        this.maxSupply,
                        this.freezeDefault,
                        convertedKeys,
                        (PrecompileTestContract.Expiry) this.expiry);
            } else if (classType.equals(ModificationPrecompileTestContract.class)) {
                final var convertedKeys = this.tokenKeys.stream()
                        .map(key -> (ModificationPrecompileTestContract.TokenKey) key)
                        .toList();
                return new ModificationPrecompileTestContract.HederaToken(
                        this.name,
                        this.symbol,
                        this.treasury,
                        this.memo,
                        this.tokenSupplyType,
                        this.maxSupply,
                        this.freezeDefault,
                        convertedKeys,
                        (ModificationPrecompileTestContract.Expiry) this.expiry);
            }
            throw new RuntimeException("Class type not supported.");
        }
    }
}
