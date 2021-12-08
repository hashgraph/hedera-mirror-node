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
	"time"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/go-playground/validator/v10"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
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
	SupplyType       string           `json:"supply_type"`
	Symbol           string           `json:"symbol" validate:"required"`
	Type             string           `json:"type"`
	WipeKey          publicKey        `json:"wipe_key"`
}

type tokenCreateTransactionConstructor struct {
	transactionType string
	validate        *validator.Validate
}

func (t *tokenCreateTransactionConstructor) Construct(
	ctx context.Context,
	nodeAccountId hedera.AccountID,
	operations []*rTypes.Operation,
	validStartNanos int64,
) (interfaces.Transaction, []hedera.AccountID, *rTypes.Error) {
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
		SetTransactionID(getTransactionId(treasury, validStartNanos)).
		SetTreasuryAccountID(treasury)

	if !tokenCreate.AdminKey.isEmpty() {
		tx.SetAdminKey(tokenCreate.AdminKey.PublicKey)
	}

	if !isZeroAccountId(tokenCreate.AutoRenewAccount) {
		tx.SetAutoRenewAccount(tokenCreate.AutoRenewAccount)
	} else if tokenCreate.Expiry == 0 {
		// set a valid auto renew account when expiry is not set
		tx.SetAutoRenewAccount(treasury)
	}

	if tokenCreate.AutoRenewPeriod != 0 {
		tx.SetAutoRenewPeriod(time.Second * time.Duration(tokenCreate.AutoRenewPeriod))
	}

	if tokenCreate.Expiry != 0 {
		tx.SetExpirationTime(time.Unix(tokenCreate.Expiry, 0))
	}

	if !tokenCreate.FreezeKey.isEmpty() {
		tx.SetFreezeKey(tokenCreate.FreezeKey.PublicKey)
	}

	if !tokenCreate.KycKey.isEmpty() {
		tx.SetKycKey(tokenCreate.KycKey.PublicKey)
	}

	if !tokenCreate.SupplyKey.isEmpty() {
		tx.SetSupplyKey(tokenCreate.SupplyKey.PublicKey)
	}

	// default is INFINITE
	if tokenCreate.SupplyType == domain.TokenSupplyTypeFinite {
		tx.SetSupplyType(hedera.TokenSupplyTypeFinite)
	}

	// default is FUNGIBLE_COMMON
	if tokenCreate.Type == domain.TokenTypeNonFungibleUnique {
		tx.SetTokenType(hedera.TokenTypeNonFungibleUnique)
	}

	if !tokenCreate.WipeKey.isEmpty() {
		tx.SetWipeKey(tokenCreate.WipeKey.PublicKey)
	}

	if _, err := tx.Freeze(); err != nil {
		return nil, nil, errors.ErrTransactionFreezeFailed
	}

	return tx, signers, nil
}

func (t *tokenCreateTransactionConstructor) GetOperationType() string {
	return types.OperationTypeTokenCreate
}

func (t *tokenCreateTransactionConstructor) GetSdkTransactionType() string {
	return t.transactionType
}

func (t *tokenCreateTransactionConstructor) Parse(ctx context.Context, transaction interfaces.Transaction) (
	[]*rTypes.Operation,
	[]hedera.AccountID,
	*rTypes.Error,
) {
	tokenCreateTransaction, ok := transaction.(*hedera.TokenCreateTransaction)
	if !ok {
		return nil, nil, errors.ErrTransactionInvalidType
	}

	if tokenCreateTransaction.GetTransactionID().AccountID == nil {
		return nil, nil, errors.ErrInvalidTransaction
	}

	if tokenCreateTransaction.GetTokenName() == "" ||
		tokenCreateTransaction.GetTokenSymbol() == "" {
		return nil, nil, errors.ErrInvalidTransaction
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

	if isNonEmptyPublicKey(tokenCreateTransaction.GetAdminKey()) {
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

	if isNonEmptyPublicKey(tokenCreateTransaction.GetFreezeKey()) {
		metadata["freeze_key"] = tokenCreateTransaction.GetFreezeKey().String()
	}

	if isNonEmptyPublicKey(tokenCreateTransaction.GetKycKey()) {
		metadata["kyc_key"] = tokenCreateTransaction.GetKycKey().String()
	}

	if isNonEmptyPublicKey(tokenCreateTransaction.GetSupplyKey()) {
		metadata["supply_key"] = tokenCreateTransaction.GetSupplyKey().String()
	}

	if tokenCreateTransaction.GetSupplyType() == hedera.TokenSupplyTypeFinite {
		metadata["supply_type"] = domain.TokenSupplyTypeFinite
	}

	if tokenCreateTransaction.GetTokenType() == hedera.TokenTypeNonFungibleUnique {
		metadata["type"] = domain.TokenTypeNonFungibleUnique
	}

	if isNonEmptyPublicKey(tokenCreateTransaction.GetWipeKey()) {
		metadata["wipe_key"] = tokenCreateTransaction.GetWipeKey().String()
	}

	return []*rTypes.Operation{operation}, signers, nil
}

func (t *tokenCreateTransactionConstructor) Preprocess(ctx context.Context, operations []*rTypes.Operation) (
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
	treasury := hedera.AccountID{}

	if rErr := validateOperations(operations, 1, t.GetOperationType(), true); rErr != nil {
		return treasury, nil, nil, rErr
	}

	operation := operations[0]
	tokenCreate := &tokenCreate{}
	if rErr := parseOperationMetadata(t.validate, tokenCreate, operation.Metadata); rErr != nil {
		return treasury, nil, nil, rErr
	}

	if tokenCreate.SupplyType != domain.TokenSupplyTypeUnknown &&
		tokenCreate.SupplyType != domain.TokenSupplyTypeFinite &&
		tokenCreate.SupplyType != domain.TokenSupplyTypeInfinite {
		return treasury, nil, nil, errors.ErrInvalidOperations
	}

	if tokenCreate.Type != domain.TokenTypeUnknown &&
		tokenCreate.Type != domain.TokenTypeFungibleCommon &&
		tokenCreate.Type != domain.TokenTypeNonFungibleUnique {
		return treasury, nil, nil, errors.ErrInvalidOperations
	}

	var signers []hedera.AccountID

	treasury, err := hedera.AccountIDFromString(operation.Account.Address)
	if err != nil {
		return hedera.AccountID{}, nil, nil, errors.ErrInvalidAccount
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
