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

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

// constants used in all token constructor tests
const (
	decimals = 9
	nameA    = "teebar"
	nameB    = "ueebar"
	nameC    = "xffbar"
	symbolA  = "foobar"
	symbolB  = "goobar"
	symbolC  = "fflbar"
)

// variables used in all token constructor tests
var (
	accountId = hedera.AccountID{Account: 197}
	dbTokenA  = domain.Token{
		TokenId:  tokenEntityIdA,
		Decimals: decimals,
		Name:     nameA,
		Symbol:   symbolA,
		Type:     domain.TokenTypeFungibleCommon,
	}
	dbTokenB = domain.Token{
		TokenId:  tokenEntityIdB,
		Decimals: decimals,
		Name:     nameB,
		Symbol:   symbolB,
		Type:     domain.TokenTypeFungibleCommon,
	}
	dbTokenC = domain.Token{
		TokenId:  tokenEntityIdC,
		Decimals: 0,
		Name:     nameC,
		Symbol:   symbolC,
		Type:     domain.TokenTypeNonFungibleUnique,
	}
	tokenEntityIdA = domain.MustDecodeEntityId(212)
	tokenEntityIdB = domain.MustDecodeEntityId(252)
	tokenEntityIdC = domain.MustDecodeEntityId(282)
	tokenIdA       = hedera.TokenID{Token: 212}
	tokenIdB       = hedera.TokenID{Token: 252}
	tokenIdC       = hedera.TokenID{Token: 282}
)

type newConstructorFunc func() transactionConstructorWithType
type updateOperationsFunc func(types.OperationSlice) types.OperationSlice

func TestTokenAssociateDissociateTransactionConstructorSuite(t *testing.T) {
	suite.Run(t, new(tokenAssociateDissociateTransactionConstructorSuite))
}

type tokenAssociateDissociateTransactionConstructorSuite struct {
	suite.Suite
}

func (suite *tokenAssociateDissociateTransactionConstructorSuite) TestNewTokenAssociateTransactionConstructor() {
	h := newTokenAssociateTransactionConstructor()
	assert.NotNil(suite.T(), h)
}

func (suite *tokenAssociateDissociateTransactionConstructorSuite) TestNewTokenDissociateTransactionConstructor() {
	h := newTokenDissociateTransactionConstructor()
	assert.NotNil(suite.T(), h)
}

func (suite *tokenAssociateDissociateTransactionConstructorSuite) TestGetDefaultMaxTransactionFee() {
	tests := []struct {
		name                   string
		transactionConstructor transactionConstructorWithType
		expected               types.HbarAmount
	}{
		{
			name:                   "tokenAssociate",
			transactionConstructor: newTokenAssociateTransactionConstructor(),
			expected:               types.HbarAmount{Value: 5_00000000},
		},
		{
			name:                   "tokenDissociate",
			transactionConstructor: newTokenDissociateTransactionConstructor(),
			expected:               types.HbarAmount{Value: 5_00000000},
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, tt.transactionConstructor.GetDefaultMaxTransactionFee())
		})
	}
}

func (suite *tokenAssociateDissociateTransactionConstructorSuite) TestGetOperationType() {
	tests := []struct {
		name           string
		newConstructor newConstructorFunc
		expected       string
	}{
		{
			name:           "TokenAssociateTransactionConstructor",
			newConstructor: newTokenAssociateTransactionConstructor,
			expected:       types.OperationTypeTokenAssociate,
		},
		{
			name:           "TokenDissociateTransactionConstructor",
			newConstructor: newTokenDissociateTransactionConstructor,
			expected:       types.OperationTypeTokenDissociate,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			h := tt.newConstructor()
			assert.Equal(t, tt.expected, h.GetOperationType())
		})
	}
}

