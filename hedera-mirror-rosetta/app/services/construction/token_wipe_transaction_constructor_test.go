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

func TestTokenWipeTransactionConstructorSuite(t *testing.T) {
	suite.Run(t, new(tokenWipeTransactionConstructorSuite))
}

type tokenWipeTransactionConstructorSuite struct {
	suite.Suite
}

func (suite *tokenWipeTransactionConstructorSuite) TestNewTransactionConstructor() {
	h := newTokenWipeTransactionConstructor()
	assert.NotNil(suite.T(), h)
}

func (suite *tokenWipeTransactionConstructorSuite) TestGetDefaultMaxTransactionFee() {
	h := newTokenWipeTransactionConstructor()
	assert.Equal(suite.T(), types.HbarAmount{Value: 30_00000000}, h.GetDefaultMaxTransactionFee())
}

func (suite *tokenWipeTransactionConstructorSuite) TestGetOperationType() {
	h := newTokenWipeTransactionConstructor()
	assert.Equal(suite.T(), types.OperationTypeTokenWipe, h.GetOperationType())
}

func (suite *tokenWipeTransactionConstructorSuite) TestGetSdkTransactionType() {
	h := newTokenWipeTransactionConstructor()
	assert.Equal(suite.T(), "TokenWipeTransaction", h.GetSdkTransactionType())
}

func (suite *tokenWipeTransactionConstructorSuite) TestConstruct() {
	var tests = []struct {
		name             string
		updateOperations updateOperationsFunc
		expectError      bool
	}{
		{name: "SuccessFT"},
		{
			name: "SuccessNFT",
			updateOperations: func(operations types.OperationSlice) types.OperationSlice {
				return getNonFungibleTokenWipeOperations(false)
			},
		},
		{name: "EmptyOperations", updateOperations: getEmptyOperations, expectError: true},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			operations := getFungibleTokenWipeOperations(false)
			h := newTokenWipeTransactionConstructor()

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
				assertTokenWipeTransaction(t, operations[0], tx)
			}
		})
	}
}

func (suite *tokenWipeTransactionConstructorSuite) TestParse() {
	defaultGetTransactionFT := func() interfaces.Transaction {
		return hedera.NewTokenWipeTransaction().
			SetAccountID(sdkAccountIdA).
			SetAmount(uint64(defaultAmount)).
			SetTokenID(tokenIdA).
			SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdB))
	}
	defaultGetTransactionNFT := func() interfaces.Transaction {
		return hedera.NewTokenWipeTransaction().
			SetAccountID(sdkAccountIdA).
			SetTokenID(tokenIdC).
			SetSerialNumbers([]int64{1, 2}).
			SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdB))
	}

	var tests = []struct {
		name           string
		getTransaction func() interfaces.Transaction
		nft            bool
		expectError    bool
	}{
		{name: "SuccessFT", getTransaction: defaultGetTransactionFT},
		{name: "SuccessNFT", getTransaction: defaultGetTransactionNFT, nft: true},
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
					SetAmount(uint64(defaultAmount)).
					SetTokenID(tokenIdA).
					SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdB))
			},
			expectError: true,
		},
		{
			name: "OutOfRangeAccountId",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTokenWipeTransaction().
					SetAccountID(outOfRangeAccountId).
					SetAmount(uint64(defaultAmount)).
					SetTokenID(tokenIdA).
					SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdB))
			},
			expectError: true,
		},
		{
			name: "TokenIDNotSet",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTokenWipeTransaction().
					SetAccountID(sdkAccountIdA).
					SetAmount(uint64(defaultAmount)).
					SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdB))
			},
			expectError: true,
		},
		{
			name: "OutOfRangeTokenId",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTokenWipeTransaction().
					SetAccountID(sdkAccountIdA).
					SetAmount(uint64(defaultAmount)).
					SetTokenID(outOfRangeTokenId).
					SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdB))
			},
			expectError: true,
		},
		{
			name: "TransactionIDNotSet",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTokenWipeTransaction().
					SetAccountID(sdkAccountIdA).
					SetAmount(uint64(defaultAmount)).
					SetTokenID(tokenIdA)
			},
			expectError: true,
		},
		{
			name: "OutOfRangePayerAccountId",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTokenWipeTransaction().
					SetAccountID(sdkAccountIdA).
					SetAmount(uint64(defaultAmount)).
					SetTokenID(tokenIdA).
					SetTransactionID(hedera.TransactionIDGenerate(outOfRangeAccountId))
			},
			expectError: true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			var expectedOperations types.OperationSlice
			if !tt.nft {
				expectedOperations = getFungibleTokenWipeOperations(true)
			} else {
				expectedOperations = getNonFungibleTokenWipeOperations(true)
			}
			h := newTokenWipeTransactionConstructor()
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
				assert.ElementsMatch(t, []types.AccountId{accountIdB}, signers)
				assert.ElementsMatch(t, expectedOperations, operations)
			}
		})
	}
}

