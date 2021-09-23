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

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks"
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
	h := newTokenFreezeTransactionConstructor(&mocks.MockTokenRepository{})
	assert.NotNil(suite.T(), h)
}

func (suite *tokenFreezeUnfreezeTransactionConstructorSuite) TestNewTokenUnfreezeTransactionConstructor() {
	h := newTokenUnfreezeTransactionConstructor(&mocks.MockTokenRepository{})
	assert.NotNil(suite.T(), h)
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
			expected:   config.OperationTypeTokenFreeze,
		},
		{
			name:       "TokenUnfreezeTransactionConstructor",
			newHandler: newTokenUnfreezeTransactionConstructor,
			expected:   config.OperationTypeTokenUnfreeze,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			h := tt.newHandler(&mocks.MockTokenRepository{})
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
			h := tt.newHandler(&mocks.MockTokenRepository{})
			assert.Equal(t, tt.expected, h.GetSdkTransactionType())
		})
	}
}

func (suite *tokenFreezeUnfreezeTransactionConstructorSuite) TestConstruct() {
	var tests = []struct {
		name             string
		updateOperations updateOperationsFunc
		validStartNanos  int64
		expectError      bool
	}{
		{name: "Success"},
		{name: "SuccessValidStartNanos", validStartNanos: 100},
		{name: "EmptyOperations", updateOperations: getEmptyOperations, expectError: true},
	}

	runTests := func(t *testing.T, operationType string, newHandler newConstructorFunc) {
		for _, tt := range tests {
			t.Run(tt.name, func(t *testing.T) {
				// given
				operations := getFreezeUnfreezeOperations(operationType)
				mockTokenRepo := &mocks.MockTokenRepository{}
				h := newHandler(mockTokenRepo)
				configMockTokenRepo(mockTokenRepo, defaultMockTokenRepoConfigs[0])

				if tt.updateOperations != nil {
					operations = tt.updateOperations(operations)
				}

				// when
				tx, signers, err := h.Construct(nodeAccountId, operations, tt.validStartNanos)

				// then
				if tt.expectError {
					assert.NotNil(t, err)
					assert.Nil(t, signers)
					assert.Nil(t, tx)
				} else {
					assert.Nil(t, err)
					assert.ElementsMatch(t, []hedera.AccountID{payerId}, signers)
					assertTokenFreezeUnfreezeTransaction(t, operations[0], nodeAccountId, tx)
					mockTokenRepo.AssertExpectations(t)

					if tt.validStartNanos != 0 {
						assert.Equal(t, tt.validStartNanos, tx.GetTransactionID().ValidStart.UnixNano())
					}
				}
			})
		}
	}

	suite.T().Run("TokenFreezeTransactionConstructor", func(t *testing.T) {
		runTests(t, config.OperationTypeTokenFreeze, newTokenFreezeTransactionConstructor)
	})

	suite.T().Run("TokenUnfreezeTransactionConstructor", func(t *testing.T) {
		runTests(t, config.OperationTypeTokenUnfreeze, newTokenUnfreezeTransactionConstructor)
	})
}

