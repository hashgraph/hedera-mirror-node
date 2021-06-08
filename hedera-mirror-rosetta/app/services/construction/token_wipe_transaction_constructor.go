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
	"fmt"
	"reflect"
	"strconv"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/go-playground/validator/v10"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/repositories"
	hErrors "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/hashgraph/hedera-sdk-go/v2"
)

type tokenWipe struct {
	Account *hedera.AccountID `json:"account" validate:"required"`
	Amount  uint64
	Token   hedera.TokenID
}

type tokenWipeTransactionConstructor struct {
	tokenRepo       repositories.TokenRepository
	transactionType string
	validate        *validator.Validate
}

func (t *tokenWipeTransactionConstructor) Construct(
	nodeAccountId hedera.AccountID,
	operations []*rTypes.Operation,
) (ITransaction, []hedera.AccountID, *rTypes.Error) {
	payer, tokenWipe, rErr := t.preprocess(operations)
	if rErr != nil {
		return nil, nil, rErr
	}

	tx, err := hedera.NewTokenWipeTransaction().
		SetAccountID(*tokenWipe.Account).
		SetAmount(tokenWipe.Amount).
		SetTokenID(tokenWipe.Token).
		SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
		SetTransactionID(hedera.TransactionIDGenerate(*payer)).
		Freeze()
	if err != nil {
		return nil, nil, hErrors.ErrTransactionFreezeFailed
	}

	return tx, []hedera.AccountID{*payer}, nil
}

func (t *tokenWipeTransactionConstructor) Parse(transaction ITransaction) (
	[]*rTypes.Operation,
	[]hedera.AccountID,
	*rTypes.Error,
) {
	tx, ok := transaction.(*hedera.TokenWipeTransaction)
	if !ok {
		return nil, nil, hErrors.ErrTransactionInvalidType
	}

	account := tx.GetAccountID()
	payer := tx.GetTransactionID().AccountID
	token := tx.GetTokenID()

	if isZeroAccountId(account) || payer == nil || isZeroAccountId(*payer) || isZeroTokenId(token) {
		return nil, nil, hErrors.ErrInvalidTransaction
	}

	dbToken, err := t.tokenRepo.Find(token.String())
	if err != nil {
		return nil, nil, err
	}

	operation := &rTypes.Operation{
		OperationIdentifier: &rTypes.OperationIdentifier{
			Index: 0,
		},
		Account: &rTypes.AccountIdentifier{Address: payer.String()},
		Amount: &rTypes.Amount{
			Value:    fmt.Sprintf("%d", tx.GetAmount()),
			Currency: dbToken.ToRosettaCurrency(),
		},
		Type: t.GetOperationType(),
		Metadata: map[string]interface{}{
			"account": account.String(),
		},
	}

	return []*rTypes.Operation{operation}, []hedera.AccountID{*payer}, nil
}

func (t *tokenWipeTransactionConstructor) Preprocess(operations []*rTypes.Operation) (
	[]hedera.AccountID,
	*rTypes.Error,
) {
	payer, _, err := t.preprocess(operations)
	if err != nil {
		return nil, err
	}

	return []hedera.AccountID{*payer}, nil
}

func (t *tokenWipeTransactionConstructor) preprocess(operations []*rTypes.Operation) (
	*hedera.AccountID,
	*tokenWipe,
	*rTypes.Error,
) {
	if rErr := validateOperations(operations, 1, t.GetOperationType(), false); rErr != nil {
		return nil, nil, rErr
	}

	operation := operations[0]
	tokenWipe := &tokenWipe{}
	if rErr := parseOperationMetadata(t.validate, tokenWipe, operation.Metadata); rErr != nil {
		return nil, nil, rErr
	}

	if isZeroAccountId(*tokenWipe.Account) {
		return nil, nil, hErrors.ErrInvalidAccount
	}

	value, err := strconv.ParseInt(operation.Amount.Value, 10, 64)
	if err != nil || value <= 0 {
		return nil, nil, hErrors.ErrInvalidAmount
	}
	tokenWipe.Amount = uint64(value)

	token, rErr := validateToken(t.tokenRepo, operation.Amount.Currency)
	if rErr != nil {
		return nil, nil, rErr
	}
	tokenWipe.Token = *token

	payer, err := hedera.AccountIDFromString(operations[0].Account.Address)
	if err != nil || isZeroAccountId(payer) {
		return nil, nil, hErrors.ErrInvalidAccount
	}

	return &payer, tokenWipe, nil
}

func (t *tokenWipeTransactionConstructor) GetOperationType() string {
	return config.OperationTypeTokenWipe
}

func (t *tokenWipeTransactionConstructor) GetSdkTransactionType() string {
	return t.transactionType
}

func newTokenWipeTransactionConstructor(tokenRepo repositories.TokenRepository) transactionConstructorWithType {
	return &tokenWipeTransactionConstructor{
		tokenRepo:       tokenRepo,
		transactionType: reflect.TypeOf(hedera.TokenWipeTransaction{}).Name(),
		validate:        validator.New(),
	}
}
