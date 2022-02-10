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
	nodeAccountId          = hedera.AccountID{Account: 7}
	payerAccountIdentifier = &rTypes.AccountIdentifier{Address: payerId.String()}
	payerId                = hedera.AccountID{Account: 100}
	tokenEntityIdA         = domain.MustDecodeEntityId(212)
	tokenEntityIdB         = domain.MustDecodeEntityId(252)
	tokenEntityIdC         = domain.MustDecodeEntityId(282)
	tokenACurrency         = types.Token{Token: dbTokenA}.ToRosettaCurrency()
	tokenBCurrency         = types.Token{Token: dbTokenB}.ToRosettaCurrency()
	tokenCCurrency         = types.Token{Token: dbTokenC}.ToRosettaCurrency()
	tokenAPartialCurrency  = newRosettaCurrencyBuilder().
				setSymbol(tokenEntityIdA.String()).
				setType(domain.TokenTypeUnknown).
				build()
	tokenBPartialCurrency = newRosettaCurrencyBuilder().
				setSymbol(tokenEntityIdB.String()).
				setType(domain.TokenTypeUnknown).
				build()
	tokenCPartialCurrency = newRosettaCurrencyBuilder().
				setSymbol(tokenEntityIdC.String()).
				setType(domain.TokenTypeUnknown).
				build()
	tokenIdA = hedera.TokenID{Token: 212}
	tokenIdB = hedera.TokenID{Token: 252}
	tokenIdC = hedera.TokenID{Token: 282}
)

