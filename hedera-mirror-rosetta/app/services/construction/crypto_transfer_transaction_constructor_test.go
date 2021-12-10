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
	"strconv"
	"testing"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/mocks"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
)

const (
	accountIdAStr = "0.0.9500"
	accountIdBStr = "0.0.9505"
)

var (
	accountIdA = hedera.AccountID{Account: 9500}
	accountIdB = hedera.AccountID{Account: 9505}

	defaultSerialNumbers = []interface{}{"1"}
	defaultSigners       = []hedera.AccountID{accountIdA, accountIdB}
	defaultTransfers     = []transferOperation{
		{account: accountIdAStr, amount: -15, currency: types.CurrencyHbar},
		{account: accountIdBStr, amount: 15, currency: types.CurrencyHbar},
		{account: accountIdBStr, amount: -25, currency: tokenACurrency},
		{account: accountIdAStr, amount: 25, currency: tokenACurrency},
		{account: accountIdAStr, amount: -30, currency: tokenBCurrency},
		{account: accountIdBStr, amount: 30, currency: tokenBCurrency},
		{account: accountIdAStr, amount: -1, currency: tokenCCurrency, serialNumbers: defaultSerialNumbers},
		{account: accountIdBStr, amount: 1, currency: tokenCCurrency, serialNumbers: defaultSerialNumbers},
	}
)

type transferOperation struct {
	account       string
	amount        int64
	currency      *rTypes.Currency
	serialNumbers []interface{}
}

func TestCryptoTransferTransactionConstructorSuite(t *testing.T) {
	suite.Run(t, new(cryptoTransferTransactionConstructorSuite))
}

type cryptoTransferTransactionConstructorSuite struct {
	suite.Suite
}

func (suite *cryptoTransferTransactionConstructorSuite) TestNewTransactionConstructor() {
	h := newCryptoTransferTransactionConstructor(&mocks.MockTokenRepository{})
	assert.NotNil(suite.T(), h)
}

func (suite *cryptoTransferTransactionConstructorSuite) TestGetOperationType() {
	h := newCryptoTransferTransactionConstructor(&mocks.MockTokenRepository{})
	assert.Equal(suite.T(), types.OperationTypeCryptoTransfer, h.GetOperationType())
}

func (suite *cryptoTransferTransactionConstructorSuite) TestGetSdkTransactionType() {
	h := newCryptoTransferTransactionConstructor(&mocks.MockTokenRepository{})
	assert.Equal(suite.T(), "TransferTransaction", h.GetSdkTransactionType())
}

func (suite *cryptoTransferTransactionConstructorSuite) TestConstruct() {
	var tests = []struct {
		name            string
		transfers       []transferOperation
		validStartNanos int64
		expectError     bool
		expectedSigners []hedera.AccountID
	}{
		{
			name:            "Success",
			transfers:       defaultTransfers,
			expectedSigners: defaultSigners,
		},
		{
			name:            "SuccessValidStartNanos",
			transfers:       defaultTransfers,
			validStartNanos: 100,
			expectedSigners: defaultSigners,
		},
		{
			name:        "EmptyOperations",
			expectError: true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			operations := suite.makeOperations(tt.transfers)
			mockTokenRepo := &mocks.MockTokenRepository{}
			h := newCryptoTransferTransactionConstructor(mockTokenRepo)
			configMockTokenRepo(mockTokenRepo, defaultMockTokenRepoConfigs...)

			// when
			tx, signers, err := h.Construct(defaultContext, nodeAccountId, operations, tt.validStartNanos)

			// then
			if tt.expectError {
				assert.NotNil(t, err)
				assert.Nil(t, signers)
				assert.Nil(t, tx)
			} else {
				assert.Nil(t, err)
				assert.ElementsMatch(t, tt.expectedSigners, signers)
				assertCryptoTransferTransaction(t, operations, nodeAccountId, tx)
				mockTokenRepo.AssertExpectations(t)

				if tt.validStartNanos != 0 {
					assert.Equal(t, tt.validStartNanos, tx.GetTransactionID().ValidStart.UnixNano())
				}
			}
		})
	}
}

