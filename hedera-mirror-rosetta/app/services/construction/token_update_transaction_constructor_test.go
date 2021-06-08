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
	"testing"
	"time"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks/repository"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

const (
	adminKeyStr  = "302a300506032b6570032100d619a3a22d6bd2a9e4b08f3d999df757e5a9ef0364c13b4b3356bc065b34fa01"
	freezeKeyStr = "302a300506032b65700321006663a95da28adcb0fc129d1b4eda4be7dd90b54a337cd2dd953e1d2dc03ca6d1"
	kycKeyStr    = "302a300506032b6570032100cf84dd35980ba09a3a4c5a19a3d63fe4937ab3fde3e2c5c6ba41e42c0c60ee9f"
	memo         = "new memo"
	name         = "new name"
	supplyKeyStr = "302a300506032b65700321000d469b64f14cc584c6fc48f03c339defcba719c2b71abe05c4aea19d16127ba1"
	symbol       = "new symbol"
	wipeKeyStr   = "302a300506032b6570032100647b6d2a4efa666858c6d0d85e660698410d1bbe67763bc50ece0b5dcff0b6db"
)

var (
	adminKey, _            = hedera.PublicKeyFromString(adminKeyStr)
	autoRenewAccount       = hedera.AccountID{Account: 1981}
	autoRenewPeriod  int64 = 3600
	expiry                 = time.Unix(167000, 0)
	freezeKey, _           = hedera.PublicKeyFromString(freezeKeyStr)
	kycKey, _              = hedera.PublicKeyFromString(kycKeyStr)
	supplyKey, _           = hedera.PublicKeyFromString(supplyKeyStr)
	treasury               = hedera.AccountID{Account: 1701}
	wipeKey, _             = hedera.PublicKeyFromString(wipeKeyStr)
)

func TestTokenUpdateTransactionConstructorSuite(t *testing.T) {
	suite.Run(t, new(tokenUpdateTransactionConstructorSuite))
}

type tokenUpdateTransactionConstructorSuite struct {
	suite.Suite
}

func (suite *tokenUpdateTransactionConstructorSuite) TestNewTransactionConstructor() {
	h := newTokenUpdateTransactionConstructor(&repository.MockTokenRepository{})
	assert.NotNil(suite.T(), h)
}

func (suite *tokenUpdateTransactionConstructorSuite) TestGetOperationType() {
	h := newTokenUpdateTransactionConstructor(&repository.MockTokenRepository{})
	assert.Equal(suite.T(), config.OperationTypeTokenUpdate, h.GetOperationType())
}

func (suite *tokenUpdateTransactionConstructorSuite) TestGetSdkTransactionType() {
	h := newTokenUpdateTransactionConstructor(&repository.MockTokenRepository{})
	assert.Equal(suite.T(), "TokenUpdateTransaction", h.GetSdkTransactionType())
}

func (suite *tokenUpdateTransactionConstructorSuite) TestConstruct() {
	var tests = []struct {
		name             string
		updateOperations updateOperationsFunc
		expectError      bool
	}{
		{
			name: "Success",
		},
		{
			name: "EmptyOperations",
			updateOperations: func([]*rTypes.Operation) []*rTypes.Operation {
				return make([]*rTypes.Operation, 0)
			},
			expectError: true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			operations := getTokenUpdateOperations()
			mockTokenRepo := &repository.MockTokenRepository{}
			h := newTokenUpdateTransactionConstructor(mockTokenRepo)
			configMockTokenRepo(mockTokenRepo, defaultMockTokenRepoConfigs[0])

			if tt.updateOperations != nil {
				operations = tt.updateOperations(operations)
			}

			// when
			tx, signers, err := h.Construct(nodeAccountId, operations)

			// then
			if tt.expectError {
				assert.NotNil(t, err)
				assert.Nil(t, signers)
				assert.Nil(t, tx)
			} else {
				assert.Nil(t, err)
				assert.ElementsMatch(t, []hedera.AccountID{payerId}, signers)
				assertTokenUpdateTransaction(t, operations[0], nodeAccountId, tx)
				mockTokenRepo.AssertExpectations(t)
			}
		})
	}
}

