package com.hedera.mirror.importer.parser.record.transactionhandler;

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

import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;
import javax.inject.Named;
import lombok.AllArgsConstructor;
import org.apache.commons.codec.binary.Hex;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;

import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;

@AllArgsConstructor
@Named
class TokenAssociateTransactionHandler implements TransactionHandler {

    private static final String ASSOCIATE_TOKEN_FUNCTION_NAME = "associateToken";
    private static final String ASSOCIATE_TOKENS_FUNCTION_NAME = "associateTokens";
    protected final EntityIdService entityIdService;
    protected final EntityProperties entityProperties;

    @Override
    public ContractResult getContractResult(Transaction transaction, RecordItem recordItem) {
        if (entityProperties.getPersist().isContracts() && recordItem.getRecord().hasContractCallResult()) {

            var functionResult = recordItem.getRecord().getContractCallResult();
            if (functionResult != ContractFunctionResult.getDefaultInstance() && functionResult.hasContractID()) {
                ContractResult contractResult = new ContractResult();
                contractResult.setConsensusTimestamp(recordItem.getConsensusTimestamp());
                contractResult.setContractId(entityIdService.lookup(functionResult.getContractID()));
                contractResult.setPayerAccountId(transaction.getPayerAccountId());

                // TokenAssociate signature - associateToken(address account, address tokens)
                // TokenAssociate signature - associateTokens(address account, address[] memory tokens)
                var transactionBody = recordItem.getTransactionBody().getTokenAssociate();
                boolean singleAssociate = transactionBody.getTokensList().size() == 1;
                Function function = new Function(
                        singleAssociate ? ASSOCIATE_TOKEN_FUNCTION_NAME : ASSOCIATE_TOKENS_FUNCTION_NAME,
                        Arrays.asList(
                                getAddress(EntityId.of(transactionBody.getAccount())),
                                singleAssociate ? getAddress(EntityId.of(transactionBody.getTokens(0))) :
                                        getAddresses(transactionBody)),
                        Collections.emptyList()
                );
                contractResult.setFunctionResult(FunctionEncoder.encode(function).getBytes(StandardCharsets.UTF_8));

                return contractResult;
            }
        }

        return null;
    }

    private Address getAddress(EntityId entityId) {
        return new Address(Hex.encodeHexString(DomainUtils
                .toEvmAddress(entityId)));
    }

    private DynamicArray getAddresses(TokenAssociateTransactionBody transactionBody) {
        return new DynamicArray(
                Address.class,
                transactionBody.getTokensList().stream().map(x ->
                        new Address(Hex
                                .encodeHexString(DomainUtils.toEvmAddress(EntityId.of(x)))))
                        .collect(Collectors.toList()));
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getTokenAssociate().getAccount());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENASSOCIATE;
    }
}
