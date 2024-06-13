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

package com.hedera.mirror.web3.service.resources;

import static com.hedera.mirror.common.domain.entity.EntityType.CONTRACT;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;

import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.service.ContractCallService;
import com.hedera.mirror.web3.utils.TestWeb3jService;
import java.lang.reflect.InvocationTargetException;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.util.encoders.Hex;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.protocol.Web3j;
import org.web3j.tx.Contract;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Numeric;

@RequiredArgsConstructor
public class ContractDeployer {
    private static final String MOCK_KEY = "0x4e3c5c727f3f4b8f8e8a8fe7e032cf78b8693a2b711e682da1d3a26a6a3b58b6";

    private final Web3j web3j;
    private final Credentials credentials;
    private final ContractGasProvider contractGasProvider;
    private final DomainBuilder domainBuilder;
    private final ContractCallService contractCallService;

    public ContractDeployer(
            DomainBuilder domainBuilder, ContractCallService contractCallService, TestWeb3jService testWeb3jService) {
        final var mockEcKeyPair = ECKeyPair.create(Numeric.hexStringToByteArray(MOCK_KEY));
        this.domainBuilder = domainBuilder;
        this.web3j = Web3j.build(testWeb3jService);
        this.credentials = Credentials.create(mockEcKeyPair);
        ;
        this.contractGasProvider = new DefaultGasProvider();
        this.contractCallService = contractCallService;
    }

    public <T extends Contract> T deploy(Class<T> contractClass) throws Exception {
        T contract;
        try {
            final var ctor = contractClass.getDeclaredConstructor(
                    String.class, Web3j.class, Credentials.class, ContractGasProvider.class);
            final var id = domainBuilder.id();
            final var contractAddress = toAddress(EntityId.of(id)).toHexString();
            contract = ctor.newInstance(contractAddress, web3j, credentials, contractGasProvider);
            precompileContractPersist(contract.getContractBinary(), id);
        } catch (InstantiationException
                | IllegalAccessException
                | InvocationTargetException
                | NoSuchMethodException e) {
            throw new Exception("Contract deployment failed", e);
        }

        return contract;
    }

    private void precompileContractPersist(String binary, long entityId) {
        final var contractBytes = Hex.decode(binary);
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.type(CONTRACT).id(entityId).num(entityId))
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(entity.getId()).runtimeBytecode(contractBytes))
                .persist();

        domainBuilder
                .contractState()
                .customize(c -> c.contractId(entity.getId()))
                .persist();
    }
}
