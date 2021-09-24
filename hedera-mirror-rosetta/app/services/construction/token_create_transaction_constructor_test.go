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
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
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

func (suite *tokenCreateTransactionConstructorSuite) TestGetOperationType() {
	h := newTokenCreateTransactionConstructor()
	assert.Equal(suite.T(), config.OperationTypeTokenCreate, h.GetOperationType())
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
		validStartNanos  int64
		expectError      bool
	}{
		{name: "SuccessFT"},
		{name: "SuccessFTExplicit", tokenType: domain.TokenTypeFungibleCommon},
		{name: "SuccessNFT", tokenType: domain.TokenTypeNonFungibleUnique},
		{name: "SuccessValidStartNanos", validStartNanos: 100},
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
			operations := getTokenCreateOperations(tt.tokenType)
			h := newTokenCreateTransactionConstructor()

			if tt.updateOperations != nil {
				operations = tt.updateOperations(operations)
			}

			// when
			tx, signers, err := h.Construct(defaultContext, nodeAccountId, operations, tt.validStartNanos)

			// then
			if tt.expectError {
				assert.NotNil(t, err)
				assert.Nil(t, signers)
				assert.Nil(t, tx)
			} else {
				assert.Nil(t, err)
				assert.ElementsMatch(t, []hedera.AccountID{treasury, autoRenewAccount}, signers)
				assertTokenCreateTransaction(t, operations[0], nodeAccountId, tx)

				if tt.validStartNanos != 0 {
					assert.Equal(t, tt.validStartNanos, tx.GetTransactionID().ValidStart.UnixNano())
				}
			}
		})
	}
}

func (suite *tokenCreateTransactionConstructorSuite) TestParse() {
	defaultGetTransaction := func() interfaces.Transaction {
		return hedera.NewTokenCreateTransaction().
			SetAdminKey(adminKey).
			SetAutoRenewAccount(autoRenewAccount).
			SetAutoRenewPeriod(time.Second * time.Duration(autoRenewPeriod)).
			SetDecimals(decimals).
			SetExpirationTime(expiry).
			SetFreezeDefault(false).
			SetFreezeKey(freezeKey).
			SetInitialSupply(initialSupply).
			SetKycKey(kycKey).
			SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
			SetSupplyKey(supplyKey).
			SetTokenMemo(memo).
			SetTokenName(name).
			SetTokenSymbol(symbol).
			SetTransactionID(hedera.TransactionIDGenerate(treasury)).
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
					SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
					SetTokenSymbol(symbol).
					SetTransactionID(hedera.TransactionIDGenerate(treasury))
			},
			expectError: true,
		},
		{
			name: "TokenSymboolNotSet",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTokenCreateTransaction().
					SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
					SetTokenName(name).
					SetTransactionID(hedera.TransactionIDGenerate(treasury))
			},
			expectError: true,
		},
		{
			name: "TransactionIDNotSet",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTokenCreateTransaction().
					SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
					SetTokenName(name).
					SetTokenSymbol(symbol)
			},
			expectError: true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			expectedOperations := getTokenCreateOperations("")

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
				assert.ElementsMatch(t, []hedera.AccountID{treasury, autoRenewAccount}, signers)
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
			name:             "InvalidAccountAddress",
			updateOperations: updateOperationAccount("x.y.z"),
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
			updateOperations: updateOperationType(config.OperationTypeCryptoTransfer),
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
				assert.ElementsMatch(t, []hedera.AccountID{treasury, autoRenewAccount}, signers)
			}
		})
	}
}

func assertTokenCreateTransaction(
	t *testing.T,
	operation *rTypes.Operation,
	nodeAccountId hedera.AccountID,
	actual interfaces.Transaction,
) {
	assert.IsType(t, &hedera.TokenCreateTransaction{}, actual)

	tx, _ := actual.(*hedera.TokenCreateTransaction)
	payer := tx.GetTransactionID().AccountID.String()

	assert.Equal(t, operation.Account.Address, payer)
	assert.ElementsMatch(t, []hedera.AccountID{nodeAccountId}, actual.GetNodeAccountIDs())

	assert.Equal(t, operation.Metadata["admin_key"], tx.GetAdminKey().String())
	assert.Equal(t, operation.Metadata["auto_renew_account"], tx.GetAutoRenewAccount().String())
	assert.Equal(t, 0.0, tx.GetAutoRenewPeriod().Seconds())
	assert.Equal(t, operation.Metadata["decimals"], tx.GetDecimals())
	assert.Equal(t, operation.Metadata["expiry"], tx.GetExpirationTime().Unix())
	assert.Equal(t, operation.Metadata["freeze_default"], tx.GetFreezeDefault())
	assert.Equal(t, operation.Metadata["freeze_key"], tx.GetFreezeKey().String())
	assert.Equal(t, operation.Metadata["initial_supply"], tx.GetInitialSupply())
	assert.Equal(t, operation.Metadata["kyc_key"], tx.GetKycKey().String())
	assert.Equal(t, operation.Metadata["memo"], tx.GetTokenMemo())
	assert.Equal(t, operation.Metadata["name"], tx.GetTokenName())
	assert.Equal(t, operation.Metadata["supply_key"], tx.GetSupplyKey().String())
	assert.Equal(t, operation.Metadata["symbol"], tx.GetTokenSymbol())
	assert.Equal(t, operation.Metadata["wipe_key"], tx.GetWipeKey().String())

	tokenType, _ := operation.Metadata[types.MetadataKeyType].(string)
	sdkTokenType := hedera.TokenTypeNonFungibleUnique
	if tokenType == "" || tokenType == domain.TokenTypeFungibleCommon {
		sdkTokenType = hedera.TokenTypeFungibleCommon
	}

	assert.Equal(t, sdkTokenType, tx.GetTokenType())
}

func getTokenCreateOperations(tokenType string) []*rTypes.Operation {
	operation := &rTypes.Operation{
		OperationIdentifier: &rTypes.OperationIdentifier{Index: 0},
		Type:                config.OperationTypeTokenCreate,
		Account:             &rTypes.AccountIdentifier{Address: treasury.String()},
		Metadata: map[string]interface{}{
			"admin_key":          adminKeyStr,
			"auto_renew_account": autoRenewAccount.String(),
			"decimals":           uint(decimals),
			"expiry":             expiry.Unix(),
			"freeze_default":     false,
			"freeze_key":         freezeKeyStr,
			"initial_supply":     initialSupply,
			"kyc_key":            kycKeyStr,
			"memo":               memo,
			"name":               name,
			"supply_key":         supplyKeyStr,
			"symbol":             symbol,
			"wipe_key":           wipeKeyStr,
		},
	}
	if tokenType != "" {
		operation.Metadata[types.MetadataKeyType] = tokenType
	}

	return []*rTypes.Operation{operation}
}
