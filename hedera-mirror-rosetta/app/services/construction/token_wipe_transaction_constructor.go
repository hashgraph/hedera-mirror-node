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
	"context"
	"fmt"
	"reflect"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/go-playground/validator/v10"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-sdk-go/v2"
)

type tokenWipeTransactionConstructor struct {
	tokenRepo       interfaces.TokenRepository
	transactionType string
	validate        *validator.Validate
}

func (t *tokenWipeTransactionConstructor) Construct(
	ctx context.Context,
	nodeAccountId hedera.AccountID,
	operations []*rTypes.Operation,
	validStartNanos int64,
) (interfaces.Transaction, []hedera.AccountID, *rTypes.Error) {
	payer, account, tokenAmount, rErr := t.preprocess(ctx, operations)
	if rErr != nil {
		return nil, nil, rErr
	}

	tokenId, _ := hedera.TokenIDFromString(tokenAmount.TokenId.String())
	tx := hedera.NewTokenWipeTransaction().
		SetAccountID(*account).
		SetTokenID(tokenId).
		SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
		SetTransactionID(getTransactionId(*payer, validStartNanos))
	if len(tokenAmount.SerialNumbers) != 0 {
		tx.SetSerialNumbers(tokenAmount.SerialNumbers)
	} else {
		tx.SetAmount(uint64(-tokenAmount.Value))
	}

	if _, err := tx.Freeze(); err != nil {
		return nil, nil, errors.ErrTransactionFreezeFailed
	}

	return tx, []hedera.AccountID{*payer}, nil
}

func (t *tokenWipeTransactionConstructor) Parse(ctx context.Context, transaction interfaces.Transaction) (
	[]*rTypes.Operation,
	[]hedera.AccountID,
	*rTypes.Error,
) {
	tx, ok := transaction.(*hedera.TokenWipeTransaction)
	if !ok {
		return nil, nil, errors.ErrTransactionInvalidType
	}

	account := tx.GetAccountID()
	payer := tx.GetTransactionID().AccountID
	token := tx.GetTokenID()

	if isZeroAccountId(account) || payer == nil || isZeroAccountId(*payer) || isZeroTokenId(token) {
		return nil, nil, errors.ErrInvalidTransaction
	}

	dbToken, err := t.tokenRepo.Find(ctx, token.String())
	if err != nil {
		return nil, nil, err
	}

	operation := &rTypes.Operation{
		OperationIdentifier: &rTypes.OperationIdentifier{Index: 0},
		Account:             &rTypes.AccountIdentifier{Address: account.String()},
		Amount: &rTypes.Amount{
			Value:    fmt.Sprintf("%d", -int64(tx.GetAmount())),
			Currency: types.Token{Token: dbToken}.ToRosettaCurrency(),
		},
		Type:     t.GetOperationType(),
		Metadata: map[string]interface{}{"payer": payer.String()},
	}

	return []*rTypes.Operation{operation}, []hedera.AccountID{*payer}, nil
}

func (t *tokenWipeTransactionConstructor) Preprocess(ctx context.Context, operations []*rTypes.Operation) (
	[]hedera.AccountID,
	*rTypes.Error,
) {
	payer, _, _, err := t.preprocess(ctx, operations)
	if err != nil {
		return nil, err
	}

	return []hedera.AccountID{*payer}, nil
}

func (t *tokenWipeTransactionConstructor) preprocess(ctx context.Context, operations []*rTypes.Operation) (
	payer *hedera.AccountID,
	account *hedera.AccountID,
	tokenAmount *types.TokenAmount,
	err *rTypes.Error,
) {
	if err = validateOperations(operations, 1, t.GetOperationType(), false); err != nil {
		return
	}

	operation := operations[0]
	// operation.Account is the account with balance changes, the payer for token wipe is normally the account who owns
	// the token wipe key and different than operation.Account; so the payer account is in set in operation.Metadata
	payerAccountId, err := parsePayerMetadata(t.validate, operation.Metadata)
	if err != nil {
		return
	}

	var amount types.Amount
	if amount, err = types.NewAmount(operation.Amount); err != nil {
		return
	}

	var ok bool
	var tmpTokenAmount *types.TokenAmount
	err = errors.ErrInvalidOperationsAmount
	if tmpTokenAmount, ok = amount.(*types.TokenAmount); !ok {
		return
	}

	if tmpTokenAmount.Value >= 0 ||
		(tmpTokenAmount.Type == domain.TokenTypeNonFungibleUnique && len(tmpTokenAmount.SerialNumbers) == 0) {
		return
	}

	if _, err = validateToken(ctx, t.tokenRepo, operation.Amount.Currency); err != nil {
		return
	}

	accountId, _ := hedera.AccountIDFromString(operation.Account.Address)
	err = errors.ErrInvalidAccount
	if isZeroAccountId(*payerAccountId) || isZeroAccountId(accountId) {
		return
	}

	payer = payerAccountId
	account = &accountId
	tokenAmount = tmpTokenAmount
	err = nil
	return
}

func (t *tokenWipeTransactionConstructor) GetOperationType() string {
	return types.OperationTypeTokenWipe
}

func (t *tokenWipeTransactionConstructor) GetSdkTransactionType() string {
	return t.transactionType
}

func newTokenWipeTransactionConstructor(tokenRepo interfaces.TokenRepository) transactionConstructorWithType {
	return &tokenWipeTransactionConstructor{
		tokenRepo:       tokenRepo,
		transactionType: reflect.TypeOf(hedera.TokenWipeTransaction{}).Name(),
		validate:        validator.New(),
	}
}
