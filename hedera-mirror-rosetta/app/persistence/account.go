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

package persistence

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	hErrors "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	log "github.com/sirupsen/logrus"
)

const (
	balanceChangeBetween = "with" + genesisTimestampCte + `select
                              coalesce((
                                select sum(amount) from crypto_transfer
                                where
                                  consensus_timestamp > @start and
                                  consensus_timestamp <= @end and
                                  entity_id = @account_id
                              ), 0) as value,
                              coalesce((
                                select json_agg(change)
                                from (
                                  select json_build_object(
                                      'token_id', tt.token_id,
                                      'decimals', t.decimals,
                                      'type', t.type,
                                      'value', sum(tt.amount)
                                  ) change
                                  from token_transfer tt
                                  join token t
                                    on t.token_id = tt.token_id
                                  join genesis
                                    on t.created_timestamp > genesis.timestamp
                                  where
                                    consensus_timestamp > @start and
                                    consensus_timestamp <= @end and
                                    account_id = @account_id and
                                    t.type = 'FUNGIBLE_COMMON'
                                  group by tt.account_id, tt.token_id, t.decimals, t.type
                                ) token_change
                              ), '[]') as token_values,
                              (
                                select coalesce(json_agg(json_build_object(
                                  'associated', associated,
                                  'decimals', decimals,
                                  'token_id', token_id,
                                  'type',  type
                                ) order by token_id), '[]')
                                from (
                                  select distinct on (t.token_id) ta.associated, t.decimals, t.token_id, t.type
                                  from token_account ta
                                  join token t on t.token_id = ta.token_id
                                  join genesis on t.created_timestamp > genesis.timestamp
                                  where account_id = @account_id and ta.modified_timestamp <= @end
                                  order by t.token_id, ta.modified_timestamp desc
                                ) as associations
                              ) as token_associations`
	latestBalanceBeforeConsensus = "with" + genesisTimestampCte + `, abm as (
                                      select consensus_timestamp as max
                                      from account_balance_file
                                      where consensus_timestamp <= @timestamp
                                      order by consensus_timestamp desc
                                      limit 1
                                    )
                                    select
                                      abm.max consensus_timestamp,
                                      coalesce(ab.balance, 0) balance,
                                      coalesce((
                                        select json_agg(json_build_object(
                                          'decimals', t.decimals,
                                          'token_id', tb.token_id,
                                          'type', t.type,
                                          'value', tb.balance
                                        ))
                                        from token_balance tb
                                        join token t on t.token_id = tb.token_id
                                        join genesis on t.created_timestamp > genesis.timestamp
                                        where tb.consensus_timestamp = abm.max and tb.account_id = @account_id
                                      ), '[]') token_balances
                                    from abm
                                    left join account_balance ab
                                      on ab.consensus_timestamp = abm.max and ab.account_id = @account_id`
	selectCryptoEntityByAlias = `select id, deleted, timestamp_range
                                 from entity
                                 where alias = @alias and timestamp_range @> @consensus_end
                                 union all
                                 select id, deleted, timestamp_range
                                 from entity_history
                                 where alias = @alias and timestamp_range @> @consensus_end
                                 order by lower(timestamp_range) desc`
	selectCryptoEntityById = `select id, deleted, timestamp_range
                              from entity
                              where type = 'ACCOUNT' and id = @id
                              union all
                              select id, deleted, timestamp_range
                              from contract
                              where id = @id`
	selectNftTransfersForAccount = "with" + genesisTimestampCte + `
                                    select nt.*
                                    from nft_transfer nt
                                    join token t on t.token_id = nt.token_id
                                    join genesis on t.created_timestamp > genesis.timestamp
                                    where consensus_timestamp > @start and consensus_timestamp <= @end and
                                      (receiver_account_id = @account_id or sender_account_id = @account_id)
                                    order by consensus_timestamp`
)

type accountBalanceChange struct {
	TokenAssociations string
	TokenValues       string
	Value             int64
}

type combinedAccountBalance struct {
	ConsensusTimestamp int64
	Balance            int64
	TokenBalances      string
}

