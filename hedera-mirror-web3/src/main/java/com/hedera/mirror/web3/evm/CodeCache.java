package com.hedera.mirror.web3.evm;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;

import com.hedera.mirror.web3.repository.ContractRepository;
import com.hedera.mirror.web3.repository.EntityRepository;

@Named
@RequiredArgsConstructor
public class CodeCache {

    final ContractRepository contractRepository;
    final EntityRepository entityRepository;

    public Code getIfPresent(final Address address) {

        final Long id = entityRepository.findAccountIdByAddress(address.toArray()).orElse(null);

        final var runtimeBytecode = contractRepository.findRuntimeBytecodeById(id).orElse(null);

        if (runtimeBytecode != null) {
            final var bytes = Bytes.fromHexString(runtimeBytecode);

            return Code.createLegacyCode(bytes, Hash.hash(bytes));
        } else {
            return Code.EMPTY_CODE;
        }
    }
}
