/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

const initialSupply uint64 = 20000

func TestTokenCreateTransactionConstructorSuite(t *testing.T) {
	suite.Run(t, new(tokenCreateTransactionConstructorSuite))
}

type tokenCreateTransactionConstructorSuite struct {
	suite.Suite
}

func (suite *tokenCreateTransactionConstructorSuite) TestNewTransactionConstructor() {
	h := newTokenCreateTransactionConstructor()
	assert.NotNil(suite.T(), h)
}

func (suite *tokenCreateTransactionConstructorSuite) TestGetDefaultMaxTransactionFee() {
	h := newTokenCreateTransactionConstructor()
	assert.Equal(suite.T(), types.HbarAmount{Value: 30_00000000}, h.GetDefaultMaxTransactionFee())
}

func (suite *tokenCreateTransactionConstructorSuite) TestGetOperationType() {
	h := newTokenCreateTransactionConstructor()
	assert.Equal(suite.T(), types.OperationTypeTokenCreate, h.GetOperationType())
}

func (suite *tokenCreateTransactionConstructorSuite) TestGetSdkTransactionType() {
	h := newTokenCreateTransactionConstructor()
	assert.Equal(suite.T(), "TokenCreateTransaction", h.GetSdkTransactionType())
}

func (suite *tokenCreateTransactionConstructorSuite) TestConstruct() {
	var tests = []struct {
		name             string
		tokenType        string
		updateOperations updateOperationsFunc
		expectError      bool
		expectedSigners  []types.AccountId
	}{
		{name: "SuccessFT"},
		{name: "SuccessFTExplicit", tokenType: domain.TokenTypeFungibleCommon},
		{name: "SuccessNFT", tokenType: domain.TokenTypeNonFungibleUnique},
		{
			name: "SuccessWithoutAutoRenewAccountAndExpiry",
			updateOperations: func(operations types.OperationSlice) types.OperationSlice {
				metadata := operations[0].Metadata
				delete(metadata, "auto_renew_account")
				delete(metadata, "expiry")
				return operations
			},
			expectedSigners: []types.AccountId{accountIdA},
		},
		{
			name:             "EmptyOperations",
			updateOperations: getEmptyOperations,
			expectError:      true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			operations := getTokenCreateOperations(tt.tokenType)
			h := newTokenCreateTransactionConstructor()

			if tt.updateOperations != nil {
				operations = tt.updateOperations(operations)
			}

			// when
			tx, signers, err := h.Construct(defaultContext, operations)

			// then
			if tt.expectError {
				assert.NotNil(t, err)
				assert.Nil(t, signers)
				assert.Nil(t, tx)
			} else {
				// the default
				expectedSigners := []types.AccountId{accountIdA, autoRenewAccountId}
				if tt.expectedSigners != nil {
					expectedSigners = tt.expectedSigners
				}

				assert.Nil(t, err)
				assert.ElementsMatch(t, expectedSigners, signers)
				assertTokenCreateTransaction(t, operations[0], tx)
			}
		})
	}
}

func (suite *tokenCreateTransactionConstructorSuite) TestParse() {
	defaultGetTransaction := func() interfaces.Transaction {
		return hedera.NewTokenCreateTransaction().
			SetAdminKey(adminKey).
			SetAutoRenewAccount(autoRenewAccountId.ToSdkAccountId()).
			SetAutoRenewPeriod(time.Second * time.Duration(autoRenewPeriod)).
			SetDecimals(decimals).
			SetExpirationTime(expiry).
			SetFreezeDefault(false).
			SetFreezeKey(freezeKey).
			SetInitialSupply(initialSupply).
			SetKycKey(kycKey).
			SetSupplyKey(supplyKey).
			SetSupplyType(hedera.TokenSupplyTypeFinite).
			SetTokenMemo(memo).
			SetTokenName(name).
			SetTokenSymbol(symbol).
			SetTokenType(hedera.TokenTypeNonFungibleUnique).
			SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdA)).
			SetTreasuryAccountID(sdkAccountIdA).
			SetWipeKey(wipeKey)
	}

	var tests = []struct {
		name           string
		getTransaction func() interfaces.Transaction
		expectError    bool
	}{
		{
			name:           "Success",
			getTransaction: defaultGetTransaction,
		},
		{
			name: "InvalidTransaction",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTransferTransaction()
			},
			expectError: true,
		},
		{
			name: "TokenNameNotSet",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTokenCreateTransaction().
					SetTokenSymbol(symbol).
					SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdA))
			},
			expectError: true,
		},
		{
			name: "TokenSymbolNotSet",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTokenCreateTransaction().
					SetTokenName(name).
					SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdA))
			},
			expectError: true,
		},
		{
			name: "TransactionIDNotSet",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTokenCreateTransaction().SetTokenName(name).SetTokenSymbol(symbol)
			},
			expectError: true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			expectedOperations := getTokenCreateOperations(domain.TokenTypeNonFungibleUnique)

			h := newTokenCreateTransactionConstructor()
			tx := tt.getTransaction()

			// when
			operations, signers, err := h.Parse(defaultContext, tx)

			// then
			if tt.expectError {
				assert.NotNil(t, err)
				assert.Nil(t, operations)
				assert.Nil(t, signers)
			} else {
				assert.Nil(t, err)
				assert.ElementsMatch(t, []types.AccountId{accountIdA, autoRenewAccountId}, signers)
				assert.ElementsMatch(t, expectedOperations, operations)
			}
		})
	}
}

