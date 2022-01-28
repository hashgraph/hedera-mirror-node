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
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

const (
	defaultAmount = 2
	burnAmount    = -defaultAmount
	mintAmount    = defaultAmount
)

var (
	metadatasBytes  = [][]byte{[]byte("foo"), []byte("bar")}
	metadatasBase64 = []interface{}{"Zm9v", "YmFy"}
)

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
		validStartNanos  int64
		expectError      bool
	}{
		{name: "SuccessFT", token: dbTokenA},
		{name: "SuccessNFT", token: dbTokenC},
		{name: "SuccessValidStartNanos", token: dbTokenA, validStartNanos: 100},
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
				tx, signers, err := h.Construct(defaultContext, nodeAccountId, operations, tt.validStartNanos)

				// then
				if tt.expectError {
					assert.NotNil(t, err)
					assert.Nil(t, signers)
					assert.Nil(t, tx)
				} else {
					assert.Nil(t, err)
					assert.ElementsMatch(t, []hedera.AccountID{payerId}, signers)
					assertTokenBurnMintTransaction(t, operations, nodeAccountId, tx, tt.token)

					if tt.validStartNanos != 0 {
						assert.Equal(t, tt.validStartNanos, tx.GetTransactionID().ValidStart.UnixNano())
					}
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
				SetAmount(-burnAmount).
				SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
				SetTokenID(tokenId).
				SetTransactionID(hedera.TransactionIDGenerate(payerId))
			if token.Type == domain.TokenTypeNonFungibleUnique {
				tx.SetSerialNumbers([]int64{1, 2})
			}
			return tx
		}

		tx := hedera.NewTokenMintTransaction().
			SetAmount(mintAmount).
			SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
			SetTokenID(tokenId).
			SetTransactionID(hedera.TransactionIDGenerate(payerId))
		if token.Type == domain.TokenTypeNonFungibleUnique {
			tx.SetMetadatas(metadatasBytes)
		}
		return tx
	}

	tests := []struct {
		name           string
		getTransaction func(operationType string, token domain.Token) interfaces.Transaction
		token          domain.Token
		expectError    bool
	}{
		{name: "SuccessFT", getTransaction: defaultGetTransaction, token: dbTokenA},
		{name: "SuccessNFT", getTransaction: defaultGetTransaction, token: dbTokenC},
		{
			name: "InvalidTransaction",
			getTransaction: func(string, domain.Token) interfaces.Transaction {
				return hedera.NewTransferTransaction()
			},
			expectError: true,
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
						SetAmount(-burnAmount).
						SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
						SetTransactionID(hedera.TransactionIDGenerate(payerId))
				}

				return hedera.NewTokenMintTransaction().
					SetAmount(mintAmount).
					SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
					SetTransactionID(hedera.TransactionIDGenerate(payerId))
			},
			expectError: true,
		},
		{
			name: "TransactionTransactionIDNotSet",
			getTransaction: func(operationType string, token domain.Token) interfaces.Transaction {
				if operationType == types.OperationTypeTokenBurn {
					return hedera.NewTokenBurnTransaction().
						SetAmount(-burnAmount).
						SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
						SetTokenID(tokenIdA)
				}

				return hedera.NewTokenMintTransaction().
					SetAmount(mintAmount).
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
					assert.ElementsMatch(t, []hedera.AccountID{payerId}, signers)
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
			name:             "InvalidAccountAddress",
			token:            dbTokenA,
			updateOperations: updateOperationAccount("x.y.z"),
			expectError:      true,
		},
		{
			name:             "ZeroAccountAddress",
			token:            dbTokenA,
			updateOperations: updateOperationAccount("0.0.0"),
			expectError:      true,
		},
		{
			name:             "InvalidCurrency",
			token:            dbTokenA,
			updateOperations: updateCurrency(types.CurrencyHbar),
			expectError:      true,
		},
		{
			name:             "InvalidCurrencySymbol",
			token:            dbTokenA,
			updateOperations: updateTokenSymbol("1"),
			expectError:      true,
		},
		{
			name:             "ZeroAmountValue",
			token:            dbTokenA,
			updateOperations: updateAmountValue("0"),
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
					assert.ElementsMatch(t, []hedera.AccountID{payerId}, signers)
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
) []*rTypes.Operation {
	amount := burnAmount
	if operationType == types.OperationTypeTokenMint {
		amount = mintAmount
	}

	currency := newRosettaCurrencyBuilder().
		setSymbol(token.TokenId.String()).
		setType(token.Type).
		build()
	operation := &rTypes.Operation{
		OperationIdentifier: &rTypes.OperationIdentifier{Index: 0},
		Type:                operationType,
		Account:             payerAccountIdentifier,
		Amount: &rTypes.Amount{
			Value:    fmt.Sprintf("%d", amount),
			Currency: currency,
		},
	}

	if token.Type == domain.TokenTypeNonFungibleUnique {
		if operationType == types.OperationTypeTokenBurn {
			operation.Amount.Metadata = map[string]interface{}{types.MetadataKeySerialNumbers: []interface{}{"1", "2"}}
		} else {
			operation.Amount.Metadata = map[string]interface{}{types.MetadataKeyMetadatas: metadatasBase64}
		}
	}

	return []*rTypes.Operation{operation}
}

func assertTokenBurnMintTransaction(
	t *testing.T,
	operations []*rTypes.Operation,
	nodeAccountId hedera.AccountID,
	actual interfaces.Transaction,
	dbToken domain.Token,
) {
	assert.True(t, actual.IsFrozen())
	if operations[0].Type == types.OperationTypeTokenBurn {
		assert.IsType(t, &hedera.TokenBurnTransaction{}, actual)
	} else {
		assert.IsType(t, &hedera.TokenMintTransaction{}, actual)
	}

	var payer string
	var token string
	var value string

	switch tx := actual.(type) {
	case *hedera.TokenBurnTransaction:
		payer = tx.GetTransactionID().AccountID.String()
		token = tx.GetTokenID().String()
		value = fmt.Sprintf("%d", -int64(tx.GetAmount()))
		if dbToken.Type == domain.TokenTypeNonFungibleUnique {
			value = fmt.Sprintf("%d", -len(tx.GetSerialNumbers()))
		}
	case *hedera.TokenMintTransaction:
		payer = tx.GetTransactionID().AccountID.String()
		token = tx.GetTokenID().String()
		value = fmt.Sprintf("%d", tx.GetAmount())
		if dbToken.Type == domain.TokenTypeNonFungibleUnique {
			value = fmt.Sprintf("%d", len(tx.GetMetadatas()))
		}
	}

	assert.Equal(t, operations[0].Account.Address, payer)
	assert.Equal(t, operations[0].Amount.Currency.Symbol, token)
	assert.Equal(t, operations[0].Amount.Value, value)
	assert.ElementsMatch(t, []hedera.AccountID{nodeAccountId}, actual.GetNodeAccountIDs())
}
