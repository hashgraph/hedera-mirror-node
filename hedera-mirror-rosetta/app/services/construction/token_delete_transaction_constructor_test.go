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
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
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
	h := newTokenDeleteTransactionConstructor()
	assert.NotNil(suite.T(), h)
}

func (suite *tokenDeleteTransactionConstructorSuite) TestGetDefaultMaxTransactionFee() {
	h := newTokenDeleteTransactionConstructor()
	assert.Equal(suite.T(), types.HbarAmount{Value: 30_00000000}, h.GetDefaultMaxTransactionFee())
}

func (suite *tokenDeleteTransactionConstructorSuite) TestGetOperationType() {
	h := newTokenDeleteTransactionConstructor()
	assert.Equal(suite.T(), types.OperationTypeTokenDelete, h.GetOperationType())
}

func (suite *tokenDeleteTransactionConstructorSuite) TestGetSdkTransactionType() {
	h := newTokenDeleteTransactionConstructor()
	assert.Equal(suite.T(), "TokenDeleteTransaction", h.GetSdkTransactionType())
}

func (suite *tokenDeleteTransactionConstructorSuite) TestConstruct() {
	var tests = []struct {
		name             string
		token            domain.Token
		updateOperations updateOperationsFunc
		expectError      bool
	}{
		{name: "SuccessFT", token: dbTokenA},
		{name: "SuccessNFT", token: dbTokenC},
		{name: "EmptyOperations", token: dbTokenA, updateOperations: getEmptyOperations, expectError: true},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			operations := getTokenDeleteOperations(tt.token)
			h := newTokenDeleteTransactionConstructor()

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
				assertTokenDeleteTransaction(t, operations[0], tx)
			}
		})
	}
}

func (suite *tokenDeleteTransactionConstructorSuite) TestParse() {
	defaultGetTransaction := func() interfaces.Transaction {
		return hedera.NewTokenDeleteTransaction().
			SetTokenID(tokenIdA).
			SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdA))
	}

	tests := []struct {
		name           string
		getTransaction func() interfaces.Transaction
		expectError    bool
	}{
		{name: "Success", getTransaction: defaultGetTransaction},
		{
			name: "InvalidTokenId",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTokenDeleteTransaction().
					SetTokenID(outOfRangeTokenId).
					SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdA))
			},
			expectError: true,
		},
		{
			name: "InvalidTransaction",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTransferTransaction()
			},
			expectError: true,
		},
		{
			name: "TokenIDNotSet",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTokenDeleteTransaction().SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdA))
			},
			expectError: true,
		},
		{
			name: "TransactionIDNotSet",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTokenDeleteTransaction().SetTokenID(tokenIdA)
			},
			expectError: true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			expectedOperations := getTokenDeleteOperations(getPartialDbToken(dbTokenA))

			h := newTokenDeleteTransactionConstructor()
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

func (suite *tokenDeleteTransactionConstructorSuite) TestPreprocess() {
	var tests = []struct {
		name             string
		updateOperations updateOperationsFunc
		expectError      bool
	}{
		{name: "Success"},
		{
			name:             "InvalidAmount",
			updateOperations: updateAmount(&types.HbarAmount{}),
			expectError:      true,
		},
		{
			name:             "InvalidAmountValue",
			updateOperations: updateAmountValue(10),
			expectError:      true,
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
			operations := getTokenDeleteOperations(getPartialDbToken(dbTokenA))
			h := newTokenDeleteTransactionConstructor()

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

func assertTokenDeleteTransaction(t *testing.T, operation types.Operation, actual interfaces.Transaction) {
	assert.False(t, actual.IsFrozen())
	assert.IsType(t, &hedera.TokenDeleteTransaction{}, actual)

	tx, _ := actual.(*hedera.TokenDeleteTransaction)
	token := tx.GetTokenID().String()
	assert.Equal(t, operation.Amount.GetSymbol(), token)
}

func getTokenDeleteOperations(token domain.Token) types.OperationSlice {
	return types.OperationSlice{
		{
			AccountId: accountIdA,
			Type:      types.OperationTypeTokenDelete,
			Amount:    types.NewTokenAmount(token, 0),
		},
	}
}
