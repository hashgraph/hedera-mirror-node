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
	"time"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-sdk-go/v2"
)

type tokenCreate struct {
	AdminKey         types.PublicKey  `json:"admin_key"`
	AutoRenewAccount hedera.AccountID `json:"auto_renew_account"`
	AutoRenewPeriod  int64            `json:"auto_renew_period"`
	Decimals         uint32           `json:"decimals"`
	Expiry           int64            `json:"expiry"`
	FreezeDefault    bool             `json:"freeze_default"`
	FreezeKey        types.PublicKey  `json:"freeze_key"`
	InitialSupply    uint64           `json:"initial_supply"`
	KycKey           types.PublicKey  `json:"kyc_key"`
	Memo             string           `json:"memo"`
	Name             string           `json:"name" validate:"required"`
	SupplyKey        types.PublicKey  `json:"supply_key"`
	SupplyType       string           `json:"supply_type"`
	Symbol           string           `json:"symbol" validate:"required"`
	Type             string           `json:"type"`
	WipeKey          types.PublicKey  `json:"wipe_key"`
}

type tokenCreateTransactionConstructor struct {
	commonTransactionConstructor
}

func (t *tokenCreateTransactionConstructor) Construct(
	_ context.Context,
	operations types.OperationSlice,
) (interfaces.Transaction, []types.AccountId, *rTypes.Error) {
	treasury, signers, tokenCreate, err := t.preprocess(operations)
	if err != nil {
		return nil, nil, err
	}

	tx := hedera.NewTokenCreateTransaction().
		SetDecimals(uint(tokenCreate.Decimals)).
		SetFreezeDefault(tokenCreate.FreezeDefault).
		SetInitialSupply(tokenCreate.InitialSupply).
		SetTokenMemo(tokenCreate.Memo).
		SetTokenName(tokenCreate.Name).
		SetTokenSymbol(tokenCreate.Symbol).
		SetTreasuryAccountID(treasury.ToSdkAccountId())

	if !tokenCreate.AdminKey.IsEmpty() {
		tx.SetAdminKey(tokenCreate.AdminKey.PublicKey)
	}

	if !isZeroAccountId(tokenCreate.AutoRenewAccount) {
		tx.SetAutoRenewAccount(tokenCreate.AutoRenewAccount)
	} else if tokenCreate.Expiry == 0 {
		// set a valid auto renew account when expiry is not set
		tx.SetAutoRenewAccount(treasury.ToSdkAccountId())
	}

	if tokenCreate.AutoRenewPeriod != 0 {
		tx.SetAutoRenewPeriod(time.Second * time.Duration(tokenCreate.AutoRenewPeriod))
	}

	if tokenCreate.Expiry != 0 {
		tx.SetExpirationTime(time.Unix(tokenCreate.Expiry, 0))
	}

	if !tokenCreate.FreezeKey.IsEmpty() {
		tx.SetFreezeKey(tokenCreate.FreezeKey.PublicKey)
	}

	if !tokenCreate.KycKey.IsEmpty() {
		tx.SetKycKey(tokenCreate.KycKey.PublicKey)
	}

	if !tokenCreate.SupplyKey.IsEmpty() {
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

	if !tokenCreate.WipeKey.IsEmpty() {
		tx.SetWipeKey(tokenCreate.WipeKey.PublicKey)
	}

	return tx, signers, nil
}

func (t *tokenCreateTransactionConstructor) Parse(_ context.Context, transaction interfaces.Transaction) (
	types.OperationSlice,
	[]types.AccountId,
	*rTypes.Error,
) {
	tokenCreateTransaction, ok := transaction.(*hedera.TokenCreateTransaction)
	if !ok {
		return nil, nil, errors.ErrTransactionInvalidType
	}

	treasury := tokenCreateTransaction.GetTransactionID().AccountID
	if treasury == nil {
		return nil, nil, errors.ErrInvalidTransaction
	}

	if tokenCreateTransaction.GetTokenName() == "" ||
		tokenCreateTransaction.GetTokenSymbol() == "" {
		return nil, nil, errors.ErrInvalidTransaction
	}

	treasuryAccountId, err := types.NewAccountIdFromSdkAccountId(*treasury)
	if err != nil {
		return nil, nil, errors.ErrInvalidAccount
	}
	metadata := make(map[string]interface{})
	operation := types.Operation{
		AccountId: treasuryAccountId,
		Metadata:  metadata,
		Type:      t.GetOperationType(),
	}

	metadata["decimals"] = tokenCreateTransaction.GetDecimals()
	metadata["expiry"] = tokenCreateTransaction.GetExpirationTime().Unix()
	metadata["freeze_default"] = tokenCreateTransaction.GetFreezeDefault()
	metadata["initial_supply"] = tokenCreateTransaction.GetInitialSupply()
	metadata["memo"] = tokenCreateTransaction.GetTokenMemo()
	metadata["name"] = tokenCreateTransaction.GetTokenName()
	metadata["symbol"] = tokenCreateTransaction.GetTokenSymbol()

	signers := []types.AccountId{treasuryAccountId}

	if isNonEmptyPublicKey(tokenCreateTransaction.GetAdminKey()) {
		metadata["admin_key"] = tokenCreateTransaction.GetAdminKey().String()
	}

	if !isZeroAccountId(tokenCreateTransaction.GetAutoRenewAccount()) {
		autoRenewAccount, err := types.NewAccountIdFromSdkAccountId(tokenCreateTransaction.GetAutoRenewAccount())
		if err != nil {
			return nil, nil, errors.ErrInvalidAccount
		}
		metadata["auto_renew_account"] = autoRenewAccount.String()
		signers = append(signers, autoRenewAccount)
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

	return types.OperationSlice{operation}, signers, nil
}

func (t *tokenCreateTransactionConstructor) Preprocess(_ context.Context, operations types.OperationSlice) (
	[]types.AccountId,
	*rTypes.Error,
) {
	_, signers, _, err := t.preprocess(operations)
	if err != nil {
		return nil, err
	}

	return signers, nil
}

func (t *tokenCreateTransactionConstructor) preprocess(operations types.OperationSlice) (
	*types.AccountId,
	[]types.AccountId,
	*tokenCreate,
	*rTypes.Error,
) {
	if rErr := validateOperations(operations, 1, t.GetOperationType(), true); rErr != nil {
		return nil, nil, nil, rErr
	}

	operation := operations[0]
	tokenCreate := &tokenCreate{}
	if rErr := parseOperationMetadata(t.validate, tokenCreate, operation.Metadata); rErr != nil {
		return nil, nil, nil, rErr
	}

	if tokenCreate.SupplyType != domain.TokenSupplyTypeUnknown &&
		tokenCreate.SupplyType != domain.TokenSupplyTypeFinite &&
		tokenCreate.SupplyType != domain.TokenSupplyTypeInfinite {
		return nil, nil, nil, errors.ErrInvalidOperations
	}

	if tokenCreate.Type != domain.TokenTypeUnknown &&
		tokenCreate.Type != domain.TokenTypeFungibleCommon &&
		tokenCreate.Type != domain.TokenTypeNonFungibleUnique {
		return nil, nil, nil, errors.ErrInvalidOperations
	}

	signers := []types.AccountId{operation.AccountId}
	autoRenewAccount, err := types.NewAccountIdFromSdkAccountId(tokenCreate.AutoRenewAccount)
	if err != nil {
		return nil, nil, nil, errors.ErrInvalidAccount
	}
	if !autoRenewAccount.IsZero() {
		signers = append(signers, autoRenewAccount)
	}

	return &operation.AccountId, signers, tokenCreate, nil
}

func newTokenCreateTransactionConstructor() transactionConstructorWithType {
	return &tokenCreateTransactionConstructor{
		commonTransactionConstructor: newCommonTransactionConstructor(
			hedera.NewTokenCreateTransaction(),
			types.OperationTypeTokenCreate,
		),
	}
}
