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
	"reflect"
	"time"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/cucumber/godog"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/bdd-client/client"
	"github.com/hashgraph/hedera-sdk-go/v2"
	log "github.com/sirupsen/logrus"
	"github.com/thanhpk/randstr"
)

const (
	decimals            = int64(5)
	adminOperatorIndex  = 0
	normalOperatorIndex = 1

	burnAmount     = 2
	mintAmount     = 5
	wipeAmount     = 1
	transferAmount = 2
)

type tokenFeature struct {
	*baseFeature
	adminOperator  client.Operator
	normalOperator client.Operator
	tokenId        *hedera.TokenID

	currency                 *types.Currency
	decimals                 int64
	metadatas                []string // base64 encoding
	burntSerialNumbers       []string
	serialNumbers            []string
	transferredSerialNumbers []string
	tokenType                string
}

func (t *tokenFeature) createToken(ctx context.Context, tokenType string) error {
	log.Infof("Creating %s token", tokenType)
	t.tokenType = tokenType
	if t.tokenType == "FUNGIBLE_COMMON" {
		t.decimals = decimals
	}

	operator := t.adminOperator
	pkStr := operator.PrivateKey.PublicKey().String()
	operations := []*types.Operation{
		{
			OperationIdentifier: &types.OperationIdentifier{Index: 0},
			Account:             getRosettaAccountIdentifier(operator.Id),
			Type:                operationTypeTokenCreate,
			Metadata: map[string]interface{}{
				"admin_key":  pkStr,
				"decimals":   t.decimals,
				"freeze_key": pkStr,
				"kyc_key":    pkStr,
				"name":       randstr.String(6),
				"memo":       "rosetta bdd token test " + time.Now().Format(time.RFC3339),
				"symbol":     randstr.String(6),
				"supply_key": pkStr,
				"type":       t.tokenType,
				"wipe_key":   pkStr,
			},
		},
	}

	return t.submit(ctx, operations, nil)
}

func (t *tokenFeature) verifyTokenCreateTransaction(ctx context.Context) error {
	log.Info("Verifying the TokenCreate transaction")

	transaction, err := t.findTransaction(ctx, operationTypeTokenCreate)
	if err != nil {
		return err
	}

	if err = assertTransactionAll(
		transaction,
		assertTransactionOpSuccess,
		assertTransactionOpCount(1, gte),
		assertTransactionOpType(operationTypeTokenCreate),
		assertTransactionMetadataAndType("entity_id", ""),
	); err != nil {
		return err
	}

	tokenIdStr := transaction.Metadata["entity_id"].(string)
	tokenId, err := hedera.TokenIDFromString(tokenIdStr)
	if err != nil {
		log.Errorf("Invalid token id: %s", tokenIdStr)
		return err
	}
	t.setTokenId(tokenId)
	log.Infof("Successfully retrieved new token %s from transaction", tokenIdStr)

	return nil
}

func (t *tokenFeature) tokenAssociateOrDissociate(ctx context.Context, associate bool) error {
	opType := operationTypeTokenAssociate
	if !associate {
		opType = operationTypeTokenDissociate
	}

	log.Infof("%s account %s and token %s", opType, t.normalOperator.Id, t.tokenId)

	operator := t.normalOperator
	operations := []*types.Operation{
		{
			OperationIdentifier: &types.OperationIdentifier{Index: 0},
			Account:             getRosettaAccountIdentifier(operator.Id),
			Amount:              &types.Amount{Value: "0", Currency: t.currency},
			Type:                opType,
		},
	}

	return t.submit(ctx, operations, getSigners(operator))
}

func (t *tokenFeature) verifyTokenAssociateOrDissociate(ctx context.Context, associate bool) error {
	opType := operationTypeTokenAssociate
	if !associate {
		opType = operationTypeTokenDissociate
	}

	log.Infof("Verifying %s transaction for account %s and token %s", opType, t.normalOperator.Id, t.tokenId)

	transaction, err := t.findTransaction(ctx, opType)
	if err != nil {
		return err
	}

	operator := t.normalOperator
	return assertTransactionAll(
		transaction,
		assertTransactionOpSuccess,
		assertTransactionOpCount(1, gte),
		assertTransactionOpType(opType),
		assertTransactionMetadata("entity_id", operator.Id.String()),
	)
}