func (suite *tokenAssociateDissociateTransactionConstructorSuite) TestGetSdkTransactionType() {
	tests := []struct {
		name       string
		newHandler newConstructorFunc
		expected   string
	}{
		{
			name:       "TokenAssociateTransactionConstructor",
			newHandler: newTokenAssociateTransactionConstructor,
			expected:   "TokenAssociateTransaction",
		},
		{
			name:       "TokenDissociateTransactionConstructor",
			newHandler: newTokenDissociateTransactionConstructor,
			expected:   "TokenDissociateTransaction",
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			h := tt.newHandler()
			assert.Equal(t, tt.expected, h.GetSdkTransactionType())
		})
	}
}

func (suite *tokenAssociateDissociateTransactionConstructorSuite) TestConstruct() {
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
					assert.ElementsMatch(t, []types.AccountId{accountIdA}, signers)
					assertTokenAssociateDissociateTransaction(t, operations, tx)
				}
			})
		}
	}

	suite.T().Run("TokenAssociateTransactionConstructor", func(t *testing.T) {
		runTests(t, types.OperationTypeTokenAssociate, newTokenAssociateTransactionConstructor)
	})

	suite.T().Run("TokenDissociateTransactionConstructor", func(t *testing.T) {
		runTests(t, types.OperationTypeTokenDissociate, newTokenDissociateTransactionConstructor)
	})
}

func (suite *tokenAssociateDissociateTransactionConstructorSuite) TestParse() {
	defaultGetTransaction := func(operationType string) interfaces.Transaction {
		if operationType == types.OperationTypeTokenAssociate {
			return hedera.NewTokenAssociateTransaction().
				SetAccountID(sdkAccountIdA).
				SetTokenIDs(tokenIdA, tokenIdB, tokenIdC).
				SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdA))
		}
		return hedera.NewTokenDissociateTransaction().
			SetAccountID(sdkAccountIdA).
			SetTokenIDs(tokenIdA, tokenIdB, tokenIdC).
			SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdA))
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
				if operationType == types.OperationTypeTokenAssociate {
					return hedera.NewTokenAssociateTransaction().
						SetAccountID(sdkAccountIdA).
						SetTokenIDs(outOfRangeTokenId).
						SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdA))
				}
				return hedera.NewTokenDissociateTransaction().
					SetAccountID(sdkAccountIdA).
					SetTokenIDs(outOfRangeTokenId).
					SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdA))
			},
			expectError: true,
		},
		{
			name:           "InvalidTransaction",
			getTransaction: getTransferTransaction,
			expectError:    true,
		},
		{
			name: "OutOfRangeAccountId",
			getTransaction: func(operationType string) interfaces.Transaction {
				if operationType == types.OperationTypeTokenAssociate {
					return hedera.NewTokenAssociateTransaction().
						SetAccountID(outOfRangeAccountId).
						SetTokenIDs(tokenIdA, tokenIdB, tokenIdC).
						SetTransactionID(hedera.TransactionIDGenerate(outOfRangeAccountId))
				}
				return hedera.NewTokenDissociateTransaction().
					SetAccountID(outOfRangeAccountId).
					SetTokenIDs(tokenIdA, tokenIdB, tokenIdC).
					SetTransactionID(hedera.TransactionIDGenerate(outOfRangeAccountId))
			},
			expectError: true,
		},
		{
			name: "TransactionMismatch",
			getTransaction: func(operationType string) interfaces.Transaction {
				if operationType == types.OperationTypeTokenAssociate {
					return hedera.NewTokenDissociateTransaction()
				}

				return hedera.NewTokenAssociateTransaction()

			},
			expectError: true,
		},
		{
			name: "TransactionAccountNotSet",
			getTransaction: func(operationType string) interfaces.Transaction {
				if operationType == types.OperationTypeTokenAssociate {
					return hedera.NewTokenAssociateTransaction().
						SetTokenIDs(tokenIdA, tokenIdB).
						SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdA))
				}
				return hedera.NewTokenDissociateTransaction().
					SetTokenIDs(tokenIdA, tokenIdB).
					SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdA))
			},
			expectError: true,
		},
		{
			name: "TransactionTokenIDsNotSet",
			getTransaction: func(operationType string) interfaces.Transaction {
				if operationType == types.OperationTypeTokenAssociate {
					return hedera.NewTokenAssociateTransaction().
						SetAccountID(sdkAccountIdA).
						SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdA))
				}

				return hedera.NewTokenDissociateTransaction().
					SetAccountID(sdkAccountIdA).
					SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdA))
			},
			expectError: true,
		},
		{
			name: "TransactionIDNotSet",
			getTransaction: func(operationType string) interfaces.Transaction {
				if operationType == types.OperationTypeTokenAssociate {
					return hedera.NewTokenAssociateTransaction().
						SetAccountID(sdkAccountIdA).
						SetTokenIDs(tokenIdA, tokenIdB)
				}

				return hedera.NewTokenDissociateTransaction().
					SetAccountID(sdkAccountIdA).
					SetTokenIDs(tokenIdA, tokenIdB)
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
					assert.ElementsMatch(t, []types.AccountId{accountIdA}, signers)
					assert.ElementsMatch(t, expectedOperations, operations)
				}
			})
		}
	}

	suite.T().Run("TokenAssociateTransactionConstructor", func(t *testing.T) {
		runTests(t, types.OperationTypeTokenAssociate, newTokenAssociateTransactionConstructor)
	})

	suite.T().Run("TokenDissociateTransactionConstructor", func(t *testing.T) {
		runTests(t, types.OperationTypeTokenDissociate, newTokenDissociateTransactionConstructor)
	})
}