func (suite *tokenUpdateTransactionConstructorSuite) TestParse() {
	defaultGetTransaction := func() ITransaction {
		return hedera.NewTokenUpdateTransaction().
			SetAdminKey(adminKey).
			SetAutoRenewAccount(autoRenewAccount).
			SetAutoRenewPeriod(time.Second * time.Duration(autoRenewPeriod)).
			SetExpirationTime(expiry).
			SetFreezeKey(freezeKey).
			SetKycKey(kycKey).
			SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
			SetTokenMemo(memo).
			SetTokenName(name).
			SetTokenID(tokenIdA).
			SetSupplyKey(supplyKey).
			SetTransactionID(hedera.TransactionIDGenerate(payerId)).
			SetTokenSymbol(symbol).
			SetTreasuryAccountID(treasury).
			SetWipeKey(wipeKey)
	}

	var tests = []struct {
		name           string
		tokenRepoErr   bool
		getTransaction func() ITransaction
		expectError    bool
	}{
		{
			name:           "Success",
			getTransaction: defaultGetTransaction,
		},
		{
			name:           "TokenNotFound",
			tokenRepoErr:   true,
			getTransaction: defaultGetTransaction,
			expectError:    true,
		},
		{
			name: "InvalidTransaction",
			getTransaction: func() ITransaction {
				return hedera.NewTransferTransaction()
			},
			expectError: true,
		},
		{
			name: "TokenIDNotSet",
			getTransaction: func() ITransaction {
				return hedera.NewTokenUpdateTransaction().
					SetAdminKey(adminKey).
					SetAutoRenewAccount(autoRenewAccount).
					SetAutoRenewPeriod(time.Second * time.Duration(autoRenewPeriod)).
					SetExpirationTime(expiry).
					SetFreezeKey(freezeKey).
					SetKycKey(kycKey).
					SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
					SetTokenMemo(memo).
					SetTokenName(name).
					SetSupplyKey(supplyKey).
					SetTransactionID(hedera.TransactionIDGenerate(payerId)).
					SetTokenSymbol(symbol).
					SetTreasuryAccountID(treasury).
					SetWipeKey(wipeKey)
			},
			expectError: true,
		},
		{
			name: "TransactionIDNotSet",
			getTransaction: func() ITransaction {
				return hedera.NewTokenUpdateTransaction().
					SetAdminKey(adminKey).
					SetAutoRenewAccount(autoRenewAccount).
					SetAutoRenewPeriod(time.Second * time.Duration(autoRenewPeriod)).
					SetExpirationTime(expiry).
					SetFreezeKey(freezeKey).
					SetKycKey(kycKey).
					SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
					SetTokenMemo(memo).
					SetTokenName(name).
					SetTokenID(tokenIdA).
					SetSupplyKey(supplyKey).
					SetTokenSymbol(symbol).
					SetTreasuryAccountID(treasury).
					SetWipeKey(wipeKey)
			},
			expectError: true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			expectedOperations := getTokenUpdateOperations()

			mockTokenRepo := &repository.MockTokenRepository{}
			h := newTokenUpdateTransactionConstructor(mockTokenRepo)
			tx := tt.getTransaction()

			if tt.tokenRepoErr {
				configMockTokenRepo(mockTokenRepo, mockTokenRepoNotFoundConfigs[0])
			} else {
				configMockTokenRepo(mockTokenRepo, defaultMockTokenRepoConfigs[0])
			}

			// when
			operations, signers, err := h.Parse(tx)

			// then
			if tt.expectError {
				assert.NotNil(t, err)
				assert.Nil(t, operations)
				assert.Nil(t, signers)
			} else {
				assert.Nil(t, err)
				assert.ElementsMatch(t, []hedera.AccountID{payerId}, signers)
				assert.ElementsMatch(t, expectedOperations, operations)
				mockTokenRepo.AssertExpectations(t)
			}
		})
	}
}

