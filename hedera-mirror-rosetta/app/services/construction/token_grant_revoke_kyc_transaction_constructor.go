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
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-sdk-go/v2"
)

type tokenGrantRevokeKycTransactionConstructor struct {
	operationType   string
	tokenRepo       interfaces.TokenRepository
	transactionType string
	validate        *validator.Validate
}

func (t *tokenGrantRevokeKycTransactionConstructor) Construct(
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
	if t.operationType == types.OperationTypeTokenGrantKyc {
		tx, err = hedera.NewTokenGrantKycTransaction().
			SetAccountID(*account).
			SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
			SetTokenID(*token).
			SetTransactionID(transactionId).
			Freeze()
	} else {
		tx, err = hedera.NewTokenRevokeKycTransaction().
			SetAccountID(*account).
			SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
			SetTokenID(*token).
			SetTransactionID(transactionId).
			Freeze()
	}

	if err != nil {
		return nil, nil, errors.ErrTransactionFreezeFailed
	}

	return tx, []hedera.AccountID{*payer}, nil
}

func (t *tokenGrantRevokeKycTransactionConstructor) Parse(ctx context.Context, transaction interfaces.Transaction) (
	[]*rTypes.Operation,
	[]hedera.AccountID,
	*rTypes.Error,
) {
	var account hedera.AccountID
	var payer *hedera.AccountID
	var tokenId hedera.TokenID

	switch tx := transaction.(type) {
	case *hedera.TokenGrantKycTransaction:
		if t.operationType != types.OperationTypeTokenGrantKyc {
			return nil, nil, errors.ErrTransactionInvalidType
		}

		account = tx.GetAccountID()
		payer = tx.GetTransactionID().AccountID
		tokenId = tx.GetTokenID()
	case *hedera.TokenRevokeKycTransaction:
		if t.operationType != types.OperationTypeTokenRevokeKyc {
			return nil, nil, errors.ErrTransactionInvalidType
		}

		account = tx.GetAccountID()
		payer = tx.GetTransactionID().AccountID
		tokenId = tx.GetTokenID()
	default:
		return nil, nil, errors.ErrTransactionInvalidType
	}

	if isZeroAccountId(account) || isZeroTokenId(tokenId) || payer == nil || isZeroAccountId(*payer) {
		return nil, nil, errors.ErrInvalidTransaction
	}

	dbToken, err := t.tokenRepo.Find(ctx, tokenId.String())
	if err != nil {
		return nil, nil, err
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

	return []*rTypes.Operation{operation}, []hedera.AccountID{*payer}, nil
}

func (t *tokenGrantRevokeKycTransactionConstructor) Preprocess(ctx context.Context, operations []*rTypes.Operation) (
	[]hedera.AccountID,
	*rTypes.Error,
) {
	payer, _, _, err := t.preprocess(ctx, operations)
	if err != nil {
		return nil, err
	}

	return []hedera.AccountID{*payer}, nil
}

func (t *tokenGrantRevokeKycTransactionConstructor) preprocess(ctx context.Context, operations []*rTypes.Operation) (
	*hedera.AccountID,
	*hedera.AccountID,
	*hedera.TokenID,
	*rTypes.Error,
) {
	return preprocessTokenFreezeKyc(ctx, operations, t.GetOperationType(), t.tokenRepo, t.validate)
}

func (t *tokenGrantRevokeKycTransactionConstructor) GetOperationType() string {
	return t.operationType
}

func (t *tokenGrantRevokeKycTransactionConstructor) GetSdkTransactionType() string {
	return t.transactionType
}

func newTokenGrantKycTransactionConstructor(tokenRepo interfaces.TokenRepository) transactionConstructorWithType {
	transactionType := reflect.TypeOf(hedera.TokenGrantKycTransaction{}).Name()
	return &tokenGrantRevokeKycTransactionConstructor{
		operationType:   types.OperationTypeTokenGrantKyc,
		tokenRepo:       tokenRepo,
		transactionType: transactionType,
		validate:        validator.New(),
	}
}

func newTokenRevokeKycTransactionConstructor(tokenRepo interfaces.TokenRepository) transactionConstructorWithType {
	transactionType := reflect.TypeOf(hedera.TokenRevokeKycTransaction{}).Name()
	return &tokenGrantRevokeKycTransactionConstructor{
		operationType:   types.OperationTypeTokenRevokeKyc,
		tokenRepo:       tokenRepo,
		transactionType: transactionType,
		validate:        validator.New(),
	}
}
