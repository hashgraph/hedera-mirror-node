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
	"time"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/go-playground/validator/v10"
	hErrors "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/hashgraph/hedera-sdk-go/v2"
)

type tokenCreate struct {
	AdminKey         publicKey        `json:"admin_key"`
	AutoRenewAccount hedera.AccountID `json:"auto_renew_account"`
	AutoRenewPeriod  int64            `json:"auto_renew_period"`
	Decimals         uint32           `json:"decimals"`
	Expiry           int64            `json:"expiry"`
	FreezeDefault    bool             `json:"freeze_default"`
	FreezeKey        publicKey        `json:"freeze_key"`
	InitialSupply    uint64           `json:"initial_supply"`
	KycKey           publicKey        `json:"kyc_key"`
	Memo             string           `json:"memo"`
	Name             string           `json:"name" validate:"required"`
	SupplyKey        publicKey        `json:"supply_key"`
	Symbol           string           `json:"symbol" validate:"required"`
	WipeKey          publicKey        `json:"wipe_key"`
}

type tokenCreateTransactionConstructor struct {
	transactionType string
	validate        *validator.Validate
}

func (t *tokenCreateTransactionConstructor) Construct(nodeAccountId hedera.AccountID, operations []*rTypes.Operation) (
	ITransaction,
	[]hedera.AccountID,
	*rTypes.Error,
) {
	treasury, signers, tokenCreate, err := t.preprocess(operations)
	if err != nil {
		return nil, nil, err
	}

	tx := hedera.NewTokenCreateTransaction().
		SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
		SetDecimals(uint(tokenCreate.Decimals)).
		SetFreezeDefault(tokenCreate.FreezeDefault).
		SetInitialSupply(tokenCreate.InitialSupply).
		SetTokenMemo(tokenCreate.Memo).
		SetTokenName(tokenCreate.Name).
		SetTokenSymbol(tokenCreate.Symbol).
		SetTransactionID(hedera.TransactionIDGenerate(treasury)).
		SetTreasuryAccountID(treasury)

	if !isEmptyPublicKey(tokenCreate.AdminKey) {
		tx.SetAdminKey(tokenCreate.AdminKey.PublicKey)
	}

	if !isZeroAccountId(tokenCreate.AutoRenewAccount) {
		tx.SetAutoRenewAccount(tokenCreate.AutoRenewAccount)
	}

	if tokenCreate.AutoRenewPeriod != 0 {
		tx.SetAutoRenewPeriod(time.Second * time.Duration(tokenCreate.AutoRenewPeriod))
	}

	if tokenCreate.Expiry != 0 {
		tx.SetExpirationTime(time.Unix(tokenCreate.Expiry, 0))
	}

	if !isEmptyPublicKey(tokenCreate.FreezeKey) {
		tx.SetFreezeKey(tokenCreate.FreezeKey.PublicKey)
	}

	if !isEmptyPublicKey(tokenCreate.KycKey) {
		tx.SetKycKey(tokenCreate.KycKey.PublicKey)
	}

	if !isEmptyPublicKey(tokenCreate.SupplyKey) {
		tx.SetSupplyKey(tokenCreate.SupplyKey.PublicKey)
	}

	if !isEmptyPublicKey(tokenCreate.WipeKey) {
		tx.SetWipeKey(tokenCreate.WipeKey.PublicKey)
	}

	if _, err := tx.Freeze(); err != nil {
		return nil, nil, hErrors.ErrTransactionFreezeFailed
	}

	return tx, signers, nil
}

func (t *tokenCreateTransactionConstructor) GetOperationType() string {
	return config.OperationTypeTokenCreate
}

func (t *tokenCreateTransactionConstructor) GetSdkTransactionType() string {
	return t.transactionType
}

