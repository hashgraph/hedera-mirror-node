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

type tokenUpdate struct {
	tokenId          hedera.TokenID
	AdminKey         types.PublicKey  `json:"admin_key"`
	AutoRenewAccount hedera.AccountID `json:"auto_renew_account"`
	AutoRenewPeriod  int64            `json:"auto_renew_period"` // in seconds
	Expiry           int64            `json:"expiry"`            // nanos since epoch
	FreezeKey        types.PublicKey  `json:"freeze_key"`
	KycKey           types.PublicKey  `json:"kyc_key"`
	Memo             string           `json:"memo"`
	Name             string           `json:"name"`
	SupplyKey        types.PublicKey  `json:"supply_key"`
	Symbol           string           `json:"symbol"`
	Treasury         hedera.AccountID `json:"treasury"`
	WipeKey          types.PublicKey  `json:"wipe_key"`
}

type tokenUpdateTransactionConstructor struct {
	commonTransactionConstructor
}

func (t *tokenUpdateTransactionConstructor) Construct(
	_ context.Context,
	operations types.OperationSlice,
) (interfaces.Transaction, []types.AccountId, *rTypes.Error) {
	signers, tokenUpdate, err := t.preprocess(operations)
	if err != nil {
		return nil, nil, err
	}

	tx := hedera.NewTokenUpdateTransaction().SetTokenID(tokenUpdate.tokenId)

	if !tokenUpdate.AdminKey.IsEmpty() {
		tx.SetAdminKey(tokenUpdate.AdminKey.PublicKey)
	}

	if !isZeroAccountId(tokenUpdate.AutoRenewAccount) {
		tx.SetAutoRenewAccount(tokenUpdate.AutoRenewAccount)
	}

	if tokenUpdate.AutoRenewPeriod != 0 {
		tx.SetAutoRenewPeriod(time.Second * time.Duration(tokenUpdate.AutoRenewPeriod))
	}

	if tokenUpdate.Expiry != 0 {
		tx.SetExpirationTime(time.Unix(tokenUpdate.Expiry, 0))
	}

	if !tokenUpdate.FreezeKey.IsEmpty() {
		tx.SetFreezeKey(tokenUpdate.FreezeKey.PublicKey)
	}

	if !tokenUpdate.KycKey.IsEmpty() {
		tx.SetKycKey(tokenUpdate.KycKey.PublicKey)
	}

	if tokenUpdate.Memo != "" {
		tx.SetTokenMemo(tokenUpdate.Memo)
	}

	if tokenUpdate.Name != "" {
		tx.SetTokenName(tokenUpdate.Name)
	}

	if !tokenUpdate.SupplyKey.IsEmpty() {
		tx.SetSupplyKey(tokenUpdate.SupplyKey.PublicKey)
	}

	if tokenUpdate.Symbol != "" {
		tx.SetTokenSymbol(tokenUpdate.Symbol)
	}

	if !isZeroAccountId(tokenUpdate.Treasury) {
		tx.SetTreasuryAccountID(tokenUpdate.Treasury)
	}

	if !tokenUpdate.WipeKey.IsEmpty() {
		tx.SetWipeKey(tokenUpdate.WipeKey.PublicKey)
	}

	return tx, signers, nil
}

