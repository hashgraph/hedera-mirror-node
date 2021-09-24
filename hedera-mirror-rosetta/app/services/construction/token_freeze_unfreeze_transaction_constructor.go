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
	"reflect"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/go-playground/validator/v10"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	hErrors "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/hashgraph/hedera-sdk-go/v2"
)

type tokenFreezeUnfreezeTransactionConstructor struct {
	operationType   string
	tokenRepo       interfaces.TokenRepository
	transactionType string
	validate        *validator.Validate
}

func (t *tokenFreezeUnfreezeTransactionConstructor) Construct(
	ctx context.Context,
	nodeAccountId hedera.AccountID,
	operations []*rTypes.Operation,
	validStartNanos int64,
) (interfaces.Transaction, []hedera.AccountID, *rTypes.Error) {
	payer, account, token, rErr := t.preprocess(ctx, operations)
	if rErr != nil {
		return nil, nil, rErr
	}

	var tx interfaces.Transaction
	var err error
	transactionId := getTransactionId(*payer, validStartNanos)
	if t.operationType == config.OperationTypeTokenFreeze {
		tx, err = hedera.NewTokenFreezeTransaction().
			SetAccountID(*account).
			SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
			SetTokenID(*token).
			SetTransactionID(transactionId).
			Freeze()
	} else {
		tx, err = hedera.NewTokenUnfreezeTransaction().
			SetAccountID(*account).
			SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
			SetTokenID(*token).
			SetTransactionID(transactionId).
			Freeze()
	}

	if err != nil {
		return nil, nil, hErrors.ErrInternalServerError
	}

	return tx, []hedera.AccountID{*payer}, nil
}

func (t *tokenFreezeUnfreezeTransactionConstructor) Parse(ctx context.Context, transaction interfaces.Transaction) (
	[]*rTypes.Operation,
	[]hedera.AccountID,
	*rTypes.Error,
) {
	var account hedera.AccountID
	var payer hedera.AccountID
	var token hedera.TokenID

	switch tx := transaction.(type) {
	case *hedera.TokenFreezeTransaction:
		if t.operationType != config.OperationTypeTokenFreeze {
			return nil, nil, hErrors.ErrTransactionInvalidType
		}

		account = tx.GetAccountID()
		payer = *tx.GetTransactionID().AccountID
		token = tx.GetTokenID()
	case *hedera.TokenUnfreezeTransaction:
		if t.operationType != config.OperationTypeTokenUnfreeze {
			return nil, nil, hErrors.ErrTransactionInvalidType
		}

		account = tx.GetAccountID()
		payer = *tx.GetTransactionID().AccountID
		token = tx.GetTokenID()
	default:
		return nil, nil, hErrors.ErrTransactionInvalidType
	}

	dbToken, err := t.tokenRepo.Find(ctx, token.String())
	if err != nil {
		return nil, nil, hErrors.ErrTokenNotFound
	}

	operation := &rTypes.Operation{
		OperationIdentifier: &rTypes.OperationIdentifier{Index: 0},
		Type:                t.operationType,
		Account:             &rTypes.AccountIdentifier{Address: account.String()},
		Amount: &rTypes.Amount{
			Value:    "0",
			Currency: types.Token{Token: dbToken}.ToRosettaCurrency(),
		},
		Metadata: map[string]interface{}{"payer": payer.String()},
	}

	return []*rTypes.Operation{operation}, []hedera.AccountID{payer}, nil
}

func (t *tokenFreezeUnfreezeTransactionConstructor) Preprocess(ctx context.Context, operations []*rTypes.Operation) (
	[]hedera.AccountID,
	*rTypes.Error,
) {
	payer, _, _, err := t.preprocess(ctx, operations)
	if err != nil {
		return nil, err
	}

	return []hedera.AccountID{*payer}, nil
}

func (t *tokenFreezeUnfreezeTransactionConstructor) preprocess(ctx context.Context, operations []*rTypes.Operation) (
	*hedera.AccountID,
	*hedera.AccountID,
	*hedera.TokenID,
	*rTypes.Error,
) {
	return preprocessTokenFreezeKyc(ctx, operations, t.GetOperationType(), t.tokenRepo, t.validate)
}

func (t *tokenFreezeUnfreezeTransactionConstructor) GetOperationType() string {
	return t.operationType
}

func (t *tokenFreezeUnfreezeTransactionConstructor) GetSdkTransactionType() string {
	return t.transactionType
}

func newTokenFreezeTransactionConstructor(tokenRepo interfaces.TokenRepository) transactionConstructorWithType {
	transactionType := reflect.TypeOf(hedera.TokenFreezeTransaction{}).Name()
	return &tokenFreezeUnfreezeTransactionConstructor{
		operationType:   config.OperationTypeTokenFreeze,
		tokenRepo:       tokenRepo,
		transactionType: transactionType,
		validate:        validator.New(),
	}
}

func newTokenUnfreezeTransactionConstructor(tokenRepo interfaces.TokenRepository) transactionConstructorWithType {
	transactionType := reflect.TypeOf(hedera.TokenUnfreezeTransaction{}).Name()
	return &tokenFreezeUnfreezeTransactionConstructor{
		operationType:   config.OperationTypeTokenUnfreeze,
		tokenRepo:       tokenRepo,
		transactionType: transactionType,
		validate:        validator.New(),
	}
}