func (t *tokenCreateTransactionConstructor) Parse(transaction ITransaction) (
	[]*rTypes.Operation,
	[]hedera.AccountID,
	*rTypes.Error,
) {
	tokenCreateTransaction, ok := transaction.(*hedera.TokenCreateTransaction)
	if !ok {
		return nil, nil, hErrors.ErrTransactionInvalidType
	}

	if tokenCreateTransaction.GetTransactionID().AccountID == nil {
		return nil, nil, hErrors.ErrInvalidTransaction
	}

	if tokenCreateTransaction.GetTokenName() == "" ||
		tokenCreateTransaction.GetTokenSymbol() == "" {
		return nil, nil, hErrors.ErrInvalidTransaction
	}

	treasury := *tokenCreateTransaction.GetTransactionID().AccountID
	operation := &rTypes.Operation{
		OperationIdentifier: &rTypes.OperationIdentifier{
			Index: 0,
		},
		Account: &rTypes.AccountIdentifier{Address: treasury.String()},
		Type:    t.GetOperationType(),
	}

	metadata := make(map[string]interface{})
	operation.Metadata = metadata
	metadata["decimals"] = tokenCreateTransaction.GetDecimals()
	metadata["expiry"] = tokenCreateTransaction.GetExpirationTime().Unix()
	metadata["freeze_default"] = tokenCreateTransaction.GetFreezeDefault()
	metadata["initial_supply"] = tokenCreateTransaction.GetInitialSupply()
	metadata["memo"] = tokenCreateTransaction.GetTokenMemo()
	metadata["name"] = tokenCreateTransaction.GetTokenName()
	metadata["symbol"] = tokenCreateTransaction.GetTokenSymbol()

	signers := []hedera.AccountID{treasury}

	if !isEmptyPublicKey(tokenCreateTransaction.GetAdminKey()) {
		metadata["admin_key"] = tokenCreateTransaction.GetAdminKey().String()
	}

	if !isZeroAccountId(tokenCreateTransaction.GetAutoRenewAccount()) {
		metadata["auto_renew_account"] = tokenCreateTransaction.GetAutoRenewAccount().String()
		signers = append(signers, tokenCreateTransaction.GetAutoRenewAccount())
	}

	if tokenCreateTransaction.GetAutoRenewPeriod() != 0 {
		metadata["auto_renew_period"] = int64(tokenCreateTransaction.GetAutoRenewPeriod().Seconds())
	}

	if !tokenCreateTransaction.GetExpirationTime().IsZero() {
		metadata["expiry"] = tokenCreateTransaction.GetExpirationTime().Unix()
	}

	if !isEmptyPublicKey(tokenCreateTransaction.GetFreezeKey()) {
		metadata["freeze_key"] = tokenCreateTransaction.GetFreezeKey().String()
	}

	if !isEmptyPublicKey(tokenCreateTransaction.GetKycKey()) {
		metadata["kyc_key"] = tokenCreateTransaction.GetKycKey().String()
	}

	if !isEmptyPublicKey(tokenCreateTransaction.GetSupplyKey()) {
		metadata["supply_key"] = tokenCreateTransaction.GetSupplyKey().String()
	}

	if !isEmptyPublicKey(tokenCreateTransaction.GetWipeKey()) {
		metadata["wipe_key"] = tokenCreateTransaction.GetWipeKey().String()
	}

	return []*rTypes.Operation{operation}, signers, nil
}

func (t *tokenCreateTransactionConstructor) Preprocess(operations []*rTypes.Operation) (
	[]hedera.AccountID,
	*rTypes.Error,
) {
	_, signers, _, err := t.preprocess(operations)
	if err != nil {
		return nil, err
	}

	return signers, nil
}

func (t *tokenCreateTransactionConstructor) preprocess(operations []*rTypes.Operation) (
	hedera.AccountID,
	[]hedera.AccountID,
	*tokenCreate,
	*rTypes.Error,
) {
	if rErr := validateOperations(operations, 1, t.GetOperationType(), true); rErr != nil {
		return hedera.AccountID{}, nil, nil, rErr
	}

	operation := operations[0]
	tokenCreate := &tokenCreate{}
	if rErr := parseOperationMetadata(t.validate, tokenCreate, operation.Metadata); rErr != nil {
		return hedera.AccountID{}, nil, nil, rErr
	}

	var signers []hedera.AccountID

	treasury, err := hedera.AccountIDFromString(operation.Account.Address)
	if err != nil {
		return hedera.AccountID{}, nil, nil, hErrors.ErrInvalidAccount
	}
	signers = append(signers, treasury)

	if !isZeroAccountId(tokenCreate.AutoRenewAccount) {
		signers = append(signers, tokenCreate.AutoRenewAccount)
	}

	return treasury, signers, tokenCreate, nil
}

func newTokenCreateTransactionConstructor() transactionConstructorWithType {
	transactionType := reflect.TypeOf(hedera.TokenCreateTransaction{}).Name()
	return &tokenCreateTransactionConstructor{
		transactionType: transactionType,
		validate:        validator.New(),
	}
}
