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

import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.inject.Named;
import lombok.AllArgsConstructor;
import org.apache.commons.codec.binary.Hex;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Int64;

import com.hedera.mirror.common.domain.contract.ContractResult;
import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.common.domain.transaction.Transaction;
import com.hedera.mirror.common.domain.transaction.TransactionType;
import com.hedera.mirror.common.util.DomainUtils;
import com.hedera.mirror.importer.domain.EntityIdService;
import com.hedera.mirror.importer.exception.InvalidDatasetException;
import com.hedera.mirror.importer.parser.record.entity.EntityProperties;

@AllArgsConstructor
@Named
class CryptoTransferTransactionHandler implements TransactionHandler {

    private static final String CRYPTO_TRANSFER_FUNCTION_NAME = "cryptoTransfer";
    private static final String TRANSFER_TOKEN_FUNCTION_NAME = "transferToken";
    private static final String TRANSFER_TOKENS_FUNCTION_NAME = "transferTokens";
    private static final String TRANSFER_NFT_FUNCTION_NAME = "transferNFT";
    private static final String TRANSFER_NFTS_FUNCTION_NAME = "transferNFTs";
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

                var transactionBody = recordItem.getTransactionBody().getCryptoTransfer();

                // CryptoTransfer signatures
                Function function = null;

                List<TokenTransferList> tokenTransferList = transactionBody.getTokenTransfersList();
                if (tokenTransferList.size() > 1) {
                    // cryptoTransfer(IHederaTokenService.TokenTransferList[] memory tokenTransfers)
                    function = getCryptoTransferFunction(tokenTransferList);
                } else {
                    TokenTransferList tokenTransferListItem = tokenTransferList.get(0);
                    EntityId tokenEntityId = EntityId.of(tokenTransferListItem.getToken());
                    if (tokenTransferListItem.getTransfersCount() == 1) {
                        // transferToken(address token, address sender, address receiver, int64 amount)
                        AccountAmount accountAmount = tokenTransferListItem.getTransfers(0);
                        function = new Function(
                                CRYPTO_TRANSFER_FUNCTION_NAME,
                                Arrays.asList(
                                        getAddress(tokenEntityId),
                                        getAddress(recordItem.getPayerAccountId()),
                                        getAddress(EntityId.of(accountAmount.getAccountID())),
                                        new Int64(accountAmount.getAmount())),
                                Collections.emptyList()
                        );
                    } else if (tokenTransferListItem.getTransfersCount() > 1) {
                        // transferTokens(address token, address[] memory accountIds, int64[] memory amounts)

                    } else if (tokenTransferListItem.getNftTransfersCount() == 0) {
                        // transferNFT(address token, address sender, address receiver, int64 serialNumber)
                        NftTransfer nftTransfer = tokenTransferListItem.getNftTransfers(0);
                        function = new Function(
                                CRYPTO_TRANSFER_FUNCTION_NAME,
                                Arrays.asList(
                                        getAddress(tokenEntityId),
                                        getAddress(EntityId.of(nftTransfer.getSenderAccountID())),
                                        getAddress(EntityId.of(nftTransfer.getReceiverAccountID())),
                                        new Int64(nftTransfer.getSerialNumber())),
                                Collections.emptyList()
                        );
                    } else if (tokenTransferListItem.getNftTransfersCount() > 1) {
                        // transferNFTs(address token, address[] memory sender, address[] memory receiver, int64[]
                        // memory serialNumber)
                    } else {
                        throw new InvalidDatasetException(String
                                .format("Unknown cryptoTransfer/tokenTransfer scenario"));
                    }
                }

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

    private Function getCryptoTransferFunction(List<TokenTransferList> tokenTransferList) {
        return new Function(
                CRYPTO_TRANSFER_FUNCTION_NAME,
                Arrays.asList(
                        new DynamicArray(TokenTransferList.class, tokenTransferList)),
                Collections.emptyList()
        );
    }

    @Override
    public EntityId getEntity(RecordItem recordItem) {
        return null;
    }

    @Override
    public TransactionType getType() {
        return TransactionType.CRYPTOTRANSFER;
    }
}
