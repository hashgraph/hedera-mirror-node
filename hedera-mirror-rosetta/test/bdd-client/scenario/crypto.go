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

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/cucumber/godog"
	"github.com/hashgraph/hedera-sdk-go/v2"
	log "github.com/sirupsen/logrus"
)

type cryptoFeature struct {
	*baseFeature
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
			Account:             getRosettaAccountIdentifier(testClient.GetOperator(0).Id),
			Amount: &types.Amount{
				// fund 10 hbar so the account can pay the transaction to delete itself
				Value:    "1000000000",
				Currency: currencyHbar,
			},
			Type:     operationTypeCryptoCreateAccount,
			Metadata: map[string]interface{}{"key": sk.PublicKey().String()},
		},
	}

	return c.submit(ctx, operations, nil)
}

func (c *cryptoFeature) verifyCryptoCreateTransaction(ctx context.Context) error {
	transaction, err := c.findTransaction(ctx, operationTypeCryptoCreateAccount)
	if err != nil {
		return err
	}

	if err = assertTransactionAll(
		transaction,
		assertTransactionOpSuccess,
		assertTransactionOpCount(1, gte),
		assertTransactionOpType(operationTypeCryptoCreateAccount),
		assertTransactionMetadataAndType("entity_id", ""),
	); err != nil {
		return err
	}

	accountIdStr := transaction.Metadata["entity_id"].(string)
	accountId, err := hedera.AccountIDFromString(accountIdStr)
	if err != nil {
		log.Errorf("Invalid account id: %s", accountIdStr)
		return err
	}
	c.newAccountId = &accountId
	log.Infof("Successfully retrieved new account %s from transaction", accountIdStr)

	return nil
}

func (c *cryptoFeature) transferHbarToTreasury(ctx context.Context) error {
	operations := []*types.Operation{
		{
			OperationIdentifier: &types.OperationIdentifier{Index: 0},
			Account:             getRosettaAccountIdentifier(testClient.GetOperator(0).Id),
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

	return c.submit(ctx, operations, nil)
}

func (c *cryptoFeature) verifyCryptoTransferTransaction(ctx context.Context) error {
	transaction, err := c.findTransaction(ctx, operationTypeCryptoTransfer)
	if err != nil {
		return err
	}

	expectedAccountAmounts := []accountAmount{
		{
			Account: getRosettaAccountIdentifier(testClient.GetOperator(0).Id),
			Amount:  &types.Amount{Value: "-1", Currency: currencyHbar},
		},
		{
			Account: treasuryAccount,
			Amount:  &types.Amount{Value: "1", Currency: currencyHbar},
		},
	}

	return assertTransactionAll(
		transaction,
		assertTransactionOpSuccess,
		assertTransactionOpCount(2, gte),
		assertTransactionOpType(operationTypeCryptoTransfer),
		assertTransactionIncludesTransfers(expectedAccountAmounts),
	)
}

func (c *cryptoFeature) cleanup(ctx context.Context, s *godog.Scenario, err error) (context.Context, error) {
	c.baseFeature.cleanup()

	if c.newAccountId != nil {
		testClient.DeleteAccount(*c.newAccountId, c.newAccountKey) // #nosec
	}

	c.newAccountId = nil
	c.newAccountKey = nil

	return ctx, err
}

func initializeCryptoScenario(ctx *godog.ScenarioContext) {
	crypto := &cryptoFeature{baseFeature: &baseFeature{}}

	ctx.After(crypto.cleanup)

	ctx.Step("I create a crypto account", crypto.createCryptoAccount)
	ctx.Step("the DATA API should show the CryptoCreate transaction", crypto.verifyCryptoCreateTransaction)

	ctx.Step("I transfer some hbar to the treasury account", crypto.transferHbarToTreasury)
	ctx.Step("^the DATA API should show the CryptoTransfer transaction$", crypto.verifyCryptoTransferTransaction)
}