func (t *tokenFeature) tokenAssociate(ctx context.Context) error {
	return t.tokenAssociateOrDissociate(ctx, true)
}

func (t *tokenFeature) verifyTokenAssociate(ctx context.Context) error {
	return t.verifyTokenAssociateOrDissociate(ctx, true)
}

func (t *tokenFeature) tokenDissociate(ctx context.Context) error {
	return t.tokenAssociateOrDissociate(ctx, false)
}

func (t *tokenFeature) verifyTokenDissociate(ctx context.Context) error {
	return t.verifyTokenAssociateOrDissociate(ctx, false)
}

func (t *tokenFeature) tokenFreezeOrUnfreezeAccount(ctx context.Context, freeze bool) error {
	opType := operationTypeTokenFreeze
	if !freeze {
		opType = operationTypeTokenUnfreeze
	}

	log.Infof("%s account %s and token %s", opType, t.normalOperator.Id, t.tokenId)

	admin := t.adminOperator
	operations := []*types.Operation{
		{
			OperationIdentifier: &types.OperationIdentifier{Index: 0},
			Account:             getRosettaAccountIdentifier(t.normalOperator.Id),
			Amount:              &types.Amount{Value: "0", Currency: t.currency},
			Metadata:            map[string]interface{}{"payer": admin.Id.String()},
			Type:                opType,
		},
	}

	return t.submit(ctx, operations, getSigners(admin))
}

func (t *tokenFeature) verifyTokenFreezeOrUnfreezeAccount(ctx context.Context, freeze bool) error {
	opType := operationTypeTokenFreeze
	if !freeze {
		opType = operationTypeTokenUnfreeze
	}

	log.Infof("Verifying %s for account %s and token %s", opType, t.normalOperator.Id, t.tokenId)

	transaction, err := t.findTransaction(ctx, opType)
	if err != nil {
		return err
	}

	return assertTransactionAll(
		transaction,
		assertTransactionOpSuccess,
		assertTransactionOpCount(1, gte),
		assertTransactionOpType(opType),
		assertTransactionMetadata("entity_id", t.normalOperator.Id.String()),
	)
}

func (t *tokenFeature) tokenFreezeAccount(ctx context.Context) error {
	return t.tokenFreezeOrUnfreezeAccount(ctx, true)
}

func (t *tokenFeature) verifyTokenFreezeAccount(ctx context.Context) error {
	return t.verifyTokenFreezeOrUnfreezeAccount(ctx, true)
}

func (t *tokenFeature) tokenUnfreezeAccount(ctx context.Context) error {
	return t.tokenFreezeOrUnfreezeAccount(ctx, false)
}

func (t *tokenFeature) verifyTokenUnfreezeAccount(ctx context.Context) error {
	return t.verifyTokenFreezeOrUnfreezeAccount(ctx, false)
}

func (t *tokenFeature) tokenKycGrantOrRevokeAccount(ctx context.Context, grant bool) error {
	opType := operationTypeTokenGrantKyc
	if !grant {
		opType = operationTypeTokenRevokeKyc
	}

	log.Infof("%s account %s and token %s", opType, t.normalOperator.Id, t.tokenId)

	admin := t.adminOperator
	operations := []*types.Operation{
		{
			OperationIdentifier: &types.OperationIdentifier{Index: 0},
			Account:             getRosettaAccountIdentifier(t.normalOperator.Id),
			Amount:              &types.Amount{Value: "0", Currency: t.currency},
			Metadata:            map[string]interface{}{"payer": admin.Id.String()},
			Type:                opType,
		},
	}

	return t.submit(ctx, operations, getSigners(admin))
}

