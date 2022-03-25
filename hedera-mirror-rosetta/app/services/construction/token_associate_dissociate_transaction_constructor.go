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

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	hErrors "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-sdk-go/v2"
)

type tokenAssociateDissociateTransactionConstructor struct {
	commonTransactionConstructor
}

func (t *tokenAssociateDissociateTransactionConstructor) Construct(
	_ context.Context,
	operations types.OperationSlice,
) (interfaces.Transaction, []types.AccountId, *rTypes.Error) {
	payer, tokenIds, rErr := t.preprocess(operations)
	if rErr != nil {
		return nil, nil, rErr
	}

	var tx interfaces.Transaction
	sdkAccountId := payer.ToSdkAccountId()
	if t.operationType == types.OperationTypeTokenAssociate {
		tx = hedera.NewTokenAssociateTransaction().
			SetAccountID(sdkAccountId).
			SetTokenIDs(tokenIds...)
	} else {
		tx = hedera.NewTokenDissociateTransaction().
			SetAccountID(sdkAccountId).
			SetTokenIDs(tokenIds...)
	}

	return tx, []types.AccountId{*payer}, nil
}

func (t *tokenAssociateDissociateTransactionConstructor) Parse(
	_ context.Context, transaction interfaces.Transaction,
) (types.OperationSlice, []types.AccountId, *rTypes.Error) {
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

	account, err := types.NewAccountIdFromSdkAccountId(accountId)
	if err != nil {
		return nil, nil, hErrors.ErrInvalidAccount
	}
	operations := make(types.OperationSlice, 0, len(tokenIds))
	for index, tokenId := range tokenIds {
		tokenEntityId, err := domain.EntityIdOf(int64(tokenId.Shard), int64(tokenId.Realm), int64(tokenId.Token))
		if err != nil {
			return nil, nil, hErrors.ErrInvalidToken
		}
		domainToken := domain.Token{
			TokenId: tokenEntityId,
			Type:    domain.TokenTypeUnknown,
		}
		operations = append(operations, types.Operation{
			Index:     int64(index),
			Type:      t.operationType,
			AccountId: account,
			Amount:    types.NewTokenAmount(domainToken, 0),
		})
	}

	return operations, []types.AccountId{account}, nil
}

func (t *tokenAssociateDissociateTransactionConstructor) Preprocess(
	_ context.Context,
	operations types.OperationSlice,
) ([]types.AccountId, *rTypes.Error) {
	payer, _, err := t.preprocess(operations)
	if err != nil {
		return nil, err
	}

	return []types.AccountId{*payer}, nil
}

func (t *tokenAssociateDissociateTransactionConstructor) preprocess(operations types.OperationSlice) (
	*types.AccountId,
	[]hedera.TokenID,
	*rTypes.Error,
) {
	if rErr := validateOperations(operations, 0, t.operationType, false); rErr != nil {
		return nil, nil, rErr
	}

	accountId := operations[0].AccountId
	tokenIds := make([]hedera.TokenID, 0, len(operations))
	for _, operation := range operations {
		if operation.AccountId.String() != accountId.String() {
			return nil, nil, hErrors.ErrInvalidAccount
		}

		tokenAmount, ok := operation.Amount.(*types.TokenAmount)
		if !ok {
			return nil, nil, hErrors.ErrInvalidCurrency
		}

		tokenIds = append(tokenIds, hedera.TokenID{
			Shard: uint64(tokenAmount.TokenId.ShardNum),
			Realm: uint64(tokenAmount.TokenId.RealmNum),
			Token: uint64(tokenAmount.TokenId.EntityNum),
		})
	}

	return &accountId, tokenIds, nil
}

func newTokenAssociateTransactionConstructor() transactionConstructorWithType {
	return &tokenAssociateDissociateTransactionConstructor{
		commonTransactionConstructor: newCommonTransactionConstructor(
			hedera.NewTokenAssociateTransaction(),
			types.OperationTypeTokenAssociate,
		),
	}
}

func newTokenDissociateTransactionConstructor() transactionConstructorWithType {
	return &tokenAssociateDissociateTransactionConstructor{
		commonTransactionConstructor: newCommonTransactionConstructor(
			hedera.NewTokenDissociateTransaction(),
			types.OperationTypeTokenDissociate,
		),
	}
}
