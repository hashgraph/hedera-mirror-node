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

type tokenKyc struct {
	Account *hedera.AccountID `json:"account" validate:"required"`
	Token   hedera.TokenID
}

type tokenGrantRevokeKycTransactionConstructor struct {
	operationType   string
	tokenRepo       repositories.TokenRepository
	transactionType string
	validate        *validator.Validate
}

func (t *tokenGrantRevokeKycTransactionConstructor) Construct(
	nodeAccountId hedera.AccountID,
	operations []*rTypes.Operation,
) (ITransaction, []hedera.AccountID, *rTypes.Error) {
	payer, tokenKyc, err := t.preprocess(operations)
	if err != nil {
		return nil, nil, err
	}

	var tx ITransaction
	if t.operationType == config.OperationTypeTokenGrantKyc {
		tx = hedera.NewTokenGrantKycTransaction().
			SetAccountID(*tokenKyc.Account).
			SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
			SetTokenID(tokenKyc.Token).
			SetTransactionID(hedera.TransactionIDGenerate(*payer))
	} else {
		tx = hedera.NewTokenRevokeKycTransaction().
			SetAccountID(*tokenKyc.Account).
			SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
			SetTokenID(tokenKyc.Token).
			SetTransactionID(hedera.TransactionIDGenerate(*payer))
	}

	return tx, []hedera.AccountID{*payer}, nil
}

func (t *tokenGrantRevokeKycTransactionConstructor) Parse(
	transaction ITransaction,
	signed bool,
) ([]*rTypes.Operation, []hedera.AccountID, *rTypes.Error) {
	var account hedera.AccountID
	var payer *hedera.AccountID
	var tokenId hedera.TokenID

	switch tx := transaction.(type) {
	case *hedera.TokenGrantKycTransaction:
		if t.operationType != config.OperationTypeTokenGrantKyc {
			return nil, nil, hErrors.ErrTransactionInvalidType
		}

		account = tx.GetAccountID()
		payer = tx.GetTransactionID().AccountID
		tokenId = tx.GetTokenID()
	case *hedera.TokenRevokeKycTransaction:
		if t.operationType != config.OperationTypeTokenRevokeKyc {
			return nil, nil, hErrors.ErrTransactionInvalidType
		}

		account = tx.GetAccountID()
		payer = tx.GetTransactionID().AccountID
		tokenId = tx.GetTokenID()
	default:
		return nil, nil, hErrors.ErrTransactionInvalidType
	}

	if isZeroAccountId(account) || isZeroTokenId(tokenId) || payer == nil || isZeroAccountId(*payer) {
		return nil, nil, hErrors.ErrTransactionInvalid
	}

	dbToken, err := t.tokenRepo.Find(tokenId.String())
	if err != nil {
		return nil, nil, err
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

	return []*rTypes.Operation{operation}, []hedera.AccountID{*payer}, nil
}

func (t *tokenGrantRevokeKycTransactionConstructor) Preprocess(operations []*rTypes.Operation) (
	[]hedera.AccountID,
	*rTypes.Error,
) {
	payer, _, err := t.preprocess(operations)
	if err != nil {
		return nil, err
	}

	return []hedera.AccountID{*payer}, nil
}

func (t *tokenGrantRevokeKycTransactionConstructor) preprocess(operations []*rTypes.Operation) (
	*hedera.AccountID,
	*tokenKyc,
	*rTypes.Error,
) {
	if rErr := validateOperations(operations, 1, t.operationType, false); rErr != nil {
		return nil, nil, rErr
	}

	operation := operations[0]
	if operation.Amount.Value != "0" {
		return nil, nil, hErrors.ErrInvalidOperationsAmount
	}

	tokenKyc := &tokenKyc{}
	rErr := parseOperationMetadata(t.validate, tokenKyc, operation.Metadata)
	if rErr != nil {
		return nil, nil, rErr
	} else if isZeroAccountId(*tokenKyc.Account) {
		return nil, nil, hErrors.ErrInvalidAccount
	}

	payer, err := hedera.AccountIDFromString(operations[0].Account.Address)
	if err != nil || isZeroAccountId(payer) {
		return nil, nil, hErrors.ErrInvalidAccount
	}

	token, rErr := validateToken(t.tokenRepo, operation.Amount.Currency)
	if rErr != nil {
		return nil, nil, rErr
	}
	tokenKyc.Token = *token

	return &payer, tokenKyc, nil
}

func (t *tokenGrantRevokeKycTransactionConstructor) GetOperationType() string {
	return t.operationType
}

func (t *tokenGrantRevokeKycTransactionConstructor) GetSdkTransactionType() string {
	return t.transactionType
}

func newTokenGrantKycTransactionConstructor(tokenRepo repositories.TokenRepository) transactionConstructorWithType {
	transactionType := reflect.TypeOf(hedera.TokenGrantKycTransaction{}).Name()
	return &tokenGrantRevokeKycTransactionConstructor{
		operationType:   config.OperationTypeTokenGrantKyc,
		tokenRepo:       tokenRepo,
		transactionType: transactionType,
		validate:        validator.New(),
	}
}

func newTokenRevokeKycTransactionConstructor(tokenRepo repositories.TokenRepository) transactionConstructorWithType {
	transactionType := reflect.TypeOf(hedera.TokenRevokeKycTransaction{}).Name()
	return &tokenGrantRevokeKycTransactionConstructor{
		operationType:   config.OperationTypeTokenRevokeKyc,
		tokenRepo:       tokenRepo,
		transactionType: transactionType,
		validate:        validator.New(),
	}
}
