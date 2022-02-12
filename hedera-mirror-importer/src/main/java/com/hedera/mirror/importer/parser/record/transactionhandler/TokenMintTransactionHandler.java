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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import javax.inject.Named;
import lombok.AllArgsConstructor;
import org.apache.commons.codec.binary.Hex;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.BytesType;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint64;

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
class TokenMintTransactionHandler implements TransactionHandler {

    private static final String MINT_TOKEN_FUNCTION_NAME = "mintToken";
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

                // amount, gasLimit and functionParameters are missing from proto. Generate missing values
                contractResult.setAmount(0L); // precompile Mint amount is always 0
                var transactionBody = recordItem.getTransactionBody().getTokenMint();

                // TokenMint signature - mintToken(address token, uint64 amount, bytes[] memory metadata)
                Function function = new Function(
                        MINT_TOKEN_FUNCTION_NAME,
                        Arrays.asList(
                                new Address(Hex.encodeHexString(DomainUtils
                                        .toEvmAddress(EntityId.of(transactionBody.getToken())))),
                                new Uint64(transactionBody.getAmount()),
                                new DynamicArray(BytesType.class, transactionBody.getMetadataList())),
                        Collections.emptyList()
                );
                contractResult.setFunctionResult(FunctionEncoder.encode(function).getBytes(StandardCharsets.UTF_8));

                return contractResult;
            }
        }

        return null;
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return EntityId.of(recordItem.getTransactionBody().getTokenMint().getToken());
    }

    @Override
    public TransactionType getType() {
        return TransactionType.TOKENMINT;
    }
}
