/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/cucumber/godog"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/test/bdd-client/client"
	"github.com/hashgraph/hedera-sdk-go/v2"
)

const (
	operationStatusSuccess = "SUCCESS"

	operationTypeCryptoCreateAccount = "CRYPTOCREATEACCOUNT"
	operationTypeCryptoTransfer      = "CRYPTOTRANSFER"
	operationTypeFee                 = "FEE"
	operationTypeTokenAssociate      = "TOKENASSOCIATE"
	operationTypeTokenBurn           = "TOKENBURN"
	operationTypeTokenCreate         = "TOKENCREATION"
	operationTypeTokenDelete         = "TOKENDELETION"
	operationTypeTokenDissociate     = "TOKENDISSOCIATE" // #nosec
	operationTypeTokenFreeze         = "TOKENFREEZE"
	operationTypeTokenGrantKyc       = "TOKENGRANTKYC"
	operationTypeTokenMint           = "TOKENMINT"
	operationTypeTokenRevokeKyc      = "TOKENREVOKEKYC"
	operationTypeTokenUnfreeze       = "TOKENUNFREEZE"
	operationTypeTokenUpdate         = "TOKENUPDATE"
	operationTypeTokenWipe           = "TOKENWIPE"
)

var (
	currencyHbar = &types.Currency{
		Symbol:   "HBAR",
		Decimals: 8,
		Metadata: map[string]interface{}{"issuer": "Hedera"},
	}
	testClient      client.Client
	treasuryAccount = getRosettaAccountIdentifier(hedera.AccountID{Account: 98})
)

func SetupTestClient(serverCfg client.Server, operators []client.Operator) {
	testClient = client.NewClient(serverCfg, operators)
}

func InitializeScenario(ctx *godog.ScenarioContext) {
	initializeCryptoScenario(ctx)
	InitializeTokenScenario(ctx)
}

func getRosettaAccountIdentifier(accountId hedera.AccountID) *types.AccountIdentifier {
	return &types.AccountIdentifier{Address: accountId.String()}
}