func (suite *tokenCreateTransactionConstructorSuite) TestPreprocess() {
	var tests = []struct {
		name             string
		updateOperations updateOperationsFunc
		expectError      bool
	}{
		{name: "Success"},
		{
			name:             "SuccessFT",
			updateOperations: updateOperationMetadata(types.MetadataKeyType, domain.TokenTypeFungibleCommon),
		},
		{
			name:             "SuccessNFT",
			updateOperations: updateOperationMetadata(types.MetadataKeyType, domain.TokenTypeNonFungibleUnique),
		},
		{
			name:             "SuccessInfinite",
			updateOperations: updateOperationMetadata("supply_type", domain.TokenSupplyTypeInfinite),
		},
		{
			name:             "SuccessFinite",
			updateOperations: updateOperationMetadata("supply_type", domain.TokenSupplyTypeFinite),
		},
		{
			name:             "NonNilAmount",
			updateOperations: updateAmount(types.NewTokenAmount(dbTokenA, 1)),
			expectError:      true,
		},
		{
			name:             "MissingMetadataName",
			updateOperations: deleteOperationMetadata("name"),
			expectError:      true,
		},
		{
			name:             "MissingMetadataSymbol",
			updateOperations: deleteOperationMetadata("symbol"),
			expectError:      true,
		},
		{
			name:             "InvalidMetadataAdminKey",
			updateOperations: updateOperationMetadata("admin_key", "admin_key"),
			expectError:      true,
		},
		{
			name:             "InvalidMetadataAutoRenewAccount",
			updateOperations: updateOperationMetadata("auto_renew_account", "x.y.z"),
			expectError:      true,
		},
		{
			name:             "InvalidMetadataAutoRenewPeriod",
			updateOperations: updateOperationMetadata("auto_renew_period", "x"),
			expectError:      true,
		},
		{
			name:             "InvalidMetadataExpiry",
			updateOperations: updateOperationMetadata("expiry", "x"),
			expectError:      true,
		},
		{
			name:             "InvalidMetadataFreezeDefault",
			updateOperations: updateOperationMetadata("freeze_default", "xyz"),
			expectError:      true,
		},
		{
			name:             "InvalidMetadataFreezeKey",
			updateOperations: updateOperationMetadata("freeze_key", "freeze_key"),
			expectError:      true,
		},
		{
			name:             "InvalidMetadataKycKey",
			updateOperations: updateOperationMetadata("kyc_key", "kyc_key"),
			expectError:      true,
		},
		{
			name:             "InvalidMetadataMemo",
			updateOperations: updateOperationMetadata("memo", 156),
			expectError:      true,
		},
		{
			name:             "InvalidMetadataName",
			updateOperations: updateOperationMetadata("name", 156),
			expectError:      true,
		},
		{
			name:             "InvalidMetadataSupplyKey",
			updateOperations: updateOperationMetadata("supply_key", "supply_key"),
			expectError:      true,
		},
		{
			name:             "InvalidMetadataSymbol",
			updateOperations: updateOperationMetadata("symbol", 156),
			expectError:      true,
		},
		{
			name:             "InvalidMetadataSupplyType",
			updateOperations: updateOperationMetadata("supply_type", "unknown"),
			expectError:      true,
		},
		{
			name:             "InvalidTokenType",
			updateOperations: updateOperationMetadata(types.MetadataKeyType, "unknown"),
			expectError:      true,
		},
		{
			name:             "InvalidMetadataWipeKey",
			updateOperations: updateOperationMetadata("wipe_key", "wipe_key"),
			expectError:      true,
		},
		{
			name:             "MissingMetadata",
			updateOperations: getEmptyOperationMetadata,
			expectError:      true,
		},
		{
			name:             "MultipleOperations",
			updateOperations: addOperation,
			expectError:      true,
		},
		{
			name:             "InvalidOperationType",
			updateOperations: updateOperationType(types.OperationTypeCryptoTransfer),
			expectError:      true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			operations := getTokenCreateOperations("")
			h := newTokenCreateTransactionConstructor()

			if tt.updateOperations != nil {
				operations = tt.updateOperations(operations)
			}

			// when
			signers, err := h.Preprocess(defaultContext, operations)

			// then
			if tt.expectError {
				assert.NotNil(t, err)
				assert.Nil(t, signers)
			} else {
				assert.Nil(t, err)
				assert.ElementsMatch(t, []types.AccountId{accountIdA, autoRenewAccountId}, signers)
			}
		})
	}
}