func (suite *tokenAssociateDissociateTransactionConstructorSuite) TestPreprocess() {
	tests := []struct {
		name             string
		updateOperations updateOperationsFunc
		expectError      bool
	}{
		{name: "Success"},
		{
			name:             "InvalidAmount",
			updateOperations: updateAmount(&types.HbarAmount{Value: 1}),
			expectError:      true,
		},
		{
			name: "DifferentAccountAddress",
			updateOperations: func(operations types.OperationSlice) types.OperationSlice {
				operation := &operations[0]
				operation.AccountId, _ = types.NewAccountIdFromSdkAccountId(hedera.AccountID{Realm: 1, Account: 7})
				return operations
			},
			expectError: true,
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
					assert.ElementsMatch(t, []types.AccountId{accountIdA}, signers)
				}
			})
		}
	}

	suite.T().Run("TokenAssociateTransactionConstructor", func(t *testing.T) {
		runTests(t, types.OperationTypeTokenAssociate, newTokenAssociateTransactionConstructor)
	})

	suite.T().Run("TokenDissociateTransactionConstructor", func(t *testing.T) {
		runTests(t, types.OperationTypeTokenDissociate, newTokenDissociateTransactionConstructor)
	})
}

func (suite *tokenAssociateDissociateTransactionConstructorSuite) getOperations(operationType string) types.OperationSlice {
	return types.OperationSlice{
		{
			AccountId: accountIdA,
			Index:     0,
			Type:      operationType,
			Amount:    types.NewTokenAmount(getPartialDbToken(dbTokenA), 0),
		},
		{
			AccountId: accountIdA,
			Index:     1,
			Type:      operationType,
			Amount:    types.NewTokenAmount(getPartialDbToken(dbTokenB), 0),
		},
		{
			AccountId: accountIdA,
			Index:     2,
			Type:      operationType,
			Amount:    types.NewTokenAmount(getPartialDbToken(dbTokenC), 0),
		},
	}
}

func assertTokenAssociateDissociateTransaction(
	t *testing.T,
	operations types.OperationSlice,
	actual interfaces.Transaction,
) {
	assert.False(t, actual.IsFrozen())
	if operations[0].Type == types.OperationTypeTokenAssociate {
		assert.IsType(t, &hedera.TokenAssociateTransaction{}, actual)
	} else {
		assert.IsType(t, &hedera.TokenDissociateTransaction{}, actual)
	}

	var expectedTokens []hedera.TokenID
	for _, operation := range operations {
		token, _ := hedera.TokenIDFromString(operation.Amount.GetSymbol())
		expectedTokens = append(expectedTokens, token)
	}

	var account string
	var tokens []hedera.TokenID

	switch tx := actual.(type) {
	case *hedera.TokenAssociateTransaction:
		account = tx.GetAccountID().String()
		tokens = tx.GetTokenIDs()
	case *hedera.TokenDissociateTransaction:
		account = tx.GetAccountID().String()
		tokens = tx.GetTokenIDs()
	}

	assert.Equal(t, operations[0].AccountId.ToSdkAccountId().String(), account)
	assert.ElementsMatch(t, expectedTokens, tokens)
}

