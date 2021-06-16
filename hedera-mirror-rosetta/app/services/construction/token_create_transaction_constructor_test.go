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
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

const (
	initialSupply uint64 = 20000
)

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
			operations := getTokenCreateOperations()
			h := newTokenCreateTransactionConstructor()

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
				assert.ElementsMatch(t, []hedera.AccountID{treasury, autoRenewAccount}, signers)
				assertTokenCreateTransaction(t, operations[0], nodeAccountId, tx)
			}
		})
	}
}

func (suite *tokenCreateTransactionConstructorSuite) TestParse() {
	defaultGetTransaction := func() ITransaction {
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
		getTransaction func() ITransaction
		expectError    bool
	}{
		{
			name:           "Success",
			getTransaction: defaultGetTransaction,
		},
		{
			name: "InvalidTransaction",
			getTransaction: func() ITransaction {
				return hedera.NewTransferTransaction()
			},
			expectError: true,
		},
		{
			name: "TokenNameNotSet",
			getTransaction: func() ITransaction {
				return hedera.NewTokenCreateTransaction().
					SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
					SetTokenSymbol(symbol).
					SetTransactionID(hedera.TransactionIDGenerate(treasury))
			},
			expectError: true,
		},
		{
			name: "TokenSymboolNotSet",
			getTransaction: func() ITransaction {
				return hedera.NewTokenCreateTransaction().
					SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
					SetTokenName(name).
					SetTransactionID(hedera.TransactionIDGenerate(treasury))
			},
			expectError: true,
		},
		{
			name: "TransactionIDNotSet",
			getTransaction: func() ITransaction {
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
			expectedOperations := getTokenCreateOperations()

			h := newTokenCreateTransactionConstructor()
			tx := tt.getTransaction()

			// when
			operations, signers, err := h.Parse(tx)

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
		{
			name: "Success",
		},
		{
			name: "MissingMetadataName",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				delete(operations[0].Metadata, "name")
				return operations
			},
			expectError: true,
		},
		{
			name: "MissingMetadataSymbol",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				delete(operations[0].Metadata, "symbol")
				return operations
			},
			expectError: true,
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
			name: "InvalidMetadataFreezeDefault",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Metadata["freeze_default"] = "xyz"
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
			operations := getTokenCreateOperations()
			h := newTokenCreateTransactionConstructor()

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
				assert.ElementsMatch(t, []hedera.AccountID{treasury, autoRenewAccount}, signers)
			}
		})
	}
}

func assertTokenCreateTransaction(
	t *testing.T,
	operation *rTypes.Operation,
	nodeAccountId hedera.AccountID,
	actual ITransaction,
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
}

func getTokenCreateOperations() []*rTypes.Operation {
	return []*rTypes.Operation{
		{
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
		},
	}
}