func (suite *cryptoTransferTransactionConstructorSuite) TestParse() {
	defaultGetTransaction := func() interfaces.Transaction {
		return hedera.NewTransferTransaction().
			SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
			SetTransactionID(hedera.TransactionIDGenerate(accountIdA)).
			AddHbarTransfer(accountIdA, hedera.HbarFromTinybar(-15)).
			AddHbarTransfer(accountIdB, hedera.HbarFromTinybar(15)).
			AddTokenTransfer(tokenIdA, accountIdA, -25).
			AddTokenTransfer(tokenIdA, accountIdB, 25).
			AddTokenTransfer(tokenIdB, accountIdB, -35).
			AddTokenTransfer(tokenIdB, accountIdA, 35).
			AddNftTransfer(hedera.NftID{TokenID: tokenIdC, SerialNumber: 1}, accountIdA, accountIdB)
	}

	expectedTransfers := []string{
		transferStringify(accountIdA, -15, types.CurrencyHbar.Symbol, 0),
		transferStringify(accountIdB, 15, types.CurrencyHbar.Symbol, 0),
		transferStringify(accountIdA, -25, tokenIdA.String(), 0),
		transferStringify(accountIdB, 25, tokenIdA.String(), 0),
		transferStringify(accountIdB, -35, tokenIdB.String(), 0),
		transferStringify(accountIdA, 35, tokenIdB.String(), 0),
		transferStringify(accountIdA, -1, tokenIdC.String(), 1),
		transferStringify(accountIdB, 1, tokenIdC.String(), 1),
	}

	var tests = []struct {
		name           string
		tokenRepoErr   bool
		getTransaction func() interfaces.Transaction
		expectError    bool
	}{
		{
			name:           "Success",
			getTransaction: defaultGetTransaction,
		},
		{
			name:           "TokenNotFound",
			tokenRepoErr:   true,
			getTransaction: defaultGetTransaction,
			expectError:    true,
		},
		{
			name: "InvalidTransaction",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTokenCreateTransaction()
			},
			expectError: true,
		},
		{
			name: "TransactionIDNotSet",
			getTransaction: func() interfaces.Transaction {
				return hedera.NewTransferTransaction().SetNodeAccountIDs([]hedera.AccountID{nodeAccountId})
			},
			expectError: true,
		},
	}

	for _, tt := range tests {
		suite.T().Run(tt.name, func(t *testing.T) {
			// given
			mockTokenRepo := &mocks.MockTokenRepository{}
			h := newCryptoTransferTransactionConstructor(mockTokenRepo)
			tx := tt.getTransaction()

			if tt.tokenRepoErr {
				configMockTokenRepo(mockTokenRepo, mockTokenRepoNotFoundConfigs...)
			} else {
				configMockTokenRepo(mockTokenRepo, defaultMockTokenRepoConfigs...)
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
				assert.ElementsMatch(t, []hedera.AccountID{accountIdA, accountIdB}, signers)
				mockTokenRepo.AssertExpectations(t)

				actualTransfers := make([]string, 0, len(operations))
				for _, operation := range operations {
					actualTransfers = append(
						actualTransfers,
						operationTransferStringify(operation),
					)
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
		operations      []*rTypes.Operation
		tokenRepoErr    bool
		expectError     bool
		expectedSigners []hedera.AccountID
	}{
		{
			name:            "Success",
			transfers:       defaultTransfers,
			expectedSigners: defaultSigners,
		},
		{
			name: "InvalidAccountAddress",
			transfers: []transferOperation{
				{account: "x.y.z", amount: -15, currency: types.CurrencyHbar},
				{account: accountIdBStr, amount: 15, currency: types.CurrencyHbar},
				{account: accountIdBStr, amount: -25, currency: tokenACurrency},
				{account: accountIdAStr, amount: 25, currency: tokenACurrency},
				{account: accountIdAStr, amount: -30, currency: tokenBCurrency},
				{account: accountIdBStr, amount: 30, currency: tokenBCurrency},
			},
			expectError: true,
		},
		{
			name: "InvalidTokenAddress",
			transfers: []transferOperation{
				{account: accountIdAStr, amount: -15, currency: types.CurrencyHbar},
				{account: accountIdBStr, amount: 15, currency: types.CurrencyHbar},
				{account: accountIdBStr, amount: -25, currency: &rTypes.Currency{Symbol: "x.y.z", Decimals: 6}},
				{account: accountIdAStr, amount: 25, currency: tokenACurrency},
				{account: accountIdAStr, amount: -30, currency: tokenBCurrency},
				{account: accountIdBStr, amount: 30, currency: tokenBCurrency},
			},
			expectError: true,
		},
		{
			name: "ZeroAmount",
			transfers: []transferOperation{
				{account: accountIdAStr, amount: 0, currency: types.CurrencyHbar},
				{account: accountIdBStr, amount: 15, currency: types.CurrencyHbar},
				{account: accountIdBStr, amount: -25, currency: tokenACurrency},
				{account: accountIdAStr, amount: 25, currency: tokenACurrency},
				{account: accountIdAStr, amount: -30, currency: tokenBCurrency},
				{account: accountIdBStr, amount: 30, currency: tokenBCurrency},
			},
			expectError: true,
		},

		{
			name: "InvalidHbarSum",
			transfers: []transferOperation{
				{account: accountIdAStr, amount: -15, currency: types.CurrencyHbar},
				{account: accountIdBStr, amount: 10, currency: types.CurrencyHbar},
				{account: accountIdBStr, amount: -25, currency: tokenACurrency},
				{account: accountIdAStr, amount: 25, currency: tokenACurrency},
			},
			expectError: true,
		},
		{
			name: "InvalidTokenSum",
			transfers: []transferOperation{
				{account: accountIdAStr, amount: -15, currency: types.CurrencyHbar},
				{account: accountIdBStr, amount: 10, currency: types.CurrencyHbar},
				{account: accountIdBStr, amount: -25, currency: tokenACurrency},
				{account: accountIdAStr, amount: 20, currency: tokenACurrency},
			},
			expectError: true,
		},
		{
			name: "TokenDecimalsMismatch",
			transfers: []transferOperation{
				{account: accountIdAStr, amount: -15, currency: types.CurrencyHbar},
				{account: accountIdBStr, amount: 10, currency: types.CurrencyHbar},
				{
					account:  accountIdBStr,
					amount:   -25,
					currency: &rTypes.Currency{Symbol: tokenIdA.String(), Decimals: 1980},
				},
				{account: accountIdAStr, amount: 25, currency: tokenACurrency},
			},
			expectError: true,
		},
		{
			name: "TokenNotFound",
			transfers: []transferOperation{
				{account: accountIdAStr, amount: -15, currency: types.CurrencyHbar},
				{account: accountIdBStr, amount: 15, currency: types.CurrencyHbar},
				{account: accountIdBStr, amount: -25, currency: tokenACurrency},
				{account: accountIdAStr, amount: 25, currency: tokenACurrency},
				{account: accountIdAStr, amount: -30, currency: tokenBCurrency},
				{account: accountIdBStr, amount: 30, currency: tokenBCurrency},
			},
			tokenRepoErr: true,
			expectError:  true,
		},
		{
			name: "InvalidNftAmount",
			transfers: []transferOperation{
				{account: accountIdAStr, amount: -2, currency: tokenCCurrency, serialNumbers: []interface{}{"1", "2"}},
				{account: accountIdBStr, amount: 2, currency: tokenCCurrency, serialNumbers: []interface{}{"1", "2"}},
			},
			expectError: true,
		},
		{
			name: "InvalidNftTransferSum",
			transfers: []transferOperation{
				{account: accountIdAStr, amount: -1, currency: tokenCCurrency, serialNumbers: defaultSerialNumbers},
			},
			expectError: true,
		},
		{
			name: "DoubleNftTransfer",
			transfers: []transferOperation{
				{account: accountIdAStr, amount: -1, currency: tokenCCurrency, serialNumbers: defaultSerialNumbers},
				{account: accountIdAStr, amount: -1, currency: tokenCCurrency, serialNumbers: defaultSerialNumbers},
				{account: accountIdBStr, amount: 1, currency: tokenCCurrency, serialNumbers: defaultSerialNumbers},
				{account: accountIdBStr, amount: 1, currency: tokenCCurrency, serialNumbers: defaultSerialNumbers},
			},
			expectError: true,
		},
		{
			name: "InvalidCurrencySymbol",
			transfers: []transferOperation{
				{account: accountIdAStr, amount: -10, currency: &rTypes.Currency{Symbol: "badsymbol", Decimals: 0}},
			},
			expectError: true,
		},
		{
			name: "InvalidOperationType",
			operations: []*rTypes.Operation{
				{
					OperationIdentifier: &rTypes.OperationIdentifier{Index: 0},
					Type:                types.OperationTypeTokenWipe,
					Account:             &rTypes.AccountIdentifier{Address: "0.0.158"},
					Amount:              &rTypes.Amount{Value: "100", Currency: types.CurrencyHbar},
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

			mockTokenRepo := &mocks.MockTokenRepository{}
			h := newCryptoTransferTransactionConstructor(mockTokenRepo)

			if !tt.tokenRepoErr {
				configMockTokenRepo(mockTokenRepo, defaultMockTokenRepoConfigs...)
			} else {
				configMockTokenRepo(mockTokenRepo, mockTokenRepoNotFoundConfigs...)
			}

			// when
			signers, err := h.Preprocess(defaultContext, operations)

			// then
			if tt.expectError {
				assert.NotNil(t, err)
				assert.Nil(t, signers)
			} else {
				assert.Nil(t, err)
				assert.ElementsMatch(t, tt.expectedSigners, signers)
				mockTokenRepo.AssertExpectations(t)
			}
		})
	}
}

func (suite *cryptoTransferTransactionConstructorSuite) makeOperations(transfers []transferOperation) []*rTypes.Operation {
	operations := make([]*rTypes.Operation, 0, len(transfers))
	for _, transfer := range transfers {
		operation := rTypes.Operation{
			OperationIdentifier: &rTypes.OperationIdentifier{Index: int64(len(operations))},
			Type:                types.OperationTypeCryptoTransfer,
			Account:             &rTypes.AccountIdentifier{Address: transfer.account},
			Amount: &rTypes.Amount{
				Value:    strconv.FormatInt(transfer.amount, 10),
				Currency: transfer.currency,
			},
		}
		if len(transfer.serialNumbers) != 0 {
			operation.Amount.Metadata = map[string]interface{}{types.MetadataKeySerialNumbers: transfer.serialNumbers}
		}
		operations = append(operations, &operation)
	}

	return operations
}

func assertCryptoTransferTransaction(
	t *testing.T,
	operations []*rTypes.Operation,
	nodeAccountId hedera.AccountID,
	actual interfaces.Transaction,
) {
	assert.IsType(t, &hedera.TransferTransaction{}, actual)
	assert.True(t, actual.IsFrozen())

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
	actualNftTransfers := tx.GetNftTransfers()

	actualTransfers := make([]string, 0)
	for accountId, amount := range actualHbarTransfers {
		actualTransfers = append(
			actualTransfers,
			transferStringify(accountId, amount.AsTinybar(), types.CurrencyHbar.Symbol, 0),
		)
	}

	for token, tokenTransfers := range actualTokenTransfers {
		for _, transfer := range tokenTransfers {
			actualTransfers = append(
				actualTransfers,
				transferStringify(transfer.AccountID, transfer.Amount, token.String(), 0),
			)
		}
	}

	for token, nftTransfers := range actualNftTransfers {
		for _, nftTransfer := range nftTransfers {
			actualTransfers = append(
				actualTransfers,
				transferStringify(nftTransfer.ReceiverAccountID, 1, token.String(), nftTransfer.SerialNumber),
				transferStringify(nftTransfer.SenderAccountID, -1, token.String(), nftTransfer.SerialNumber),
			)
		}
	}

	assert.ElementsMatch(t, expectedTransfers, actualTransfers)
	assert.ElementsMatch(t, []hedera.AccountID{nodeAccountId}, actual.GetNodeAccountIDs())
}

func operationTransferStringify(operation *rTypes.Operation) string {
	serialNumber := "0"
	amount := operation.Amount
	serialNumbersMetadata := amount.Metadata[types.MetadataKeySerialNumbers]
	if serialNumbersMetadata != nil {
		if serialNumbers, ok := serialNumbersMetadata.([]interface{}); ok {
			serialNumber = serialNumbers[0].(string)
		}
	}

	return fmt.Sprintf("%s_%s_%s_%s", operation.Account.Address, amount.Value, amount.Currency.Symbol,
		serialNumber)
}

func transferStringify(account hedera.AccountID, amount int64, symbol string, serialNumber int64) string {
	return fmt.Sprintf("%s_%d_%s_%d", account, amount, symbol, serialNumber)
}