func (t *tokenFeature) verifyTokenKycGrantOrRevokeAccount(ctx context.Context, grant bool) error {
	opType := operationTypeTokenGrantKyc
	if !grant {
		opType = operationTypeTokenRevokeKyc
	}

	log.Infof("Verifying %s for account %s and token %s", opType, t.normalOperator.Id, t.tokenId)

	transaction, err := t.findTransaction(ctx, opType)
	if err != nil {
		return err
	}

	return assertTransactionAll(
		transaction,
		assertTransactionOpSuccess,
		assertTransactionOpCount(1, gte),
		assertTransactionOpType(opType),
		assertTransactionMetadata("entity_id", t.normalOperator.Id.String()),
	)
}

func (t *tokenFeature) tokenKycGrantAccount(ctx context.Context) error {
	return t.tokenKycGrantOrRevokeAccount(ctx, true)
}

func (t *tokenFeature) verifyTokenKycGrantAccount(ctx context.Context) error {
	return t.verifyTokenKycGrantOrRevokeAccount(ctx, true)
}

func (t *tokenFeature) tokenKycRevokeAccount(ctx context.Context) error {
	return t.tokenKycGrantOrRevokeAccount(ctx, false)
}

func (t *tokenFeature) verifyTokenKycRevokeAccount(ctx context.Context) error {
	return t.tokenKycGrantOrRevokeAccount(ctx, false)
}

func (t *tokenFeature) getTokenTransferOperations() []*types.Operation {
	var operations []*types.Operation
	if t.tokenType == domain.TokenTypeFungibleCommon {
		value := fmt.Sprintf("%d", transferAmount)
		operations = []*types.Operation{
			{
				OperationIdentifier: &types.OperationIdentifier{Index: 0},
				Account:             getRosettaAccountIdentifier(t.adminOperator.Id),
				Amount:              &types.Amount{Value: "-" + value, Currency: t.currency},
				Type:                operationTypeCryptoTransfer,
			},
			{
				OperationIdentifier: &types.OperationIdentifier{Index: 1},
				Account:             getRosettaAccountIdentifier(t.normalOperator.Id),
				Amount:              &types.Amount{Value: value, Currency: t.currency},
				Type:                operationTypeCryptoTransfer,
			},
		}
	} else {
		t.transferredSerialNumbers = t.serialNumbers[:transferAmount]
		t.serialNumbers = t.serialNumbers[transferAmount:]
		for index, serialNumber := range t.transferredSerialNumbers {
			metadata := map[string]interface{}{"serial_numbers": []string{serialNumber}}
			operations = append(
				operations,
				&types.Operation{
					OperationIdentifier: &types.OperationIdentifier{Index: int64(index * 2)},
					Account:             getRosettaAccountIdentifier(t.adminOperator.Id),
					Amount:              &types.Amount{Value: "-1", Currency: t.currency, Metadata: metadata},
					Type:                operationTypeCryptoTransfer,
				},
				&types.Operation{
					OperationIdentifier: &types.OperationIdentifier{Index: int64(index*2 + 1)},
					Account:             getRosettaAccountIdentifier(t.normalOperator.Id),
					Amount:              &types.Amount{Value: "1", Currency: t.currency, Metadata: metadata},
					Metadata:            metadata,
					Type:                operationTypeCryptoTransfer,
				},
			)
		}
	}

	return operations
}

func (t *tokenFeature) getExpectedAccountAmountForTokenTransfer() []accountAmount {
	var expectedAccountAmounts []accountAmount
	if t.tokenType == domain.TokenTypeFungibleCommon {
		value := fmt.Sprintf("%d", transferAmount)
		expectedAccountAmounts = []accountAmount{
			{
				Account: getRosettaAccountIdentifier(t.adminOperator.Id),
				Amount:  &types.Amount{Value: "-" + value, Currency: t.currency},
			},
			{
				Account: getRosettaAccountIdentifier(t.normalOperator.Id),
				Amount:  &types.Amount{Value: value, Currency: t.currency},
			},
		}
	} else {
		index := 0
		for index < transferAmount {
			serialNumber := t.transferredSerialNumbers[index]
			metadata := map[string]interface{}{"serial_numbers": []interface{}{serialNumber}}
			expectedAccountAmounts = append(
				expectedAccountAmounts,
				accountAmount{
					Account: getRosettaAccountIdentifier(t.adminOperator.Id),
					Amount:  &types.Amount{Value: "-1", Currency: t.currency, Metadata: metadata},
				},
				accountAmount{
					Account: getRosettaAccountIdentifier(t.normalOperator.Id),
					Amount:  &types.Amount{Value: "1", Currency: t.currency, Metadata: metadata},
				},
			)

			index += 1
		}
	}
	return expectedAccountAmounts
}

