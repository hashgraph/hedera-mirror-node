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
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	hErrors "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/hashgraph/hedera-sdk-go/v2"
)

type tokenUpdate struct {
	tokenId          hedera.TokenID
	AdminKey         publicKey        `json:"admin_key"`
	AutoRenewAccount hedera.AccountID `json:"auto_renew_account"`
	AutoRenewPeriod  int64            `json:"auto_renew_period"` // in seconds
	Expiry           int64            `json:"expiry"`            // nanos since epoch
	FreezeKey        publicKey        `json:"freeze_key"`
	KycKey           publicKey        `json:"kyc_key"`
	Memo             string           `json:"memo"`
	Name             string           `json:"name"`
	SupplyKey        publicKey        `json:"supply_key"`
	Symbol           string           `json:"symbol"`
	Treasury         hedera.AccountID `json:"treasury"`
	WipeKey          publicKey        `json:"wipe_key"`
}

type tokenUpdateTransactionConstructor struct {
	transactionType string
	tokenRepo       interfaces.TokenRepository
}

func (t *tokenUpdateTransactionConstructor) Construct(
	ctx context.Context,
	nodeAccountId hedera.AccountID,
	operations []*rTypes.Operation,
	validStartNanos int64,
) (interfaces.Transaction, []hedera.AccountID, *rTypes.Error) {
	payer, tokenUpdate, err := t.preprocess(ctx, operations)
	if err != nil {
		return nil, nil, err
	}

	tx := hedera.NewTokenUpdateTransaction().
		SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
		SetTokenID(tokenUpdate.tokenId).
		SetTransactionID(getTransactionId(*payer, validStartNanos))

	if !tokenUpdate.AdminKey.isEmpty() {
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

	if !tokenUpdate.FreezeKey.isEmpty() {
		tx.SetFreezeKey(tokenUpdate.FreezeKey.PublicKey)
	}

	if !tokenUpdate.KycKey.isEmpty() {
		tx.SetKycKey(tokenUpdate.KycKey.PublicKey)
	}

	if tokenUpdate.Memo != "" {
		tx.SetTokenMemo(tokenUpdate.Memo)
	}

	if tokenUpdate.Name != "" {
		tx.SetTokenName(tokenUpdate.Name)
	}

	if !tokenUpdate.SupplyKey.isEmpty() {
		tx.SetSupplyKey(tokenUpdate.SupplyKey.PublicKey)
	}

	if tokenUpdate.Symbol != "" {
		tx.SetTokenSymbol(tokenUpdate.Symbol)
	}

	if !isZeroAccountId(tokenUpdate.Treasury) {
		tx.SetTreasuryAccountID(tokenUpdate.Treasury)
	}

	if !tokenUpdate.WipeKey.isEmpty() {
		tx.SetWipeKey(tokenUpdate.WipeKey.PublicKey)
	}

	if _, err := tx.Freeze(); err != nil {
		return nil, nil, hErrors.ErrTransactionFreezeFailed
	}

	return tx, []hedera.AccountID{*payer}, nil
}

func (t *tokenUpdateTransactionConstructor) GetOperationType() string {
	return config.OperationTypeTokenUpdate
}

func (t *tokenUpdateTransactionConstructor) GetSdkTransactionType() string {
	return t.transactionType
}

func (t *tokenUpdateTransactionConstructor) Parse(ctx context.Context, transaction interfaces.Transaction) (
	[]*rTypes.Operation,
	[]hedera.AccountID,
	*rTypes.Error,
) {
	tokenUpdateTransaction, ok := transaction.(*hedera.TokenUpdateTransaction)
	if !ok {
		return nil, nil, hErrors.ErrTransactionInvalidType
	}

	payerId := tokenUpdateTransaction.GetTransactionID().AccountID
	tokenId := tokenUpdateTransaction.GetTokenID()

	if payerId == nil || isZeroAccountId(*payerId) || isZeroTokenId(tokenId) {
		return nil, nil, hErrors.ErrInvalidTransaction
	}

	token, err := t.tokenRepo.Find(ctx, tokenId.String())
	if err != nil {
		return nil, nil, err
	}

	operation := &rTypes.Operation{
		OperationIdentifier: &rTypes.OperationIdentifier{
			Index: 0,
		},
		Account: &rTypes.AccountIdentifier{Address: payerId.String()},
		Amount: &rTypes.Amount{
			Value:    "0",
			Currency: types.Token{Token: token}.ToRosettaCurrency(),
		},
		Type: t.GetOperationType(),
	}

	metadata := make(map[string]interface{})
	operation.Metadata = metadata
	metadata["memo"] = tokenUpdateTransaction.GeTokenMemo()
	metadata["name"] = tokenUpdateTransaction.GetTokenName()
	metadata["symbol"] = tokenUpdateTransaction.GetTokenSymbol()

	if !isEmptyPublicKey(tokenUpdateTransaction.GetAdminKey()) {
		metadata["admin_key"] = tokenUpdateTransaction.GetAdminKey().String()
	}

	if !isZeroAccountId(tokenUpdateTransaction.GetAutoRenewAccount()) {
		metadata["auto_renew_account"] = tokenUpdateTransaction.GetAutoRenewAccount().String()
	}

	if tokenUpdateTransaction.GetAutoRenewPeriod() != 0 {
		metadata["auto_renew_period"] = int64(tokenUpdateTransaction.GetAutoRenewPeriod().Seconds())
	}

	if !tokenUpdateTransaction.GetExpirationTime().IsZero() {
		metadata["expiry"] = tokenUpdateTransaction.GetExpirationTime().Unix()
	}

	if !isEmptyPublicKey(tokenUpdateTransaction.GetFreezeKey()) {
		metadata["freeze_key"] = tokenUpdateTransaction.GetFreezeKey().String()
	}

	if !isEmptyPublicKey(tokenUpdateTransaction.GetKycKey()) {
		metadata["kyc_key"] = tokenUpdateTransaction.GetKycKey().String()
	}

	if !isEmptyPublicKey(tokenUpdateTransaction.GetSupplyKey()) {
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

	if !isEmptyPublicKey(tokenUpdateTransaction.GetWipeKey()) {
		metadata["wipe_key"] = tokenUpdateTransaction.GetWipeKey().String()
	}

	return []*rTypes.Operation{operation}, []hedera.AccountID{*payerId}, nil
}

func (t *tokenUpdateTransactionConstructor) Preprocess(ctx context.Context, operations []*rTypes.Operation) (
	[]hedera.AccountID,
	*rTypes.Error,
) {
	payer, _, err := t.preprocess(ctx, operations)
	if err != nil {
		return nil, err
	}

	return []hedera.AccountID{*payer}, nil
}

func (t *tokenUpdateTransactionConstructor) preprocess(ctx context.Context, operations []*rTypes.Operation) (
	*hedera.AccountID,
	*tokenUpdate,
	*rTypes.Error,
) {
	if rErr := validateOperations(operations, 1, t.GetOperationType(), false); rErr != nil {
		return nil, nil, rErr
	}

	operation := operations[0]

	tokenId, rErr := validateToken(ctx, t.tokenRepo, operation.Amount.Currency)
	if rErr != nil {
		return nil, nil, rErr
	}

	tokenUpdate := &tokenUpdate{tokenId: *tokenId}
	if rErr = parseOperationMetadata(nil, tokenUpdate, operation.Metadata); rErr != nil {
		return nil, nil, rErr
	}

	payer, err := hedera.AccountIDFromString(operation.Account.Address)
	if err != nil {
		return nil, nil, hErrors.ErrInvalidAccount
	}

	return &payer, tokenUpdate, nil
}

func newTokenUpdateTransactionConstructor(tokenRepo interfaces.TokenRepository) transactionConstructorWithType {
	transactionType := reflect.TypeOf(hedera.TokenUpdateTransaction{}).Name()
	return &tokenUpdateTransactionConstructor{
		transactionType: transactionType,
		tokenRepo:       tokenRepo,
	}
}
