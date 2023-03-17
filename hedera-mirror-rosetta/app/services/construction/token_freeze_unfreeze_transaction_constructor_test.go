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

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

func TestTokenFreezeUnfreezeTransactionConstructorSuite(t *testing.T) {
	suite.Run(t, new(tokenFreezeUnfreezeTransactionConstructorSuite))
}

type tokenFreezeUnfreezeTransactionConstructorSuite struct {
	suite.Suite
}

func (suite *tokenFreezeUnfreezeTransactionConstructorSuite) TestNewTokenFreezeTransactionConstructor() {
	h := newTokenFreezeTransactionConstructor()
	assert.NotNil(suite.T(), h)
}

func (suite *tokenFreezeUnfreezeTransactionConstructorSuite) TestNewTokenUnfreezeTransactionConstructor() {
	h := newTokenUnfreezeTransactionConstructor()
	assert.NotNil(suite.T(), h)
}

func (suite *tokenFreezeUnfreezeTransactionConstructorSuite) TestGetDefaultMaxTransactionFee() {
	tests := []struct {
		name                   string
		transactionConstructor transactionConstructorWithType
		expected               types.HbarAmount
	}{
		{
			name:                   "tokenFreeze",
			transactionConstructor: newTokenFreezeTransactionConstructor(),
			expected:               types.HbarAmount{Value: 30_00000000},
		},
		{
			name:                   "tokenUnfreeze",
			transactionConstructor: newTokenUnfreezeTransactionConstructor(),
			expected:               types.HbarAmount{Value: 30_00000000},
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, tt.transactionConstructor.GetDefaultMaxTransactionFee())
		})
	}
}

func (suite *tokenFreezeUnfreezeTransactionConstructorSuite) TestGetOperationType() {
	tests := []struct {
		name       string
		newHandler newConstructorFunc
		expected   string
	}{
		{
			name:       "TokenFreezeTransactionConstructor",
			newHandler: newTokenFreezeTransactionConstructor,
			expected:   types.OperationTypeTokenFreeze,
		},
		{
			name:       "TokenUnfreezeTransactionConstructor",
			newHandler: newTokenUnfreezeTransactionConstructor,
			expected:   types.OperationTypeTokenUnfreeze,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			h := tt.newHandler()
			assert.Equal(t, tt.expected, h.GetOperationType())
		})
	}
}

func (suite *tokenFreezeUnfreezeTransactionConstructorSuite) TestGetSdkTransactionType() {
	tests := []struct {
		name       string
		newHandler newConstructorFunc
		expected   string
	}{
		{
			name:       "TokenFreezeTransactionConstructor",
			newHandler: newTokenFreezeTransactionConstructor,
			expected:   "TokenFreezeTransaction",
		},
		{
			name:       "TokenUnfreezeTransactionConstructor",
			newHandler: newTokenUnfreezeTransactionConstructor,
			expected:   "TokenUnfreezeTransaction",
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			h := tt.newHandler()
			assert.Equal(t, tt.expected, h.GetSdkTransactionType())
		})
	}
}

func (suite *tokenFreezeUnfreezeTransactionConstructorSuite) TestConstruct() {
	var tests = []struct {
		name             string
		updateOperations updateOperationsFunc
		expectError      bool
	}{
		{name: "Success"},
		{name: "EmptyOperations", updateOperations: getEmptyOperations, expectError: true},
	}

	runTests := func(t *testing.T, operationType string, newHandler newConstructorFunc) {
		for _, tt := range tests {
			t.Run(tt.name, func(t *testing.T) {
				// given
				operations := getFreezeUnfreezeOperations(operationType)
				h := newHandler()

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
					assert.ElementsMatch(t, []types.AccountId{accountIdB}, signers)
					assertTokenFreezeUnfreezeTransaction(t, operations[0], tx)
				}
			})
		}
	}

	suite.T().Run("TokenFreezeTransactionConstructor", func(t *testing.T) {
		runTests(t, types.OperationTypeTokenFreeze, newTokenFreezeTransactionConstructor)
	})

	suite.T().Run("TokenUnfreezeTransactionConstructor", func(t *testing.T) {
		runTests(t, types.OperationTypeTokenUnfreeze, newTokenUnfreezeTransactionConstructor)
	})
}

