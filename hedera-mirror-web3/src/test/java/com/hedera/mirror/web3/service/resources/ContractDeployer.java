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

import com.google.common.collect.Range;
import com.hedera.mirror.common.config.CommonIntegrationTest;
import com.hedera.mirror.common.domain.DomainBuilder;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.web3.service.TestWeb3jService;
import java.lang.reflect.InvocationTargetException;
import lombok.RequiredArgsConstructor;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.protocol.Web3j;
import org.web3j.tx.Contract;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Numeric;

@RequiredArgsConstructor
public class ContractDeployer extends CommonIntegrationTest {
    protected static final long EVM_V_34_BLOCK = 50L;
    Web3j web3j;
    Credentials credentials;
    ContractGasProvider contractGasProvider;

    private String MOCK_KEY = "0x4e3c5c727f3f4b8f8e8a8fe7e032cf78b8693a2b711e682da1d3a26a6a3b58b6";
    private static ContractDeployer instance;

    public ContractDeployer getInstance() {
        if (instance == null) {
            final var mockEcKeyPair = ECKeyPair.create(Numeric.hexStringToByteArray(MOCK_KEY));
            credentials = Credentials.create(mockEcKeyPair);
            contractGasProvider = new DefaultGasProvider();
            web3j = Web3j.build(new TestWeb3jService());
            instance = new ContractDeployer();
        }
        return instance;
    }

    public static <T extends Contract> T deploy(Class<T> contractClass, String binary) throws Exception {
        T contract;
        try {
            final var deployerInstance = ContractDeployer.instance;
            final var ctor = contractClass.getDeclaredConstructor(
                    String.class, Web3j.class, Credentials.class, ContractGasProvider.class);
            final var contractEntityNum =
                    ContractDeployer.precompileContractPersist(binary, deployerInstance.domainBuilder);
            final var contractAddress =
                    toAddress(EntityId.of(0, 0, contractEntityNum)).toHexString();
            contract = ctor.newInstance(
                    contractAddress,
                    deployerInstance.web3j,
                    deployerInstance.credentials,
                    deployerInstance.contractGasProvider);
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
        final var recordFileBeforeEvm34 = domainBuilder
                .recordFile()
                .customize(f -> f.index(EVM_V_34_BLOCK - 1))
                .persist();
        final var entity = domainBuilder
                .entity()
                .customize(e -> e.type(CONTRACT)
                        .balance(1500L)
                        .timestampRange(Range.closedOpen(
                                recordFileBeforeEvm34.getConsensusStart(), recordFileBeforeEvm34.getConsensusEnd())))
                .persist();

        domainBuilder
                .contract()
                .customize(c -> c.id(entity.getId()).runtimeBytecode(contractBytes))
                .persist();

        domainBuilder
                .contractState()
                .customize(c -> c.contractId(entity.getId())
                        .slot(Bytes.fromHexString("0x0000000000000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe())
                        .value(Bytes.fromHexString("0x4746573740000000000000000000000000000000000000000000000000000000")
                                .toArrayUnsafe()))
                .persist();

        domainBuilder.recordFile().customize(f -> f.bytes(contractBytes)).persist();

        return entity.getNum();
    }
}
