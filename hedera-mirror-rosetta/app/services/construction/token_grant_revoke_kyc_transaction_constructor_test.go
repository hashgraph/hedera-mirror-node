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
	"testing"

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
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
	h := newTokenGrantKycTransactionConstructor()
	assert.NotNil(suite.T(), h)
}

func (suite *tokenGrantRevokeKycTransactionConstructorSuite) TestNewTokenRevokeKycTransactionConstructor() {
	h := newTokenRevokeKycTransactionConstructor()
	assert.NotNil(suite.T(), h)
}

func (suite *tokenGrantRevokeKycTransactionConstructorSuite) TestGetDefaultMaxTransactionFee() {
	tests := []struct {
		name                   string
		transactionConstructor transactionConstructorWithType
		expected               types.HbarAmount
	}{
		{
			name:                   "tokenGrantKyc",
			transactionConstructor: newTokenGrantKycTransactionConstructor(),
			expected:               types.HbarAmount{Value: 30_00000000},
		},
		{
			name:                   "tokenRevokeKyc",
			transactionConstructor: newTokenRevokeKycTransactionConstructor(),
			expected:               types.HbarAmount{Value: 30_00000000},
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, tt.transactionConstructor.GetDefaultMaxTransactionFee())
		})
	}
}

func (suite *tokenGrantRevokeKycTransactionConstructorSuite) TestGetOperationType() {
	tests := []struct {
		name       string
		newHandler newConstructorFunc
		expected   string
	}{
		{
			name:       "TokenGrantKycTransactionConstructor",
			newHandler: newTokenGrantKycTransactionConstructor,
			expected:   types.OperationTypeTokenGrantKyc,
		},
		{
			name:       "TokenRevokeKycTransactionConstructor",
			newHandler: newTokenRevokeKycTransactionConstructor,
			expected:   types.OperationTypeTokenRevokeKyc,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			h := tt.newHandler()
			assert.Equal(t, tt.expected, h.GetOperationType())
		})
	}
}

func (suite *tokenGrantRevokeKycTransactionConstructorSuite) TestGetSdkTransactionType() {
	tests := []struct {
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
			h := tt.newHandler()
			assert.Equal(t, tt.expected, h.GetSdkTransactionType())
		})
	}
}

func (suite *tokenGrantRevokeKycTransactionConstructorSuite) TestConstruct() {
	tests := []struct {
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
				operations := suite.getOperations(operationType)
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
					assertTokenGrantRevokeKycTransaction(t, operations, tx)
				}
			})
		}
	}

	suite.T().Run("TokenGrantKycTransactionConstructor", func(t *testing.T) {
		runTests(t, types.OperationTypeTokenGrantKyc, newTokenGrantKycTransactionConstructor)
	})

	suite.T().Run("TokenDissociateTransactionConstructor", func(t *testing.T) {
		runTests(t, types.OperationTypeTokenRevokeKyc, newTokenRevokeKycTransactionConstructor)
	})
}