type tokenAssociation struct {
	Associated bool
	Decimals   int64
	TokenId    domain.EntityId `json:"token_id"`
	Type       string
}

// accountRepository struct that has connection to the Database
type accountRepository struct {
	dbClient interfaces.DbClient
}

// NewAccountRepository creates an instance of a accountRepository struct
func NewAccountRepository(dbClient interfaces.DbClient) interfaces.AccountRepository {
	return &accountRepository{dbClient}
}

func (ar *accountRepository) RetrieveBalanceAtBlock(
	ctx context.Context,
	accountId types.AccountId,
	consensusEnd int64,
) (types.AmountSlice, string, *rTypes.Error) {
	var entityIdString string
	entity, err := ar.getCryptoEntity(ctx, accountId, consensusEnd)
	if err != nil {
		return nil, entityIdString, err
	}

	balanceChangeEndTimestamp := consensusEnd
	balanceSnapshotEndTimestamp := consensusEnd
	if entity != nil && entity.Deleted != nil && *entity.Deleted && entity.GetModifiedTimestamp() <= consensusEnd {
		// if an account / contract is deleted at t1, a balance snapshot at t1 (if exists) won't have info for the
		// entity, thus look for a balance snapshot at or before the deleted timestamp - 1
		// however, the balanceChangeEndTimestamp should be the deletion timestamp since the crypto delete transaction
		// may have a transfer which moves the remaining hbar balance to another account
		balanceChangeEndTimestamp = entity.GetModifiedTimestamp()
		balanceSnapshotEndTimestamp = balanceChangeEndTimestamp - 1
	}

	id := accountId.GetId()
	if accountId.HasAlias() {
		// entity can't be nil if accountId has alias
		id = entity.Id.EncodedId
	}
	snapshotTimestamp, hbarAmount, tokenAmountMap, err := ar.getLatestBalanceSnapshot(
		ctx,
		id,
		balanceSnapshotEndTimestamp,
	)
	if err != nil {
		return nil, entityIdString, err
	}

	hbarValue, tokenValues, tokenAssociationMap, err := ar.getBalanceChange(
		ctx,
		id,
		snapshotTimestamp,
		balanceChangeEndTimestamp,
	)
	if err != nil {
		return nil, entityIdString, err
	}

	hbarAmount.Value += hbarValue

	ftAssociationMap := tokenAssociationMap[domain.TokenTypeFungibleCommon]
	ftAmountMap := tokenAmountMap[domain.TokenTypeFungibleCommon]
	ftAmounts := getUpdatedTokenAmounts(ftAmountMap, tokenValues, ftAssociationMap)

	nftAssociationMap := tokenAssociationMap[domain.TokenTypeNonFungibleUnique]
	nftAmountMap := tokenAmountMap[domain.TokenTypeNonFungibleUnique]
	nftAmounts, err := ar.getNftBalance(
		ctx,
		id,
		snapshotTimestamp,
		consensusEnd,
		nftAmountMap,
		nftAssociationMap,
	)
	if err != nil {
		return nil, entityIdString, err
	}

	amounts := make(types.AmountSlice, 0, 1+len(ftAmounts)+len(nftAmounts))
	amounts = append(amounts, hbarAmount)
	amounts = append(amounts, ftAmounts...)
	amounts = append(amounts, nftAmounts...)

	if entity != nil {
		// return the entity id string in the format of 'shard.realm.num'
		entityIdString = entity.Id.String()
	}
	return amounts, entityIdString, nil
}

