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
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/hashgraph/hedera-sdk-go/v2"
)

type tokenDeleteTransactionConstructor struct {
	tokenRepo       interfaces.TokenRepository
	transactionType string
}

func (t *tokenDeleteTransactionConstructor) Construct(
	nodeAccountId hedera.AccountID,
	operations []*rTypes.Operation,
	validStartNanos int64,
) (interfaces.Transaction, []hedera.AccountID, *rTypes.Error) {
	payerId, tokenId, rErr := t.preprocess(operations)
	if rErr != nil {
		return nil, nil, rErr
	}

	tx, err := hedera.NewTokenDeleteTransaction().
		SetTokenID(*tokenId).
		SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
		SetTransactionID(getTransactionId(*payerId, validStartNanos)).
		Freeze()
	if err != nil {
		return nil, nil, hErrors.ErrTransactionFreezeFailed
	}

	return tx, []hedera.AccountID{*payerId}, nil
}

func (t *tokenDeleteTransactionConstructor) Parse(transaction interfaces.Transaction) (
	[]*rTypes.Operation,
	[]hedera.AccountID,
	*rTypes.Error,
) {
	tokenDeleteTransaction, ok := transaction.(*hedera.TokenDeleteTransaction)
	if !ok {
		return nil, nil, hErrors.ErrTransactionInvalidType
	}

	payerId := tokenDeleteTransaction.GetTransactionID().AccountID
	tokenId := tokenDeleteTransaction.GetTokenID()

	if payerId == nil || isZeroAccountId(*payerId) || isZeroTokenId(tokenId) {
		return nil, nil, hErrors.ErrInvalidTransaction
	}

	dbToken, err := t.tokenRepo.Find(tokenId.String())
	if err != nil {
		return nil, nil, err
	}

	operation := &rTypes.Operation{
		OperationIdentifier: &rTypes.OperationIdentifier{Index: 0},
		Account:             &rTypes.AccountIdentifier{Address: payerId.String()},
		Amount: &rTypes.Amount{
			Value:    "0",
			Currency: types.Token{Token: dbToken}.ToRosettaCurrency(),
		},
		Type: t.GetOperationType(),
	}

	return []*rTypes.Operation{operation}, []hedera.AccountID{*payerId}, nil
}

func (t *tokenDeleteTransactionConstructor) Preprocess(operations []*rTypes.Operation) (
	[]hedera.AccountID,
	*rTypes.Error,
) {
	payer, _, err := t.preprocess(operations)
	if err != nil {
		return nil, err
	}

	return []hedera.AccountID{*payer}, nil
}

func (t *tokenDeleteTransactionConstructor) preprocess(operations []*rTypes.Operation) (
	*hedera.AccountID,
	*hedera.TokenID,
	*rTypes.Error,
) {
	if rErr := validateOperations(operations, 1, t.GetOperationType(), false); rErr != nil {
		return nil, nil, rErr
	}

	operation := operations[0]
	payerId, err := hedera.AccountIDFromString(operation.Account.Address)
	if err != nil || isZeroAccountId(payerId) {
		return nil, nil, hErrors.ErrInvalidAccount
	}

	if operation.Amount.Value != "0" {
		return nil, nil, hErrors.ErrInvalidOperationsAmount
	}

	tokenId, rErr := validateToken(t.tokenRepo, operation.Amount.Currency)
	if rErr != nil {
		return nil, nil, rErr
	}

	return &payerId, tokenId, nil
}

func (t *tokenDeleteTransactionConstructor) GetOperationType() string {
	return config.OperationTypeTokenDelete
}

func (t *tokenDeleteTransactionConstructor) GetSdkTransactionType() string {
	return t.transactionType
}

func newTokenDeleteTransactionConstructor(tokenRepo interfaces.TokenRepository) transactionConstructorWithType {
	return &tokenDeleteTransactionConstructor{
		tokenRepo:       tokenRepo,
		transactionType: reflect.TypeOf(hedera.TokenDeleteTransaction{}).Name(),
	}
}
