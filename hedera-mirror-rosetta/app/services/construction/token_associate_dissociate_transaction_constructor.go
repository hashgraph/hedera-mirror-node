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

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	hErrors "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-sdk-go/v2"
)

type tokenAssociateDissociateTransactionConstructor struct {
	operationType   string
	transactionType string
}

func (t *tokenAssociateDissociateTransactionConstructor) Construct(
	_ context.Context,
	nodeAccountId hedera.AccountID,
	operations []*rTypes.Operation,
	validStartNanos int64,
) (interfaces.Transaction, []hedera.AccountID, *rTypes.Error) {
	payer, tokenIds, rErr := t.preprocess(operations)
	if rErr != nil {
		return nil, nil, rErr
	}

	var tx interfaces.Transaction
	var err error
	transactionId := getTransactionId(*payer, validStartNanos)
	if t.operationType == types.OperationTypeTokenAssociate {
		tx, err = hedera.NewTokenAssociateTransaction().
			SetAccountID(*payer).
			SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
			SetTokenIDs(tokenIds...).
			SetTransactionID(transactionId).
			Freeze()
	} else {
		tx, err = hedera.NewTokenDissociateTransaction().
			SetAccountID(*payer).
			SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
			SetTokenIDs(tokenIds...).
			SetTransactionID(transactionId).
			Freeze()
	}

	if err != nil {
		return nil, nil, hErrors.ErrTransactionFreezeFailed
	}

	return tx, []hedera.AccountID{*payer}, nil
}

func (t *tokenAssociateDissociateTransactionConstructor) Parse(
	_ context.Context, transaction interfaces.Transaction,
) ([]*rTypes.Operation, []hedera.AccountID, *rTypes.Error) {
	var accountId hedera.AccountID
	var payerId *hedera.AccountID
	var tokenIds []hedera.TokenID

	switch tx := transaction.(type) {
	case *hedera.TokenAssociateTransaction:
		if t.operationType != types.OperationTypeTokenAssociate {
			return nil, nil, hErrors.ErrTransactionInvalidType
		}

		accountId = tx.GetAccountID()
		payerId = tx.GetTransactionID().AccountID
		tokenIds = tx.GetTokenIDs()
	case *hedera.TokenDissociateTransaction:
		if t.operationType != types.OperationTypeTokenDissociate {
			return nil, nil, hErrors.ErrTransactionInvalidType
		}

		accountId = tx.GetAccountID()
		payerId = tx.GetTransactionID().AccountID
		tokenIds = tx.GetTokenIDs()
	default:
		return nil, nil, hErrors.ErrTransactionInvalidType
	}

	if isZeroAccountId(accountId) || payerId == nil || isZeroAccountId(*payerId) {
		return nil, nil, hErrors.ErrInvalidTransaction
	}

	if accountId != *payerId {
		return nil, nil, hErrors.ErrInvalidTransaction
	}

	if len(tokenIds) == 0 {
		return nil, nil, hErrors.ErrInvalidTransaction
	}

	account := &rTypes.AccountIdentifier{Address: accountId.String()}
	operations := make([]*rTypes.Operation, 0, len(tokenIds))

	for index, tokenId := range tokenIds {
		tokenEntityId, err := domain.EntityIdOf(int64(tokenId.Shard), int64(tokenId.Realm), int64(tokenId.Token))
		if err != nil {
			return nil, nil, hErrors.ErrInvalidToken
		}
		domainToken := domain.Token{
			TokenId: tokenEntityId,
			Type:    domain.TokenTypeUnknown,
		}
		operations = append(operations, &rTypes.Operation{
			OperationIdentifier: &rTypes.OperationIdentifier{Index: int64(index)},
			Type:                t.operationType,
			Account:             account,
			Amount: &rTypes.Amount{
				Value:    "0",
				Currency: types.Token{Token: domainToken}.ToRosettaCurrency(),
			},
		})
	}

	return operations, []hedera.AccountID{accountId}, nil
}

func (t *tokenAssociateDissociateTransactionConstructor) Preprocess(
	_ context.Context,
	operations []*rTypes.Operation,
) ([]hedera.AccountID, *rTypes.Error) {
	payer, _, err := t.preprocess(operations)
	if err != nil {
		return nil, err
	}

	return []hedera.AccountID{*payer}, nil
}

func (t *tokenAssociateDissociateTransactionConstructor) preprocess(operations []*rTypes.Operation) (
	*hedera.AccountID,
	[]hedera.TokenID,
	*rTypes.Error,
) {
	if rErr := validateOperations(operations, 0, t.operationType, false); rErr != nil {
		return nil, nil, rErr
	}

	tokenIds := make([]hedera.TokenID, 0, len(operations))
	address := operations[0].Account.Address
	for _, operation := range operations {
		if operation.Account.Address != address {
			return nil, nil, hErrors.ErrInvalidAccount
		}

		tokenId, err := hedera.TokenIDFromString(operation.Amount.Currency.Symbol)
		if err != nil {
			return nil, nil, hErrors.ErrInvalidToken
		}

		tokenIds = append(tokenIds, tokenId)
	}

	payer, err := hedera.AccountIDFromString(address)
	if err != nil {
		return nil, nil, hErrors.ErrInvalidAccount
	}

	return &payer, tokenIds, nil
}

func (t *tokenAssociateDissociateTransactionConstructor) GetOperationType() string {
	return t.operationType
}

func (t *tokenAssociateDissociateTransactionConstructor) GetSdkTransactionType() string {
	return t.transactionType
}

func newTokenAssociateTransactionConstructor() transactionConstructorWithType {
	transactionType := reflect.TypeOf(hedera.TokenAssociateTransaction{}).Name()
	return &tokenAssociateDissociateTransactionConstructor{
		operationType:   types.OperationTypeTokenAssociate,
		transactionType: transactionType,
	}
}

func newTokenDissociateTransactionConstructor() transactionConstructorWithType {
	transactionType := reflect.TypeOf(hedera.TokenDissociateTransaction{}).Name()
	return &tokenAssociateDissociateTransactionConstructor{
		operationType:   types.OperationTypeTokenDissociate,
		transactionType: transactionType,
	}
}
