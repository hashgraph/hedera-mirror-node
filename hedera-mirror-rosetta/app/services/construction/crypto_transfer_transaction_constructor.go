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
	"reflect"
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
	transactionType string
}

type transfer struct {
	account hedera.AccountID
	amount  types.Amount
}

type senderMap map[hedera.AccountID]int

func (m senderMap) toSenders() []hedera.AccountID {
	senders := make([]hedera.AccountID, 0, len(m))
	for sender := range m {
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
	nodeAccountId hedera.AccountID,
	operations []*rTypes.Operation,
	validStartNanos int64,
) (interfaces.Transaction, []hedera.AccountID, *rTypes.Error) {
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

	// set to a single node account ID, so later can add signature
	_, err := transaction.
		SetTransactionID(getTransactionId(senders[0], validStartNanos)).
		SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
		Freeze()
	if err != nil {
		return nil, nil, errors.ErrTransactionFreezeFailed
	}

	return transaction, senders, nil
}

func (c *cryptoTransferTransactionConstructor) GetOperationType() string {
	return types.OperationTypeCryptoTransfer
}

func (c *cryptoTransferTransactionConstructor) GetSdkTransactionType() string {
	return c.transactionType
}

func (c *cryptoTransferTransactionConstructor) Parse(_ context.Context, transaction interfaces.Transaction) (
	[]*rTypes.Operation,
	[]hedera.AccountID,
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
	operations := make([]*rTypes.Operation, 0, numOperations)
	senderMap := senderMap{}

	for accountId, hbarAmount := range hbarTransferMap {
		operations = c.addOperation(accountId, &types.HbarAmount{Value: hbarAmount.AsTinybar()}, operations, senderMap)
	}

	for token, tokenTransfers := range tokenTransferMap {
		decimals, ok := tokenDecimals[token]
		if !ok {
			return nil, nil, errors.ErrInvalidToken
		}
		tokenId, err := domain.EntityIdOf(int64(token.Shard), int64(token.Realm), int64(token.Token))
		if err != nil {
			return nil, nil, errors.ErrInvalidToken
		}
		domainToken := domain.Token{
			Decimals: int64(decimals),
			TokenId:  tokenId,
			Type:     domain.TokenTypeFungibleCommon,
		}
		for _, tokenTransfer := range tokenTransfers {
			tokenAmount := types.NewTokenAmount(domainToken, tokenTransfer.Amount)
			operations = c.addOperation(tokenTransfer.AccountID, tokenAmount, operations, senderMap)
		}
	}

	for token, nftTransfers := range nftTransferMap {
		tokenId, err := domain.EntityIdOf(int64(token.Shard), int64(token.Realm), int64(token.Token))
		if err != nil {
			return nil, nil, errors.ErrInvalidToken
		}
		domainTokan := domain.Token{
			TokenId: tokenId,
			Type:    domain.TokenTypeNonFungibleUnique,
		}
		for _, nftTransfer := range nftTransfers {
			tokenAmount := types.NewTokenAmount(domainTokan, 1).SetSerialNumbers([]int64{nftTransfer.SerialNumber})
			operations = c.addOperation(nftTransfer.ReceiverAccountID, tokenAmount, operations, senderMap)
			tokenAmount = types.NewTokenAmount(domainTokan, -1).SetSerialNumbers([]int64{nftTransfer.SerialNumber})
			operations = c.addOperation(nftTransfer.SenderAccountID, tokenAmount, operations, senderMap)
		}
	}

	return operations, senderMap.toSenders(), nil
}

func (c *cryptoTransferTransactionConstructor) Preprocess(_ context.Context, operations []*rTypes.Operation) (
	[]hedera.AccountID,
	*rTypes.Error,
) {
	_, senders, err := c.preprocess(operations)
	if err != nil {
		return nil, err
	}

	return senders, nil
}

func (c *cryptoTransferTransactionConstructor) addOperation(
	accountId hedera.AccountID,
	amount types.Amount,
	operations []*rTypes.Operation,
	senderMap senderMap,
) []*rTypes.Operation {
	operation := &rTypes.Operation{
		OperationIdentifier: &rTypes.OperationIdentifier{Index: int64(len(operations))},
		Type:                c.GetOperationType(),
		Account:             &rTypes.AccountIdentifier{Address: accountId.String()},
		Amount:              amount.ToRosetta(),
	}

	if amount.GetValue() < 0 {
		senderMap[accountId] = 1
	}

	return append(operations, operation)
}

func (c *cryptoTransferTransactionConstructor) preprocess(operations []*rTypes.Operation) (
	[]transfer,
	[]hedera.AccountID,
	*rTypes.Error,
) {
	if err := validateOperations(operations, 0, c.GetOperationType(), false); err != nil {
		return nil, nil, err
	}

	transfers := make([]transfer, 0, len(operations))
	senderMap := senderMap{}
	sums := make(map[string]int64)
	nftValues := make(map[string][]int64)

	for _, operation := range operations {
		account, err := hedera.AccountIDFromString(operation.Account.Address)
		if err != nil {
			return nil, nil, errors.ErrInvalidAccount
		}

		amount, rErr := types.NewAmount(operation.Amount)
		if rErr != nil {
			return nil, nil, rErr
		}

		if amount.GetValue() == 0 {
			return nil, nil, errors.ErrInvalidOperationsAmount
		}

		transfers = append(transfers, transfer{account: account, amount: amount})

		if amount.GetValue() < 0 {
			senderMap[account] = 1
		}

		sums[operation.Amount.Currency.Symbol] += amount.GetValue()

		if tokenAmount, ok := amount.(*types.TokenAmount); ok && tokenAmount.Type == domain.TokenTypeNonFungibleUnique {
			if tokenAmount.Value != 1 && tokenAmount.Value != -1 {
				return nil, nil, errors.ErrInvalidOperationsAmount
			}
			nftId := tokenAmount.TokenId.String() + "-" + strconv.FormatInt(tokenAmount.SerialNumbers[0], 10)
			nftValues[nftId] = append(nftValues[nftId], tokenAmount.Value)
		}
	}

	for symbol, sum := range sums {
		if sum != 0 {
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

func newCryptoTransferTransactionConstructor() transactionConstructorWithType {
	transactionType := reflect.TypeOf(hedera.TransferTransaction{}).Name()
	return &cryptoTransferTransactionConstructor{
		transactionType: transactionType,
	}
}