func (suite *tokenFreezeUnfreezeTransactionConstructorSuite) TestParse() {
	tokenFreezeTransaction := hedera.NewTokenFreezeTransaction().
		SetAccountID(sdkAccountIdA).
		SetTokenID(tokenIdA).
		SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdB))
	tokenUnfreezeTransaction := hedera.NewTokenUnfreezeTransaction().
		SetAccountID(sdkAccountIdA).
		SetTokenID(tokenIdA).
		SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdB))

	defaultGetTransaction := func(operationType string) interfaces.Transaction {
		if operationType == types.OperationTypeTokenFreeze {
			return tokenFreezeTransaction
		}
		return tokenUnfreezeTransaction
	}

	var tests = []struct {
		name           string
		getTransaction func(operationType string) interfaces.Transaction
		expectError    bool
	}{
		{name: "Success", getTransaction: defaultGetTransaction},
		{
			name: "InvalidTokenId",
			getTransaction: func(operationType string) interfaces.Transaction {
				if operationType == types.OperationTypeTokenFreeze {
					return hedera.NewTokenFreezeTransaction().
						SetAccountID(sdkAccountIdA).
						SetTokenID(outOfRangeTokenId).
						SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdB))
				}
				return hedera.NewTokenUnfreezeTransaction().
					SetAccountID(sdkAccountIdA).
					SetTokenID(outOfRangeTokenId).
					SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdB))
			},
			expectError: true,
		},
		{
			name: "InvalidTransaction",
			getTransaction: func(operationType string) interfaces.Transaction {
				return hedera.NewTransferTransaction()
			},
			expectError: true,
		},
		{
			name: "OutOfRangeAccountId",
			getTransaction: func(operationType string) interfaces.Transaction {
				if operationType == types.OperationTypeTokenFreeze {
					return hedera.NewTokenFreezeTransaction().
						SetAccountID(outOfRangeAccountId).
						SetTokenID(tokenIdA).
						SetTransactionID(hedera.TransactionIDGenerate(outOfRangeAccountId))
				}
				return hedera.NewTokenUnfreezeTransaction().
					SetAccountID(outOfRangeAccountId).
					SetTokenID(tokenIdA).
					SetTransactionID(hedera.TransactionIDGenerate(outOfRangeAccountId))
			},
			expectError: true,
		},
		{
			name: "TransactionMismatch",
			getTransaction: func(operationType string) interfaces.Transaction {
				if operationType == types.OperationTypeTokenFreeze {
					return tokenUnfreezeTransaction
				}
				return tokenFreezeTransaction
			},
			expectError: true,
		},
		{
			name: "TransactionIDNotSet",
			getTransaction: func(operationType string) interfaces.Transaction {
				if operationType == types.OperationTypeTokenFreeze {
					return hedera.NewTokenFreezeTransaction().
						SetAccountID(sdkAccountIdA).
						SetTokenID(outOfRangeTokenId)
				}
				return hedera.NewTokenUnfreezeTransaction().
					SetAccountID(sdkAccountIdA).
					SetTokenID(outOfRangeTokenId)
			},
			expectError: true,
		},
	}

	runTests := func(t *testing.T, operationType string, newHandler newConstructorFunc) {
		for _, tt := range tests {
			t.Run(tt.name, func(t *testing.T) {
				// given
				expectedOperations := getFreezeUnfreezeOperations(operationType)
				h := newHandler()
				tx := tt.getTransaction(operationType)

				// when
				operations, signers, err := h.Parse(defaultContext, tx)

				// then
				if tt.expectError {
					assert.NotNil(t, err)
					assert.Nil(t, operations)
					assert.Nil(t, signers)
				} else {
					assert.Nil(t, err)
					assert.ElementsMatch(t, []types.AccountId{accountIdB}, signers)
					assert.ElementsMatch(t, expectedOperations, operations)
				}
			})
		}
	}

	suite.T().Run("TokenFreezeTransactionConstructor", func(t *testing.T) {
		runTests(t, types.OperationTypeTokenFreeze, newTokenFreezeTransactionConstructor)
	})

	suite.T().Run("TokenUnfreezeTransactionConstructor", func(t *testing.T) {
		runTests(t, types.OperationTypeTokenUnfreeze, newTokenUnfreezeTransactionConstructor)
	})
}