func (suite *tokenFreezeUnfreezeTransactionConstructorSuite) TestParse() {
	tokenFreezeTransaction := hedera.NewTokenFreezeTransaction().
		SetAccountID(accountId).
		SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
		SetTokenID(tokenIdA).
		SetTransactionID(hedera.TransactionIDGenerate(payerId))
	tokenUnfreezeTransaction := hedera.NewTokenUnfreezeTransaction().
		SetAccountID(accountId).
		SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
		SetTokenID(tokenIdA).
		SetTransactionID(hedera.TransactionIDGenerate(payerId))

	defaultGetTransaction := func(operationType string) interfaces.Transaction {
		if operationType == config.OperationTypeTokenFreeze {
			return tokenFreezeTransaction
		}
		return tokenUnfreezeTransaction
	}

	var tests = []struct {
		name           string
		tokenRepoErr   bool
		getTransaction func(operationType string) interfaces.Transaction
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
			getTransaction: func(operationType string) interfaces.Transaction {
				return hedera.NewTransferTransaction()
			},
			expectError: true,
		},
		{
			name: "TransactionMismatch",
			getTransaction: func(operationType string) interfaces.Transaction {
				if operationType == config.OperationTypeTokenFreeze {
					return tokenUnfreezeTransaction
				}
				return tokenFreezeTransaction
			},
			expectError: true,
		},
	}

	runTests := func(t *testing.T, operationType string, newHandler newConstructorFunc) {
		for _, tt := range tests {
			t.Run(tt.name, func(t *testing.T) {
				// given
				expectedOperations := getFreezeUnfreezeOperations(operationType)

				mockTokenRepo := &mocks.MockTokenRepository{}
				h := newHandler(mockTokenRepo)
				tx := tt.getTransaction(operationType)

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

	suite.T().Run("TokenFreezeTransactionConstructor", func(t *testing.T) {
		runTests(t, config.OperationTypeTokenFreeze, newTokenFreezeTransactionConstructor)
	})

	suite.T().Run("TokenUnfreezeTransactionConstructor", func(t *testing.T) {
		runTests(t, config.OperationTypeTokenUnfreeze, newTokenUnfreezeTransactionConstructor)
	})
}

func (suite *tokenFreezeUnfreezeTransactionConstructorSuite) TestPreprocess() {
	var tests = []struct {
		name             string
		tokenRepoErr     bool
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
			name:             "InvalidOperationMetadata",
			updateOperations: updateOperationMetadata("payer", "x.y.z"),
			expectError:      true,
		},
		{
			name:             "InvalidAccountAddress",
			updateOperations: updateOperationAccount("x.y.z"),
			expectError:      true,
		},
		{
			name:             "ZeroAccountId",
			updateOperations: updateOperationAccount("0.0.0"),
			expectError:      true,
		},

		{
			name:             "TokenDecimalsMismatch",
			updateOperations: updateTokenDecimals(1990),
			expectError:      true,
		},
		{
			name:         "TokenNotFound",
			tokenRepoErr: true,
			expectError:  true,
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

	runTests := func(t *testing.T, operationType string, newHandler newConstructorFunc) {
		for _, tt := range tests {
			t.Run(tt.name, func(t *testing.T) {
				// given
				operations := getFreezeUnfreezeOperations(operationType)

				mockTokenRepo := &mocks.MockTokenRepository{}
				h := newHandler(mockTokenRepo)

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

	suite.T().Run("TokenFreezeTransactionConstructor", func(t *testing.T) {
		runTests(t, config.OperationTypeTokenFreeze, newTokenFreezeTransactionConstructor)
	})

	suite.T().Run("TokenUnfreezeTransactionConstructor", func(t *testing.T) {
		runTests(t, config.OperationTypeTokenUnfreeze, newTokenUnfreezeTransactionConstructor)
	})
}

func assertTokenFreezeUnfreezeTransaction(
	t *testing.T,
	operation *rTypes.Operation,
	nodeAccountId hedera.AccountID,
	actual interfaces.Transaction,
) {
	if operation.Type == config.OperationTypeTokenFreeze {
		assert.IsType(t, &hedera.TokenFreezeTransaction{}, actual)
	} else {
		assert.IsType(t, &hedera.TokenUnfreezeTransaction{}, actual)
	}

	var account string
	var payer string
	var token string

	switch tx := actual.(type) {
	case *hedera.TokenFreezeTransaction:
		account = tx.GetAccountID().String()
		payer = tx.GetTransactionID().AccountID.String()
		token = tx.GetTokenID().String()
	case *hedera.TokenUnfreezeTransaction:
		account = tx.GetAccountID().String()
		payer = tx.GetTransactionID().AccountID.String()
		token = tx.GetTokenID().String()
	}

	assert.Equal(t, operation.Metadata["payer"], payer)
	assert.Equal(t, operation.Account.Address, account)
	assert.Equal(t, operation.Amount.Currency.Symbol, token)
	assert.ElementsMatch(t, []hedera.AccountID{nodeAccountId}, actual.GetNodeAccountIDs())
}

func getFreezeUnfreezeOperations(operationType string) []*rTypes.Operation {
	return []*rTypes.Operation{
		{
			OperationIdentifier: &rTypes.OperationIdentifier{Index: 0},
			Type:                operationType,
			Account:             &rTypes.AccountIdentifier{Address: accountId.String()},
			Amount:              &rTypes.Amount{Value: "0", Currency: tokenACurrency},
			Metadata:            map[string]interface{}{"payer": payerId.String()},
		},
	}
}
