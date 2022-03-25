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

package construction

import (
	"context"
	"strconv"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-sdk-go/v2"
	log "github.com/sirupsen/logrus"
)

type cryptoTransferTransactionConstructor struct {
	commonTransactionConstructor
}

type transfer struct {
	account hedera.AccountID
	amount  types.Amount
}

type senderMap map[string]types.AccountId

func (m senderMap) toSenders() []types.AccountId {
	senders := make([]types.AccountId, 0, len(m))
	for _, sender := range m {
		senders = append(senders, sender)
	}
	return senders
}

type nftTransfer struct {
	nftId    hedera.NftID
	receiver hedera.AccountID
	sender   hedera.AccountID
}

func (c *cryptoTransferTransactionConstructor) Construct(
	_ context.Context,
	operations types.OperationSlice,
) (interfaces.Transaction, []types.AccountId, *rTypes.Error) {
	transfers, senders, rErr := c.preprocess(operations)
	if rErr != nil {
		return nil, nil, rErr
	}

	nftTransfers := make(map[hedera.NftID]*nftTransfer)
	transaction := hedera.NewTransferTransaction()

	for _, transfer := range transfers {
		switch amount := transfer.amount.(type) {
		case *types.HbarAmount:
			transaction.AddHbarTransfer(transfer.account, hedera.HbarFromTinybar(amount.Value))
		case *types.TokenAmount:
			tokenId, _ := hedera.TokenIDFromString(amount.TokenId.String())
			if amount.Type == domain.TokenTypeFungibleCommon {
				transaction.AddTokenTransferWithDecimals(tokenId, transfer.account, amount.Value,
					uint32(amount.Decimals))
			} else {
				// build nft transfers
				nftId := hedera.NftID{SerialNumber: amount.SerialNumbers[0], TokenID: tokenId}
				if _, ok := nftTransfers[nftId]; !ok {
					nftTransfers[nftId] = &nftTransfer{nftId: nftId}
				}

				if amount.Value == 1 {
					nftTransfers[nftId].receiver = transfer.account
				} else {
					nftTransfers[nftId].sender = transfer.account
				}
			}
		}
	}

	for _, nftTransfer := range nftTransfers {
		transaction.AddNftTransfer(nftTransfer.nftId, nftTransfer.sender, nftTransfer.receiver)
	}

	return transaction, senders, nil
}

func (c *cryptoTransferTransactionConstructor) Parse(_ context.Context, transaction interfaces.Transaction) (
	types.OperationSlice,
	[]types.AccountId,
	*rTypes.Error,
) {
	transferTransaction, ok := transaction.(*hedera.TransferTransaction)
	if !ok {
		return nil, nil, errors.ErrTransactionInvalidType
	}

	if transferTransaction.GetTransactionID().AccountID == nil {
		return nil, nil, errors.ErrInvalidTransaction
	}

	hbarTransferMap := transferTransaction.GetHbarTransfers()
	tokenTransferMap := transferTransaction.GetTokenTransfers()
	tokenDecimals := transferTransaction.GetTokenIDDecimals()
	nftTransferMap := transferTransaction.GetNftTransfers()

	numOperations := len(hbarTransferMap)
	for _, tokenTransfers := range tokenTransferMap {
		numOperations += len(tokenTransfers)
	}
	for _, nftTransfers := range nftTransferMap {
		numOperations += len(nftTransfers) * 2
	}
	operations := make(types.OperationSlice, 0, numOperations)

	for accountId, hbarAmount := range hbarTransferMap {
		var err *rTypes.Error
		amount := &types.HbarAmount{Value: hbarAmount.AsTinybar()}
		if operations, err = c.addOperation(accountId, amount, operations); err != nil {
			return nil, nil, err
		}
	}

	for token, tokenTransfers := range tokenTransferMap {
		domainToken, err := getDomainToken(token, tokenDecimals, domain.TokenTypeFungibleCommon)
		if err != nil {
			return nil, nil, err
		}
		for _, tokenTransfer := range tokenTransfers {
			tokenAmount := types.NewTokenAmount(domainToken, tokenTransfer.Amount)
			if operations, err = c.addOperation(tokenTransfer.AccountID, tokenAmount, operations); err != nil {
				return nil, nil, err
			}
		}
	}

	for token, nftTransfers := range nftTransferMap {
		domainToken, err := getDomainToken(token, tokenDecimals, domain.TokenTypeNonFungibleUnique)
		if err != nil {
			return nil, nil, err
		}
		for _, nftTransfer := range nftTransfers {
			tokenAmount := types.NewTokenAmount(domainToken, 1).SetSerialNumbers([]int64{nftTransfer.SerialNumber})
			if operations, err = c.addOperation(nftTransfer.ReceiverAccountID, tokenAmount, operations); err != nil {
				return nil, nil, err
			}

			tokenAmount = types.NewTokenAmount(domainToken, -1).SetSerialNumbers([]int64{nftTransfer.SerialNumber})
			if operations, err = c.addOperation(nftTransfer.SenderAccountID, tokenAmount, operations); err != nil {
				return nil, nil, err
			}
		}
	}

	senderMap := senderMap{}
	for _, operation := range operations {
		if operation.Amount.GetValue() < 0 {
			senderMap[operation.AccountId.String()] = operation.AccountId
		}
	}

	return operations, senderMap.toSenders(), nil
}