func addOperation(operations types.OperationSlice) types.OperationSlice {
	return append(operations, types.Operation{})
}

func getEmptyOperations(types.OperationSlice) types.OperationSlice {
	return make(types.OperationSlice, 0)
}

func getEmptyOperationMetadata(operations types.OperationSlice) types.OperationSlice {
	for index := range operations {
		operation := &operations[index]
		operation.Metadata = nil
	}
	return operations
}

func getPartialDbToken(dbToken domain.Token) domain.Token {
	token := dbToken
	token.Decimals = 0
	token.Type = domain.TokenTypeUnknown
	return token
}

func getTransferTransaction(string) interfaces.Transaction {
	return hedera.NewTransferTransaction()
}

func deleteOperationMetadata(key string) updateOperationsFunc {
	return func(operations types.OperationSlice) types.OperationSlice {
		for index := range operations {
			operation := &operations[index]
			delete(operation.Metadata, key)
		}
		return operations
	}
}

func negateAmountValue(operations types.OperationSlice) types.OperationSlice {
	for index := range operations {
		operation := &operations[index]
		amount := operation.Amount
		switch a := amount.(type) {
		case *types.HbarAmount:
			a.Value = -a.Value
		case *types.TokenAmount:
			a.Value = -a.Value
		}
	}
	return operations
}

func updateAmount(amount types.Amount) updateOperationsFunc {
	return func(operations types.OperationSlice) types.OperationSlice {
		for index := range operations {
			operation := &operations[index]
			operation.Amount = amount
		}
		return operations
	}
}

func updateAmountValue(value int64) updateOperationsFunc {
	return func(operations types.OperationSlice) types.OperationSlice {
		for index := range operations {
			operation := &operations[index]
			amount := operation.Amount
			switch a := amount.(type) {
			case *types.HbarAmount:
				a.Value = value
			case *types.TokenAmount:
				a.Value = value
			}
		}
		return operations
	}
}

func updateOperationMetadata(key string, value interface{}) updateOperationsFunc {
	return func(operations types.OperationSlice) types.OperationSlice {
		for index := range operations {
			operation := &operations[index]
			operation.Metadata[key] = value
		}
		return operations
	}
}

func updateOperationType(operationType string) updateOperationsFunc {
	return func(operations types.OperationSlice) types.OperationSlice {
		for index := range operations {
			operation := &operations[index]
			operation.Type = operationType
		}
		return operations
	}
}

type rosettaCurrencyBuilder struct {
	symbol   string
	decimals int32
	metadata map[string]interface{}
}

func (b *rosettaCurrencyBuilder) build() *rTypes.Currency {
	return &rTypes.Currency{
		Decimals: b.decimals,
		Symbol:   b.symbol,
		Metadata: b.metadata,
	}
}

func (b *rosettaCurrencyBuilder) setDecimals(decimals int32) *rosettaCurrencyBuilder {
	b.decimals = decimals
	return b
}

func (b *rosettaCurrencyBuilder) setSymbol(symbol string) *rosettaCurrencyBuilder {
	b.symbol = symbol
	return b
}

func (b *rosettaCurrencyBuilder) setType(tokenType string) *rosettaCurrencyBuilder {
	b.metadata[types.MetadataKeyType] = tokenType
	return b
}

func newRosettaCurrencyBuilder() *rosettaCurrencyBuilder {
	return &rosettaCurrencyBuilder{
		metadata: make(map[string]interface{}),
	}
}