func (t *tokenFeature) tokenTransfer(ctx context.Context) error {
	log.Infof("Transfer %d %s token %s from %s to %s", transferAmount, t.tokenType, t.tokenId, t.adminOperator.Id,
		t.normalOperator.Id)

	operations := t.getTokenTransferOperations()
	return t.submit(ctx, operations, getSigners(t.adminOperator))
}

func (t *tokenFeature) verifyTokenTransfer(ctx context.Context) error {
	log.Info("Verifying CryptoTransfer transaction with token transfer")

	transaction, err := t.findTransaction(ctx, operationTypeCryptoTransfer)
	if err != nil {
		return err
	}

	expectedAccountAmounts := t.getExpectedAccountAmountForTokenTransfer()

	return assertTransactionAll(
		transaction,
		assertTransactionOpSuccess,
		assertTransactionOpCount(2, gte),
		assertTransactionOpType(operationTypeCryptoTransfer),
		assertTransactionOnlyIncludesTransfers(expectedAccountAmounts, getMatchCurrencyFilter(t.currency), false),
	)
}

func (t *tokenFeature) tokenBurnOrMint(ctx context.Context, burn bool) error {
	opType := operationTypeTokenBurn
	value := fmt.Sprintf("%d", -burnAmount)
	if !burn {
		opType = operationTypeTokenMint
		value = fmt.Sprintf("%d", mintAmount)
	}

	log.Infof("%s token %s", opType, t.tokenId)

	var metadata map[string]interface{}
	if t.tokenType == domain.TokenTypeNonFungibleUnique {
		// set metadata for nft
		if burn {
			t.burntSerialNumbers = t.serialNumbers[:burnAmount]
			t.serialNumbers = t.serialNumbers[burnAmount:]
			metadata = map[string]interface{}{"serial_numbers": t.burntSerialNumbers}
		} else {
			metadata = map[string]interface{}{"metadatas": t.metadatas}
		}
	}

	admin := t.adminOperator
	operations := []*types.Operation{
		{
			OperationIdentifier: &types.OperationIdentifier{Index: 0},
			Account:             getRosettaAccountIdentifier(admin.Id),
			Amount:              &types.Amount{Value: value, Currency: t.currency, Metadata: metadata},
			Type:                opType,
		},
	}

	return t.submit(ctx, operations, getSigners(admin))
}

