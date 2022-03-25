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
	"fmt"
	"testing"

	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

var (
	accountIdA    = types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(9500))
	accountIdB    = types.NewAccountIdFromEntityId(domain.MustDecodeEntityId(9505))
	sdkAccountIdA = accountIdA.ToSdkAccountId()
	sdkAccountIdB = accountIdB.ToSdkAccountId()

	currencyHbar = types.CurrencyHbar

	defaultSerialNumbers = []int64{1}
	defaultSigners       = []types.AccountId{accountIdA, accountIdB}
	defaultTransfers     = []transferOperation{
		{accountId: accountIdA, amount: &types.HbarAmount{Value: -15}},
		{accountId: accountIdB, amount: &types.HbarAmount{Value: 15}},
		{accountId: accountIdB, amount: types.NewTokenAmount(dbTokenA, -25)},
		{accountId: accountIdA, amount: types.NewTokenAmount(dbTokenA, 25)},
		{accountId: accountIdA, amount: types.NewTokenAmount(dbTokenB, -30)},
		{accountId: accountIdB, amount: types.NewTokenAmount(dbTokenB, 30)},
		{accountId: accountIdA, amount: types.NewTokenAmount(dbTokenC, -1).SetSerialNumbers(defaultSerialNumbers)},
		{accountId: accountIdB, amount: types.NewTokenAmount(dbTokenC, 1).SetSerialNumbers(defaultSerialNumbers)},
	}
	nftId               = hedera.NftID{TokenID: tokenIdC, SerialNumber: 1}
	outOfRangeAccountId = hedera.AccountID{Shard: 2 << 15, Realm: 2 << 16, Account: 2<<32 + 5}
	outOfRangeTokenId   = hedera.TokenID{Shard: 2 << 15, Realm: 2 << 16, Token: 2 << 32}
)

type transferOperation struct {
	accountId types.AccountId
	amount    types.Amount
}

func TestCryptoTransferTransactionConstructorSuite(t *testing.T) {
	suite.Run(t, new(cryptoTransferTransactionConstructorSuite))
}

type cryptoTransferTransactionConstructorSuite struct {
	suite.Suite
}

func (suite *cryptoTransferTransactionConstructorSuite) TestNewTransactionConstructor() {
	h := newCryptoTransferTransactionConstructor()
	assert.NotNil(suite.T(), h)
}

func (suite *cryptoTransferTransactionConstructorSuite) TestGetDefaultMaxTransactionFee() {
	h := newCryptoTransferTransactionConstructor()
	assert.Equal(suite.T(), types.HbarAmount{Value: 1_00000000}, h.GetDefaultMaxTransactionFee())
}

func (suite *cryptoTransferTransactionConstructorSuite) TestGetOperationType() {
	h := newCryptoTransferTransactionConstructor()
	assert.Equal(suite.T(), types.OperationTypeCryptoTransfer, h.GetOperationType())
}

func (suite *cryptoTransferTransactionConstructorSuite) TestGetSdkTransactionType() {
	h := newCryptoTransferTransactionConstructor()
	assert.Equal(suite.T(), "TransferTransaction", h.GetSdkTransactionType())
}

func (suite *cryptoTransferTransactionConstructorSuite) TestConstruct() {
	var tests = []struct {
		name            string
		transfers       []transferOperation
		expectError     bool
		expectedSigners []types.AccountId
	}{
		{name: "Success", transfers: defaultTransfers, expectedSigners: defaultSigners},
		{name: "EmptyOperations", expectError: true},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			operations := suite.makeOperations(tt.transfers)
			h := newCryptoTransferTransactionConstructor()

			// when
			tx, signers, err := h.Construct(defaultContext, operations)

			// then
			if tt.expectError {
				assert.NotNil(t, err)
				assert.Nil(t, signers)
				assert.Nil(t, tx)
			} else {
				assert.Nil(t, err)
				assert.ElementsMatch(t, tt.expectedSigners, signers)
				assertCryptoTransferTransaction(t, operations, tx)
			}
		})
	}
}

