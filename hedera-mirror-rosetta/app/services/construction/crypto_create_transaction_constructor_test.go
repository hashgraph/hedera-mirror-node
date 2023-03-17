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
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/domain"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

const (
	initialBalance                       = 10
	maxAutomaticTokenAssociations uint32 = 10
)

var (
	_, newAccountPublicKey = domain.GenEd25519KeyPair()
	proxyAccountId         = hedera.AccountID{Account: 6000}
)

func TestCryptoCreateTransactionConstructorSuite(t *testing.T) {
	suite.Run(t, new(cryptoCreateTransactionConstructorSuite))
}

type cryptoCreateTransactionConstructorSuite struct {
	suite.Suite
}

func (suite *cryptoCreateTransactionConstructorSuite) TestNewTransactionConstructor() {
	h := newCryptoCreateTransactionConstructor()
	assert.NotNil(suite.T(), h)
}

func (suite *cryptoCreateTransactionConstructorSuite) TestGetDefaultMaxTransactionFee() {
	h := newCryptoCreateTransactionConstructor()
	assert.Equal(suite.T(), types.HbarAmount{Value: 3_00000000}, h.GetDefaultMaxTransactionFee())
}

func (suite *cryptoCreateTransactionConstructorSuite) TestGetOperationType() {
	h := newCryptoCreateTransactionConstructor()
	assert.Equal(suite.T(), types.OperationTypeCryptoCreateAccount, h.GetOperationType())
}

func (suite *cryptoCreateTransactionConstructorSuite) TestGetSdkTransactionType() {
	h := newCryptoCreateTransactionConstructor()
	assert.Equal(suite.T(), "AccountCreateTransaction", h.GetSdkTransactionType())
}

func (suite *cryptoCreateTransactionConstructorSuite) TestConstruct() {
	var tests = []struct {
		name             string
		updateOperations updateOperationsFunc
		expectError      bool
	}{
		{name: "Success"},
		{name: "EmptyOperations", updateOperations: getEmptyOperations, expectError: true},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			operations := getCryptoCreateOperations()
			h := newCryptoCreateTransactionConstructor()

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
				assert.Nil(t, err)
				assert.ElementsMatch(t, []types.AccountId{accountIdA}, signers)
				assertCryptoCreateTransaction(t, operations[0], tx)
			}
		})
	}
}

func (suite *cryptoCreateTransactionConstructorSuite) TestParse() {
	defaultGetTransaction := func() interfaces.Transaction {
		return hedera.NewAccountCreateTransaction().
			SetAccountMemo(memo).
			SetAutoRenewPeriod(time.Second * time.Duration(autoRenewPeriod)).
			SetInitialBalance(hedera.HbarFromTinybar(initialBalance)).
			SetKey(newAccountPublicKey).
			SetMaxAutomaticTokenAssociations(maxAutomaticTokenAssociations).
			SetProxyAccountID(proxyAccountId).
			SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdA))
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
			name: "KeyNotSet",
			getTransaction: func() interfaces.Transaction {
				tx := defaultGetTransaction()
				accountCreateTx, _ := tx.(*hedera.AccountCreateTransaction)
				accountCreateTx.SetKey(nil)
				return tx
			},
			expectError: true,
		},
		{
			name: "OutOfRangePayerAccountId",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewAccountCreateTransaction().
					SetAccountMemo(memo).
					SetAutoRenewPeriod(time.Second * time.Duration(autoRenewPeriod)).
					SetInitialBalance(hedera.HbarFromTinybar(initialBalance)).
					SetKey(newAccountPublicKey).
					SetMaxAutomaticTokenAssociations(maxAutomaticTokenAssociations).
					SetProxyAccountID(proxyAccountId).
					SetTransactionID(hedera.TransactionIDGenerate(outOfRangeAccountId))
			},
			expectError: true,
		},
		{
			name: "TransactionIDNotSet",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewAccountCreateTransaction().
					SetAccountMemo(memo).
					SetAutoRenewPeriod(time.Second * time.Duration(autoRenewPeriod)).
					SetInitialBalance(hedera.HbarFromTinybar(initialBalance)).
					SetKey(newAccountPublicKey).
					SetMaxAutomaticTokenAssociations(maxAutomaticTokenAssociations).
					SetProxyAccountID(proxyAccountId)
			},
			expectError: true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			expectedOperations := getCryptoCreateOperations()

			h := newCryptoCreateTransactionConstructor()
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
				assert.ElementsMatch(t, []types.AccountId{accountIdA}, signers)
				assert.ElementsMatch(t, expectedOperations, operations)
			}
		})
	}
}