type newConstructorFunc func() transactionConstructorWithType
type updateOperationsFunc func([]*rTypes.Operation) []*rTypes.Operation

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
					assertTokenAssociateDissociateTransaction(t, operations, nodeAccountId, tx)

					if tt.validStartNanos != 0 {
						assert.Equal(t, tt.validStartNanos, tx.GetTransactionID().ValidStart.UnixNano())
					}
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
				SetAccountID(payerId).
				SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
				SetTokenIDs(tokenIdA, tokenIdB, tokenIdC).
				SetTransactionID(hedera.TransactionIDGenerate(payerId))
		}

		return hedera.NewTokenDissociateTransaction().
			SetAccountID(payerId).
			SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
			SetTokenIDs(tokenIdA, tokenIdB, tokenIdC).
			SetTransactionID(hedera.TransactionIDGenerate(payerId))
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
						SetAccountID(payerId).
						SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
						SetTokenIDs(outOfRangeTokenId).
						SetTransactionID(hedera.TransactionIDGenerate(payerId))
				}

				return hedera.NewTokenDissociateTransaction().
					SetAccountID(payerId).
					SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
					SetTokenIDs(outOfRangeTokenId).
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
						SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
						SetTokenIDs(tokenIdA, tokenIdB).
						SetTransactionID(hedera.TransactionIDGenerate(payerId))
				}

				return hedera.NewTokenDissociateTransaction().
					SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
					SetTokenIDs(tokenIdA, tokenIdB).
					SetTransactionID(hedera.TransactionIDGenerate(payerId))
			},
			expectError: true,
		},
		{
			name: "TransactionTokenIDsNotSet",
			getTransaction: func(operationType string) interfaces.Transaction {
				if operationType == types.OperationTypeTokenAssociate {
					return hedera.NewTokenAssociateTransaction().
						SetAccountID(payerId).
						SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
						SetTransactionID(hedera.TransactionIDGenerate(payerId))
				}

				return hedera.NewTokenDissociateTransaction().
					SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
					SetAccountID(payerId).
					SetTransactionID(hedera.TransactionIDGenerate(payerId))
			},
			expectError: true,
		},
		{
			name: "TransactionTransactionIDNotSet",
			getTransaction: func(operationType string) interfaces.Transaction {
				if operationType == types.OperationTypeTokenAssociate {
					return hedera.NewTokenAssociateTransaction().
						SetAccountID(payerId).
						SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
						SetTokenIDs(tokenIdA, tokenIdB)
				}

				return hedera.NewTokenDissociateTransaction().
					SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
					SetAccountID(payerId).
					SetTokenIDs(tokenIdA, tokenIdB)
			},
			expectError: true,
		},
		{
			name: "TransactionAccountPayerMismatch",
			getTransaction: func(operationType string) interfaces.Transaction {
				if operationType == types.OperationTypeTokenAssociate {
					return hedera.NewTokenAssociateTransaction().
						SetAccountID(accountId).
						SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
						SetTokenIDs(tokenIdA, tokenIdB).
						SetTransactionID(hedera.TransactionIDGenerate(payerId))
				}

				return hedera.NewTokenDissociateTransaction().
					SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
					SetAccountID(accountId).
					SetTokenIDs(tokenIdA, tokenIdB).
					SetTransactionID(hedera.TransactionIDGenerate(payerId))
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
			name:             "InvalidAccountAddress",
			updateOperations: updateOperationAccount("x.y.z"),
			expectError:      true,
		},
		{
			name:             "InvalidCurrencySymbol",
			updateOperations: updateCurrency(types.CurrencyHbar),
			expectError:      true,
		},
		{
			name: "DifferentAccountAddress",
			updateOperations: func(operations []*rTypes.Operation) []*rTypes.Operation {
				operations[0].Account = &rTypes.AccountIdentifier{Address: "0.1.7"}
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
					assert.ElementsMatch(t, []hedera.AccountID{payerId}, signers)
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

func (suite *tokenAssociateDissociateTransactionConstructorSuite) getOperations(operationType string) []*rTypes.Operation {
	return []*rTypes.Operation{
		{
			OperationIdentifier: &rTypes.OperationIdentifier{Index: 0},
			Type:                operationType,
			Account:             payerAccountIdentifier,
			Amount:              &rTypes.Amount{Value: "0", Currency: tokenAPartialCurrency},
		},
		{
			OperationIdentifier: &rTypes.OperationIdentifier{Index: 1},
			Type:                operationType,
			Account:             payerAccountIdentifier,
			Amount:              &rTypes.Amount{Value: "0", Currency: tokenBPartialCurrency},
		},
		{
			OperationIdentifier: &rTypes.OperationIdentifier{Index: 2},
			Type:                operationType,
			Account:             payerAccountIdentifier,
			Amount:              &rTypes.Amount{Value: "0", Currency: tokenCPartialCurrency},
		},
	}
}

func assertTokenAssociateDissociateTransaction(
	t *testing.T,
	operations []*rTypes.Operation,
	nodeAccountId hedera.AccountID,
	actual interfaces.Transaction,
) {
	assert.True(t, actual.IsFrozen())
	if operations[0].Type == types.OperationTypeTokenAssociate {
		assert.IsType(t, &hedera.TokenAssociateTransaction{}, actual)
	} else {
		assert.IsType(t, &hedera.TokenDissociateTransaction{}, actual)
	}

	var expectedTokens []hedera.TokenID
	for _, operation := range operations {
		token, _ := hedera.TokenIDFromString(operation.Amount.Currency.Symbol)
		expectedTokens = append(expectedTokens, token)
	}

	var account string
	var payer string
	var tokens []hedera.TokenID

	switch tx := actual.(type) {
	case *hedera.TokenAssociateTransaction:
		account = tx.GetAccountID().String()
		payer = tx.GetTransactionID().AccountID.String()
		tokens = tx.GetTokenIDs()
	case *hedera.TokenDissociateTransaction:
		account = tx.GetAccountID().String()
		payer = tx.GetTransactionID().AccountID.String()
		tokens = tx.GetTokenIDs()
	}

	assert.Equal(t, operations[0].Account.Address, account)
	assert.Equal(t, operations[0].Account.Address, payer)
	assert.ElementsMatch(t, expectedTokens, tokens)
	assert.ElementsMatch(t, []hedera.AccountID{nodeAccountId}, actual.GetNodeAccountIDs())
}

func addOperation(operations []*rTypes.Operation) []*rTypes.Operation {
	return append(operations, &rTypes.Operation{})
}

func getEmptyOperations([]*rTypes.Operation) []*rTypes.Operation {
	return make([]*rTypes.Operation, 0)
}

func getEmptyOperationMetadata(operations []*rTypes.Operation) []*rTypes.Operation {
	for _, operation := range operations {
		operation.Metadata = nil
	}
	return operations
}

func getTransferTransaction(string) interfaces.Transaction {
	return hedera.NewTransferTransaction()
}

func deleteOperationMetadata(key string) updateOperationsFunc {
	return func(operations []*rTypes.Operation) []*rTypes.Operation {
		for _, operation := range operations {
			delete(operation.Metadata, key)
		}
		return operations
	}
}

func negateAmountValue(operations []*rTypes.Operation) []*rTypes.Operation {
	for _, operation := range operations {
		amount := *operation.Amount
		if amount.Value[0] == uint8('-') {
			amount.Value = amount.Value[1:]
		} else {
			amount.Value = "-" + amount.Value
		}
		operation.Amount = &amount
	}
	return operations
}

func updateAmount(amount *rTypes.Amount) updateOperationsFunc {
	return func(operations []*rTypes.Operation) []*rTypes.Operation {
		for _, operation := range operations {
			operation.Amount = amount
		}
		return operations
	}
}

func updateAmountValue(value string) updateOperationsFunc {
	return func(operations []*rTypes.Operation) []*rTypes.Operation {
		for _, operation := range operations {
			amount := *operation.Amount
			amount.Value = value
			operation.Amount = &amount
		}
		return operations
	}
}

func updateCurrency(currency *rTypes.Currency) updateOperationsFunc {
	return func(operations []*rTypes.Operation) []*rTypes.Operation {
		for _, operation := range operations {
			amount := *operation.Amount
			amount.Currency = currency
			operation.Amount = &amount
		}
		return operations
	}
}

func updateOperationAccount(account string) updateOperationsFunc {
	return func(operations []*rTypes.Operation) []*rTypes.Operation {
		for _, operation := range operations {
			operation.Account = &rTypes.AccountIdentifier{Address: account}
		}
		return operations
	}
}

func updateOperationMetadata(key string, value interface{}) updateOperationsFunc {
	return func(operations []*rTypes.Operation) []*rTypes.Operation {
		for _, operation := range operations {
			operation.Metadata[key] = value
		}
		return operations
	}
}

func updateOperationType(operationType string) updateOperationsFunc {
	return func(operations []*rTypes.Operation) []*rTypes.Operation {
		result := make([]*rTypes.Operation, 0, len(operations))
		for idx := range operations {
			operation := *operations[idx]
			operation.Type = operationType
			result = append(result, &operation)
		}
		return result
	}
}

func updateTokenSymbol(symbol string) updateOperationsFunc {
	return func(operations []*rTypes.Operation) []*rTypes.Operation {
		for _, operation := range operations {
			currency := *operation.Amount.Currency
			currency.Symbol = symbol
			operation.Amount.Currency = &currency
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
