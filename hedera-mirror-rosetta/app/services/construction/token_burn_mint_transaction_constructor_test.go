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

const (
	defaultAmount int64 = 2
	burnAmount          = -defaultAmount
	mintAmount          = defaultAmount
)

var metadatasBytes = [][]byte{[]byte("foo"), []byte("bar")}

func TestTokenBurnMintTransactionConstructorSuite(t *testing.T) {
	suite.Run(t, new(tokenTokenBurnMintTransactionConstructorSuite))
}

type tokenTokenBurnMintTransactionConstructorSuite struct {
	suite.Suite
}

func (suite *tokenTokenBurnMintTransactionConstructorSuite) TestNewTokenBurnTransactionConstructor() {
	h := newTokenBurnTransactionConstructor()
	assert.NotNil(suite.T(), h)
}

func (suite *tokenTokenBurnMintTransactionConstructorSuite) TestNewTokenMintTransactionConstructor() {
	h := newTokenMintTransactionConstructor()
	assert.NotNil(suite.T(), h)
}

func (suite *tokenTokenBurnMintTransactionConstructorSuite) TestGetDefaultMaxTransactionFee() {
	tests := []struct {
		name                   string
		transactionConstructor transactionConstructorWithType
		expected               types.HbarAmount
	}{
		{
			name:                   "tokenBurn",
			transactionConstructor: newTokenBurnTransactionConstructor(),
			expected:               types.HbarAmount{Value: 2_00000000},
		},
		{
			name:                   "tokenMint",
			transactionConstructor: newTokenMintTransactionConstructor(),
			expected:               types.HbarAmount{Value: 30_00000000},
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, tt.transactionConstructor.GetDefaultMaxTransactionFee())
		})
	}
}

func (suite *tokenTokenBurnMintTransactionConstructorSuite) TestGetOperationType() {
	tests := []struct {
		name       string
		newHandler newConstructorFunc
		expected   string
	}{
		{
			name:       "TokenBurnTransactionConstructor",
			newHandler: newTokenBurnTransactionConstructor,
			expected:   types.OperationTypeTokenBurn,
		},
		{
			name:       "TokenMintTransactionConstructor",
			newHandler: newTokenMintTransactionConstructor,
			expected:   types.OperationTypeTokenMint,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			h := tt.newHandler()
			assert.Equal(t, tt.expected, h.GetOperationType())
		})
	}
}

func (suite *tokenTokenBurnMintTransactionConstructorSuite) TestGetSdkTransactionType() {
	tests := []struct {
		name       string
		newHandler newConstructorFunc
		expected   string
	}{
		{
			name:       "TokenBurnTransactionConstructor",
			newHandler: newTokenBurnTransactionConstructor,
			expected:   "TokenBurnTransaction",
		},
		{
			name:       "TokenMintTransactionConstructor",
			newHandler: newTokenMintTransactionConstructor,
			expected:   "TokenMintTransaction",
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			h := tt.newHandler()
			assert.Equal(t, tt.expected, h.GetSdkTransactionType())
		})
	}
}

func (suite *tokenTokenBurnMintTransactionConstructorSuite) TestConstruct() {
	tests := []struct {
		name             string
		updateOperations updateOperationsFunc
		token            domain.Token
		expectError      bool
	}{
		{name: "SuccessFT", token: dbTokenA},
		{name: "SuccessNFT", token: dbTokenC},
		{name: "EmptyOperations", updateOperations: getEmptyOperations, expectError: true},
	}

	runTests := func(t *testing.T, operationType string, newHandler newConstructorFunc) {
		for _, tt := range tests {
			t.Run(tt.name, func(t *testing.T) {
				// given
				operations := suite.getOperations(operationType, tt.token)
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
					assert.ElementsMatch(t, []types.AccountId{accountIdA}, signers)
					assertTokenBurnMintTransaction(t, operations, tx, tt.token)
				}
			})
		}
	}

	suite.T().Run("TokenBurnTransactionConstructor", func(t *testing.T) {
		runTests(t, types.OperationTypeTokenBurn, newTokenBurnTransactionConstructor)
	})

	suite.T().Run("TokenDissociateTransactionConstructor", func(t *testing.T) {
		runTests(t, types.OperationTypeTokenMint, newTokenMintTransactionConstructor)
	})
}