func (suite *tokenWipeTransactionConstructorSuite) TestPreprocess() {
	var tests = []struct {
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
			name:             "InvalidAmountValue",
			updateOperations: updateAmountValue(0),
			expectError:      true,
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
			name:             "OutOfRangeMetadataPayer",
			updateOperations: updateOperationMetadata("payer", "0.65536.4294967296"),
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
			updateOperations: updateOperationType(types.OperationTypeCryptoTransfer),
			expectError:      true,
		},
		{
			name:             "NFTSerialNumbersCountMismatch",
			updateOperations: updateAmount(types.NewTokenAmount(dbTokenC, -2)),
			expectError:      true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			operations := getFungibleTokenWipeOperations(false)
			h := newTokenWipeTransactionConstructor()

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

func assertTokenWipeTransaction(t *testing.T, operation types.Operation, actual interfaces.Transaction) {
	assert.False(t, actual.IsFrozen())
	assert.IsType(t, &hedera.TokenWipeTransaction{}, actual)

	tx, _ := actual.(*hedera.TokenWipeTransaction)
	account := tx.GetAccountID().String()
	token := tx.GetTokenID().String()

	assert.Equal(t, operation.AccountId.ToSdkAccountId().String(), account)
	assert.Equal(t, operation.Amount.GetSymbol(), token)

	assert.IsType(t, &types.TokenAmount{}, operation.Amount)
	tokenAmount := operation.Amount.(*types.TokenAmount)
	if len(tx.GetSerialNumbers()) == 0 {
		amount := -int64(tx.GetAmount())
		assert.Equal(t, tokenAmount.GetValue(), amount)
	} else {
		// nft
		assert.Zero(t, tx.GetAmount())
		assert.ElementsMatch(t, tx.GetSerialNumbers(), tokenAmount.SerialNumbers)
	}
}

func getFungibleTokenWipeOperations(resetDecimals bool) types.OperationSlice {
	token := dbTokenA
	if resetDecimals {
		token = getTokenWithoutDecimals(token)
	}
	return types.OperationSlice{
		{
			AccountId: accountIdA,
			Amount:    types.NewTokenAmount(token, -defaultAmount),
			Metadata:  map[string]interface{}{"payer": accountIdB.String()},
			Type:      types.OperationTypeTokenWipe,
		},
	}
}

func getNonFungibleTokenWipeOperations(resetDecimals bool) types.OperationSlice {
	token := dbTokenC
	if resetDecimals {
		token = getTokenWithoutDecimals(token)
	}
	return types.OperationSlice{
		{
			AccountId: accountIdA,
			Amount:    types.NewTokenAmount(token, -defaultAmount).SetSerialNumbers([]int64{1, 2}),
			Metadata:  map[string]interface{}{"payer": accountIdB.String()},
			Type:      types.OperationTypeTokenWipe,
		},
	}
}