func (t *tokenUpdateTransactionConstructor) Parse(_ context.Context, transaction interfaces.Transaction) (
	types.OperationSlice,
	[]types.AccountId,
	*rTypes.Error,
) {
	tokenUpdateTransaction, ok := transaction.(*hedera.TokenUpdateTransaction)
	if !ok {
		return nil, nil, errors.ErrTransactionInvalidType
	}

	payerId := tokenUpdateTransaction.GetTransactionID().AccountID
	tokenId := tokenUpdateTransaction.GetTokenID()

	if payerId == nil || isZeroAccountId(*payerId) || isZeroTokenId(tokenId) {
		return nil, nil, errors.ErrInvalidTransaction
	}

	payerAccountId, err := types.NewAccountIdFromSdkAccountId(*payerId)
	if err != nil {
		return nil, nil, errors.ErrInvalidAccount
	}
	signers := []types.AccountId{payerAccountId}

	tokenEntityId, err := domain.EntityIdOf(int64(tokenId.Shard), int64(tokenId.Realm), int64(tokenId.Token))
	if err != nil {
		return nil, nil, errors.ErrInvalidToken
	}

	domainToken := domain.Token{TokenId: tokenEntityId, Type: domain.TokenTypeUnknown}
	metadata := make(map[string]interface{})
	operation := types.Operation{
		AccountId: payerAccountId,
		Amount:    types.NewTokenAmount(domainToken, 0),
		Metadata:  metadata,
		Type:      t.GetOperationType(),
	}

	metadata["memo"] = tokenUpdateTransaction.GeTokenMemo()
	metadata["name"] = tokenUpdateTransaction.GetTokenName()
	metadata["symbol"] = tokenUpdateTransaction.GetTokenSymbol()

	if isNonEmptyPublicKey(tokenUpdateTransaction.GetAdminKey()) {
		metadata["admin_key"] = tokenUpdateTransaction.GetAdminKey().String()
	}

	if !isZeroAccountId(tokenUpdateTransaction.GetAutoRenewAccount()) {
		metadata["auto_renew_account"] = tokenUpdateTransaction.GetAutoRenewAccount().String()
		autoRenewAccountId, err := types.NewAccountIdFromSdkAccountId(tokenUpdateTransaction.GetAutoRenewAccount())
		if err != nil {
			return nil, nil, errors.ErrInvalidAccount
		}
		signers = append(signers, autoRenewAccountId)
	}

	if tokenUpdateTransaction.GetAutoRenewPeriod() != 0 {
		metadata["auto_renew_period"] = int64(tokenUpdateTransaction.GetAutoRenewPeriod().Seconds())
	}

	if !tokenUpdateTransaction.GetExpirationTime().IsZero() {
		metadata["expiry"] = tokenUpdateTransaction.GetExpirationTime().Unix()
	}

	if isNonEmptyPublicKey(tokenUpdateTransaction.GetFreezeKey()) {
		metadata["freeze_key"] = tokenUpdateTransaction.GetFreezeKey().String()
	}

	if isNonEmptyPublicKey(tokenUpdateTransaction.GetKycKey()) {
		metadata["kyc_key"] = tokenUpdateTransaction.GetKycKey().String()
	}

	if isNonEmptyPublicKey(tokenUpdateTransaction.GetSupplyKey()) {
		metadata["supply_key"] = tokenUpdateTransaction.GetSupplyKey().String()
	}

	if tokenUpdateTransaction.GeTokenMemo() != "" {
		metadata["memo"] = tokenUpdateTransaction.GeTokenMemo()
	}

	if tokenUpdateTransaction.GetTokenName() != "" {
		metadata["name"] = tokenUpdateTransaction.GetTokenName()
	}

	if tokenUpdateTransaction.GetTokenSymbol() != "" {
		metadata["symbol"] = tokenUpdateTransaction.GetTokenSymbol()
	}

	if !isZeroAccountId(tokenUpdateTransaction.GetTreasuryAccountID()) {
		metadata["treasury"] = tokenUpdateTransaction.GetTreasuryAccountID().String()
	}

	if isNonEmptyPublicKey(tokenUpdateTransaction.GetWipeKey()) {
		metadata["wipe_key"] = tokenUpdateTransaction.GetWipeKey().String()
	}

	return types.OperationSlice{operation}, signers, nil
}

func (t *tokenUpdateTransactionConstructor) Preprocess(_ context.Context, operations types.OperationSlice) (
	[]types.AccountId,
	*rTypes.Error,
) {
	signers, _, err := t.preprocess(operations)
	if err != nil {
		return nil, err
	}

	return signers, nil
}

func (t *tokenUpdateTransactionConstructor) preprocess(operations types.OperationSlice) (
	[]types.AccountId,
	*tokenUpdate,
	*rTypes.Error,
) {
	if rErr := validateOperations(operations, 1, t.GetOperationType(), false); rErr != nil {
		return nil, nil, rErr
	}

	operation := operations[0]
	tokenAmount, ok := operation.Amount.(*types.TokenAmount)
	if !ok {
		return nil, nil, errors.ErrInvalidCurrency
	}

	tokenUpdate := &tokenUpdate{tokenId: tokenAmount.GetSdkTokenId()}
	if err := parseOperationMetadata(nil, tokenUpdate, operation.Metadata); err != nil {
		return nil, nil, err
	}

	payer := operation.AccountId
	signers := []types.AccountId{payer}

	if !isZeroAccountId(tokenUpdate.AutoRenewAccount) {
		autoRenewAccountId, err := types.NewAccountIdFromSdkAccountId(tokenUpdate.AutoRenewAccount)
		if err != nil {
			return nil, nil, errors.ErrInvalidAccount
		}
		if payer.String() != autoRenewAccountId.String() {
			signers = append(signers, autoRenewAccountId)
		}
	}
	return signers, tokenUpdate, nil
}

func newTokenUpdateTransactionConstructor() transactionConstructorWithType {
	return &tokenUpdateTransactionConstructor{
		commonTransactionConstructor: newCommonTransactionConstructor(
			hedera.NewTokenUpdateTransaction(),
			types.OperationTypeTokenUpdate,
		),
	}
}