func (t *tokenFeature) verifyTokenBurnOrMint(ctx context.Context, burn bool) error {
	ignoreAmountMetadata := false
	opType := operationTypeTokenBurn
	amount := &types.Amount{Value: fmt.Sprintf("%d", -burnAmount), Currency: t.currency}
	if !burn {
		opType = operationTypeTokenMint
		amount.Value = fmt.Sprintf("%d", mintAmount)
	}

	log.Infof("Verifying %s transaction", opType)

	transaction, err := t.findTransaction(ctx, opType)
	if err != nil {
		return err
	}

	adminAccountId := getRosettaAccountIdentifier(t.adminOperator.Id)
	expectedAccountAmounts := []accountAmount{{Account: adminAccountId, Amount: amount}}
	if t.tokenType == domain.TokenTypeNonFungibleUnique {
		amount.Value = "-1"
		size := burnAmount
		if !burn {
			amount.Value = "1"
			size = mintAmount
			ignoreAmountMetadata = true // for TokenMint, ignore the serial_numbers metadata as we don't know it apriori
		}

		expectedAccountAmounts = make([]accountAmount, 0, size)
		index := 0
		for index < size {
			accountAmount := accountAmount{Account: adminAccountId, Amount: amount}
			if burn {
				// for TokenBurn, we know the serial numbers
				clone := *accountAmount.Amount
				clone.Metadata = map[string]interface{}{"serial_numbers": []interface{}{t.burntSerialNumbers[index]}}
				accountAmount.Amount = &clone
			}

			expectedAccountAmounts = append(expectedAccountAmounts, accountAmount)
			index += 1
		}
	}

	if err = assertTransactionAll(
		transaction,
		assertTransactionOpSuccess,
		assertTransactionOpCount(1, gte),
		assertTransactionOpType(opType),
		assertTransactionOnlyIncludesTransfers(expectedAccountAmounts, getMatchCurrencyFilter(t.currency), ignoreAmountMetadata),
		assertTransactionMetadata("entity_id", t.tokenId.String()),
	); err != nil {
		return err
	}

	if t.tokenType == domain.TokenTypeNonFungibleUnique && !burn {
		// extract newly mint serial numbers for nft
		for _, operation := range transaction.Operations {
			if reflect.DeepEqual(operation.Amount.Currency, t.currency) {
				serialNumbers := operation.Amount.Metadata["serial_numbers"].([]interface{})
				for _, number := range serialNumbers {
					t.serialNumbers = append(t.serialNumbers, number.(string))
				}
			}
		}

		log.Infof("Extracted mint NON_FUNGIBLE_UNQIUE token serial numbers: %+v", t.serialNumbers)
	}

	return nil
}

func (t *tokenFeature) tokenBurn(ctx context.Context) error {
	return t.tokenBurnOrMint(ctx, true)
}

func (t *tokenFeature) verifyTokenBurn(ctx context.Context) error {
	return t.verifyTokenBurnOrMint(ctx, true)
}

func (t *tokenFeature) tokenMint(ctx context.Context) error {
	return t.tokenBurnOrMint(ctx, false)
}

func (t *tokenFeature) verifyTokenMint(ctx context.Context) error {
	return t.verifyTokenBurnOrMint(ctx, false)
}

func (t *tokenFeature) tokenWipeAccount(ctx context.Context) error {
	log.Infof("TokenWipeAccount for account %s and token %s", t.normalOperator.Id, t.tokenId)

	admin := t.adminOperator
	operation := &types.Operation{
		OperationIdentifier: &types.OperationIdentifier{Index: 0},
		Account:             getRosettaAccountIdentifier(t.normalOperator.Id),
		Amount:              &types.Amount{Value: fmt.Sprintf("%d", -wipeAmount), Currency: t.currency},
		Metadata:            map[string]interface{}{"payer": admin.Id.String()},
		Type:                operationTypeTokenWipe,
	}
	if t.tokenType == domain.TokenTypeNonFungibleUnique {
		operation.Amount.Metadata = map[string]interface{}{
			"serial_numbers": t.transferredSerialNumbers[:wipeAmount],
		}
	}

	return t.submit(ctx, []*types.Operation{operation}, getSigners(admin))
}

func (t *tokenFeature) verifyTokenWipeAccount(ctx context.Context) error {
	log.Info("Verifying TokenWipeAccount transaction")

	transaction, err := t.findTransaction(ctx, operationTypeTokenWipe)
	if err != nil {
		return err
	}

	expectedAccountAmounts := []accountAmount{
		{
			Account: getRosettaAccountIdentifier(t.normalOperator.Id),
			Amount:  &types.Amount{Value: fmt.Sprintf("%d", -wipeAmount), Currency: t.currency},
		},
	}
	if t.tokenType == domain.TokenTypeNonFungibleUnique {
		expectedAccountAmounts = make([]accountAmount, 0, wipeAmount)
		index := 0
		for index < wipeAmount {
			expectedAccountAmounts = append(expectedAccountAmounts, accountAmount{
				Account: getRosettaAccountIdentifier(t.normalOperator.Id),
				Amount: &types.Amount{
					Value:    "-1",
					Currency: t.currency,
					Metadata: map[string]interface{}{
						"serial_numbers": []interface{}{t.transferredSerialNumbers[index]},
					},
				},
			})
			index += 1
		}
	}

	return assertTransactionAll(
		transaction,
		assertTransactionOpSuccess,
		assertTransactionOpCount(1, gte),
		assertTransactionOpType(operationTypeTokenWipe),
		assertTransactionOnlyIncludesTransfers(expectedAccountAmounts, getMatchCurrencyFilter(t.currency), false),
		assertTransactionMetadata("entity_id", t.tokenId.String()),
	)
}

