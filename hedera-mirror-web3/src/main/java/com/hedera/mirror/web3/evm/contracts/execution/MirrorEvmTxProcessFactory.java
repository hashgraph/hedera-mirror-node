package com.hedera.mirror.web3.evm.contracts.execution;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import com.hedera.mirror.web3.evm.account.AccountAccessorImpl;
import com.hedera.mirror.web3.evm.account.MirrorEvmContractAliases;
import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hedera.mirror.web3.evm.properties.StaticBlockMetaSource;
import com.hedera.mirror.web3.evm.store.contract.MirrorEntityAccess;

import javax.inject.Named;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.FactoryBean;

@Named
@RequiredArgsConstructor
public class MirrorEvmTxProcessFactory implements FactoryBean<MirrorEvmTxProcessorFacadeImpl> {

    final MirrorEntityAccess entityAccess;
    final MirrorNodeEvmProperties evmProperties;
    final StaticBlockMetaSource blockMetaSource;
    final MirrorEvmContractAliases aliasManager;
    final PricesAndFeesImpl pricesAndFees;
    final AccountAccessorImpl accountAccessor;

    @Override
    public MirrorEvmTxProcessorFacadeImpl getObject() {
        return new MirrorEvmTxProcessorFacadeImpl(entityAccess, evmProperties,
                blockMetaSource, aliasManager, pricesAndFees, accountAccessor);
    }

    @Override
    public Class<?> getObjectType() {
        return MirrorEvmTxProcessorFacadeImpl.class;
    }

    @Override
    public boolean isSingleton() {
        return false;
    }
}
