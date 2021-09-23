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
	"fmt"
	"testing"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

func TestTokenWipeTransactionConstructorSuite(t *testing.T) {
	suite.Run(t, new(tokenWipeTransactionConstructorSuite))
}

type tokenWipeTransactionConstructorSuite struct {
	suite.Suite
}

func (suite *tokenWipeTransactionConstructorSuite) TestNewTransactionConstructor() {
	h := newTokenWipeTransactionConstructor(&mocks.MockTokenRepository{})
	assert.NotNil(suite.T(), h)
}

func (suite *tokenWipeTransactionConstructorSuite) TestGetOperationType() {
	h := newTokenWipeTransactionConstructor(&mocks.MockTokenRepository{})
	assert.Equal(suite.T(), config.OperationTypeTokenWipe, h.GetOperationType())
}

func (suite *tokenWipeTransactionConstructorSuite) TestGetSdkTransactionType() {
	h := newTokenWipeTransactionConstructor(&mocks.MockTokenRepository{})
	assert.Equal(suite.T(), "TokenWipeTransaction", h.GetSdkTransactionType())
}

func (suite *tokenWipeTransactionConstructorSuite) TestConstruct() {
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

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			operations := getTokenWipeOperations()
			mockTokenRepo := &mocks.MockTokenRepository{}
			h := newTokenWipeTransactionConstructor(mockTokenRepo)
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
				assertTokenWipeTransaction(t, operations[0], nodeAccountId, tx)
				mockTokenRepo.AssertExpectations(t)

				if tt.validStartNanos != 0 {
					assert.Equal(t, tt.validStartNanos, tx.GetTransactionID().ValidStart.UnixNano())
				}
			}
		})
	}
}

func (suite *tokenWipeTransactionConstructorSuite) TestParse() {
	defaultGetTransaction := func() interfaces.Transaction {
		return hedera.NewTokenWipeTransaction().
			SetAccountID(accountId).
			SetAmount(defaultAmount).
			SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
			SetTransactionID(hedera.TransactionIDGenerate(payerId)).
			SetTokenID(tokenIdA)
	}

	var tests = []struct {
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
			name: "AccountIDNotSet",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTokenWipeTransaction().
					SetAmount(defaultAmount).
					SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
					SetTransactionID(hedera.TransactionIDGenerate(payerId)).
					SetTokenID(tokenIdA)
			},
			expectError: true,
		},
		{
			name: "TransactionIDNotSet",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTokenWipeTransaction().
					SetAccountID(accountId).
					SetAmount(defaultAmount).
					SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
					SetTokenID(tokenIdA)
			},
			expectError: true,
		},
		{
			name: "TokenIDNotSet",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTokenWipeTransaction().
					SetAccountID(accountId).
					SetAmount(defaultAmount).
					SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
					SetTransactionID(hedera.TransactionIDGenerate(payerId))
			},
			expectError: true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			expectedOperations := getTokenWipeOperations()

			mockTokenRepo := &mocks.MockTokenRepository{}
			h := newTokenWipeTransactionConstructor(mockTokenRepo)
			tx := tt.getTransaction()

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

func (suite *tokenWipeTransactionConstructorSuite) TestPreprocess() {
	var tests = []struct {
		name                     string
		mockTokenRepoConfigIndex int
		tokenRepoErr             bool
		updateOperations         updateOperationsFunc
		expectError              bool
	}{
		{name: "Success"},
		{
			name:             "InvalidAccountAddress",
			updateOperations: updateOperationAccount("x.y.z"),
			expectError:      true,
		},
		{
			name:             "InvalidAmountValue",
			updateOperations: updateAmountValue("0"),
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
			name:             "InvalidMetadataPayer",
			updateOperations: updateOperationMetadata("payer", "x.y.z"),
			expectError:      true,
		},
		{
			name:             "ZeroMetadataPayer",
			updateOperations: updateOperationMetadata("payer", "0.0.0"),
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
		{
			name:                     "NFTSerialNumbersCountMismatch",
			mockTokenRepoConfigIndex: 2,
			updateOperations:         updateAmount(&rTypes.Amount{Value: "-2", Currency: tokenCCurrency}),
			expectError:              true,
		},
		{
			name:             "InvalidCurrency",
			updateOperations: updateCurrency(config.CurrencyHbar),
			expectError:      true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			operations := getTokenWipeOperations()

			mockTokenRepo := &mocks.MockTokenRepository{}
			h := newTokenWipeTransactionConstructor(mockTokenRepo)

			if tt.tokenRepoErr {
				configMockTokenRepo(mockTokenRepo, mockTokenRepoNotFoundConfigs[tt.mockTokenRepoConfigIndex])
			} else {
				configMockTokenRepo(mockTokenRepo, defaultMockTokenRepoConfigs[tt.mockTokenRepoConfigIndex])
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

func assertTokenWipeTransaction(
	t *testing.T,
	operation *rTypes.Operation,
	nodeAccountId hedera.AccountID,
	actual interfaces.Transaction,
) {
	assert.IsType(t, &hedera.TokenWipeTransaction{}, actual)

	tx, _ := actual.(*hedera.TokenWipeTransaction)
	account := tx.GetAccountID().String()
	amount := fmt.Sprintf("%d", -int64(tx.GetAmount()))
	payer := tx.GetTransactionID().AccountID.String()
	token := tx.GetTokenID().String()

	assert.Equal(t, operation.Metadata["payer"], payer)
	assert.Equal(t, operation.Amount.Value, amount)
	assert.Equal(t, operation.Account.Address, account)
	assert.Equal(t, operation.Amount.Currency.Symbol, token)
	assert.ElementsMatch(t, []hedera.AccountID{nodeAccountId}, actual.GetNodeAccountIDs())
}

func getTokenWipeOperations() []*rTypes.Operation {
	return []*rTypes.Operation{
		{
			OperationIdentifier: &rTypes.OperationIdentifier{Index: 0},
			Type:                config.OperationTypeTokenWipe,
			Account:             &rTypes.AccountIdentifier{Address: accountId.String()},
			Amount: &rTypes.Amount{
				Value:    fmt.Sprintf("%d", -defaultAmount),
				Currency: tokenACurrency,
			},
			Metadata: map[string]interface{}{"payer": payerId.String()},
		},
	}
}