func (suite *tokenFreezeUnfreezeTransactionConstructorSuite) TestPreprocess() {
	var tests = []struct {
		name             string
		updateOperations updateOperationsFunc
		expectError      bool
	}{
		{name: "Success"},
		{
			name:             "NoOperationMetadata",
			updateOperations: getEmptyOperationMetadata,
			expectError:      true,
		},
		{
			name:             "ZeroPayerMetadata",
			updateOperations: updateOperationMetadata("payer", "0.0.0"),
			expectError:      true,
		},
		{
			name:             "InvalidPayerMetadata",
			updateOperations: updateOperationMetadata("payer", "x.y.z"),
			expectError:      true,
		},
		{
			name:             "MissingPayerMetadata",
			updateOperations: deleteOperationMetadata("payer"),
			expectError:      true,
		},
		{
			name:             "OutOfRangePayerMetadata",
			updateOperations: updateOperationMetadata("payer", "0.65536.4294967296"),
			expectError:      true,
		},
		{
			name:             "MultipleOperations",
			updateOperations: addOperation,
			expectError:      true,
		},
		{
			name:             "InvalidAmount",
			updateOperations: updateAmount(&types.HbarAmount{}),
			expectError:      true,
		},
		{
			name:             "NegativeAmountValue",
			updateOperations: updateAmountValue(-100),
			expectError:      true,
		},
		{
			name:             "MissingAmount",
			updateOperations: updateAmount(nil),
			expectError:      true,
		},
		{
			name:             "InvalidOperationType",
			updateOperations: updateOperationType(types.OperationTypeCryptoTransfer),
			expectError:      true,
		},
	}

	runTests := func(t *testing.T, operationType string, newHandler newConstructorFunc) {
		for _, tt := range tests {
			t.Run(tt.name, func(t *testing.T) {
				// given
				operations := getFreezeUnfreezeOperations(operationType)
				h := newHandler()

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
					assert.ElementsMatch(t, []types.AccountId{accountIdB}, signers)
				}
			})
		}
	}

	suite.T().Run("TokenFreezeTransactionConstructor", func(t *testing.T) {
		runTests(t, types.OperationTypeTokenFreeze, newTokenFreezeTransactionConstructor)
	})

	suite.T().Run("TokenUnfreezeTransactionConstructor", func(t *testing.T) {
		runTests(t, types.OperationTypeTokenUnfreeze, newTokenUnfreezeTransactionConstructor)
	})
}

func assertTokenFreezeUnfreezeTransaction(t *testing.T, operation types.Operation, actual interfaces.Transaction) {
	assert.False(t, actual.IsFrozen())
	if operation.Type == types.OperationTypeTokenFreeze {
		assert.IsType(t, &hedera.TokenFreezeTransaction{}, actual)
	} else {
		assert.IsType(t, &hedera.TokenUnfreezeTransaction{}, actual)
	}

	var account string
	var token string
	switch tx := actual.(type) {
	case *hedera.TokenFreezeTransaction:
		account = tx.GetAccountID().String()
		token = tx.GetTokenID().String()
	case *hedera.TokenUnfreezeTransaction:
		account = tx.GetAccountID().String()
		token = tx.GetTokenID().String()
	}

	assert.Equal(t, operation.AccountId.ToSdkAccountId().String(), account)
	assert.Equal(t, operation.Amount.GetSymbol(), token)
}

func getFreezeUnfreezeOperations(operationType string) types.OperationSlice {
	return types.OperationSlice{
		{
			AccountId: accountIdA,
			Amount:    types.NewTokenAmount(getPartialDbToken(dbTokenA), 0),
			Metadata:  map[string]interface{}{"payer": accountIdB.String()},
			Type:      operationType,
		},
	}
}
