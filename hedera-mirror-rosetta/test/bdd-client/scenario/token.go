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
	"time"

	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/cucumber/godog"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"github.com/thanhpk/randstr"
)

const decimals = int64(5)

type tokenFeature struct {
	*baseFeature
	tokenId *hedera.TokenID
}

func (t *tokenFeature) createToken(ctx context.Context) error {
	pkStr := testClient.GetFirstOperatorPrivateKey().
		PublicKey().
		String()
	operations := []*types.Operation{
		{
			OperationIdentifier: &types.OperationIdentifier{Index: 0},
			Account:             testClient.GetFirstOperatorAccount(),
			Type:                operationTypeTokenCreate,
			Metadata: map[string]interface{}{
				"admin_key":  pkStr,
				"decimals":   decimals,
				"freeze_key": pkStr,
				"kyc_key":    pkStr,
				"name":       randstr.String(6),
				"memo":       "rosetta bdd token test " + time.Now().String(),
				"symbol":     randstr.String(6),
				"supply_key": pkStr,
				"wipe_key":   pkStr,
			},
		},
	}

	return t.submit(ctx, operations, nil)
}

func (t *tokenFeature) verifyTokenCreateTransaction(ctx context.Context) error {
	// transaction, err := t.findTransaction(ctx, operationTypeTokenCreate)
	// if err != nil {
	// 	return err
	// }

	// // check operation type
	// if err = t.assertOperationType(transaction.Operations, operationTypeTokenCreate); err != nil {
	// 	return err
	// }
	//
	// var a asserter
	// if !assert.Contains(&a, transaction.Metadata, "entity_id") {
	// 	return a.err
	// }

	return nil
}

func (t *tokenFeature) tokenFreezeAccount(ctx context.Context) error {
	return nil
}

func (t *tokenFeature) verifyTokenFreezeAccount(ctx context.Context) error {
	return nil
}

func (t *tokenFeature) tokenUnfreezeAccount(ctx context.Context) error {
	return nil
}

func (t *tokenFeature) verifyTokenUnfreezeAccount(ctx context.Context) error {
	return nil
}

func (t *tokenFeature) tokenKycGrantAccount(ctx context.Context) error {
	return nil
}

func (t *tokenFeature) verifyTokenKycGrantAccount(ctx context.Context) error {
	return nil
}

func (t *tokenFeature) tokenKycRevokeAccount(ctx context.Context) error {
	return nil
}

func (t *tokenFeature) verifyTokenKycRevokeAccount(ctx context.Context) error {
	return nil
}

func (t *tokenFeature) tokenTransfer(ctx context.Context) error {
	return nil
}

func (t *tokenFeature) verifyTokenTransfer(ctx context.Context) error {
	return nil
}

func (t *tokenFeature) tokenMint(ctx context.Context) error {
	return nil
}

func (t *tokenFeature) verifyTokenMint(ctx context.Context) error {
	return nil
}

func (t *tokenFeature) tokenBurn(ctx context.Context) error {
	return nil
}

func (t *tokenFeature) verifyTokenBurn(ctx context.Context) error {
	return nil
}

func (t *tokenFeature) tokenWipeAccount(ctx context.Context) error {
	return nil
}

func (t *tokenFeature) verifyTokenWipeAccount(ctx context.Context) error {
	return nil
}

func (t *tokenFeature) cleanup(ctx context.Context, s *godog.Scenario, err error) (context.Context, error) {
	t.baseFeature.cleanup()
	return nil, err
}

func InitializeTokenScenario(ctx *godog.ScenarioContext) {
	// token := &tokenFeature{baseFeature: &baseFeature{}}
	//
	// ctx.After(token.cleanup)
	//
	// ctx.Step("I create a token", token.createToken)
	// ctx.Step("the DATA API should show the TokenCreate transaction", token.verifyTokenCreateTransaction)
}
