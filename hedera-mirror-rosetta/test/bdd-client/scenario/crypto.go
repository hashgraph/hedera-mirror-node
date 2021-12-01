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

package scenario

import (
	"context"
	"fmt"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/cucumber/godog"
	"github.com/hashgraph/hedera-sdk-go/v2"
	log "github.com/sirupsen/logrus"
	"github.com/stretchr/testify/assert"
)

type cryptoFeature struct {
	newAccountId    *hedera.AccountID
	newAccountKey   *hedera.PrivateKey
	transactionHash string
}

func (c *cryptoFeature) createCryptoAccount(ctx context.Context) error {
	sk, err := hedera.GeneratePrivateKey()
	if err != nil {
		log.Errorf("Failed to generate private key for new account: %v", err)
		return err
	}
	c.newAccountKey = &sk
	log.Debug("Generated private key for new account")

	operations := []*types.Operation{
		{
			OperationIdentifier: &types.OperationIdentifier{Index: 0},
			Account:             testClient.GetFirstOperatorAccount(),
			Amount: &types.Amount{
				// fund 10 hbar so the account can pay the transaction to delete itself
				Value:    "1000000000",
				Currency: currencyHbar,
			},
			Type:     operationTypeCryptoCreateAccount,
			Metadata: map[string]interface{}{"key": sk.PublicKey().String()},
		},
	}

	transactionIdentifier, err := testClient.Submit(ctx, operations, nil)
	if err != nil {
		return err
	}

	log.Infof("Submitted CryptoCreate transaction %s succcesfully", transactionIdentifier.Hash)
	c.transactionHash = transactionIdentifier.Hash

	return nil
}

func (c *cryptoFeature) verifyCryptoCreateTransaction(ctx context.Context) error {
	transaction, err := testClient.FindTransaction(ctx, c.transactionHash)
	if err != nil {
		log.Infof("Failed to find cryptocreate with hash %s", c.transactionHash)
		return err
	}

	var t asserter
	if !assert.GreaterOrEqual(
		&t,
		len(transaction.Operations),
		1,
		"Transaction should have at least one operation",
	) {
		return t.err
	}

	operation := transaction.Operations[0]
	if !assert.Equal(&t, operationTypeCryptoCreateAccount, operation.Type) {
		return t.err
	}

	if !assert.Contains(&t, transaction.Metadata, "entity_id") {
		return t.err
	}

	if !assert.IsType(
		&t,
		"",
		transaction.Metadata["entity_id"],
		"Transaction metadata 'entity_id' value should be of type string",
	) {
		return t.err
	}

	accountStr, _ := transaction.Metadata["entity_id"].(string)
	accountId, err := hedera.AccountIDFromString(accountStr)
	if err != nil {
		log.Errorf("Invalid account id: %s", accountStr)
		return err
	}
	c.newAccountId = &accountId
	log.Infof("Successfully retrieved new account %s from transaction", accountStr)

	return err
}

func (c *cryptoFeature) transferHbarToTreasury(ctx context.Context) error {
	operations := []*types.Operation{
		{
			OperationIdentifier: &types.OperationIdentifier{Index: 0},
			Account:             testClient.GetFirstOperatorAccount(),
			Amount:              &types.Amount{Value: "-1", Currency: currencyHbar},
			Type:                operationTypeCryptoTransfer,
		},
		{
			OperationIdentifier: &types.OperationIdentifier{Index: 1},
			Account:             treasuryAccount,
			Amount:              &types.Amount{Value: "1", Currency: currencyHbar},
			Type:                operationTypeCryptoTransfer,
		},
	}

	transactionIdentifier, err := testClient.Submit(ctx, operations, nil)
	if err != nil {
		return err
	}

	log.Infof("Submitted CryptoTransfer transaction %s succcesfully", transactionIdentifier.Hash)
	c.transactionHash = transactionIdentifier.Hash

	return nil
}

func (c *cryptoFeature) verifyCryptoTransferTransaction(ctx context.Context) error {
	transaction, err := testClient.FindTransaction(ctx, c.transactionHash)
	if err != nil {
		log.Infof("Failed to find cryptotransfer with hash %s", c.transactionHash)
		return err
	}

	// check operation type and the transfer from operator to treasury
	actualOperationTypes := make(map[string]int)
	actualTransfers := make([]string, 0)
	for _, operation := range transaction.Operations {
		actualOperationTypes[operation.Type] = 1
		actualTransfers = append(actualTransfers, encodeTransfer(operation))
	}

	var t asserter
	if !assert.Equal(&t, map[string]int{operationTypeCryptoTransfer: 1}, actualOperationTypes) {
		return err
	}

	expectedTransfers := []string{
		fmt.Sprintf("%s_%s_-1", testClient.GetFirstOperatorAccount().Address, currencyHbar.Symbol),
		fmt.Sprintf("%s_%s_1", treasuryAccount.Address, currencyHbar.Symbol),
	}
	if !assert.Subset(&t, actualTransfers, expectedTransfers) {
		return t.err
	}

	return err
}

func (c *cryptoFeature) cleanup(ctx context.Context, s *godog.Scenario, err error) (context.Context, error) {
	if c.newAccountId != nil {
		testClient.DeleteAccount(*c.newAccountId, c.newAccountKey) // #nosec
	}

	c.newAccountId = nil
	c.newAccountKey = nil
	c.transactionHash = ""
	return nil, err
}

func initializeCryptoScenario(ctx *godog.ScenarioContext) {
	crypto := &cryptoFeature{}

	ctx.After(crypto.cleanup)

	ctx.Step("I send a CryptoCreate transaction to network", crypto.createCryptoAccount)
	ctx.Step("the DATA API should show the CryptoCreate transaction", crypto.verifyCryptoCreateTransaction)

	ctx.Step("I transfer some hbar to the treasury account", crypto.transferHbarToTreasury)
	ctx.Step("the DATA API should show the CryptoTransfer transaction", crypto.verifyCryptoTransferTransaction)
}
