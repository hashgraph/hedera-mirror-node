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
	"reflect"
	"strconv"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/repositories"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/tools/parse"
	"github.com/hashgraph/hedera-sdk-go/v2"
	log "github.com/sirupsen/logrus"
)

type cryptoTransferTransactionConstructor struct {
	tokenRepo       repositories.TokenRepository
	transactionType string
}

type transfer struct {
	account hedera.AccountID
	amount  int64
	token   hedera.TokenID
}

type senderMap map[hedera.AccountID]int

func (m senderMap) toSenders() []hedera.AccountID {
	senders := make([]hedera.AccountID, 0, len(m))
	for sender := range m {
		senders = append(senders, sender)
	}
	return senders
}

func (c *cryptoTransferTransactionConstructor) Construct(nodeAccountId hedera.AccountID, operations []*rTypes.Operation) (
	ITransaction,
	[]hedera.AccountID,
	*rTypes.Error,
) {
	transfers, senders, rErr := c.preprocess(operations)
	if rErr != nil {
		return nil, nil, rErr
	}

	transaction := hedera.NewTransferTransaction()

	for _, transfer := range transfers {
		if isZeroTokenId(transfer.token) {
			transaction.AddHbarTransfer(transfer.account, hedera.HbarFromTinybar(transfer.amount))
		} else {
			transaction.AddTokenTransfer(transfer.token, transfer.account, transfer.amount)
		}
	}

	// set to a single node account ID, so later can add signature
	_, err := transaction.
		SetTransactionID(hedera.TransactionIDGenerate(senders[0])).
		SetNodeAccountIDs([]hedera.AccountID{nodeAccountId}).
		Freeze()
	if err != nil {
		return nil, nil, errors.ErrTransactionFreezeFailed
	}

	return transaction, senders, nil
}

func (c *cryptoTransferTransactionConstructor) GetOperationType() string {
	return config.OperationTypeCryptoTransfer
}

func (c *cryptoTransferTransactionConstructor) GetSdkTransactionType() string {
	return c.transactionType
}

func (c *cryptoTransferTransactionConstructor) Parse(transaction ITransaction) (
	[]*rTypes.Operation,
	[]hedera.AccountID,
	*rTypes.Error,
) {
	transferTransaction, ok := transaction.(*hedera.TransferTransaction)
	if !ok {
		return nil, nil, errors.ErrTransactionInvalidType
	}

	if transferTransaction.GetTransactionID().AccountID == nil {
		return nil, nil, errors.ErrInvalidTransaction
	}

	hbarTransfers := transferTransaction.GetHbarTransfers()
	tokenTransfers := transferTransaction.GetTokenTransfers()

	if len(tokenTransfers) != 0 && c.tokenRepo == nil {
		return nil, nil, errors.ErrOperationTypeUnsupported
	}

	numOperations := len(hbarTransfers)
	for _, sameTokenTransfers := range tokenTransfers {
		numOperations += len(sameTokenTransfers)
	}
	operations := make([]*rTypes.Operation, 0, numOperations)
	senderMap := senderMap{}

	for accountId, hbarAmount := range hbarTransfers {
		operations = c.addOperation(accountId, hbarAmount.AsTinybar(), config.CurrencyHbar, operations, senderMap)
	}

	for token, sameTokenTransfers := range tokenTransfers {
		dbToken, err := c.tokenRepo.Find(token.String())
		if err != nil {
			return nil, nil, err
		}

		currency := dbToken.ToRosettaCurrency()
		for _, tokenTransfer := range sameTokenTransfers {
			operations = c.addOperation(tokenTransfer.AccountID, tokenTransfer.Amount, currency, operations, senderMap)
		}
	}

	return operations, senderMap.toSenders(), nil
}

func (c *cryptoTransferTransactionConstructor) Preprocess(operations []*rTypes.Operation) (
	[]hedera.AccountID,
	*rTypes.Error,
) {
	_, senders, err := c.preprocess(operations)
	if err != nil {
		return nil, err
	}

	return senders, nil
}

func (c *cryptoTransferTransactionConstructor) addOperation(
	accountId hedera.AccountID,
	amount int64,
	currency *rTypes.Currency,
	operations []*rTypes.Operation,
	senderMap senderMap,
) []*rTypes.Operation {
	operation := &rTypes.Operation{
		OperationIdentifier: &rTypes.OperationIdentifier{Index: int64(len(operations))},
		Type:                c.GetOperationType(),
		Account:             &rTypes.AccountIdentifier{Address: accountId.String()},
		Amount: &rTypes.Amount{
			Value:    strconv.FormatInt(amount, 10),
			Currency: currency,
		},
	}

	if amount < 0 {
		senderMap[accountId] = 1
	}

	return append(operations, operation)
}

func (c *cryptoTransferTransactionConstructor) preprocess(operations []*rTypes.Operation) (
	[]transfer,
	[]hedera.AccountID,
	*rTypes.Error,
) {
	if rErr := validateOperations(operations, 0, c.GetOperationType(), false); rErr != nil {
		return nil, nil, rErr
	}

	currencies := map[string]rTypes.Currency{config.CurrencyHbar.Symbol: *config.CurrencyHbar}
	transfers := make([]transfer, 0, len(operations))
	senderMap := senderMap{}
	sums := make(map[string]int64)

	for _, operation := range operations {
		account, err := hedera.AccountIDFromString(operation.Account.Address)
		if err != nil {
			return nil, nil, errors.ErrInvalidAccount
		}

		amount, err := parse.ToInt64(operation.Amount.Value)
		if err != nil || amount == 0 {
			return nil, nil, errors.ErrInvalidAmount
		}

		currency := operation.Amount.Currency
		if !c.validateCurrency(currency, currencies) {
			return nil, nil, errors.ErrInvalidCurrency
		}

		tokenId, _ := hedera.TokenIDFromString(currency.Symbol)
		transfers = append(transfers, transfer{
			account: account,
			amount:  amount,
			token:   tokenId,
		})

		if amount < 0 {
			senderMap[account] = 1
		}

		sums[currency.Symbol] += amount
	}

	for symbol, sum := range sums {
		if sum != 0 {
			log.Errorf("Transfer sum for symbol %s is not 0", symbol)
			return nil, nil, errors.ErrInvalidOperationsTotalAmount
		}
	}

	return transfers, senderMap.toSenders(), nil
}

func (c *cryptoTransferTransactionConstructor) validateCurrency(
	currency *rTypes.Currency,
	currencies map[string]rTypes.Currency,
) bool {
	if cached, ok := currencies[currency.Symbol]; ok {
		if compareCurrency(&cached, currency) {
			return true
		}
	}

	if c.tokenRepo == nil {
		// offline mode
		return false
	}

	if _, err := hedera.TokenIDFromString(currency.Symbol); err != nil {
		return false
	}

	if _, err := validateToken(c.tokenRepo, currency); err != nil {
		return false
	}

	currencies[currency.Symbol] = *currency
	return true
}

func newCryptoTransferTransactionConstructor(tokenRepo repositories.TokenRepository) transactionConstructorWithType {
	transactionType := reflect.TypeOf(hedera.TransferTransaction{}).Name()
	return &cryptoTransferTransactionConstructor{
		tokenRepo:       tokenRepo,
		transactionType: transactionType,
	}
}
