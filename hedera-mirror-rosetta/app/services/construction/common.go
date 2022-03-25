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
	"encoding/json"
	"reflect"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/go-playground/validator/v10"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-sdk-go/v2"
	log "github.com/sirupsen/logrus"
)

type commonTransactionConstructor struct {
	defaultMaxFee   types.HbarAmount
	operationType   string
	transactionType string
	validate        *validator.Validate
}

func (c *commonTransactionConstructor) GetDefaultMaxTransactionFee() types.HbarAmount {
	return c.defaultMaxFee
}

func (c *commonTransactionConstructor) GetOperationType() string {
	return c.operationType
}

func (c *commonTransactionConstructor) GetSdkTransactionType() string {
	return c.transactionType
}

func newCommonTransactionConstructor(
	transaction interfaces.Transaction,
	operationType string,
) commonTransactionConstructor {
	defaultMaxFee := types.HbarAmount{Value: transaction.GetMaxTransactionFee().AsTinybar()}
	transactionType := reflect.TypeOf(transaction).Elem().Name()
	return commonTransactionConstructor{
		defaultMaxFee:   defaultMaxFee,
		operationType:   operationType,
		transactionType: transactionType,
		validate:        validator.New(),
	}
}

type payerMetadata struct {
	Payer *hedera.AccountID `json:"payer" validate:"required"`
}

func compareCurrency(currencyA *rTypes.Currency, currencyB *rTypes.Currency) bool {
	if currencyA == currencyB {
		return true
	}

	if currencyA == nil || currencyB == nil {
		return false
	}

	if currencyA.Symbol != currencyB.Symbol ||
		currencyA.Decimals != currencyB.Decimals ||
		!reflect.DeepEqual(currencyA.Metadata, currencyB.Metadata) {
		return false
	}

	return true
}

func isNonEmptyPublicKey(key hedera.Key) bool {
	pk, ok := key.(hedera.PublicKey)
	if !ok {
		return false
	}

	return len(pk.Bytes()) != 0
}

func isZeroAccountId(accountId hedera.AccountID) bool {
	return accountId.Shard == 0 && accountId.Realm == 0 && accountId.Account == 0
}

func isZeroTokenId(tokenId hedera.TokenID) bool {
	return tokenId.Shard == 0 && tokenId.Realm == 0 && tokenId.Token == 0
}

func parseOperationMetadata(
	validate *validator.Validate,
	out interface{},
	metadatas ...map[string]interface{},
) *rTypes.Error {
	metadata := make(map[string]interface{})

	for _, m := range metadatas {
		for k, v := range m {
			metadata[k] = v
		}
	}

	data, err := json.Marshal(metadata)
	if err != nil {
		return errors.ErrInvalidOperationMetadata
	}

	if err := json.Unmarshal(data, out); err != nil {
		log.Errorf("Failed to unmarshal operation metadata: %s", err)
		return errors.ErrInvalidOperationMetadata
	}

	if validate != nil {
		if err := validate.Struct(out); err != nil {
			log.Errorf("Failed to validate metadata: %s", err)
			return errors.ErrInvalidOperationMetadata
		}
	}

	return nil
}

func parsePayerMetadata(validate *validator.Validate, metadata map[string]interface{}) (
	*hedera.AccountID,
	*rTypes.Error,
) {
	payerMetadata := payerMetadata{}
	if err := parseOperationMetadata(validate, &payerMetadata, metadata); err != nil {
		return nil, err
	}
	if isZeroAccountId(*payerMetadata.Payer) {
		return nil, errors.ErrInvalidAccount
	}

	return payerMetadata.Payer, nil
}

func parseTokenFreezeKyc(operationType string, transaction interfaces.Transaction) (
	types.OperationSlice,
	[]types.AccountId,
	*rTypes.Error,
) {
	var account hedera.AccountID
	var payer *hedera.AccountID
	var tokenId hedera.TokenID

	switch tx := transaction.(type) {
	case *hedera.TokenFreezeTransaction:
		if operationType != types.OperationTypeTokenFreeze {
			return nil, nil, errors.ErrTransactionInvalidType
		}

		account = tx.GetAccountID()
		payer = tx.GetTransactionID().AccountID
		tokenId = tx.GetTokenID()
	case *hedera.TokenUnfreezeTransaction:
		if operationType != types.OperationTypeTokenUnfreeze {
			return nil, nil, errors.ErrTransactionInvalidType
		}

		account = tx.GetAccountID()
		payer = tx.GetTransactionID().AccountID
		tokenId = tx.GetTokenID()
	case *hedera.TokenGrantKycTransaction:
		if operationType != types.OperationTypeTokenGrantKyc {
			return nil, nil, errors.ErrTransactionInvalidType
		}

		account = tx.GetAccountID()
		payer = tx.GetTransactionID().AccountID
		tokenId = tx.GetTokenID()
	case *hedera.TokenRevokeKycTransaction:
		if operationType != types.OperationTypeTokenRevokeKyc {
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

	accountId, err := types.NewAccountIdFromSdkAccountId(account)
	if err != nil {
		return nil, nil, errors.ErrInvalidAccount
	}

	payerAccountId, err := types.NewAccountIdFromSdkAccountId(*payer)
	if err != nil {
		return nil, nil, errors.ErrInvalidAccount
	}

	tokenEntityId, err := domain.EntityIdOf(int64(tokenId.Shard), int64(tokenId.Realm), int64(tokenId.Token))
	if err != nil {
		return nil, nil, errors.ErrInvalidToken
	}

	domainToken := domain.Token{TokenId: tokenEntityId, Type: domain.TokenTypeUnknown}
	operation := types.Operation{
		AccountId: accountId,
		Amount:    types.NewTokenAmount(domainToken, 0),
		Metadata:  map[string]interface{}{"payer": payer.String()},
		Type:      operationType,
	}

	return types.OperationSlice{operation}, []types.AccountId{payerAccountId}, nil
}

func preprocessTokenFreezeKyc(
	operations types.OperationSlice,
	operationType string,
	validate *validator.Validate,
) (*types.AccountId, *types.AccountId, *hedera.TokenID, *rTypes.Error) {
	if rErr := validateOperations(operations, 1, operationType, false); rErr != nil {
		return nil, nil, nil, rErr
	}

	operation := operations[0]
	payer, rErr := parsePayerMetadata(validate, operation.Metadata)
	if rErr != nil {
		return nil, nil, nil, rErr
	}
	payerAccountId, err := types.NewAccountIdFromSdkAccountId(*payer)
	if err != nil {
		return nil, nil, nil, errors.ErrInvalidAccount
	}

	amount := operation.Amount
	if amount.GetValue() != 0 {
		return nil, nil, nil, errors.ErrInvalidOperationsAmount
	}

	tokenAmount, ok := amount.(*types.TokenAmount)
	if !ok {
		return nil, nil, nil, errors.ErrInvalidCurrency
	}
	tokenId := tokenAmount.GetSdkTokenId()

	return &payerAccountId, &operation.AccountId, &tokenId, nil
}

func validateOperations(operations types.OperationSlice, size int, opType string, expectNilAmount bool) *rTypes.Error {
	if len(operations) == 0 {
		return errors.ErrEmptyOperations
	}

	if size != 0 && len(operations) != size {
		return errors.ErrInvalidOperations
	}

	for _, operation := range operations {
		if expectNilAmount && operation.Amount != nil {
			return errors.ErrInvalidOperations
		}

		if !expectNilAmount && operation.Amount == nil {
			return errors.ErrInvalidOperations
		}

		if operation.Type != opType {
			return errors.ErrInvalidOperationType
		}
	}

	return nil
}
