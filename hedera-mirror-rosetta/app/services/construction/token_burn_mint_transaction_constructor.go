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
	"fmt"
	"reflect"
	"strconv"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/repositories"
	hErrors "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/hashgraph/hedera-sdk-go/v2"
)

type tokenAmount struct {
	amount uint64
	token  hedera.TokenID
}

type tokenBurnMintTransactionConstructor struct {
	operationType   string
	tokeRepo        repositories.TokenRepository
	transactionType string
}

func (t *tokenBurnMintTransactionConstructor) Construct(
	nodeAccountId hedera.AccountID,
	operations []*rTypes.Operation,
) (ITransaction, []hedera.AccountID, *rTypes.Error) {
	payer, tokenAmount, rErr := t.preprocess(operations)
	if rErr != nil {
		return nil, nil, rErr
	}

	var tx ITransaction
	var err error

	if t.operationType == config.OperationTypeTokenBurn {
		tx, err = hedera.NewTokenBurnTransaction().
			SetAmount(tokenAmount.amount).
			SetTokenID(tokenAmount.token).
			SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
			SetTransactionID(hedera.TransactionIDGenerate(*payer)).
			Freeze()
	} else {
		tx, err = hedera.NewTokenMintTransaction().
			SetAmount(tokenAmount.amount).
			SetTokenID(tokenAmount.token).
			SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
			SetTransactionID(hedera.TransactionIDGenerate(*payer)).
			Freeze()
	}

	if err != nil {
		return nil, nil, hErrors.ErrTransactionFreezeFailed
	}

	return tx, []hedera.AccountID{*payer}, nil
}

func (t *tokenBurnMintTransactionConstructor) Parse(transaction ITransaction) (
	[]*rTypes.Operation,
	[]hedera.AccountID,
	*rTypes.Error,
) {
	var amount uint64
	var payer *hedera.AccountID
	var tokenId hedera.TokenID

	switch tx := transaction.(type) {
	case *hedera.TokenBurnTransaction:
		if t.operationType != config.OperationTypeTokenBurn {
			return nil, nil, hErrors.ErrTransactionInvalidType
		}

		amount = tx.GetAmmount()
		payer = tx.GetTransactionID().AccountID
		tokenId = tx.GetTokenID()
	case *hedera.TokenMintTransaction:
		if t.operationType != config.OperationTypeTokenMint {
			return nil, nil, hErrors.ErrTransactionInvalidType
		}

		amount = tx.GetAmount()
		payer = tx.GetTransactionID().AccountID
		tokenId = tx.GetTokenID()
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

	operation := &rTypes.Operation{
		OperationIdentifier: &rTypes.OperationIdentifier{
			Index: 0,
		},
		Account: &rTypes.AccountIdentifier{Address: payer.String()},
		Amount: &rTypes.Amount{
			Value:    fmt.Sprintf("%d", amount),
			Currency: dbToken.ToRosettaCurrency(),
		},
		Type: t.operationType,
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

func (t *tokenBurnMintTransactionConstructor) preprocess(operations []*rTypes.Operation) (
	*hedera.AccountID,
	*tokenAmount,
	*rTypes.Error,
) {
	if rErr := validateOperations(operations, 1, t.operationType, false); rErr != nil {
		return nil, nil, rErr
	}

	operation := operations[0]
	tokenAmount := &tokenAmount{}
	amount := operation.Amount

	value, err := strconv.ParseInt(amount.Value, 10, 64)
	if err != nil || value <= 0 {
		return nil, nil, hErrors.ErrInvalidAmount
	}
	tokenAmount.amount = uint64(value)

	tokenId, rErr := validateToken(t.tokeRepo, amount.Currency)
	if rErr != nil {
		return nil, nil, rErr
	}
	tokenAmount.token = *tokenId

	payer, err := hedera.AccountIDFromString(operation.Account.Address)
	if err != nil || isZeroAccountId(payer) {
		return nil, nil, hErrors.ErrInvalidAccount
	}

	return &payer, tokenAmount, nil
}

func (t *tokenBurnMintTransactionConstructor) GetOperationType() string {
	return t.operationType
}

func (t *tokenBurnMintTransactionConstructor) GetSdkTransactionType() string {
	return t.transactionType
}

func newTokenBurnTransactionConstructor(tokenRepo repositories.TokenRepository) transactionConstructorWithType {
	transactionType := reflect.TypeOf(hedera.TokenBurnTransaction{}).Name()
	return &tokenBurnMintTransactionConstructor{
		operationType:   config.OperationTypeTokenBurn,
		tokeRepo:        tokenRepo,
		transactionType: transactionType,
	}
}

func newTokenMintTransactionConstructor(tokenRepo repositories.TokenRepository) transactionConstructorWithType {
	transactionType := reflect.TypeOf(hedera.TokenMintTransaction{}).Name()
	return &tokenBurnMintTransactionConstructor{
		operationType:   config.OperationTypeTokenMint,
		tokeRepo:        tokenRepo,
		transactionType: transactionType,
	}
}
