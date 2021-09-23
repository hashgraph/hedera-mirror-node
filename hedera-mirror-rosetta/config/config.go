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

package config

import "github.com/coinbase/rosetta-sdk-go/types"

const (
	OperationTypeCryptoTransfer  = "CRYPTOTRANSFER"
	OperationTypeTokenAssociate  = "TOKENASSOCIATE"
	OperationTypeTokenBurn       = "TOKENBURN"
	OperationTypeTokenCreate     = "TOKENCREATION"
	OperationTypeTokenDelete     = "TOKENDELETION"
	OperationTypeTokenDissociate = "TOKENDISSOCIATE" // #nosec
	OperationTypeTokenFreeze     = "TOKENFREEZE"
	OperationTypeTokenGrantKyc   = "TOKENGRANTKYC"
	OperationTypeTokenMint       = "TOKENMINT"
	OperationTypeTokenRevokeKyc  = "TOKENREVOKEKYC"
	OperationTypeTokenUnfreeze   = "TOKENUNFREEZE"
	OperationTypeTokenUpdate     = "TOKENUPDATE"
	OperationTypeTokenWipe       = "TOKENWIPE"
)

const (
	Blockchain = "Hedera"

	currencySymbol   = "HBAR"
	currencyDecimals = 8
)

var (
	CurrencyHbar = &types.Currency{
		Symbol:   currencySymbol,
		Decimals: currencyDecimals,
		Metadata: map[string]interface{}{
			"issuer": Blockchain,
		},
	}

	SupportedOperationTypes = []string{
		OperationTypeCryptoTransfer,
		OperationTypeTokenAssociate,
		OperationTypeTokenBurn,
		OperationTypeTokenCreate,
		OperationTypeTokenDelete,
		OperationTypeTokenDissociate,
		OperationTypeTokenFreeze,
		OperationTypeTokenGrantKyc,
		OperationTypeTokenMint,
		OperationTypeTokenRevokeKyc,
		OperationTypeTokenUnfreeze,
		OperationTypeTokenUpdate,
		OperationTypeTokenWipe,
	}
)
