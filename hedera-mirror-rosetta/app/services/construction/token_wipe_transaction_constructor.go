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

type tokenWipeTransactionConstructor struct {
	commonTransactionConstructor
}

func (t *tokenWipeTransactionConstructor) Construct(
	_ context.Context,
	operations types.OperationSlice,
) (interfaces.Transaction, []types.AccountId, *rTypes.Error) {
	payer, account, tokenAmount, rErr := t.preprocess(operations)
	if rErr != nil {
		return nil, nil, rErr
	}

	tokenId, _ := hedera.TokenIDFromString(tokenAmount.TokenId.String())
	tx := hedera.NewTokenWipeTransaction().
		SetAccountID(account.ToSdkAccountId()).
		SetTokenID(tokenId)
	if len(tokenAmount.SerialNumbers) != 0 {
		tx.SetSerialNumbers(tokenAmount.SerialNumbers)
	} else {
		tx.SetAmount(uint64(-tokenAmount.Value))
	}

	return tx, []types.AccountId{*payer}, nil
}

func (t *tokenWipeTransactionConstructor) Parse(_ context.Context, transaction interfaces.Transaction) (
	types.OperationSlice,
	[]types.AccountId,
	*rTypes.Error,
) {
	tx, ok := transaction.(*hedera.TokenWipeTransaction)
	if !ok {
		return nil, nil, errors.ErrTransactionInvalidType
	}

	account := tx.GetAccountID()
	payer := tx.GetTransactionID().AccountID
	serials := tx.GetSerialNumbers()
	token := tx.GetTokenID()

	if isZeroAccountId(account) || payer == nil || isZeroAccountId(*payer) || isZeroTokenId(token) {
		return nil, nil, errors.ErrInvalidTransaction
	}

	accountId, err := types.NewAccountIdFromSdkAccountId(account)
	if err != nil {
		return nil, nil, errors.ErrInvalidAccount
	}

	payerAccountId, err := types.NewAccountIdFromSdkAccountId(*payer)
	if err != nil {
		return nil, nil, errors.ErrInvalidAccount
	}

	tokenEntityId, err := domain.EntityIdOf(int64(token.Shard), int64(token.Realm), int64(token.Token))
	if err != nil {
		return nil, nil, errors.ErrInvalidToken
	}

	amount := int64(tx.GetAmount())
	domainToken := domain.Token{TokenId: tokenEntityId, Type: domain.TokenTypeFungibleCommon}
	if len(serials) != 0 {
		amount = int64(len(serials))
		domainToken.Type = domain.TokenTypeNonFungibleUnique
	}
	operation := types.Operation{
		AccountId: accountId,
		Amount:    types.NewTokenAmount(domainToken, -amount).SetSerialNumbers(serials),
		Metadata:  map[string]interface{}{"payer": payer.String()},
		Type:      t.GetOperationType(),
	}

	return types.OperationSlice{operation}, []types.AccountId{payerAccountId}, nil
}

func (t *tokenWipeTransactionConstructor) Preprocess(_ context.Context, operations types.OperationSlice) (
	[]types.AccountId,
	*rTypes.Error,
) {
	payer, _, _, err := t.preprocess(operations)
	if err != nil {
		return nil, err
	}

	return []types.AccountId{*payer}, nil
}

func (t *tokenWipeTransactionConstructor) preprocess(operations types.OperationSlice) (
	*types.AccountId,
	*types.AccountId,
	*types.TokenAmount,
	*rTypes.Error,
) {
	if err := validateOperations(operations, 1, t.GetOperationType(), false); err != nil {
		return nil, nil, nil, err
	}

	operation := operations[0]
	// operation.Account is the account with balance changes, the payer for token wipe is normally the account who owns
	// the token wipe key and different from operation.Account; so the payer account is in set in operation.Metadata
	payer, err := parsePayerMetadata(t.validate, operation.Metadata)
	if err != nil {
		return nil, nil, nil, err
	}
	payerAccountId, err1 := types.NewAccountIdFromSdkAccountId(*payer)
	if err1 != nil {
		return nil, nil, nil, errors.ErrInvalidAccount
	}

	tokenAmount, ok := operation.Amount.(*types.TokenAmount)
	if !ok {
		return nil, nil, nil, errors.ErrInvalidCurrency
	}

	if tokenAmount.Value >= 0 ||
		(tokenAmount.Type == domain.TokenTypeNonFungibleUnique && len(tokenAmount.SerialNumbers) == 0) {
		return nil, nil, nil, errors.ErrInvalidOperationsTotalAmount
	}

	return &payerAccountId, &operation.AccountId, tokenAmount, nil
}

func newTokenWipeTransactionConstructor() transactionConstructorWithType {
	return &tokenWipeTransactionConstructor{
		commonTransactionConstructor: newCommonTransactionConstructor(
			hedera.NewTokenWipeTransaction(),
			types.OperationTypeTokenWipe,
		),
	}
}