func (t *tokenFeature) tokenDelete(ctx context.Context) error {
	log.Infof("Delete token %s", t.tokenId)
	operations := []*types.Operation{
		{
			OperationIdentifier: &types.OperationIdentifier{Index: 0},
			Account:             getRosettaAccountIdentifier(t.adminOperator.Id),
			Amount:              &types.Amount{Value: "0", Currency: t.currency},
			Type:                operationTypeTokenDelete,
		},
	}

	return t.submit(ctx, operations, getSigners(t.adminOperator))
}

func (t *tokenFeature) verifyTokenDelete(ctx context.Context) error {
	log.Info("Verifying TokenDelete transaction")

	transaction, err := t.findTransaction(ctx, operationTypeTokenDelete)
	if err != nil {
		return err
	}

	return assertTransactionAll(
		transaction,
		assertTransactionOpSuccess,
		assertTransactionOpCount(1, gte),
		assertTransactionOpType(operationTypeTokenDelete),
		assertTransactionMetadata("entity_id", t.tokenId.String()),
	)
}

func (t *tokenFeature) tokenUpdate(ctx context.Context) error {
	log.Infof("Update token %s", t.tokenId)
	metadata := map[string]interface{}{
		"memo":   "rosetta bdd updated token memo",
		"name":   randstr.String(8),
		"symbol": randstr.String(8),
	}
	operations := []*types.Operation{
		{
			OperationIdentifier: &types.OperationIdentifier{Index: 0},
			Account:             getRosettaAccountIdentifier(t.adminOperator.Id),
			Amount:              &types.Amount{Value: "0", Currency: t.currency},
			Metadata:            metadata,
			Type:                operationTypeTokenUpdate,
		},
	}

	return t.submit(ctx, operations, getSigners(t.adminOperator))
}

func (t *tokenFeature) verifyTokenUpdate(ctx context.Context) error {
	log.Info("Verifying TokenUpdate transaction")

	transaction, err := t.findTransaction(ctx, operationTypeTokenUpdate)
	if err != nil {
		return err
	}

	return assertTransactionAll(
		transaction,
		assertTransactionOpSuccess,
		assertTransactionOpCount(1, gte),
		assertTransactionOpType(operationTypeTokenUpdate),
		assertTransactionMetadata("entity_id", t.tokenId.String()),
	)
}

func (t *tokenFeature) setTokenId(tokenId hedera.TokenID) {
	t.tokenId = &tokenId
	t.currency = &types.Currency{
		Symbol:   tokenId.String(),
		Decimals: int32(t.decimals),
		Metadata: map[string]interface{}{"type": t.tokenType},
	}
}

func (t *tokenFeature) cleanup(ctx context.Context, s *godog.Scenario, err error) (context.Context, error) {
	log.Info("Cleaning up token feature")

	t.baseFeature.cleanup()

	if t.tokenId != nil {
		if err1 := testClient.DeleteToken(*t.tokenId); err1 != nil {
			log.Errorf("Failed to delete token: %s", err)
		}
		if err1 := testClient.TokenDissociate(t.normalOperator, *t.tokenId); err1 != nil {
			log.Errorf("Failed to dissociate account %s: %s", t.normalOperator.Id, err1)
		}
		if err1 := testClient.TokenDissociate(t.adminOperator, *t.tokenId); err1 != nil {
			log.Errorf("Failed to dissociate account %s: %s", t.adminOperator.Id, err1)
		}
	}

	t.tokenId = nil
	t.currency = nil
	t.decimals = 0

	t.burntSerialNumbers = nil
	t.serialNumbers = nil
	t.transferredSerialNumbers = nil

	t.tokenType = ""

	return ctx, err
}

