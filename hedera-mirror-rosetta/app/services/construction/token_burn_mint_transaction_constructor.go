/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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
	"reflect"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	hErrors "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/hashgraph/hedera-sdk-go/v2"
)

type tokenBurnMintTransactionConstructor struct {
	operationType   string
	tokeRepo        interfaces.TokenRepository
	transactionType string
}

func (t *tokenBurnMintTransactionConstructor) Construct(
	nodeAccountId hedera.AccountID,
	operations []*rTypes.Operation,
	validStartNanos int64,
) (interfaces.Transaction, []hedera.AccountID, *rTypes.Error) {
	payer, tokenAmount, rErr := t.preprocess(operations)
	if rErr != nil {
		return nil, nil, rErr
	}

	var tx interfaces.Transaction
	var err error
	tokenId, _ := hedera.TokenIDFromString(tokenAmount.TokenId.String())
	transactionId := getTransactionId(*payer, validStartNanos)
	if t.operationType == config.OperationTypeTokenBurn {
		tokenBurnTx := hedera.NewTokenBurnTransaction().
			SetTokenID(tokenId).
			SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
			SetTransactionID(transactionId)
		if tokenAmount.Type == domain.TokenTypeFungibleCommon {
			tokenBurnTx.SetAmount(uint64(tokenAmount.Value))
		} else {
			tokenBurnTx.SetSerialNumbers(tokenAmount.SerialNumbers)
		}

		tx, err = tokenBurnTx.Freeze()
	} else {
		tokenMintTx := hedera.NewTokenMintTransaction().
			SetTokenID(tokenId).
			SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
			SetTransactionID(transactionId)
		if tokenAmount.Type == domain.TokenTypeFungibleCommon {
			tokenMintTx.SetAmount(uint64(tokenAmount.Value))
		} else {
			tokenMintTx.SetMetadatas(tokenAmount.Metadatas)
		}

		tx, err = tokenMintTx.Freeze()
	}

	if err != nil {
		return nil, nil, hErrors.ErrTransactionFreezeFailed
	}

	return tx, []hedera.AccountID{*payer}, nil
}

func (t *tokenBurnMintTransactionConstructor) Parse(transaction interfaces.Transaction) (
	[]*rTypes.Operation,
	[]hedera.AccountID,
	*rTypes.Error,
) {
	var amount int64
	var metadatas [][]byte
	var payer *hedera.AccountID
	var serialNumbers []int64
	var tokenId hedera.TokenID

	switch tx := transaction.(type) {
	case *hedera.TokenBurnTransaction:
		if t.operationType != config.OperationTypeTokenBurn {
			return nil, nil, hErrors.ErrTransactionInvalidType
		}

		amount = -int64(tx.GetAmount())
		payer = tx.GetTransactionID().AccountID
		tokenId = tx.GetTokenID()
		serialNumbers = tx.GetSerialNumbers()
	case *hedera.TokenMintTransaction:
		if t.operationType != config.OperationTypeTokenMint {
			return nil, nil, hErrors.ErrTransactionInvalidType
		}

		amount = int64(tx.GetAmount())
		payer = tx.GetTransactionID().AccountID
		tokenId = tx.GetTokenID()
		metadatas = tx.GetMetadatas()
	default:
		return nil, nil, hErrors.ErrTransactionInvalidType
	}

	if isZeroTokenId(tokenId) || payer == nil || isZeroAccountId(*payer) {
		return nil, nil, hErrors.ErrInvalidTransaction
	}

	dbToken, err := t.tokeRepo.Find(tokenId.String())
	if err != nil {
		return nil, nil, err
	}

	tokenAmount := types.NewTokenAmount(dbToken, amount).
		SetMetadatas(metadatas).
		SetSerialNumbers(serialNumbers)
	operation := &rTypes.Operation{
		OperationIdentifier: &rTypes.OperationIdentifier{Index: 0},
		Account:             &rTypes.AccountIdentifier{Address: payer.String()},
		Amount:              tokenAmount.ToRosetta(),
		Type:                t.operationType,
	}

	return []*rTypes.Operation{operation}, []hedera.AccountID{*payer}, nil
}

func (t *tokenBurnMintTransactionConstructor) Preprocess(operations []*rTypes.Operation) (
	[]hedera.AccountID,
	*rTypes.Error,
) {
	payer, _, err := t.preprocess(operations)
	if err != nil {
		return nil, err
	}

	return []hedera.AccountID{*payer}, nil
}

func (t *tokenBurnMintTransactionConstructor) GetOperationType() string {
	return t.operationType
}

func (t *tokenBurnMintTransactionConstructor) GetSdkTransactionType() string {
	return t.transactionType
}

func (t *tokenBurnMintTransactionConstructor) preprocess(operations []*rTypes.Operation) (
	*hedera.AccountID,
	*types.TokenAmount,
	*rTypes.Error,
) {
	if rErr := validateOperations(operations, 1, t.operationType, false); rErr != nil {
		return nil, nil, rErr
	}

	operation := operations[0]
	tokenAmount, rErr := t.preprocessOperationAmount(operation.Amount)
	if rErr != nil {
		return nil, nil, rErr
	}

	payer, err := hedera.AccountIDFromString(operation.Account.Address)
	if err != nil || isZeroAccountId(payer) {
		return nil, nil, hErrors.ErrInvalidAccount
	}

	return &payer, tokenAmount, nil
}

func (t *tokenBurnMintTransactionConstructor) preprocessOperationAmount(operationAmount *rTypes.Amount) (
	*types.TokenAmount, *rTypes.Error,
) {
	amount, err := types.NewAmount(operationAmount)
	if err != nil {
		return nil, err
	}

	tokenAmount, ok := amount.(*types.TokenAmount)
	if !ok {
		return nil, hErrors.ErrInvalidCurrency
	}

	isNft := tokenAmount.Type == domain.TokenTypeNonFungibleUnique
	if t.operationType == config.OperationTypeTokenBurn {
		if tokenAmount.Value >= 0 || (isNft && len(tokenAmount.SerialNumbers) == 0) {
			return nil, hErrors.ErrInvalidOperationsAmount
		}
	} else {
		if tokenAmount.Value <= 0 || (isNft && len(tokenAmount.Metadatas) == 0) {
			return nil, hErrors.ErrInvalidOperationsAmount
		}
	}

	if _, err = validateToken(t.tokeRepo, operationAmount.Currency); err != nil {
		return nil, err
	}

	return tokenAmount, nil
}

func newTokenBurnTransactionConstructor(tokenRepo interfaces.TokenRepository) transactionConstructorWithType {
	transactionType := reflect.TypeOf(hedera.TokenBurnTransaction{}).Name()
	return &tokenBurnMintTransactionConstructor{
		operationType:   config.OperationTypeTokenBurn,
		tokeRepo:        tokenRepo,
		transactionType: transactionType,
	}
}

func newTokenMintTransactionConstructor(tokenRepo interfaces.TokenRepository) transactionConstructorWithType {
	transactionType := reflect.TypeOf(hedera.TokenMintTransaction{}).Name()
	return &tokenBurnMintTransactionConstructor{
		operationType:   config.OperationTypeTokenMint,
		tokeRepo:        tokenRepo,
		transactionType: transactionType,
	}
}