func (suite *cryptoCreateTransactionConstructorSuite) TestPreprocess() {
	var tests = []struct {
		name             string
		updateOperations updateOperationsFunc
		expectError      bool
	}{
		{name: "Success"},
		{
			name:             "InvalidAmount",
			updateOperations: updateAmount(types.NewTokenAmount(dbTokenA, 1)),
			expectError:      true,
		},
		{
			name:             "InvalidMetadataKey",
			updateOperations: updateOperationMetadata("key", "key"),
			expectError:      true,
		},
		{
			name:             "InvalidMetadataAutoRenewPeriod",
			updateOperations: updateOperationMetadata("auto_renew_period", "x"),
			expectError:      true,
		},
		{
			name:             "InvalidMetadataMaxAutomaticTokenAssociations",
			updateOperations: updateOperationMetadata("max_automatic_token_associations", "xyz"),
			expectError:      true,
		},
		{
			name:             "InvalidMetadataMemo",
			updateOperations: updateOperationMetadata("memo", 156),
			expectError:      true,
		},
		{
			name:             "InvalidMetadataProxyAccountId",
			updateOperations: updateOperationMetadata("proxy_account_id", "x.y.z"),
			expectError:      true,
		},
		{
			name:             "MissingMetadata",
			updateOperations: getEmptyOperationMetadata,
			expectError:      true,
		},
		{
			name:             "MissingMetadataKey",
			updateOperations: deleteOperationMetadata("key"),
			expectError:      true,
		},
		{
			name:             "MultipleOperations",
			updateOperations: addOperation,
			expectError:      true,
		},
		{
			name:             "NegativeInitialBalance",
			updateOperations: negateAmountValue,
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
			operations := getCryptoCreateOperations()
			h := newCryptoCreateTransactionConstructor()

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
				assert.ElementsMatch(t, []types.AccountId{accountIdA}, signers)
			}
		})
	}
}

func assertCryptoCreateTransaction(
	t *testing.T,
	operation types.Operation,
	actual interfaces.Transaction,
) {
	assert.IsType(t, &hedera.AccountCreateTransaction{}, actual)
	assert.False(t, actual.IsFrozen())

	tx, _ := actual.(*hedera.AccountCreateTransaction)
	key, err := tx.GetKey()
	assert.NoError(t, err)
	assert.Equal(t, operation.Metadata["key"], key.String())
	assert.Equal(t, operation.Metadata["auto_renew_period"], int64(tx.GetAutoRenewPeriod().Seconds()))
	assert.Equal(t, operation.Metadata["max_automatic_token_associations"], tx.GetMaxAutomaticTokenAssociations())
	assert.Equal(t, operation.Metadata["memo"], tx.GetAccountMemo())
	assert.Equal(t, operation.Metadata["proxy_account_id"], tx.GetProxyAccountID().String())
}

func getCryptoCreateOperations() types.OperationSlice {
	operation := types.Operation{
		AccountId: accountIdA,
		Amount:    &types.HbarAmount{Value: -initialBalance},
		Type:      types.OperationTypeCryptoCreateAccount,
		Metadata: map[string]interface{}{
			"auto_renew_period":                autoRenewPeriod,
			"key":                              newAccountPublicKey.String(),
			"max_automatic_token_associations": maxAutomaticTokenAssociations,
			"memo":                             memo,
			"proxy_account_id":                 proxyAccountId.String(),
		},
	}

	return types.OperationSlice{operation}
}
