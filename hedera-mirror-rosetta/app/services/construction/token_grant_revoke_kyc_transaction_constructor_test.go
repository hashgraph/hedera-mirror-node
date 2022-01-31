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
				operations := suite.getOperations(operationType)
				h := newHandler()

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
					assert.ElementsMatch(t, []hedera.AccountID{payerId}, signers)
					assertTokenGrantRevokeKycTransaction(t, operations, nodeAccountId, tx)

					if tt.validStartNanos != 0 {
						assert.Equal(t, tt.validStartNanos, tx.GetTransactionID().ValidStart.UnixNano())
					}
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
		SetAccountID(accountId).
		SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
		SetTokenID(tokenIdA).
		SetTransactionID(hedera.TransactionIDGenerate(payerId))
	tokenRevokeKycTransaction := hedera.NewTokenRevokeKycTransaction().
		SetAccountID(accountId).
		SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
		SetTokenID(tokenIdA).
		SetTransactionID(hedera.TransactionIDGenerate(payerId))
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
						SetAccountID(accountId).
						SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
						SetTokenID(outOfRangeTokenId).
						SetTransactionID(hedera.TransactionIDGenerate(payerId))
				}

				return hedera.NewTokenRevokeKycTransaction().
					SetAccountID(accountId).
					SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
					SetTokenID(outOfRangeTokenId).
					SetTransactionID(hedera.TransactionIDGenerate(payerId))
			},
			expectError: true,
		},
		{
			name:           "InvalidTransaction",
			getTransaction: getTransferTransaction,
			expectError:    true,
		},
		{
			name: "TransactionAccountIDNotSet",
			getTransaction: func(operationType string) interfaces.Transaction {
				if operationType == types.OperationTypeTokenGrantKyc {
					return hedera.NewTokenGrantKycTransaction().
						SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
						SetTokenID(tokenIdA).
						SetTransactionID(hedera.TransactionIDGenerate(payerId))
				}

				return hedera.NewTokenRevokeKycTransaction().
					SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
					SetTokenID(tokenIdA).
					SetTransactionID(hedera.TransactionIDGenerate(payerId))
			},
			expectError: true,
		},
		{
			name: "TransactionTokenIDNotSet",
			getTransaction: func(operationType string) interfaces.Transaction {
				if operationType == types.OperationTypeTokenGrantKyc {
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
				if operationType == types.OperationTypeTokenGrantKyc {
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
					assert.ElementsMatch(t, []hedera.AccountID{payerId}, signers)
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
			name:             "InvalidAccountAddress",
			updateOperations: updateOperationAccount("x.y.z"),
			expectError:      true,
		},
		{
			name:             "InvalidToken",
			updateOperations: updateCurrency(currencyHbar),
			expectError:      true,
		},
		{
			name:             "NegativeAmountValue",
			updateOperations: updateAmountValue("-100"),
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
					assert.ElementsMatch(t, []hedera.AccountID{payerId}, signers)
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

func (suite *tokenGrantRevokeKycTransactionConstructorSuite) getOperations(operationType string) []*rTypes.Operation {
	return []*rTypes.Operation{
		{
			OperationIdentifier: &rTypes.OperationIdentifier{Index: 0},
			Type:                operationType,
			Account:             &rTypes.AccountIdentifier{Address: accountId.String()},
			Amount:              &rTypes.Amount{Value: "0", Currency: tokenAPartialCurrency},
			Metadata:            map[string]interface{}{"payer": payerId.String()},
		},
	}
}

func assertTokenGrantRevokeKycTransaction(
	t *testing.T,
	operations []*rTypes.Operation,
	nodeAccountId hedera.AccountID,
	actual interfaces.Transaction,
) {
	assert.True(t, actual.IsFrozen())
	if operations[0].Type == types.OperationTypeTokenGrantKyc {
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

	assert.Equal(t, operations[0].Metadata["payer"], payer)
	assert.Equal(t, operations[0].Account.Address, account)
	assert.Equal(t, operations[0].Amount.Currency.Symbol, token)
	assert.ElementsMatch(t, []hedera.AccountID{nodeAccountId}, actual.GetNodeAccountIDs())
}
