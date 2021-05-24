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
	"github.com/go-playground/validator/v10"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/repositories"
	hErrors "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/hashgraph/hedera-sdk-go/v2"
)

type tokenFreezeUnfreeze struct {
	Account *hedera.AccountID `json:"account" validate:"required"`
	Token   *hedera.TokenID
}

type tokenFreezeUnfreezeTransactionConstructor struct {
	operationType   string
	tokenRepo       repositories.TokenRepository
	transactionType string
	validate        *validator.Validate
}

func (t *tokenFreezeUnfreezeTransactionConstructor) Construct(
	nodeAccountId hedera.AccountID,
	operations []*rTypes.Operation,
) (ITransaction, []hedera.AccountID, *rTypes.Error) {
	payer, tokenFreezeUnfreeze, err := t.preprocess(operations)
	if err != nil {
		return nil, nil, err
	}

	var tx ITransaction

	if t.operationType == config.OperationTypeTokenFreeze {
		tx = hedera.NewTokenFreezeTransaction().
			SetAccountID(*tokenFreezeUnfreeze.Account).
			SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
			SetTokenID(*tokenFreezeUnfreeze.Token).
			SetTransactionID(hedera.TransactionIDGenerate(*payer))
	} else {
		tx = hedera.NewTokenUnfreezeTransaction().
			SetAccountID(*tokenFreezeUnfreeze.Account).
			SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
			SetTokenID(*tokenFreezeUnfreeze.Token).
			SetTransactionID(hedera.TransactionIDGenerate(*payer))
	}

	return tx, []hedera.AccountID{*payer}, nil
}

func (t *tokenFreezeUnfreezeTransactionConstructor) Parse(
	transaction ITransaction,
	signed bool,
) ([]*rTypes.Operation, []hedera.AccountID, *rTypes.Error) {
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

	dbToken, err := t.tokenRepo.Find(token.String())
	if err != nil {
		return nil, nil, hErrors.ErrTokenNotFound
	}

	operation := &rTypes.Operation{
		OperationIdentifier: &rTypes.OperationIdentifier{Index: 0},
		Type:                t.operationType,
		Account:             &rTypes.AccountIdentifier{Address: payer.String()},
		Amount: &rTypes.Amount{
			Value:    "0",
			Currency: dbToken.ToRosettaCurrency(),
		},
		Metadata: map[string]interface{}{
			"account": account.String(),
		},
	}

	return []*rTypes.Operation{operation}, []hedera.AccountID{payer}, nil
}

func (t *tokenFreezeUnfreezeTransactionConstructor) Preprocess(operations []*rTypes.Operation) (
	[]hedera.AccountID,
	*rTypes.Error,
) {
	payer, _, err := t.preprocess(operations)
	if err != nil {
		return nil, err
	}

	return []hedera.AccountID{*payer}, nil
}

func (t *tokenFreezeUnfreezeTransactionConstructor) preprocess(operations []*rTypes.Operation) (
	*hedera.AccountID,
	*tokenFreezeUnfreeze,
	*rTypes.Error,
) {
	if rErr := validateOperations(operations, 1, t.operationType, false); rErr != nil {
		return nil, nil, rErr
	}

	operation := operations[0]
	tokenFreeze := &tokenFreezeUnfreeze{}
	rErr := parseOperationMetadata(t.validate, tokenFreeze, operation.Metadata)
	if rErr != nil {
		return nil, nil, rErr
	}

	if isZeroAccountId(*tokenFreeze.Account) {
		return nil, nil, hErrors.ErrInvalidAccount
	}

	tokenFreeze.Token, rErr = validateToken(t.tokenRepo, operation.Amount.Currency)
	if rErr != nil {
		return nil, nil, rErr
	}

	payer, err := hedera.AccountIDFromString(operations[0].Account.Address)
	if err != nil || isZeroAccountId(payer) {
		return nil, nil, hErrors.ErrInvalidAccount
	}

	return &payer, tokenFreeze, nil
}

func (t *tokenFreezeUnfreezeTransactionConstructor) GetOperationType() string {
	return t.operationType
}

func (t *tokenFreezeUnfreezeTransactionConstructor) GetSdkTransactionType() string {
	return t.transactionType
}

func newTokenFreezeTransactionConstructor(tokenRepo repositories.TokenRepository) transactionConstructorWithType {
	transactionType := reflect.TypeOf(hedera.TokenFreezeTransaction{}).Name()
	return &tokenFreezeUnfreezeTransactionConstructor{
		operationType:   config.OperationTypeTokenFreeze,
		tokenRepo:       tokenRepo,
		transactionType: transactionType,
		validate:        validator.New(),
	}
}

func newTokenUnfreezeTransactionConstructor(tokenRepo repositories.TokenRepository) transactionConstructorWithType {
	transactionType := reflect.TypeOf(hedera.TokenUnfreezeTransaction{}).Name()
	return &tokenFreezeUnfreezeTransactionConstructor{
		operationType:   config.OperationTypeTokenUnfreeze,
		tokenRepo:       tokenRepo,
		transactionType: transactionType,
		validate:        validator.New(),
	}
}