func assertTokenCreateTransaction(t *testing.T, operation types.Operation, actual interfaces.Transaction) {
	assert.False(t, actual.IsFrozen())
	assert.IsType(t, &hedera.TokenCreateTransaction{}, actual)

	tx, _ := actual.(*hedera.TokenCreateTransaction)
	metadata := operation.Metadata
	expectedAutoRenewAccount := metadata["auto_renew_account"]
	expectedAutoRenewPeriod := 0.0
	if expectedAutoRenewAccount == nil && metadata["expiry"] == nil {
		expectedAutoRenewAccount = operation.AccountId.ToSdkAccountId().String()
		expectedAutoRenewPeriod = hedera.NewTokenCreateTransaction().GetAutoRenewPeriod().Seconds()
	}

	var expectedExpiry time.Time
	if metadata["expiry"] != nil {
		expectedExpiry = time.Unix(metadata["expiry"].(int64), 0)
	} else {
		expectedExpiry = time.Time{}
	}

	expectedSupplyType := hedera.TokenSupplyTypeInfinite
	if metadata["supply_type"] != nil && metadata["supply_type"].(string) == domain.TokenSupplyTypeFinite {
		expectedSupplyType = hedera.TokenSupplyTypeFinite
	}

	assert.Equal(t, metadata["admin_key"], tx.GetAdminKey().String())
	assert.Equal(t, expectedAutoRenewAccount, tx.GetAutoRenewAccount().String())
	assert.Equal(t, expectedAutoRenewPeriod, tx.GetAutoRenewPeriod().Seconds())
	assert.Equal(t, metadata["decimals"], tx.GetDecimals())
	assert.Equal(t, expectedExpiry, tx.GetExpirationTime())
	assert.Equal(t, metadata["freeze_default"], tx.GetFreezeDefault())
	assert.Equal(t, metadata["freeze_key"], tx.GetFreezeKey().String())
	assert.Equal(t, metadata["initial_supply"], tx.GetInitialSupply())
	assert.Equal(t, metadata["kyc_key"], tx.GetKycKey().String())
	assert.Equal(t, metadata["memo"], tx.GetTokenMemo())
	assert.Equal(t, metadata["name"], tx.GetTokenName())
	assert.Equal(t, metadata["supply_key"], tx.GetSupplyKey().String())
	assert.Equal(t, expectedSupplyType, tx.GetSupplyType())
	assert.Equal(t, metadata["symbol"], tx.GetTokenSymbol())
	assert.Equal(t, metadata["wipe_key"], tx.GetWipeKey().String())

	tokenType, _ := metadata[types.MetadataKeyType].(string)
	sdkTokenType := hedera.TokenTypeNonFungibleUnique
	if tokenType == "" || tokenType == domain.TokenTypeFungibleCommon {
		sdkTokenType = hedera.TokenTypeFungibleCommon
	}

	assert.Equal(t, sdkTokenType, tx.GetTokenType())
}

func getTokenCreateOperations(tokenType string) types.OperationSlice {
	operation := types.Operation{
		AccountId: accountIdA,
		Type:      types.OperationTypeTokenCreate,
		Metadata: map[string]interface{}{
			"admin_key":          adminKeyStr,
			"auto_renew_account": autoRenewAccountId.String(),
			"decimals":           uint(decimals),
			"expiry":             expiry.Unix(),
			"freeze_default":     false,
			"freeze_key":         freezeKeyStr,
			"initial_supply":     initialSupply,
			"kyc_key":            kycKeyStr,
			"memo":               memo,
			"name":               name,
			"supply_key":         supplyKeyStr,
			"supply_type":        domain.TokenSupplyTypeFinite,
			"symbol":             symbol,
			"wipe_key":           wipeKeyStr,
		},
	}
	if tokenType != "" {
		operation.Metadata[types.MetadataKeyType] = tokenType
	}

	return types.OperationSlice{operation}
}
