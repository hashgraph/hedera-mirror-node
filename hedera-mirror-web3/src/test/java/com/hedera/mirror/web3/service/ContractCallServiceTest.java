package com.hedera.mirror.web3.service;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.common.util.DomainUtils.fromEvmAddress;
import static com.hedera.mirror.common.util.DomainUtils.toEvmAddress;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import javax.annotation.Resource;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.hedera.mirror.common.domain.entity.Entity;
import com.hedera.mirror.web3.Web3IntegrationTest;
import com.hedera.mirror.web3.evm.properties.StaticBlockMetaSource;
import com.hedera.mirror.web3.evm.store.contract.MirrorEntityAccess;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.node.app.service.evm.contracts.execution.HederaBlockValues;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;

class ContractCallServiceTest extends Web3IntegrationTest {

    private static final Bytes RUNTIME_BYTES_VIEW_FUNC = Bytes.fromHexString(
            "608060405234801561001057600080fd5b50600436106100415760003560e01c80632e64cec1146100465780636057361d146100645780636d4ce63c14610080575b600080fd5b61004e61009e565b60405161005b919061010b565b60405180910390f35b61007e600480360381019061007991906100cf565b6100a7565b005b6100886100b1565b604051610095919061010b565b60405180910390f35b60006033905090565b8060008190555050565b60006048905090565b6000813590506100c981610135565b92915050565b6000602082840312156100e5576100e4610130565b5b60006100f3848285016100ba565b91505092915050565b61010581610126565b82525050565b600060208201905061012060008301846100fc565b92915050565b6000819050919050565b600080fd5b61013e81610126565b811461014957600080fd5b5056fea264697066735822122055a1bf21b71aa5fa69a6d162c173c2e547f0c9263c69887e08891f1d7257ef7464736f6c63430008070033");

    private static final HederaBlockValues BLOCK =
            new HederaBlockValues(120000000, 1030,
            Instant.now());
    @Resource
    private ContractCallService contractCallService;

    @MockBean
    private MirrorEntityAccess mirrorEntityAccess;

    @MockBean
    private StaticBlockMetaSource blockMetaSource;

    @Test
    void processCallSuccess() {
        final var serviceParameters = callBody("0x2e64cec1");
        final var entity = entity(serviceParameters.getReceiver());
        final var successfulReadResponse = "0x0000000000000000000000000000000000000000000000000000000000000033";

        when(mirrorEntityAccess.findEntity(serviceParameters.getReceiver()))
                .thenReturn(Optional.of(entity));
        when(mirrorEntityAccess.fetchCodeIfPresent(serviceParameters.getReceiver()))
                .thenReturn(RUNTIME_BYTES_VIEW_FUNC);
        when(blockMetaSource.computeBlockValues(120000000)).thenReturn(BLOCK);
        assertThat(contractCallService.processCall(serviceParameters)).isEqualTo(successfulReadResponse);
    }

    private CallServiceParameters callBody(String callData) {
        final var fromAddress = Address.ZERO;
        final var sender = new HederaEvmAccount(fromAddress);
        final var receiver = Address.fromHexString("0x00000000000000000000000000000000000004e2");
        final var data = Bytes.fromHexString(callData);

        return CallServiceParameters.builder()
                .sender(sender)
                .receiver(receiver)
                .callData(data)
                .providedGasLimit(120000000L)
                .isStatic(true)
                .build();
    }

    private Entity entity(Address id) {
        final var entityId = fromEvmAddress(id.toArrayUnsafe());
        return Entity.builder()
                .id(entityId.getId())
                .evmAddress(toEvmAddress(entityId))
                .build();
    }
}