func (suite *tokenUpdateTransactionConstructorSuite) TestPreprocess() {
	var tests = []struct {
		name             string
		tokenRepoErr     bool
		updateOperations updateOperationsFunc
		expectError      bool
	}{
		{
			name: "Success",
		},
		{
			name: "InvalidAccountAddress",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Account.Address = "x.y.z"
				return operations
			},
			expectError: true,
		},
		{
			name: "TokenDecimalsMismatch",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Amount.Currency.Decimals = 1990
				return operations
			},
			expectError: true,
		},
		{
			name:         "TokenNotFound",
			tokenRepoErr: true,
			expectError:  true,
		},
		{
			name: "InvalidMetadataAdminKey",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Metadata["admin_key"] = "admin key"
				return operations
			},
			expectError: true,
		},
		{
			name: "InvalidMetadataAutoRenewAccount",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Metadata["auto_renew_account"] = "x.y.z"
				return operations
			},
			expectError: true,
		},
		{
			name: "InvalidMetadataAutoRenewPeriod",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Metadata["auto_renew_period"] = "x"
				return operations
			},
			expectError: true,
		},
		{
			name: "InvalidMetadataExpiry",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Metadata["expiry"] = "x"
				return operations
			},
			expectError: true,
		},
		{
			name: "InvalidMetadataAutoFreezeKey",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Metadata["freeze_key"] = "freeze key"
				return operations
			},
			expectError: true,
		},
		{
			name: "InvalidMetadataAutoKycKey",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Metadata["kyc_key"] = "kyc key"
				return operations
			},
			expectError: true,
		},
		{
			name: "InvalidMetadataMemo",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Metadata["memo"] = 156
				return operations
			},
			expectError: true,
		},
		{
			name: "InvalidMetadataName",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Metadata["name"] = 156
				return operations
			},
			expectError: true,
		},
		{
			name: "InvalidMetadataSupplyKey",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Metadata["supply_key"] = "supply key"
				return operations
			},
			expectError: true,
		},
		{
			name: "InvalidMetadataSymbol",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Metadata["symbol"] = 156
				return operations
			},
			expectError: true,
		},
		{
			name: "InvalidMetadataTreasury",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Metadata["treasury"] = "x.y.z"
				return operations
			},
			expectError: true,
		},
		{
			name: "InvalidMetadataWipeKey",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Metadata["wipe_key"] = "wipe key"
				return operations
			},
			expectError: true,
		},
		{
			name: "MissingMetadata",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Metadata = nil
				return operations
			},
			expectError: true,
		},
		{
			name: "MultipleOperations",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				return append(operations, &rTypes.Operation{})
			},
			expectError: true,
		},
		{
			name: "InvalidOperationType",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Type = config.OperationTypeCryptoTransfer
				return operations
			},
			expectError: true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			operations := getTokenUpdateOperations()

			mockTokenRepo := &repository.MockTokenRepository{}
			h := newTokenUpdateTransactionConstructor(mockTokenRepo)

			if tt.tokenRepoErr {
				configMockTokenRepo(mockTokenRepo, mockTokenRepoNotFoundConfigs[0])
			} else {
				configMockTokenRepo(mockTokenRepo, defaultMockTokenRepoConfigs[0])
			}

			if tt.updateOperations != nil {
				operations = tt.updateOperations(operations)
			}

			// when
			signers, err := h.Preprocess(operations)

			// then
			if tt.expectError {
				assert.NotNil(t, err)
				assert.Nil(t, signers)
			} else {
				assert.Nil(t, err)
				assert.ElementsMatch(t, []hedera.AccountID{payerId}, signers)
				mockTokenRepo.AssertExpectations(t)
			}
		})
	}
}

func assertTokenUpdateTransaction(
	t *testing.T,
	operation *rTypes.Operation,
	nodeAccountId hedera.AccountID,
	actual ITransaction,
) {
	assert.IsType(t, &hedera.TokenUpdateTransaction{}, actual)

	tx, _ := actual.(*hedera.TokenUpdateTransaction)
	payer := tx.GetTransactionID().AccountID.String()
	token := tx.GetTokenID().String()

	assert.Equal(t, operation.Account.Address, payer)
	assert.Equal(t, operation.Amount.Currency.Symbol, token)
	assert.ElementsMatch(t, []hedera.AccountID{nodeAccountId}, actual.GetNodeAccountIDs())

	assert.Equal(t, operation.Metadata["admin_key"], tx.GetAdminKey().String())
	assert.Equal(t, operation.Metadata["auto_renew_account"], tx.GetAutoRenewAccount().String())
	assert.Equal(t, operation.Metadata["auto_renew_period"], int64(tx.GetAutoRenewPeriod().Seconds()))
	assert.Equal(t, operation.Metadata["expiry"], tx.GetExpirationTime().Unix())
	assert.Equal(t, operation.Metadata["freeze_key"], tx.GetFreezeKey().String())
	assert.Equal(t, operation.Metadata["kyc_key"], tx.GetKycKey().String())
	assert.Equal(t, operation.Metadata["memo"], tx.GeTokenMemo())
	assert.Equal(t, operation.Metadata["name"], tx.GetTokenName())
	assert.Equal(t, operation.Metadata["supply_key"], tx.GetSupplyKey().String())
	assert.Equal(t, operation.Metadata["symbol"], tx.GetTokenSymbol())
	assert.Equal(t, operation.Metadata["treasury"], tx.GetTreasuryAccountID().String())
	assert.Equal(t, operation.Metadata["wipe_key"], tx.GetWipeKey().String())
}

func getTokenUpdateOperations() []*rTypes.Operation {
	return []*rTypes.Operation{
		{
			OperationIdentifier: &rTypes.OperationIdentifier{Index: 0},
			Type:                config.OperationTypeTokenUpdate,
			Account:             &rTypes.AccountIdentifier{Address: payerId.String()},
			Amount: &rTypes.Amount{
				Value:    "0",
				Currency: dbTokenA.ToRosettaCurrency(),
			},
			Metadata: map[string]interface{}{
				"admin_key":          adminKeyStr,
				"auto_renew_account": autoRenewAccount.String(),
				"auto_renew_period":  autoRenewPeriod,
				"expiry":             expiry.Unix(),
				"freeze_key":         freezeKeyStr,
				"kyc_key":            kycKeyStr,
				"memo":               memo,
				"name":               name,
				"supply_key":         supplyKeyStr,
				"symbol":             symbol,
				"treasury":           treasury.String(),
				"wipe_key":           wipeKeyStr,
			},
		},
	}
}
