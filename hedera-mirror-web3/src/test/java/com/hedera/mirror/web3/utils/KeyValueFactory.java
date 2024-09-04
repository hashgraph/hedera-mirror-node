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

public class KeyValueFactory {

    // Private constructor to prevent direct instantiation
    private KeyValueFactory() {}

    public static Object getInstance(final Class<?> classType, final Builder builder) {
        return builder.classType(classType).build();
    }

    public static class Builder {

        private Boolean inheritAccountKey;
        private String contractId;
        private byte[] ed25519;
        private byte[] ECDSA_secp256k1;
        private String delegatableContractId;
        private Class<?> classType;

        public Builder inheritAccountKey(Boolean inheritAccountKey) {
            this.inheritAccountKey = inheritAccountKey;
            return this;
        }

        public Builder contractId(String contractId) {
            this.contractId = contractId;
            return this;
        }

        public Builder ed25519(byte[] ed25519) {
            this.ed25519 = ed25519;
            return this;
        }

        public Builder ECDSA_secp256k1(byte[] ECDSA_secp256k1) {
            this.ECDSA_secp256k1 = ECDSA_secp256k1;
            return this;
        }

        public Builder delegatableContractId(String delegatableContractId) {
            this.delegatableContractId = delegatableContractId;
            return this;
        }

        private Builder classType(Class<?> classType) {
            this.classType = classType;
            return this;
        }

        private Object build() {
            assert classType != null;
            if (classType.equals(NestedCalls.class)) {
                return new NestedCalls.KeyValue(
                        this.inheritAccountKey,
                        this.contractId,
                        this.ed25519,
                        this.ECDSA_secp256k1,
                        this.delegatableContractId);
            } else if (classType.equals(PrecompileTestContract.class)) {
                return new PrecompileTestContract.KeyValue(
                        this.inheritAccountKey,
                        this.contractId,
                        this.ed25519,
                        this.ECDSA_secp256k1,
                        this.delegatableContractId);
            } else if (classType.equals(ModificationPrecompileTestContract.class)) {
                return new ModificationPrecompileTestContract.KeyValue(
                        this.inheritAccountKey,
                        this.contractId,
                        this.ed25519,
                        this.ECDSA_secp256k1,
                        this.delegatableContractId);
            }
            throw new RuntimeException("Class type not supported.");
        }
    }
}