func (c *cryptoTransferTransactionConstructor) Preprocess(_ context.Context, operations types.OperationSlice) (
	[]types.AccountId,
	*rTypes.Error,
) {
	_, senders, err := c.preprocess(operations)
	if err != nil {
		return nil, err
	}

	return senders, nil
}

func (c *cryptoTransferTransactionConstructor) addOperation(
	sdkAccountId hedera.AccountID,
	amount types.Amount,
	operations types.OperationSlice,
) (types.OperationSlice, *rTypes.Error) {
	accountId, err := types.NewAccountIdFromSdkAccountId(sdkAccountId)
	if err != nil {
		return nil, errors.ErrInvalidAccount
	}
	operation := types.Operation{
		Index:     int64(len(operations)),
		Type:      c.GetOperationType(),
		AccountId: accountId,
		Amount:    amount,
	}
	return append(operations, operation), nil
}

func (c *cryptoTransferTransactionConstructor) preprocess(operations types.OperationSlice) (
	[]transfer,
	[]types.AccountId,
	*rTypes.Error,
) {
	if err := validateOperations(operations, 0, c.GetOperationType(), false); err != nil {
		return nil, nil, err
	}

	nftValues := make(map[string][]int64)
	senderMap := senderMap{}
	totalAmounts := make(map[string]int64)
	transfers := make([]transfer, 0, len(operations))

	for _, operation := range operations {
		accountId := operation.AccountId
		amount := operation.Amount
		if amount.GetValue() == 0 {
			return nil, nil, errors.ErrInvalidOperationsAmount
		}

		transfers = append(transfers, transfer{account: accountId.ToSdkAccountId(), amount: amount})

		if amount.GetValue() < 0 {
			senderMap[accountId.String()] = accountId
		}

		totalAmounts[amount.GetSymbol()] += amount.GetValue()

		if tokenAmount, ok := amount.(*types.TokenAmount); ok && tokenAmount.Type == domain.TokenTypeNonFungibleUnique {
			if tokenAmount.Value != 1 && tokenAmount.Value != -1 {
				return nil, nil, errors.ErrInvalidOperationsAmount
			}
			nftId := tokenAmount.TokenId.String() + "-" + strconv.FormatInt(tokenAmount.SerialNumbers[0], 10)
			nftValues[nftId] = append(nftValues[nftId], tokenAmount.Value)
		}
	}

	for symbol, totalAmount := range totalAmounts {
		if totalAmount != 0 {
			log.Errorf("Transfer sum for symbol %s is not 0", symbol)
			return nil, nil, errors.ErrInvalidOperationsTotalAmount
		}
	}

	for nftId, values := range nftValues {
		if len(values) != 2 && values[0]+values[1] != 0 {
			log.Errorf("Transfers for nft id %s violate nft tranfer requirement", nftId)
			return nil, nil, errors.ErrInvalidOperationsTotalAmount
		}
	}

	return transfers, senderMap.toSenders(), nil
}

func getDomainToken(token hedera.TokenID, tokenDecimals map[hedera.TokenID]uint32, tokenType string) (
	domain.Token,
	*rTypes.Error,
) {
	decimals := int64(0)
	if tokenType == domain.TokenTypeFungibleCommon {
		d, ok := tokenDecimals[token]
		if !ok {
			return domain.Token{}, errors.ErrInvalidToken
		}
		decimals = int64(d)
	}

	tokenId, err := domain.EntityIdOf(int64(token.Shard), int64(token.Realm), int64(token.Token))
	if err != nil {
		return domain.Token{}, errors.ErrInvalidToken
	}
	return domain.Token{Decimals: decimals, TokenId: tokenId, Type: tokenType}, nil
}

func newCryptoTransferTransactionConstructor() transactionConstructorWithType {
	return &cryptoTransferTransactionConstructor{
		commonTransactionConstructor: newCommonTransactionConstructor(
			hedera.NewTransferTransaction(),
			types.OperationTypeCryptoTransfer,
		),
	}
}
