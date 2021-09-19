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
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

func TestTokenGrantRevokeKycTransactionConstructorSuite(t *testing.T) {
	suite.Run(t, new(tokenGrantRevokeKycTransactionConstructorSuite))
}

type tokenGrantRevokeKycTransactionConstructorSuite struct {
	suite.Suite
}

func (suite *tokenGrantRevokeKycTransactionConstructorSuite) TestNewTokenGrantKycTransactionConstructor() {
	h := newTokenGrantKycTransactionConstructor(&mocks.MockTokenRepository{})
	assert.NotNil(suite.T(), h)
}

func (suite *tokenGrantRevokeKycTransactionConstructorSuite) TestNewTokenRevokeKycTransactionConstructor() {
	h := newTokenRevokeKycTransactionConstructor(&mocks.MockTokenRepository{})
	assert.NotNil(suite.T(), h)
}

func (suite *tokenGrantRevokeKycTransactionConstructorSuite) TestGetOperationType() {
	var tests = []struct {
		name       string
		newHandler newConstructorFunc
		expected   string
	}{
		{
			name:       "TokenGrantKycTransactionConstructor",
			newHandler: newTokenGrantKycTransactionConstructor,
			expected:   config.OperationTypeTokenGrantKyc,
		},
		{
			name:       "TokenRevokeKycTransactionConstructor",
			newHandler: newTokenRevokeKycTransactionConstructor,
			expected:   config.OperationTypeTokenRevokeKyc,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			h := tt.newHandler(&mocks.MockTokenRepository{})
			assert.Equal(t, tt.expected, h.GetOperationType())
		})
	}
}

func (suite *tokenGrantRevokeKycTransactionConstructorSuite) TestGetSdkTransactionType() {
	var tests = []struct {
		name       string
		newHandler newConstructorFunc
		expected   string
	}{
		{
			name:       "TokenGrantKycTransactionConstructor",
			newHandler: newTokenGrantKycTransactionConstructor,
			expected:   "TokenGrantKycTransaction",
		},
		{
			name:       "TokenRevokeKycTransactionConstructor",
			newHandler: newTokenRevokeKycTransactionConstructor,
			expected:   "TokenRevokeKycTransaction",
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			h := tt.newHandler(&mocks.MockTokenRepository{})
			assert.Equal(t, tt.expected, h.GetSdkTransactionType())
		})
	}
}

func (suite *tokenGrantRevokeKycTransactionConstructorSuite) TestConstruct() {
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

	runTests := func(t *testing.T, operationType string, newHandler newConstructorFunc) {
		for _, tt := range tests {
			t.Run(tt.name, func(t *testing.T) {
				// given
				operations := suite.getOperations(operationType)
				mockTokenRepo := &mocks.MockTokenRepository{}
				h := newHandler(mockTokenRepo)

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
					assertTokenGrantRevokeKycTransaction(t, operations, nodeAccountId, tx)
					mockTokenRepo.AssertExpectations(t)
				}
			})
		}
	}

	suite.T().Run("TokenGrantKycTransactionConstructor", func(t *testing.T) {
		runTests(t, config.OperationTypeTokenGrantKyc, newTokenGrantKycTransactionConstructor)
	})

	suite.T().Run("TokenDissociateTransactionConstructor", func(t *testing.T) {
		runTests(t, config.OperationTypeTokenRevokeKyc, newTokenRevokeKycTransactionConstructor)
	})
}