func (ar *accountRepository) getCryptoEntity(ctx context.Context, accountId types.AccountId, consensusEnd int64) (
	*domain.Entity,
	*rTypes.Error,
) {
	db, cancel := ar.dbClient.GetDbWithContext(ctx)
	defer cancel()

	var query string
	var args []interface{}
	var notFoundError *rTypes.Error
	if accountId.HasAlias() {
		query = selectCryptoEntityByAlias
		args = []interface{}{sql.Named("alias", accountId.GetAlias()), sql.Named("consensus_end", consensusEnd)}
		notFoundError = hErrors.AddErrorDetails(hErrors.ErrAccountNotFound, "reason",
			"Account with the alias not found")
	} else {
		query = selectCryptoEntityById
		args = []interface{}{sql.Named("id", accountId.GetId())}
	}

	entities := make([]domain.Entity, 0)
	if err := db.Raw(query, args...).Scan(&entities).Error; err != nil {
		log.Errorf(
			databaseErrorFormat,
			hErrors.ErrDatabaseError.Message,
			fmt.Sprintf("%v looking for entity %s", err, accountId),
		)
		return nil, hErrors.ErrDatabaseError
	}

	if len(entities) == 0 {
		return nil, notFoundError
	}

	// if it's by alias, return the first match which is the current one owns the alias, even though it may be deleted
	return &entities[0], nil
}

func (ar *accountRepository) getLatestBalanceSnapshot(ctx context.Context, accountId, timestamp int64) (
	int64,
	*types.HbarAmount,
	map[string]map[int64]*types.TokenAmount,
	*rTypes.Error,
) {
	db, cancel := ar.dbClient.GetDbWithContext(ctx)
	defer cancel()

	// gets the most recent balance at or before timestamp
	cb := &combinedAccountBalance{}
	if err := db.Raw(
		latestBalanceBeforeConsensus,
		sql.Named("account_id", accountId),
		sql.Named("timestamp", timestamp),
	).First(cb).Error; err != nil {
		log.Errorf(
			databaseErrorFormat,
			hErrors.ErrDatabaseError.Message,
			fmt.Sprintf("%v looking for account %d's balance at or before %d", err, accountId, timestamp),
		)
		return 0, nil, nil, hErrors.ErrDatabaseError
	}

	if cb.ConsensusTimestamp == 0 {
		return 0, nil, nil, hErrors.ErrNodeIsStarting
	}

	hbarAmount := types.HbarAmount{Value: cb.Balance}

	var tokenAmounts []*types.TokenAmount
	if err := json.Unmarshal([]byte(cb.TokenBalances), &tokenAmounts); err != nil {
		return 0, nil, nil, hErrors.ErrInvalidToken
	}

	tokenAmountMap := map[string]map[int64]*types.TokenAmount{
		domain.TokenTypeFungibleCommon:    {},
		domain.TokenTypeNonFungibleUnique: {},
	}
	for _, tokenAmount := range tokenAmounts {
		tokenAmountMap[tokenAmount.Type][tokenAmount.TokenId.EncodedId] = tokenAmount
	}

	return cb.ConsensusTimestamp, &hbarAmount, tokenAmountMap, nil
}

func (ar *accountRepository) getBalanceChange(ctx context.Context, accountId, consensusStart, consensusEnd int64) (
	int64,
	[]*types.TokenAmount,
	map[string]map[int64]tokenAssociation,
	*rTypes.Error,
) {
	db, cancel := ar.dbClient.GetDbWithContext(ctx)
	defer cancel()

	change := &accountBalanceChange{}
	// gets the balance change from the Balance snapshot until the target block
	if err := db.Raw(
		balanceChangeBetween,
		sql.Named("account_id", accountId),
		sql.Named("start", consensusStart),
		sql.Named("end", consensusEnd),
	).First(change).Error; err != nil {
		log.Errorf(
			databaseErrorFormat,
			hErrors.ErrDatabaseError.Message,
			fmt.Sprintf("%v looking for account %d's balance change in [%d, %d]", err, accountId, consensusStart,
				consensusEnd),
		)
		return 0, nil, nil, hErrors.ErrDatabaseError
	}

	// fungible token values
	var tokenValues []*types.TokenAmount
	if err := json.Unmarshal([]byte(change.TokenValues), &tokenValues); err != nil {
		return 0, nil, nil, hErrors.ErrInvalidToken
	}

	// the account's token associations at timestamp consensusEnd
	tokenAssociations := make([]tokenAssociation, 0)
	if err := json.Unmarshal([]byte(change.TokenAssociations), &tokenAssociations); err != nil {
		return 0, nil, nil, hErrors.ErrInternalServerError
	}

	// convert the token associations to a map by the token id
	tokenAssociationMap := map[string]map[int64]tokenAssociation{
		domain.TokenTypeFungibleCommon:    {},
		domain.TokenTypeNonFungibleUnique: {},
	}
	for _, ta := range tokenAssociations {
		tokenAssociationMap[ta.Type][ta.TokenId.EncodedId] = ta
	}

	return change.Value, tokenValues, tokenAssociationMap, nil
}