func newTokenFeature() *tokenFeature {
	metadatas := make([]string, 0, mintAmount)
	for len(metadatas) < mintAmount {
		metadatas = append(metadatas, randstr.Base64(8))
	}

	return &tokenFeature{
		baseFeature:    &baseFeature{},
		adminOperator:  testClient.GetOperator(adminOperatorIndex),
		normalOperator: testClient.GetOperator(normalOperatorIndex),
		metadatas:      metadatas,
	}
}

func getMatchCurrencyFilter(currency *types.Currency) includeFilterFunc {
	return func(operation *types.Operation) bool {
		return reflect.DeepEqual(currency, operation.Amount.Currency)
	}
}

func getSigners(operators ...client.Operator) map[string]hedera.PrivateKey {
	signers := make(map[string]hedera.PrivateKey)
	for _, operator := range operators {
		signers[operator.Id.String()] = operator.PrivateKey
	}
	return signers
}

func InitializeTokenScenario(ctx *godog.ScenarioContext) {
	token := newTokenFeature()

	ctx.After(token.cleanup)

	ctx.Step("^I create a (FUNGIBLE_COMMON|NON_FUNGIBLE_UNIQUE) token$", token.createToken)
	ctx.Step("the DATA API should show the TokenCreate transaction", token.verifyTokenCreateTransaction)

	ctx.Step("The user associate with the token", token.tokenAssociate)
	ctx.Step("the DATA API should show the TokenAssociate transaction", token.verifyTokenAssociate)

	ctx.Step("The user dissociate with the token", token.tokenDissociate)
	ctx.Step("the DATA API should show the TokenDissociate transaction", token.verifyTokenDissociate)

	ctx.Step("I freeze the transfer of the token for the user", token.tokenFreezeAccount)
	ctx.Step("the DATA API should show the TokenFreezeAccount transaction", token.verifyTokenFreezeAccount)

	ctx.Step("I unfreeze the transfer of the token for the user", token.tokenUnfreezeAccount)
	ctx.Step("the DATA API should show the TokenUnfreezeAccount transaction", token.verifyTokenUnfreezeAccount)

	ctx.Step("I grant kyc to the user for the token", token.tokenKycGrantAccount)
	ctx.Step("the DATA API should show the TokenGrantKyc transaction", token.verifyTokenKycGrantAccount)

	ctx.Step("I revoke kyc from the user for the token", token.tokenKycRevokeAccount)
	ctx.Step("the DATA API should show the TokenRevokeKyc transaction", token.verifyTokenKycRevokeAccount)

	ctx.Step("I mint token", token.tokenMint)
	ctx.Step("the DATA API should show the TokenMint transaction", token.verifyTokenMint)

	ctx.Step("I burn token", token.tokenBurn)
	ctx.Step("the DATA API should show the TokenBurn transaction", token.verifyTokenBurn)

	ctx.Step("I transfer some token from the treasury to the user", token.tokenTransfer)
	ctx.Step("^the DATA API should show the CryptoTransfer transaction with token transfers$",
		token.verifyTokenTransfer)

	ctx.Step("I wipe some token from the user", token.tokenWipeAccount)
	ctx.Step("the DATA API should show the TokenWipeAccount transaction", token.verifyTokenWipeAccount)

	ctx.Step("I delete the token", token.tokenDelete)
	ctx.Step("the DATA API should show the TokenDelete transaction", token.verifyTokenDelete)

	ctx.Step("I update the token", token.tokenUpdate)
	ctx.Step("the DATA API should show the TokenUpdate transaction", token.verifyTokenUpdate)
}