func (suite *tokenGrantRevokeKycTransactionConstructorSuite) TestParse() {
	defaultGetTransaction := func(operationType string) interfaces.Transaction {
		if operationType == config.OperationTypeTokenGrantKyc {
			return hedera.NewTokenGrantKycTransaction().
				SetAccountID(accountId).
				SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
				SetTokenID(tokenIdA).
				SetTransactionID(hedera.TransactionIDGenerate(payerId))
		}

		return hedera.NewTokenRevokeKycTransaction().
			SetAccountID(accountId).
			SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
			SetTokenID(tokenIdA).
			SetTransactionID(hedera.TransactionIDGenerate(payerId))
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
				if operationType == config.OperationTypeTokenGrantKyc {
					return hedera.NewTokenRevokeKycTransaction()
				}

				return hedera.NewTokenGrantKycTransaction()

			},
			expectError: true,
		},
		{
			name: "TransactionAccountIDNotSet",
			getTransaction: func(operationType string) interfaces.Transaction {
				if operationType == config.OperationTypeTokenGrantKyc {
					return hedera.NewTokenGrantKycTransaction().
						// SetAccountID(hedera.AccountID{}).
						SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
						SetTokenID(tokenIdA).
						SetTransactionID(hedera.TransactionIDGenerate(payerId))
				}

				return hedera.NewTokenRevokeKycTransaction().
					// SetAccountID(hedera.AccountID{}).
					SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
					SetTokenID(tokenIdA).
					SetTransactionID(hedera.TransactionIDGenerate(payerId))
			},
			expectError: true,
		},
		{
			name: "TransactionTokenIDNotSet",
			getTransaction: func(operationType string) interfaces.Transaction {
				if operationType == config.OperationTypeTokenGrantKyc {
					return hedera.NewTokenGrantKycTransaction().
						SetAccountID(accountId).
						SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
						SetTransactionID(hedera.TransactionIDGenerate(payerId))
				}

				return hedera.NewTokenRevokeKycTransaction().
					SetAccountID(accountId).
					SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
					SetTransactionID(hedera.TransactionIDGenerate(payerId))
			},
			expectError: true,
		},
		{
			name: "TransactionTransactionIDNotSet",
			getTransaction: func(operationType string) interfaces.Transaction {
				if operationType == config.OperationTypeTokenGrantKyc {
					return hedera.NewTokenGrantKycTransaction().
						SetAccountID(accountId).
						SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
						SetTokenID(tokenIdA)
				}

				return hedera.NewTokenRevokeKycTransaction().
					SetAccountID(accountId).
					SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
					SetTokenID(tokenIdA)
			},
			expectError: true,
		},
	}

	runTests := func(t *testing.T, operationType string, newHandler newConstructorFunc) {
		for _, tt := range tests {
			t.Run(tt.name, func(t *testing.T) {
				// given
				expectedOperations := suite.getOperations(operationType)

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

	suite.T().Run("TokenGrantKycTransactionConstructor", func(t *testing.T) {
		runTests(t, config.OperationTypeTokenGrantKyc, newTokenGrantKycTransactionConstructor)
	})

	suite.T().Run("TokenDissociateTransactionConstructor", func(t *testing.T) {
		runTests(t, config.OperationTypeTokenRevokeKyc, newTokenRevokeKycTransactionConstructor)
	})
}

func (suite *tokenGrantRevokeKycTransactionConstructorSuite) TestPreprocess() {
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
			name: "InvalidAccountMetadata",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Metadata["account"] = "x.y.z"
				return operations
			},
			expectError: true,
		},
		{
			name: "ZeroAccountMetadata",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Metadata["account"] = "0.0.0"
				return operations
			},
			expectError: true,
		},
		{
			name: "MissingAccountMetadata",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Metadata = nil
				return operations
			},
			expectError: true,
		},
		{
			name: "InvalidPayerAddress",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Account.Address = "x.y.z"
				return operations
			},
			expectError: true,
		},
		{
			name: "TokenDecimalsMismatch",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Amount.Currency.Decimals = 1900
				return operations
			},
			expectError: true,
		},
		{
			name: "NegativeAmountValue",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Amount.Value = "-100"
				return operations
			},
			expectError: true,
		},
		{
			name: "MissingAmount",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Amount = nil
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
			name: "InvalidOperationType",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Type = config.OperationTypeCryptoTransfer
				return operations
			},
			expectError: true,
		},
	}

	runTests := func(t *testing.T, operationType string, newHandler newConstructorFunc) {
		for _, tt := range tests {
			t.Run(tt.name, func(t *testing.T) {
				// given
				operations := suite.getOperations(operationType)

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

	suite.T().Run("TokenGrantKycTransactionConstructor", func(t *testing.T) {
		runTests(t, config.OperationTypeTokenGrantKyc, newTokenGrantKycTransactionConstructor)
	})

	suite.T().Run("TokenDissociateTransactionConstructor", func(t *testing.T) {
		runTests(t, config.OperationTypeTokenRevokeKyc, newTokenRevokeKycTransactionConstructor)
	})
}

func (suite *tokenGrantRevokeKycTransactionConstructorSuite) getOperations(operationType string) []*rTypes.Operation {
	return []*rTypes.Operation{
		{
			OperationIdentifier: &rTypes.OperationIdentifier{Index: 0},
			Type:                operationType,
			Account:             &rTypes.AccountIdentifier{Address: payerId.String()},
			Amount: &rTypes.Amount{
				Value:    "0",
				Currency: types.Token{Token: dbTokenA}.ToRosettaCurrency(),
			},
			Metadata: map[string]interface{}{
				"account": accountId.String(),
			},
		},
	}
}

func assertTokenGrantRevokeKycTransaction(
	t *testing.T,
	operations []*rTypes.Operation,
	nodeAccountId hedera.AccountID,
	actual interfaces.Transaction,
) {
	if operations[0].Type == config.OperationTypeTokenGrantKyc {
		assert.IsType(t, &hedera.TokenGrantKycTransaction{}, actual)
	} else {
		assert.IsType(t, &hedera.TokenRevokeKycTransaction{}, actual)
	}

	var account string
	var payer string
	var token string

	switch tx := actual.(type) {
	case *hedera.TokenGrantKycTransaction:
		account = tx.GetAccountID().String()
		payer = tx.GetTransactionID().AccountID.String()
		token = tx.GetTokenID().String()
	case *hedera.TokenRevokeKycTransaction:
		account = tx.GetAccountID().String()
		payer = tx.GetTransactionID().AccountID.String()
		token = tx.GetTokenID().String()
	}

	assert.Equal(t, operations[0].Metadata["account"], account)
	assert.Equal(t, operations[0].Account.Address, payer)
	assert.Equal(t, operations[0].Amount.Currency.Symbol, token)
	assert.ElementsMatch(t, []hedera.AccountID{nodeAccountId}, actual.GetNodeAccountIDs())
}
