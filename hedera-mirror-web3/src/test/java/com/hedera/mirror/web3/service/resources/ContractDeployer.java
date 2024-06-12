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
import java.lang.reflect.InvocationTargetException;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.tx.Contract;
import org.web3j.tx.gas.ContractGasProvider;

@Component
@CustomLog
@RequiredArgsConstructor
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ContractDeployer {
    protected static final long EVM_V_34_BLOCK = 50L;
    private final Web3j web3j;
    private final Credentials credentials;
    private final ContractGasProvider contractGasProvider;
    private final DomainBuilder domainBuilder;

    @Autowired
    private final ContractCallService contractCallService;

    public ContractDeployer(
            DomainBuilder domainBuilder,
            Web3j web3j,
            Credentials credentials,
            ContractGasProvider contractGasProvider,
            ContractCallService contractCallService) {
        this.domainBuilder = domainBuilder;
        this.web3j = web3j;
        this.credentials = credentials;
        this.contractGasProvider = contractGasProvider;
        this.contractCallService = contractCallService;
    }

    public <T extends Contract> T deploy(Class<T> contractClass, String binary) throws Exception {
        T contract;
        try {
            final var ctor = contractClass.getDeclaredConstructor(
                    String.class, Web3j.class, Credentials.class, ContractGasProvider.class);
            final var contractEntityNum = ContractDeployer.precompileContractPersist(binary, domainBuilder);
            final var contractAddress =
                    toAddress(EntityId.of(0, 0, contractEntityNum)).toHexString();
            contract = ctor.newInstance(contractAddress, web3j, credentials, contractGasProvider);
        } catch (InstantiationException
                | IllegalAccessException
                | InvocationTargetException
                | NoSuchMethodException e) {
            throw new Exception("Contract deployment failed", e);
        }

        return contract;
    }

    private static long precompileContractPersist(String binary, DomainBuilder domainBuilder) {
        final var contractBytes = Hex.decode(binary);
        final var entity =
                domainBuilder.entity().customize(e -> e.type(CONTRACT)).persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(entity.getId()).runtimeBytecode(contractBytes))
                .persist();

        domainBuilder
                .contractState()
                .customize(c -> c.contractId(entity.getId()))
                .persist();

        return entity.getNum();
    }
}
