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
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-sdk-go/v2"
)

type tokenDeleteTransactionConstructor struct {
	commonTransactionConstructor
}

func (t *tokenDeleteTransactionConstructor) Construct(
	_ context.Context,
	operations types.OperationSlice,
) (interfaces.Transaction, []types.AccountId, *rTypes.Error) {
	payerId, tokenId, rErr := t.preprocess(operations)
	if rErr != nil {
		return nil, nil, rErr
	}

	tx := hedera.NewTokenDeleteTransaction().SetTokenID(*tokenId)
	return tx, []types.AccountId{*payerId}, nil
}

func (t *tokenDeleteTransactionConstructor) Parse(_ context.Context, transaction interfaces.Transaction) (
	types.OperationSlice,
	[]types.AccountId,
	*rTypes.Error,
) {
	tokenDeleteTransaction, ok := transaction.(*hedera.TokenDeleteTransaction)
	if !ok {
		return nil, nil, errors.ErrTransactionInvalidType
	}

	payerId := tokenDeleteTransaction.GetTransactionID().AccountID
	tokenId := tokenDeleteTransaction.GetTokenID()

	if payerId == nil || isZeroAccountId(*payerId) || isZeroTokenId(tokenId) {
		return nil, nil, errors.ErrInvalidTransaction
	}

	payerAccountId, err := types.NewAccountIdFromSdkAccountId(*payerId)
	if err != nil {
		return nil, nil, errors.ErrInvalidAccount
	}

	tokenEntityId, err := domain.EntityIdOf(int64(tokenId.Shard), int64(tokenId.Realm), int64(tokenId.Token))
	if err != nil {
		return nil, nil, errors.ErrInvalidToken
	}

	domainToken := domain.Token{TokenId: tokenEntityId, Type: domain.TokenTypeUnknown}
	operation := types.Operation{
		AccountId: payerAccountId,
		Amount:    types.NewTokenAmount(domainToken, 0),
		Type:      t.GetOperationType(),
	}

	return types.OperationSlice{operation}, []types.AccountId{payerAccountId}, nil
}

func (t *tokenDeleteTransactionConstructor) Preprocess(_ context.Context, operations types.OperationSlice) (
	[]types.AccountId,
	*rTypes.Error,
) {
	payer, _, err := t.preprocess(operations)
	if err != nil {
		return nil, err
	}

	return []types.AccountId{*payer}, nil
}

func (t *tokenDeleteTransactionConstructor) preprocess(operations types.OperationSlice) (
	*types.AccountId,
	*hedera.TokenID,
	*rTypes.Error,
) {
	if rErr := validateOperations(operations, 1, t.GetOperationType(), false); rErr != nil {
		return nil, nil, rErr
	}

	operation := operations[0]
	amount := operation.Amount

	if amount.GetValue() != 0 {
		return nil, nil, errors.ErrInvalidOperationsAmount
	}

	tokenAmount, ok := amount.(*types.TokenAmount)
	if !ok {
		return nil, nil, errors.ErrInvalidCurrency
	}
	tokenId := hedera.TokenID{
		Shard: uint64(tokenAmount.TokenId.ShardNum),
		Realm: uint64(tokenAmount.TokenId.RealmNum),
		Token: uint64(tokenAmount.TokenId.EntityNum),
	}

	return &operation.AccountId, &tokenId, nil
}

func newTokenDeleteTransactionConstructor() transactionConstructorWithType {
	return &tokenDeleteTransactionConstructor{
		commonTransactionConstructor: newCommonTransactionConstructor(
			hedera.NewTokenDeleteTransaction(),
			types.OperationTypeTokenDelete,
		),
	}
}