func (suite *cryptoTransferTransactionConstructorSuite) TestParse() {
	defaultGetTransaction := func() interfaces.Transaction {
		return hedera.NewTransferTransaction().
			AddHbarTransfer(sdkAccountIdA, hedera.HbarFromTinybar(-15)).
			AddHbarTransfer(sdkAccountIdB, hedera.HbarFromTinybar(15)).
			AddTokenTransferWithDecimals(tokenIdA, sdkAccountIdA, -25, uint32(dbTokenA.Decimals)).
			AddTokenTransferWithDecimals(tokenIdA, sdkAccountIdB, 25, uint32(dbTokenA.Decimals)).
			AddTokenTransferWithDecimals(tokenIdB, sdkAccountIdB, -35, uint32(dbTokenB.Decimals)).
			AddTokenTransferWithDecimals(tokenIdB, sdkAccountIdA, 35, uint32(dbTokenB.Decimals)).
			AddNftTransfer(nftId, sdkAccountIdA, sdkAccountIdB).
			SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdA))
	}

	expectedTransfers := []string{
		transferStringify(sdkAccountIdA, -15, currencyHbar.Symbol, uint32(currencyHbar.Decimals), 0),
		transferStringify(sdkAccountIdB, 15, currencyHbar.Symbol, uint32(currencyHbar.Decimals), 0),
		transferStringify(sdkAccountIdA, -25, tokenIdA.String(), uint32(dbTokenA.Decimals), 0),
		transferStringify(sdkAccountIdB, 25, tokenIdA.String(), uint32(dbTokenA.Decimals), 0),
		transferStringify(sdkAccountIdB, -35, tokenIdB.String(), uint32(dbTokenB.Decimals), 0),
		transferStringify(sdkAccountIdA, 35, tokenIdB.String(), uint32(dbTokenB.Decimals), 0),
		transferStringify(sdkAccountIdA, -1, tokenIdC.String(), 0, 1), // nft
		transferStringify(sdkAccountIdB, 1, tokenIdC.String(), 0, 1),  // nft
	}

	var tests = []struct {
		name           string
		getTransaction func() interfaces.Transaction
		expectError    bool
	}{
		{
			name:           "Success",
			getTransaction: defaultGetTransaction,
		},
		{
			name: "InvalidTransaction",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTokenCreateTransaction()
			},
			expectError: true,
		},
		{
			name: "NoExpectedDecimals",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTransferTransaction().
					AddTokenTransfer(tokenIdA, sdkAccountIdA, -25).
					AddTokenTransfer(tokenIdA, sdkAccountIdB, 25).
					SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdA))
			},
			expectError: true,
		},
		{
			name: "InvalidFungibleCommonTokenId",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTransferTransaction().
					AddTokenTransferWithDecimals(outOfRangeTokenId, sdkAccountIdA, -25, decimals).
					AddTokenTransferWithDecimals(outOfRangeTokenId, sdkAccountIdB, 25, decimals).
					SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdA))
			},
			expectError: true,
		},
		{
			name: "InvalidNonFungibleUniqueTokenId",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTransferTransaction().
					AddNftTransfer(
						hedera.NftID{TokenID: outOfRangeTokenId, SerialNumber: 1},
						sdkAccountIdA,
						sdkAccountIdB,
					).
					SetTransactionID(hedera.TransactionIDGenerate(sdkAccountIdA))
			},
			expectError: true,
		},
		{
			name: "OutOfRangeAccountIdHbarTransfer",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTransferTransaction().
					AddHbarTransfer(outOfRangeAccountId, hedera.HbarFromTinybar(-15)).
					AddHbarTransfer(sdkAccountIdB, hedera.HbarFromTinybar(15)).
					SetTransactionID(hedera.TransactionIDGenerate(outOfRangeAccountId))
			},
			expectError: true,
		},
		{
			name: "OutOfRangeAccountIdFungibleTokenTransfer",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTransferTransaction().
					AddTokenTransferWithDecimals(tokenIdA, outOfRangeAccountId, -25, uint32(dbTokenA.Decimals)).
					AddTokenTransferWithDecimals(tokenIdA, sdkAccountIdB, 25, uint32(dbTokenA.Decimals)).
					SetTransactionID(hedera.TransactionIDGenerate(outOfRangeAccountId))
			},
			expectError: true,
		},
		{
			name: "OutOfRangeAccountIdNonFungibleTokenTransfer",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTransferTransaction().
					AddNftTransfer(nftId, outOfRangeAccountId, sdkAccountIdB).
					SetTransactionID(hedera.TransactionIDGenerate(outOfRangeAccountId))
			},
			expectError: true,
		},
		{
			name: "TransactionIDNotSet",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTransferTransaction()
			},
			expectError: true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			h := newCryptoTransferTransactionConstructor()
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
				assert.ElementsMatch(t, defaultSigners, signers)

				actualTransfers := make([]string, 0, len(operations))
				for _, operation := range operations {
					actualTransfers = append(actualTransfers, operationTransferStringify(operation))
				}
				assert.ElementsMatch(t, expectedTransfers, actualTransfers)
			}
		})
	}
}

