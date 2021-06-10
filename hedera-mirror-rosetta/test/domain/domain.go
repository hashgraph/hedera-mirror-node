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

package domain

import (
	dbTypes "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/types"
	"gorm.io/gorm"
)

const tokenEntityType = 5

func AddEntity(dbClient *gorm.DB, id int64, entityType int) {
	entity := &dbTypes.Entity{
		Id:   id,
		Num:  id,
		Type: entityType,
	}
	dbClient.Create(entity)
}

func AddTransaction(
	dbClient *gorm.DB,
	consensusNs int64,
	entityId int64,
	nodeAccountId int64,
	payerAccountId int64,
	result int,
	transactionHash []byte,
	transactionType int,
	validStartNs int64,
	cryptoTransfers []dbTypes.CryptoTransfer,
	nonFeeTransfers []dbTypes.CryptoTransfer,
	tokenTransfers []dbTypes.TokenTransfer,
) {
	tx := &dbTypes.Transaction{
		ConsensusNs:          consensusNs,
		ChargedTxFee:         17,
		EntityId:             entityId,
		NodeAccountId:        nodeAccountId,
		PayerAccountId:       payerAccountId,
		Result:               result,
		TransactionHash:      transactionHash,
		Type:                 transactionType,
		ValidDurationSeconds: 120,
		ValidStartNs:         validStartNs,
	}
	dbClient.Create(tx)

	if len(cryptoTransfers) != 0 {
		dbClient.Table("crypto_transfer").Create(cryptoTransfers)
	}

	if len(nonFeeTransfers) != 0 {
		dbClient.Table("non_fee_transfer").Create(nonFeeTransfers)
	}

	if len(tokenTransfers) != 0 {
		dbClient.Create(tokenTransfers)
	}
}

func AddToken(
	dbClient *gorm.DB,
	tokenId int64,
	decimals int64,
	freezeDefault bool,
	initialSupply int64,
	treasury int64,
) {
	token := &dbTypes.Token{
		TokenID:           tokenId,
		Decimals:          decimals,
		FreezeDefault:     freezeDefault,
		InitialSupply:     initialSupply,
		Symbol:            "",
		TreasuryAccountId: treasury,
	}
	dbClient.Create(token)

	AddEntity(dbClient, tokenId, tokenEntityType)
}