func (suite *tokenGrantRevokeKycTransactionConstructorSuite) TestParse() {
	tokenGrantKycTransaction := hedera.NewTokenGrantKycTransaction().
		SetAccountID(sdkAccountIdA).
		SetTokenID(tokenIdA).
		SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdB))
	tokenRevokeKycTransaction := hedera.NewTokenRevokeKycTransaction().
		SetAccountID(sdkAccountIdA).
		SetTokenID(tokenIdA).
		SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdB))
	defaultGetTransaction := func(operationType string) interfaces.Transaction {
		if operationType == types.OperationTypeTokenGrantKyc {
			return tokenGrantKycTransaction
		}
		return tokenRevokeKycTransaction
	}

	tests := []struct {
		name           string
		getTransaction func(operationType string) interfaces.Transaction
		expectError    bool
	}{
		{
			name:           "Success",
			getTransaction: defaultGetTransaction,
		},
		{
			name: "InvalidTokenId",
			getTransaction: func(operationType string) interfaces.Transaction {
				if operationType == types.OperationTypeTokenGrantKyc {
					return hedera.NewTokenGrantKycTransaction().
						SetAccountID(sdkAccountIdA).
						SetTokenID(outOfRangeTokenId).
						SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdB))
				}
				return hedera.NewTokenRevokeKycTransaction().
					SetAccountID(sdkAccountIdA).
					SetTokenID(outOfRangeTokenId).
					SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdB))
			},
			expectError: true,
		},
		{
			name: "OutOfRangeAccountId",
			getTransaction: func(operationType string) interfaces.Transaction {
				if operationType == types.OperationTypeTokenGrantKyc {
					return hedera.NewTokenGrantKycTransaction().
						SetAccountID(outOfRangeAccountId).
						SetTokenID(tokenIdA).
						SetTransactionID(hedera.TransactionIDGenerate(outOfRangeAccountId))
				}
				return hedera.NewTokenRevokeKycTransaction().
					SetAccountID(outOfRangeAccountId).
					SetTokenID(tokenIdA).
					SetTransactionID(hedera.TransactionIDGenerate(outOfRangeAccountId))
			},
			expectError: true,
		},
		{
			name:           "InvalidTransaction",
			getTransaction: getTransferTransaction,
			expectError:    true,
		},
		{
			name: "TransactionTokenIDNotSet",
			getTransaction: func(operationType string) interfaces.Transaction {
				if operationType == types.OperationTypeTokenGrantKyc {
					return hedera.NewTokenGrantKycTransaction().
						SetAccountID(sdkAccountIdA).
						SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdB))
				}
				return hedera.NewTokenRevokeKycTransaction().
					SetAccountID(sdkAccountIdA).
					SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdB))
			},
			expectError: true,
		},
		{
			name: "TransactionMismatch",
			getTransaction: func(operationType string) interfaces.Transaction {
				if operationType == types.OperationTypeTokenGrantKyc {
					return tokenRevokeKycTransaction
				}
				return tokenGrantKycTransaction
			},
			expectError: true,
		},
		{
			name: "TransactionIDNotSet",
			getTransaction: func(operationType string) interfaces.Transaction {
				if operationType == types.OperationTypeTokenGrantKyc {
					return hedera.NewTokenGrantKycTransaction().
						SetAccountID(sdkAccountIdA).
						SetTokenID(outOfRangeTokenId)
				}
				return hedera.NewTokenRevokeKycTransaction().
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
				expectedOperations := suite.getOperations(operationType)
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

	suite.T().Run("TokenGrantKycTransactionConstructor", func(t *testing.T) {
		runTests(t, types.OperationTypeTokenGrantKyc, newTokenGrantKycTransactionConstructor)
	})

	suite.T().Run("TokenDissociateTransactionConstructor", func(t *testing.T) {
		runTests(t, types.OperationTypeTokenRevokeKyc, newTokenRevokeKycTransactionConstructor)
	})
}

func (suite *tokenGrantRevokeKycTransactionConstructorSuite) TestPreprocess() {
	tests := []struct {
		name             string
		updateOperations updateOperationsFunc
		expectError      bool
	}{
		{name: "Success"},
		{
			name:             "InvalidPayerMetadata",
			updateOperations: updateOperationMetadata("payer", "x.y.z"),
			expectError:      true,
		},
		{
			name:             "ZeroPayerMetadata",
			updateOperations: updateOperationMetadata("payer", "0.0.0"),
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
				operations := suite.getOperations(operationType)
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

	suite.T().Run("TokenGrantKycTransactionConstructor", func(t *testing.T) {
		runTests(t, types.OperationTypeTokenGrantKyc, newTokenGrantKycTransactionConstructor)
	})

	suite.T().Run("TokenDissociateTransactionConstructor", func(t *testing.T) {
		runTests(t, types.OperationTypeTokenRevokeKyc, newTokenRevokeKycTransactionConstructor)
	})
}

func (suite *tokenGrantRevokeKycTransactionConstructorSuite) getOperations(operationType string) types.OperationSlice {
	return types.OperationSlice{
		{
			AccountId: accountIdA,
			Amount:    types.NewTokenAmount(getPartialDbToken(dbTokenA), 0),
			Metadata:  map[string]interface{}{"payer": accountIdB.String()},
			Type:      operationType,
		},
	}
}

func assertTokenGrantRevokeKycTransaction(
	t *testing.T,
	operations types.OperationSlice,
	actual interfaces.Transaction,
) {
	assert.False(t, actual.IsFrozen())
	if operations[0].Type == types.OperationTypeTokenGrantKyc {
		assert.IsType(t, &hedera.TokenGrantKycTransaction{}, actual)
	} else {
		assert.IsType(t, &hedera.TokenRevokeKycTransaction{}, actual)
	}

	var account string
	var token string
	switch tx := actual.(type) {
	case *hedera.TokenGrantKycTransaction:
		account = tx.GetAccountID().String()
		token = tx.GetTokenID().String()
	case *hedera.TokenRevokeKycTransaction:
		account = tx.GetAccountID().String()
		token = tx.GetTokenID().String()
	}

	assert.Equal(t, operations[0].AccountId.ToSdkAccountId().String(), account)
	assert.Equal(t, operations[0].Amount.GetSymbol(), token)
}