func (suite *cryptoTransferTransactionConstructorSuite) TestPreprocess() {
	var tests = []struct {
		name            string
		transfers       []transferOperation
		operations      types.OperationSlice
		expectError     bool
		expectedSigners []types.AccountId
	}{
		{
			name:            "Success",
			transfers:       defaultTransfers,
			expectedSigners: defaultSigners,
		},
		{
			name:        "ZeroAmount",
			transfers:   []transferOperation{{accountId: accountIdA, amount: &types.HbarAmount{}}},
			expectError: true,
		},

		{
			name: "InvalidHbarSum",
			transfers: []transferOperation{
				{accountId: accountIdA, amount: &types.HbarAmount{Value: -15}},
				{accountId: accountIdB, amount: &types.HbarAmount{Value: 25}},
			},
			expectError: true,
		},
		{
			name: "InvalidTokenSum",
			transfers: []transferOperation{
				{accountId: accountIdA, amount: types.NewTokenAmount(dbTokenA, -15)},
				{accountId: accountIdB, amount: types.NewTokenAmount(dbTokenA, 20)},
			},
			expectError: true,
		},
		{
			name: "InvalidNftAmount",
			transfers: []transferOperation{
				{accountId: accountIdA, amount: types.NewTokenAmount(dbTokenC, -2).SetSerialNumbers([]int64{1, 2})},
				{accountId: accountIdB, amount: types.NewTokenAmount(dbTokenC, 2).SetSerialNumbers([]int64{1, 2})},
			},
			expectError: true,
		},
		{
			name: "InvalidNftTransferSum",
			transfers: []transferOperation{
				{accountId: accountIdA, amount: types.NewTokenAmount(dbTokenC, -1).SetSerialNumbers([]int64{1})},
			},
			expectError: true,
		},
		{
			name: "DoubleNftTransfer",
			transfers: []transferOperation{
				{accountId: accountIdA, amount: types.NewTokenAmount(dbTokenC, -1).SetSerialNumbers([]int64{1})},
				{accountId: accountIdA, amount: types.NewTokenAmount(dbTokenC, -1).SetSerialNumbers([]int64{1})},
				{accountId: accountIdB, amount: types.NewTokenAmount(dbTokenC, 1).SetSerialNumbers([]int64{1})},
				{accountId: accountIdB, amount: types.NewTokenAmount(dbTokenC, 1).SetSerialNumbers([]int64{1})},
			},
			expectError: true,
		},
		{
			name: "InvalidOperationType",
			operations: types.OperationSlice{
				{
					AccountId: accountIdA,
					Type:      types.OperationTypeTokenWipe,
					Amount:    &types.HbarAmount{Value: 100},
				},
			},
			expectError: true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			operations := tt.operations
			if len(tt.operations) == 0 {
				operations = suite.makeOperations(tt.transfers)
			}

			h := newCryptoTransferTransactionConstructor()

			// when
			signers, err := h.Preprocess(defaultContext, operations)

			// then
			if tt.expectError {
				assert.NotNil(t, err)
				assert.Nil(t, signers)
			} else {
				assert.Nil(t, err)
				assert.ElementsMatch(t, tt.expectedSigners, signers)
			}
		})
	}
}