func (suite *tokenTokenBurnMintTransactionConstructorSuite) TestParse() {
	defaultGetTransaction := func(operationType string, token domain.Token) interfaces.Transaction {
		tokenId, _ := hedera.TokenIDFromString(token.TokenId.String())
		if operationType == types.OperationTypeTokenBurn {
			tx := hedera.NewTokenBurnTransaction().
				SetAmount(uint64(-burnAmount)).
				SetTokenID(tokenId).
				SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdA))
			if token.Type == domain.TokenTypeNonFungibleUnique {
				tx.SetSerialNumbers([]int64{1, 2})
			}
			return tx
		}

		tx := hedera.NewTokenMintTransaction().
			SetAmount(uint64(mintAmount)).
			SetTokenID(tokenId).
			SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdA))
		if token.Type == domain.TokenTypeNonFungibleUnique {
			tx.SetMetadatas(metadatasBytes)
		}
		return tx
	}

	getTransactionWithInvalidTokenId := func(operationType string, token domain.Token) interfaces.Transaction {
		transaction := defaultGetTransaction(operationType, token)
		switch tx := transaction.(type) {
		case *hedera.TokenBurnTransaction:
			tx.SetAmount(uint64(-burnAmount)).
				SetTokenID(outOfRangeTokenId)
			break
		case *hedera.TokenMintTransaction:
			tx.SetAmount(uint64(mintAmount)).
				SetTokenID(outOfRangeTokenId)
			break
		default:
			break
		}
		return transaction
	}

	tests := []struct {
		name           string
		getTransaction func(operationType string, token domain.Token) interfaces.Transaction
		token          domain.Token
		expectError    bool
	}{
		{name: "SuccessFT", getTransaction: defaultGetTransaction, token: getTokenWithoutDecimals(dbTokenA)},
		{name: "SuccessNFT", getTransaction: defaultGetTransaction, token: getTokenWithoutDecimals(dbTokenC)},
		{
			name: "InvalidTransaction",
			getTransaction: func(string, domain.Token) interfaces.Transaction {
				return hedera.NewTransferTransaction()
			},
			expectError: true,
		},
		{
			name:           "InvalidTokenIdFT",
			getTransaction: getTransactionWithInvalidTokenId,
			token:          dbTokenA,
			expectError:    true,
		},
		{
			name:           "InvalidTokenIdFT",
			getTransaction: getTransactionWithInvalidTokenId,
			token:          dbTokenC,
			expectError:    true,
		},
		{
			name: "TransactionMismatch",
			getTransaction: func(operationType string, token domain.Token) interfaces.Transaction {
				if operationType == types.OperationTypeTokenBurn {
					return hedera.NewTokenMintTransaction()
				}
				return hedera.NewTokenBurnTransaction()

			},
			expectError: true,
		},
		{
			name: "TransactionTokenIDNotSet",
			getTransaction: func(operationType string, token domain.Token) interfaces.Transaction {
				if operationType == types.OperationTypeTokenBurn {
					return hedera.NewTokenBurnTransaction().
						SetAmount(uint64(-burnAmount)).
						SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdA))
				}
				return hedera.NewTokenMintTransaction().
					SetAmount(uint64(mintAmount)).
					SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdA))
			},
			expectError: true,
		},
		{
			name: "TransactionIDNotSet",
			getTransaction: func(operationType string, token domain.Token) interfaces.Transaction {
				if operationType == types.OperationTypeTokenBurn {
					return hedera.NewTokenBurnTransaction().SetAmount(uint64(-burnAmount)).SetTokenID(tokenIdA)
				}
				return hedera.NewTokenMintTransaction().SetAmount(uint64(mintAmount)).SetTokenID(tokenIdA)
			},
			expectError: true,
		},
	}

	runTests := func(t *testing.T, operationType string, newHandler newConstructorFunc) {
		for _, tt := range tests {
			t.Run(tt.name, func(t *testing.T) {
				// given
				expectedOperations := suite.getOperations(operationType, tt.token)

				h := newHandler()
				tx := tt.getTransaction(operationType, tt.token)

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

	suite.T().Run("TokenBurnTransactionConstructor", func(t *testing.T) {
		runTests(t, types.OperationTypeTokenBurn, newTokenBurnTransactionConstructor)
	})

	suite.T().Run("TokenMintTransactionConstructor", func(t *testing.T) {
		runTests(t, types.OperationTypeTokenMint, newTokenMintTransactionConstructor)
	})
}

func (suite *tokenTokenBurnMintTransactionConstructorSuite) TestPreprocess() {
	tests := []struct {
		name             string
		token            domain.Token
		updateOperations updateOperationsFunc
		expectError      bool
	}{
		{name: "SuccessFT", token: dbTokenA},
		{name: "SuccessNFT", token: dbTokenC},
		{
			name:             "InvalidAmount",
			token:            dbTokenA,
			updateOperations: updateAmount(&types.HbarAmount{Value: 1}),
			expectError:      true,
		},
		{
			name:             "ZeroAmountValue",
			token:            dbTokenA,
			updateOperations: updateAmountValue(0),
			expectError:      true,
		},
		{
			name:             "NegatedAmountValue",
			token:            dbTokenA,
			updateOperations: negateAmountValue,
			expectError:      true,
		},
		{
			name:             "MissingAmount",
			token:            dbTokenA,
			updateOperations: updateAmount(nil),
			expectError:      true,
		},
		{
			name:             "InvalidOperationType",
			token:            dbTokenA,
			updateOperations: updateOperationType(types.OperationTypeCryptoTransfer),
			expectError:      true,
		},
	}

	runTests := func(t *testing.T, operationType string, newHandler newConstructorFunc) {
		for _, tt := range tests {
			t.Run(tt.name, func(t *testing.T) {
				// given
				operations := suite.getOperations(operationType, tt.token)
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
					assert.ElementsMatch(t, []types.AccountId{accountIdA}, signers)
				}
			})
		}
	}

	suite.T().Run("TokenBurnTransactionConstructor", func(t *testing.T) {
		runTests(t, types.OperationTypeTokenBurn, newTokenBurnTransactionConstructor)
	})

	suite.T().Run("TokenDissociateTransactionConstructor", func(t *testing.T) {
		runTests(t, types.OperationTypeTokenMint, newTokenMintTransactionConstructor)
	})
}

func (suite *tokenTokenBurnMintTransactionConstructorSuite) getOperations(
	operationType string,
	token domain.Token,
) types.OperationSlice {
	amount := burnAmount
	if operationType == types.OperationTypeTokenMint {
		amount = mintAmount
	}

	tokenAmount := types.NewTokenAmount(token, amount)
	operation := types.Operation{AccountId: accountIdA, Amount: tokenAmount, Type: operationType}

	if token.Type == domain.TokenTypeNonFungibleUnique {
		if operationType == types.OperationTypeTokenBurn {
			tokenAmount.SetSerialNumbers([]int64{1, 2})
		} else {
			tokenAmount.SetMetadatas(metadatasBytes)
		}
	}

	return types.OperationSlice{operation}
}

func assertTokenBurnMintTransaction(
	t *testing.T,
	operations types.OperationSlice,
	actual interfaces.Transaction,
	dbToken domain.Token,
) {
	assert.False(t, actual.IsFrozen())
	if operations[0].Type == types.OperationTypeTokenBurn {
		assert.IsType(t, &hedera.TokenBurnTransaction{}, actual)
	} else {
		assert.IsType(t, &hedera.TokenMintTransaction{}, actual)
	}

	var token string
	var value int64
	switch tx := actual.(type) {
	case *hedera.TokenBurnTransaction:
		token = tx.GetTokenID().String()
		value = -int64(tx.GetAmount())
		if dbToken.Type == domain.TokenTypeNonFungibleUnique {
			value = -int64(len(tx.GetSerialNumbers()))
		}
	case *hedera.TokenMintTransaction:
		token = tx.GetTokenID().String()
		value = int64(tx.GetAmount())
		if dbToken.Type == domain.TokenTypeNonFungibleUnique {
			value = int64(len(tx.GetMetadatas()))
		}
	}

	assert.Equal(t, operations[0].Amount.GetSymbol(), token)
	assert.Equal(t, operations[0].Amount.GetValue(), value)
}

func getTokenWithoutDecimals(token domain.Token) domain.Token {
	clone := token
	clone.Decimals = 0
	return clone
}
