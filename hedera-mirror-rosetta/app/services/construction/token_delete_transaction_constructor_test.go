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
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

func TestTokenDeleteTransactionConstructorSuite(t *testing.T) {
	suite.Run(t, new(tokenDeleteTransactionConstructorSuite))
}

type tokenDeleteTransactionConstructorSuite struct {
	suite.Suite
}

func (suite *tokenDeleteTransactionConstructorSuite) TestNewTransactionConstructor() {
	h := newTokenDeleteTransactionConstructor(&mocks.MockTokenRepository{})
	assert.NotNil(suite.T(), h)
}

func (suite *tokenDeleteTransactionConstructorSuite) TestGetOperationType() {
	h := newTokenDeleteTransactionConstructor(&mocks.MockTokenRepository{})
	assert.Equal(suite.T(), types.OperationTypeTokenDelete, h.GetOperationType())
}

func (suite *tokenDeleteTransactionConstructorSuite) TestGetSdkTransactionType() {
	h := newTokenDeleteTransactionConstructor(&mocks.MockTokenRepository{})
	assert.Equal(suite.T(), "TokenDeleteTransaction", h.GetSdkTransactionType())
}

func (suite *tokenDeleteTransactionConstructorSuite) TestConstruct() {
	var tests = []struct {
		name                 string
		currency             *rTypes.Currency
		mockTokenRepoConfigs []mockTokenRepoConfig
		updateOperations     updateOperationsFunc
		validStartNanos      int64
		expectError          bool
	}{
		{name: "SuccessFT", currency: tokenACurrency, mockTokenRepoConfigs: defaultMockTokenRepoConfigs[0:1]},
		{name: "SuccessNFT", currency: tokenCCurrency, mockTokenRepoConfigs: defaultMockTokenRepoConfigs[2:]},
		{
			name:                 "SuccessValidStartNanos",
			currency:             tokenACurrency,
			mockTokenRepoConfigs: defaultMockTokenRepoConfigs[0:1],
			validStartNanos:      100,
		},
		{name: "EmptyOperations", currency: tokenACurrency, updateOperations: getEmptyOperations, expectError: true},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			operations := getTokenDeleteOperations(tt.currency)
			mockTokenRepo := &mocks.MockTokenRepository{}
			h := newTokenDeleteTransactionConstructor(mockTokenRepo)
			if len(tt.mockTokenRepoConfigs) != 0 {
				configMockTokenRepo(mockTokenRepo, tt.mockTokenRepoConfigs...)
			}

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
				assertTokenDeleteTransaction(t, operations[0], nodeAccountId, tx)
				mockTokenRepo.AssertExpectations(t)

				if tt.validStartNanos != 0 {
					assert.Equal(t, tt.validStartNanos, tx.GetTransactionID().ValidStart.UnixNano())
				}
			}
		})
	}
}

func (suite *tokenDeleteTransactionConstructorSuite) TestParse() {
	defaultGetTransaction := func() interfaces.Transaction {
		return hedera.NewTokenDeleteTransaction().
			SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
			SetTransactionID(hedera.TransactionIDGenerate(payerId)).
			SetTokenID(tokenIdA)
	}

	tests := []struct {
		name           string
		tokenRepoErr   bool
		getTransaction func() interfaces.Transaction
		expectError    bool
	}{
		{name: "Success", getTransaction: defaultGetTransaction},
		{
			name:           "TokenNotFound",
			tokenRepoErr:   true,
			getTransaction: defaultGetTransaction,
			expectError:    true,
		},
		{
			name: "InvalidTransaction",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTransferTransaction()
			},
			expectError: true,
		},
		{
			name: "TransactionIDNotSet",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTokenDeleteTransaction().
					SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
					SetTokenID(tokenIdA)
			},
			expectError: true,
		},
		{
			name: "TokenIDNotSet",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTokenDeleteTransaction().
					SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
					SetTransactionID(hedera.TransactionIDGenerate(payerId))
			},
			expectError: true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			expectedOperations := getTokenDeleteOperations(tokenACurrency)

			mockTokenRepo := &mocks.MockTokenRepository{}
			h := newTokenDeleteTransactionConstructor(mockTokenRepo)
			tx := tt.getTransaction()

			if tt.tokenRepoErr {
				configMockTokenRepo(mockTokenRepo, mockTokenRepoNotFoundConfigs[0])
			} else {
				configMockTokenRepo(mockTokenRepo, defaultMockTokenRepoConfigs[0])
			}

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
				mockTokenRepo.AssertExpectations(t)
			}
		})
	}
}

func (suite *tokenDeleteTransactionConstructorSuite) TestPreprocess() {
	var tests = []struct {
		name             string
		tokenRepoErr     bool
		updateOperations updateOperationsFunc
		expectError      bool
	}{
		{name: "Success"},
		{
			name:             "InvalidAccountAddress",
			updateOperations: updateOperationAccount("x.y.z"),
			expectError:      true,
		},
		{
			name:             "InvalidAmountValue",
			updateOperations: updateAmountValue("10"),
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
			updateOperations: updateOperationType(types.OperationTypeCryptoTransfer),
			expectError:      true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			operations := getTokenDeleteOperations(tokenACurrency)

			mockTokenRepo := &mocks.MockTokenRepository{}
			h := newTokenDeleteTransactionConstructor(mockTokenRepo)

			if tt.tokenRepoErr {
				configMockTokenRepo(mockTokenRepo, mockTokenRepoNotFoundConfigs[0])
			} else {
				configMockTokenRepo(mockTokenRepo, defaultMockTokenRepoConfigs[0])
			}

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
				mockTokenRepo.AssertExpectations(t)
			}
		})
	}
}

func assertTokenDeleteTransaction(
	t *testing.T,
	operation *rTypes.Operation,
	nodeAccountId hedera.AccountID,
	actual interfaces.Transaction,
) {
	assert.IsType(t, &hedera.TokenDeleteTransaction{}, actual)

	tx, _ := actual.(*hedera.TokenDeleteTransaction)
	payer := tx.GetTransactionID().AccountID.String()
	token := tx.GetTokenID().String()

	assert.Equal(t, operation.Account.Address, payer)
	assert.Equal(t, operation.Amount.Currency.Symbol, token)
	assert.ElementsMatch(t, []hedera.AccountID{nodeAccountId}, actual.GetNodeAccountIDs())
}

func getTokenDeleteOperations(currency *rTypes.Currency) []*rTypes.Operation {
	return []*rTypes.Operation{
		{
			OperationIdentifier: &rTypes.OperationIdentifier{Index: 0},
			Type:                types.OperationTypeTokenDelete,
			Account:             payerAccountIdentifier,
			Amount:              &rTypes.Amount{Value: "0", Currency: currency},
		},
	}
}