func (suite *cryptoTransferTransactionConstructorSuite) makeOperations(transfers []transferOperation) types.OperationSlice {
	operations := make(types.OperationSlice, 0, len(transfers))
	for _, transfer := range transfers {
		operation := types.Operation{
			AccountId: transfer.accountId,
			Index:     int64(len(operations)),
			Type:      types.OperationTypeCryptoTransfer,
			Amount:    transfer.amount,
		}
		operations = append(operations, operation)
	}
	return operations
}

func assertCryptoTransferTransaction(
	t *testing.T,
	operations types.OperationSlice,
	actual interfaces.Transaction,
) {
	assert.IsType(t, &hedera.TransferTransaction{}, actual)
	assert.False(t, actual.IsFrozen())

	expectedTransfers := make([]string, 0, len(operations))
	for _, operation := range operations {
		expectedTransfers = append(
			expectedTransfers,
			operationTransferStringify(operation),
		)
	}

	tx, _ := actual.(*hedera.TransferTransaction)

	actualHbarTransfers := tx.GetHbarTransfers()
	actualTokenTransfers := tx.GetTokenTransfers()
	actualTokenDecimals := tx.GetTokenIDDecimals()
	actualNftTransfers := tx.GetNftTransfers()

	actualTransfers := make([]string, 0)
	for accountId, amount := range actualHbarTransfers {
		actualTransfers = append(
			actualTransfers,
			transferStringify(accountId, amount.AsTinybar(), currencyHbar.Symbol, uint32(currencyHbar.Decimals), 0),
		)
	}

	for token, tokenTransfers := range actualTokenTransfers {
		decimals, ok := actualTokenDecimals[token]
		assert.True(t, ok, "no decimals found for token %s", token.String())
		for _, transfer := range tokenTransfers {
			actualTransfers = append(
				actualTransfers,
				transferStringify(transfer.AccountID, transfer.Amount, token.String(), decimals, 0),
			)
		}
	}

	for token, nftTransfers := range actualNftTransfers {
		for _, nftTransfer := range nftTransfers {
			actualTransfers = append(
				actualTransfers,
				transferStringify(nftTransfer.ReceiverAccountID, 1, token.String(), 0, nftTransfer.SerialNumber),
				transferStringify(nftTransfer.SenderAccountID, -1, token.String(), 0, nftTransfer.SerialNumber),
			)
		}
	}

	assert.ElementsMatch(t, expectedTransfers, actualTransfers)
}

func operationTransferStringify(operation types.Operation) string {
	serialNumber := int64(0)
	amount := operation.Amount
	if tokenAmount, ok := amount.(*types.TokenAmount); ok {
		if len(tokenAmount.SerialNumbers) > 0 {
			serialNumber = tokenAmount.SerialNumbers[0]
		}
	}
	return fmt.Sprintf("%s_%d_%s_%d_%d", operation.AccountId.ToSdkAccountId(), amount.GetValue(), amount.GetSymbol(),
		amount.GetDecimals(), serialNumber)
}

func transferStringify(
	account hedera.AccountID,
	amount int64,
	symbol string,
	decimals uint32,
	serialNumber int64,
) string {
	return fmt.Sprintf("%s_%d_%s_%d_%d", account, amount, symbol, decimals, serialNumber)
}
