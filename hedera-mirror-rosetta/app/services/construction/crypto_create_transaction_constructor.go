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
	"reflect"
	"time"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/go-playground/validator/v10"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-sdk-go/v2"
	log "github.com/sirupsen/logrus"
)

type cryptoCreate struct {
	AutoRenewPeriod               int64             `json:"auto_renew_period"`
	InitialBalance                int64             `json:"-"`
	Key                           *publicKey        `json:"key" validate:"required"`
	MaxAutomaticTokenAssociations uint32            `json:"max_automatic_token_associations"`
	Memo                          string            `json:"memo"`
	ProxyAccountId                *hedera.AccountID `json:"proxy_account_id"`
	// Add support for ReceiverSigRequired if needed and when the format to present an unknown account as a rosetta
	// AccountIdentifier id decided
}

type cryptoCreateTransactionConstructor struct {
	transactionType string
	validate        *validator.Validate
}

func (c *cryptoCreateTransactionConstructor) Construct(
	_ context.Context,
	nodeAccountId hedera.AccountID,
	operations []*rTypes.Operation,
	validStartNanos int64,
) (interfaces.Transaction, []hedera.AccountID, *rTypes.Error) {
	cryptoCreate, signer, rErr := c.preprocess(operations)
	if rErr != nil {
		return nil, nil, rErr
	}

	transaction := hedera.NewAccountCreateTransaction().
		SetInitialBalance(hedera.HbarFromTinybar(cryptoCreate.InitialBalance)).
		SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
		SetKey(cryptoCreate.Key.PublicKey).
		SetTransactionID(getTransactionId(*signer, validStartNanos))

	if cryptoCreate.AutoRenewPeriod > 0 {
		transaction.SetAutoRenewPeriod(time.Second * time.Duration(cryptoCreate.AutoRenewPeriod))
	}

	if cryptoCreate.MaxAutomaticTokenAssociations != 0 {
		transaction.SetMaxAutomaticTokenAssociations(cryptoCreate.MaxAutomaticTokenAssociations)
	}

	if cryptoCreate.Memo != "" {
		transaction.SetAccountMemo(cryptoCreate.Memo)
	}

	if cryptoCreate.ProxyAccountId != nil {
		transaction.SetProxyAccountID(*cryptoCreate.ProxyAccountId)
	}

	if _, err := transaction.Freeze(); err != nil {
		log.Errorf("Failed to freeze transaction: %s", err)
		return nil, nil, errors.ErrTransactionFreezeFailed
	}

	return transaction, []hedera.AccountID{*signer}, nil
}

func (c *cryptoCreateTransactionConstructor) GetOperationType() string {
	return types.OperationTypeCryptoCreateAccount
}

func (c *cryptoCreateTransactionConstructor) GetSdkTransactionType() string {
	return c.transactionType
}

func (c *cryptoCreateTransactionConstructor) Parse(_ context.Context, transaction interfaces.Transaction) (
	[]*rTypes.Operation,
	[]hedera.AccountID,
	*rTypes.Error,
) {
	cryptoCreateTransaction, ok := transaction.(*hedera.AccountCreateTransaction)
	if !ok {
		return nil, nil, errors.ErrTransactionInvalidType
	}

	if cryptoCreateTransaction.GetTransactionID().AccountID == nil {
		log.Error("Transaction ID is not set")
		return nil, nil, errors.ErrInvalidTransaction
	}

	amount := types.HbarAmount{Value: cryptoCreateTransaction.GetInitialBalance().AsTinybar()}
	payer := *cryptoCreateTransaction.GetTransactionID().AccountID
	operation := &rTypes.Operation{
		OperationIdentifier: &rTypes.OperationIdentifier{Index: 0},
		Account:             &rTypes.AccountIdentifier{Address: payer.String()},
		Amount:              amount.ToRosetta(),
		Type:                c.GetOperationType(),
	}

	metadata := make(map[string]interface{})
	operation.Metadata = metadata
	metadata["memo"] = cryptoCreateTransaction.GetAccountMemo()

	if cryptoCreateTransaction.GetAutoRenewPeriod() != 0 {
		metadata["auto_renew_period"] = int64(cryptoCreateTransaction.GetAutoRenewPeriod().Seconds())
	}

	if key, err := cryptoCreateTransaction.GetKey(); err != nil {
		log.Errorf("Failed to get key from crypto create transaction: %v", err)
		return nil, nil, errors.ErrInvalidTransaction
	} else if key == nil {
		log.Errorf("Key not set for the crypto create transaction")
		return nil, nil, errors.ErrInvalidTransaction
	} else {
		metadata["key"] = key.String()
	}

	if cryptoCreateTransaction.GetMaxAutomaticTokenAssociations() != 0 {
		metadata["max_automatic_token_associations"] = cryptoCreateTransaction.GetMaxAutomaticTokenAssociations()
	}

	if !isZeroAccountId(cryptoCreateTransaction.GetProxyAccountID()) {
		metadata["proxy_account_id"] = cryptoCreateTransaction.GetProxyAccountID().String()
	}

	return []*rTypes.Operation{operation}, []hedera.AccountID{payer}, nil
}

func (c *cryptoCreateTransactionConstructor) Preprocess(_ context.Context, operations []*rTypes.Operation) (
	[]hedera.AccountID,
	*rTypes.Error,
) {
	_, signer, err := c.preprocess(operations)
	if err != nil {
		return nil, err
	}

	return []hedera.AccountID{*signer}, nil
}

func (c *cryptoCreateTransactionConstructor) preprocess(operations []*rTypes.Operation) (
	*cryptoCreate,
	*hedera.AccountID,
	*rTypes.Error,
) {
	if rErr := validateOperations(operations, 1, c.GetOperationType(), false); rErr != nil {
		return nil, nil, rErr
	}

	operation := operations[0]
	account, err := hedera.AccountIDFromString(operation.Account.Address)
	if err != nil {
		log.Errorf("Invalid account %s: %s", operation.Account.Address, err)
		return nil, nil, errors.ErrInvalidAccount
	}

	amount, rErr := types.NewAmount(operation.Amount)
	if rErr != nil {
		log.Errorf("Invalid amount %v: %v", operation.Amount, rErr)
		return nil, nil, rErr
	} else if _, ok := amount.(*types.HbarAmount); !ok {
		log.Errorf("Operation amount currency is not HBAR: %v", operation.Amount)
		return nil, nil, errors.ErrInvalidCurrency
	} else if amount.GetValue() < 0 {
		log.Errorf("Initial balance %d is < 0", amount.GetValue())
		return nil, nil, errors.ErrInvalidOperationsAmount
	}

	cryptoCreate := &cryptoCreate{}
	if rErr := parseOperationMetadata(c.validate, cryptoCreate, operation.Metadata); rErr != nil {
		log.Errorf("Failed to parse and validate operation metadata %v: %v", operation.Metadata, rErr)
		return nil, nil, rErr
	}

	cryptoCreate.InitialBalance = amount.GetValue()

	return cryptoCreate, &account, nil
}

func newCryptoCreateTransactionConstructor() transactionConstructorWithType {
	transactionType := reflect.TypeOf(hedera.AccountCreateTransaction{}).Name()
	return &cryptoCreateTransactionConstructor{
		transactionType: transactionType,
		validate:        validator.New(),
	}
}