func (ar *accountRepository) getNftBalance(
	ctx context.Context,
	accountId int64,
	consensusStart int64,
	consensusEnd int64,
	tokenAmountMap map[int64]*types.TokenAmount,
	tokenAssociationMap map[int64]tokenAssociation,
) (types.AmountSlice, *rTypes.Error) {
	db, cancel := ar.dbClient.GetDbWithContext(ctx)
	defer cancel()

	nftTransfers := make([]domain.NftTransfer, 0)
	if err := db.Raw(
		selectNftTransfersForAccount,
		sql.Named("account_id", accountId),
		sql.Named("start", consensusStart),
		sql.Named("end", consensusEnd),
	).Scan(&nftTransfers).Error; err != nil {
		log.Errorf(
			databaseErrorFormat,
			hErrors.ErrDatabaseError.Message,
			fmt.Sprintf("%v getting nft transfers for account %d till %d", err, accountId, consensusEnd),
		)
		return nil, hErrors.ErrDatabaseError
	}

	balanceChangeMap := make(map[int64]*types.TokenAmount)
	for _, nftTransfer := range nftTransfers {
		tokenId := nftTransfer.TokenId.EncodedId
		if ta, ok := tokenAssociationMap[tokenId]; ok && !ta.Associated {
			// skip dissociated tokens
			continue
		}

		if _, ok := balanceChangeMap[tokenId]; !ok {
			// initialize if not exist
			balanceChangeMap[tokenId] = &types.TokenAmount{
				TokenId: nftTransfer.TokenId,
				Type:    domain.TokenTypeNonFungibleUnique,
			}
		}
		// there may be historical self NftTransfer in record, i.e., sender and receiver are the same account
		change := getNftChangeForAccount(nftTransfer.ReceiverAccountId, accountId) -
			getNftChangeForAccount(nftTransfer.SenderAccountId, accountId)
		balanceChangeMap[tokenId].Value += change
	}

	nftValues := make([]*types.TokenAmount, 0, len(balanceChangeMap))
	for _, balanceChange := range balanceChangeMap {
		nftValues = append(nftValues, balanceChange)
	}

	return getUpdatedTokenAmounts(tokenAmountMap, nftValues, tokenAssociationMap), nil
}

func getNftChangeForAccount(subject *domain.EntityId, accountId int64) int64 {
	if subject != nil && subject.EncodedId == accountId {
		return 1
	}
	return 0
}

func getUpdatedTokenAmounts(
	tokenAmountMap map[int64]*types.TokenAmount,
	tokenValues []*types.TokenAmount,
	tokenAssociationMap map[int64]tokenAssociation,
) types.AmountSlice {
	for _, tokenValue := range tokenValues {
		tokenId := tokenValue.TokenId.EncodedId
		if _, ok := tokenAmountMap[tokenId]; ok {
			tokenAmountMap[tokenId].Value += tokenValue.Value
		} else {
			tokenAmountMap[tokenId] = tokenValue
		}
	}

	for tokenId, ta := range tokenAssociationMap {
		_, exist := tokenAmountMap[tokenId]
		if (exist && !ta.Associated) || !exist {
			// set / add a 0 amount for the token if it's no longer associated or there's no existing TokenAmount for it
			tokenAmountMap[tokenId] = &types.TokenAmount{
				Decimals: ta.Decimals,
				TokenId:  ta.TokenId,
				Type:     ta.Type,
			}
		}
	}

	amounts := make(types.AmountSlice, 0, len(tokenAmountMap))
	for _, tokenAmount := range tokenAmountMap {
		amounts = append(amounts, tokenAmount)
	}

	return amounts
}
