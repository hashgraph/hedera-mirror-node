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
	"encoding/hex"
	"encoding/json"
	"sync"

	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
	hErrors "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/errors"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/interfaces"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/domain"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/tools"
	log "github.com/sirupsen/logrus"
)

const (
	batchSize                            = 2000
	transactionResultSuccess       int32 = 22
	transactionTypeTokenDissociate int16 = 41
)

const (
	andTransactionHashFilter  = " and transaction_hash = @hash"
	orderByConsensusTimestamp = " order by consensus_timestamp"
	// selectDissociateTokenTransfersInTimestampRange selects the token transfers and nft transfers for successful token
	// dissociate which dissociates an account from tokens which are already deleted
	selectDissociateTokenTransfersInTimestampRange = "with" + genesisTimestampCte + `
      , success_dissociate as (
        select entity_id as account_id, consensus_timestamp
        from transaction
        where type = 41 and result = 22
           and consensus_timestamp >= @start and consensus_timestamp <= @end
      ), dissociated_token as (
        select ta.account_id, ta.token_id, t.type, t.decimals, sd.consensus_timestamp
        from token_account ta
        join success_dissociate sd
          on ta.account_id = sd.account_id and ta.modified_timestamp = sd.consensus_timestamp
        join token t
          on t.token_id = ta.token_id
        join entity e
          on e.id = t.token_id and e.deleted is true
            and sd.consensus_timestamp > lower(e.timestamp_range)
        join genesis
          on t.created_timestamp > timestamp
      ), ft_balance as (
        select
          d.*,
          (
            with snapshot as (
              select max(abf.consensus_timestamp) as timestamp
              from account_balance_file as abf
              where abf.consensus_timestamp < d.consensus_timestamp
            )
            select
              coalesce((
                select balance
                from token_balance as tb
                where tb.account_id = d.account_id and tb.token_id = d.token_id
                  and tb.consensus_timestamp = snapshot.timestamp
              ), 0) + (
                select coalesce(sum(amount), 0)
                from token_transfer as tt
                where tt.account_id = d.account_id and tt.token_id = d.token_id
                  and tt.consensus_timestamp > snapshot.timestamp
                  and tt.consensus_timestamp < d.consensus_timestamp
              )
            from snapshot
          ) balance
        from (select * from dissociated_token where type = 'FUNGIBLE_COMMON') as d
      ), ft_xfer as  (
        select
          consensus_timestamp,
          json_agg(json_build_object(
            'account_id', account_id,
            'amount', -balance,
            'decimals', decimals,
            'token_id', token_id,
            'type', type
          ) order by token_id) as token_transfers
        from ft_balance
        where balance <> 0
        group by consensus_timestamp
      ), nft_xfer as (
        select
          consensus_timestamp,
          json_agg(json_build_object(
            'receiver_account_id', null,
            'sender_account_id', nft.account_id,
            'serial_number', nft.serial_number,
            'token_id', nft.token_id
          ) order by nft.token_id, nft.serial_number) as nft_transfers
        from (select * from dissociated_token where type = 'NON_FUNGIBLE_UNIQUE') as d
        join nft on nft.account_id = d.account_id and nft.token_id = d.token_id
        where nft.deleted is false or nft.modified_timestamp = d.consensus_timestamp
        group by consensus_timestamp
      )
      select
        coalesce(fx.consensus_timestamp, nx.consensus_timestamp) as consensus_timestamp,
        coalesce(fx.token_transfers, '[]') as token_transfers,
        coalesce(nx.nft_transfers, '[]') as nft_transfers
      from ft_xfer fx
      full outer join nft_xfer nx
        on fx.consensus_timestamp = nx.consensus_timestamp
      order by coalesce(fx.consensus_timestamp, nx.consensus_timestamp)`
	// selectTransactionsInTimestampRange selects the transactions with its crypto transfers in json, non-fee transfers
	// in json, token transfers in json, and optionally the token information when the transaction is token create,
	// token delete, or token update. Note the three token transactions are the ones the entity_id in the transaction
	// table is its related token id and require an extra rosetta operation
	selectTransactionsInTimestampRange = "with" + genesisTimestampCte + `select
                                            t.consensus_timestamp,
                                            t.entity_id,
                                            t.payer_account_id,
                                            t.result,
                                            t.transaction_hash as hash,
                                            t.type,
                                            coalesce((
                                              select json_agg(json_build_object(
                                                'account_id', entity_id,
                                                'amount', amount) order by entity_id)
                                              from crypto_transfer where consensus_timestamp = t.consensus_timestamp
                                            ), '[]') as crypto_transfers,
                                            case
                                              when t.type = 14 then coalesce((
                                                  select json_agg(json_build_object(
                                                      'account_id', entity_id,
                                                      'amount', amount
                                                    ) order by entity_id)
                                                  from non_fee_transfer
                                                  where consensus_timestamp = t.consensus_timestamp
                                                ), '[]')
                                              else '[]'
                                            end as non_fee_transfers,
                                            coalesce((
                                              select json_agg(json_build_object(
                                                  'account_id', account_id,
                                                  'amount', amount,
                                                  'decimals', tk.decimals,
                                                  'token_id', tkt.token_id,
                                                  'type', tk.type
                                                ) order by account_id, tk.token_id)
                                              from token_transfer tkt
                                              join token tk on tk.token_id = tkt.token_id
                                              join genesis on tk.created_timestamp > genesis.timestamp
                                              where tkt.consensus_timestamp = t.consensus_timestamp
                                            ), '[]') as token_transfers,
                                            coalesce((
                                              select json_agg(json_build_object(
                                                  'receiver_account_id', receiver_account_id,
                                                  'sender_account_id', sender_account_id,
                                                  'serial_number', serial_number,
                                                  'token_id', tk.token_id
                                                ) order by tk.token_id, serial_number)
                                              from nft_transfer nftt
                                              join token tk on tk.token_id = nftt.token_id
                                              join genesis on tk.created_timestamp > genesis.timestamp
                                              where nftt.consensus_timestamp = t.consensus_timestamp and serial_number <> -1
                                            ), '[]') as nft_transfers,
                                            case
                                              when t.type in (29, 35, 36) then coalesce((
                                                  select json_build_object(
                                                    'decimals', decimals,
                                                    'freeze_default', freeze_default,
                                                    'initial_supply', initial_supply,
                                                    'token_id', token_id,
                                                    'type', type
                                                  )
                                                  from token
                                                  join genesis on created_timestamp > genesis.timestamp
                                                  where token_id = t.entity_id
                                                ), '{}')
                                              else '{}'
                                            end as token
                                          from transaction t
                                          where consensus_timestamp >= @start and consensus_timestamp <= @end`
	selectTransactionsByHashInTimestampRange  = selectTransactionsInTimestampRange + andTransactionHashFilter
	selectTransactionsInTimestampRangeOrdered = selectTransactionsInTimestampRange + orderByConsensusTimestamp
)

// transaction maps to the transaction query which returns the required transaction fields, CryptoTransfers json string,
// NonFeeTransfers json string, TokenTransfers json string, and Token definition json string
type transaction struct {
	ConsensusTimestamp int64
	EntityId           *domain.EntityId
	Hash               []byte
	PayerAccountId     int64
	Result             int16
	Type               int16
	CryptoTransfers    string
	NftTransfers       string
	NonFeeTransfers    string
	TokenTransfers     string
	Token              string
}

func (t transaction) getHashString() string {
	return tools.SafeAddHexPrefix(hex.EncodeToString(t.Hash))
}

type transfer interface {
	getAccountId() domain.EntityId
	getAmount() types.Amount
}

type hbarTransfer struct {
	AccountId domain.EntityId `json:"account_id"`
	Amount    int64           `json:"amount"`
}

func (t hbarTransfer) getAccountId() domain.EntityId {
	return t.AccountId
}

func (t hbarTransfer) getAmount() types.Amount {
	return &types.HbarAmount{Value: t.Amount}
}

type singleNftTransfer struct {
	accountId    domain.EntityId
	receiver     bool
	serialNumber int64
	tokenId      domain.EntityId
}

func (n singleNftTransfer) getAccountId() domain.EntityId {
	return n.accountId
}

func (n singleNftTransfer) getAmount() types.Amount {
	amount := int64(1)
	if !n.receiver {
		amount = -1
	}

	return &types.TokenAmount{
		SerialNumbers: []int64{n.serialNumber},
		TokenId:       n.tokenId,
		Type:          domain.TokenTypeNonFungibleUnique,
		Value:         amount,
	}
}

type tokenTransfer struct {
	AccountId domain.EntityId `json:"account_id"`
	Amount    int64           `json:"amount"`
	Decimals  int64           `json:"decimals"`
	TokenId   domain.EntityId `json:"token_id"`
	Type      string          `json:"type"`
}

func (t tokenTransfer) getAccountId() domain.EntityId {
	return t.AccountId
}

func (t tokenTransfer) getAmount() types.Amount {
	return &types.TokenAmount{
		Decimals: t.Decimals,
		TokenId:  t.TokenId,
		Type:     t.Type,
		Value:    t.Amount,
	}
}

// transactionRepository struct that has connection to the Database
type transactionRepository struct {
	once     sync.Once
	dbClient interfaces.DbClient
	types    map[int]string
}

// NewTransactionRepository creates an instance of a TransactionRepository struct
func NewTransactionRepository(dbClient interfaces.DbClient) interfaces.TransactionRepository {
	return &transactionRepository{dbClient: dbClient}
}

func (tr *transactionRepository) FindBetween(ctx context.Context, start, end int64) (
	[]*types.Transaction,
	*rTypes.Error,
) {
	if start > end {
		return nil, hErrors.ErrStartMustNotBeAfterEnd
	}

	db, cancel := tr.dbClient.GetDbWithContext(ctx)
	defer cancel()

	transactions := make([]*transaction, 0)
	for start <= end {
		transactionsBatch := make([]*transaction, 0)
		err := db.
			Raw(selectTransactionsInTimestampRangeOrdered, sql.Named("start", start), sql.Named("end", end)).
			Limit(batchSize).
			Find(&transactionsBatch).
			Error
		if err != nil {
			log.Errorf(databaseErrorFormat, hErrors.ErrDatabaseError.Message, err)
			return nil, hErrors.ErrDatabaseError
		}

		transactions = append(transactions, transactionsBatch...)

		if len(transactionsBatch) < batchSize {
			break
		}

		start = transactionsBatch[len(transactionsBatch)-1].ConsensusTimestamp + 1
	}

	if err := tr.processSuccessTokenDissociates(ctx, transactions, start, end); err != nil {
		return nil, err
	}

	hashes := make([]string, 0)
	sameHashMap := make(map[string][]*transaction)
	for _, t := range transactions {
		h := t.getHashString()
		if _, ok := sameHashMap[h]; !ok {
			// save the unique hashes in chronological order
			hashes = append(hashes, h)
		}

		sameHashMap[h] = append(sameHashMap[h], t)
	}

	res := make([]*types.Transaction, 0, len(sameHashMap))
	for _, hash := range hashes {
		sameHashTransactions := sameHashMap[hash]
		transaction, err := tr.constructTransaction(sameHashTransactions)
		if err != nil {
			return nil, err
		}
		res = append(res, transaction)
	}
	return res, nil
}

func (tr *transactionRepository) FindByHashInBlock(
	ctx context.Context,
	hashStr string,
	consensusStart int64,
	consensusEnd int64,
) (*types.Transaction, *rTypes.Error) {
	var transactions []*transaction
	transactionHash, err := hex.DecodeString(tools.SafeRemoveHexPrefix(hashStr))
	if err != nil {
		return nil, hErrors.ErrInvalidTransactionIdentifier
	}

	db, cancel := tr.dbClient.GetDbWithContext(ctx)
	defer cancel()

	if err = db.Raw(
		selectTransactionsByHashInTimestampRange,
		sql.Named("hash", transactionHash),
		sql.Named("start", consensusStart),
		sql.Named("end", consensusEnd),
	).Find(&transactions).Error; err != nil {
		log.Errorf(databaseErrorFormat, hErrors.ErrDatabaseError.Message, err)
		return nil, hErrors.ErrDatabaseError
	}

	if len(transactions) == 0 {
		return nil, hErrors.ErrTransactionNotFound
	}

	transaction, rErr := tr.constructTransaction(transactions)
	if rErr != nil {
		return nil, rErr
	}

	return transaction, nil
}

func (tr *transactionRepository) constructTransaction(sameHashTransactions []*transaction) (
	*types.Transaction,
	*rTypes.Error,
) {
	tResult := &types.Transaction{Hash: sameHashTransactions[0].getHashString()}
	operations := make([]*types.Operation, 0)
	success := types.TransactionResults[transactionResultSuccess]

	for _, transaction := range sameHashTransactions {
		cryptoTransfers := make([]hbarTransfer, 0)
		if err := json.Unmarshal([]byte(transaction.CryptoTransfers), &cryptoTransfers); err != nil {
			return nil, hErrors.ErrInternalServerError
		}

		nonFeeTransfers := make([]hbarTransfer, 0)
		if err := json.Unmarshal([]byte(transaction.NonFeeTransfers), &nonFeeTransfers); err != nil {
			return nil, hErrors.ErrInternalServerError
		}

		tokenTransfers := make([]tokenTransfer, 0)
		if err := json.Unmarshal([]byte(transaction.TokenTransfers), &tokenTransfers); err != nil {
			return nil, hErrors.ErrInternalServerError
		}

		nftTransfers := make([]domain.NftTransfer, 0)
		if err := json.Unmarshal([]byte(transaction.NftTransfers), &nftTransfers); err != nil {
			return nil, hErrors.ErrInternalServerError
		}

		token := domain.Token{}
		if err := json.Unmarshal([]byte(transaction.Token), &token); err != nil {
			return nil, hErrors.ErrInternalServerError
		}

		transactionResult := types.TransactionResults[int32(transaction.Result)]
		transactionType := types.TransactionTypes[int32(transaction.Type)]

		nonFeeTransferMap := aggregateNonFeeTransfers(nonFeeTransfers)
		adjustedCryptoTransfers := adjustCryptoTransfers(cryptoTransfers, nonFeeTransferMap)

		operations = tr.appendHbarTransferOperations(transactionResult, transactionType, nonFeeTransfers, operations)
		// crypto transfers are always successful regardless of the transaction result
		operations = tr.appendHbarTransferOperations(success, transactionType, adjustedCryptoTransfers, operations)
		operations = tr.appendTokenTransferOperations(transactionResult, transactionType, tokenTransfers, operations)
		operations = tr.appendNftTransferOperations(transactionResult, transactionType, nftTransfers, operations)

		if !token.TokenId.IsZero() {
			// only for TokenCreate, TokenDeletion, and TokenUpdate, TokenId is non-zero
			operation, err := getTokenOperation(len(operations), token, transaction, transactionResult, transactionType)
			if err != nil {
				return nil, err
			}
			operations = append(operations, operation)
		}

		if IsTransactionResultSuccessful(int32(transaction.Result)) {
			tResult.EntityId = transaction.EntityId
		}
	}

	tResult.Operations = operations
	return tResult, nil
}

func (tr *transactionRepository) appendHbarTransferOperations(
	transactionResult string,
	transactionType string,
	hbarTransfers []hbarTransfer,
	operations []*types.Operation,
) []*types.Operation {
	transfers := make([]transfer, 0, len(hbarTransfers))
	for _, hbarTransfer := range hbarTransfers {
		transfers = append(transfers, hbarTransfer)
	}

	return tr.appendTransferOperations(transactionResult, transactionType, transfers, operations)
}

func (tr *transactionRepository) appendNftTransferOperations(
	transactionResult string,
	transactionType string,
	nftTransfers []domain.NftTransfer,
	operations []*types.Operation,
) []*types.Operation {
	transfers := make([]transfer, 0, 2*len(nftTransfers))
	for _, nftTransfer := range nftTransfers {
		transfers = append(transfers, getSingleNftTransfers(nftTransfer)...)
	}

	return tr.appendTransferOperations(transactionResult, transactionType, transfers, operations)
}

func (tr *transactionRepository) appendTokenTransferOperations(
	transactionResult string,
	transactionType string,
	tokenTransfers []tokenTransfer,
	operations []*types.Operation,
) []*types.Operation {
	transfers := make([]transfer, 0, len(tokenTransfers))
	for _, tokenTransfer := range tokenTransfers {
		// The wiped amount of a deleted NFT class by a TokenDissociate is presented as tokenTransferList and
		// saved to token_transfer table, filter it
		if tokenTransfer.Type != domain.TokenTypeFungibleCommon {
			continue
		}

		transfers = append(transfers, tokenTransfer)
	}

	return tr.appendTransferOperations(transactionResult, transactionType, transfers, operations)
}

func (tr *transactionRepository) appendTransferOperations(
	transactionResult string,
	transactionType string,
	transfers []transfer,
	operations []*types.Operation,
) []*types.Operation {
	for _, transfer := range transfers {
		operations = append(operations, &types.Operation{
			Index:   int64(len(operations)),
			Type:    transactionType,
			Status:  transactionResult,
			Account: types.Account{EntityId: transfer.getAccountId()},
			Amount:  transfer.getAmount(),
		})
	}
	return operations
}

func (tr *transactionRepository) processSuccessTokenDissociates(
	ctx context.Context,
	transactions []*transaction,
	start int64,
	end int64,
) *rTypes.Error {
	hasSuccessTokenDissociate := false
	for _, txn := range transactions {
		if txn.Type == transactionTypeTokenDissociate && IsTransactionResultSuccessful(int32(txn.Result)) {
			hasSuccessTokenDissociate = true
			break
		}
	}
	if !hasSuccessTokenDissociate {
		return nil
	}

	db, cancel := tr.dbClient.GetDbWithContext(ctx)
	defer cancel()

	tokenDissociateTransactions := make([]*transaction, 0)
	if err := db.Raw(
		selectDissociateTokenTransfersInTimestampRange,
		sql.Named("start", start),
		sql.Named("end", end),
	).Scan(&tokenDissociateTransactions).Error; err != nil {
		log.Errorf(databaseErrorFormat, hErrors.ErrDatabaseError.Message, err)
		return hErrors.ErrDatabaseError
	}

	// replace NftTransfers and TokenTransfers for any matching transaction by consensus timestamp
	// both transactions and tokenDissociateTransactions are sorted by consensus timestamp,
	// and tokenDissociateTransactions is a subset of transactions
	i := 0
	j := 0
	for {
		if i >= len(tokenDissociateTransactions) || j >= len(transactions) {
			break
		}

		if tokenDissociateTransactions[i].ConsensusTimestamp == transactions[j].ConsensusTimestamp {
			transactions[j].NftTransfers = tokenDissociateTransactions[i].NftTransfers
			transactions[j].TokenTransfers = tokenDissociateTransactions[i].TokenTransfers
			i++
			j++
		} else {
			j++
		}
	}

	return nil
}

func IsTransactionResultSuccessful(result int32) bool {
	return result == transactionResultSuccess
}

func constructAccount(encodedId int64) (types.Account, *rTypes.Error) {
	account, err := types.NewAccountFromEncodedID(encodedId)
	if err != nil {
		log.Errorf(hErrors.CreateAccountDbIdFailed, encodedId)
		return types.Account{}, hErrors.ErrInternalServerError
	}
	return account, nil
}

func adjustCryptoTransfers(
	cryptoTransfers []hbarTransfer,
	nonFeeTransferMap map[int64]int64,
) []hbarTransfer {
	cryptoTransferMap := make(map[int64]hbarTransfer)
	for _, transfer := range cryptoTransfers {
		key := transfer.AccountId.EncodedId
		cryptoTransferMap[key] = hbarTransfer{
			AccountId: transfer.AccountId,
			Amount:    transfer.Amount + cryptoTransferMap[key].Amount,
		}
	}

	adjusted := make([]hbarTransfer, 0, len(cryptoTransfers))
	for key, aggregated := range cryptoTransferMap {
		amount := aggregated.Amount - nonFeeTransferMap[key]
		if amount != 0 {
			adjusted = append(adjusted, hbarTransfer{
				AccountId: aggregated.AccountId,
				Amount:    amount,
			})
		}
	}

	return adjusted
}

func aggregateNonFeeTransfers(nonFeeTransfers []hbarTransfer) map[int64]int64 {
	nonFeeTransferMap := make(map[int64]int64)

	// the original transfer list from the transaction body
	for _, transfer := range nonFeeTransfers {
		// the original transfer list may have multiple entries for one entity, so accumulate it
		nonFeeTransferMap[transfer.AccountId.EncodedId] += transfer.Amount
	}

	return nonFeeTransferMap
}

func getSingleNftTransfers(nftTransfer domain.NftTransfer) []transfer {
	transfers := make([]transfer, 0)
	if nftTransfer.ReceiverAccountId != nil {
		transfers = append(transfers, singleNftTransfer{
			accountId:    *nftTransfer.ReceiverAccountId,
			receiver:     true,
			serialNumber: nftTransfer.SerialNumber,
			tokenId:      nftTransfer.TokenId,
		})
	}

	if nftTransfer.SenderAccountId != nil {
		transfers = append(transfers, singleNftTransfer{
			accountId:    *nftTransfer.SenderAccountId,
			serialNumber: nftTransfer.SerialNumber,
			tokenId:      nftTransfer.TokenId,
		})
	}
	return transfers
}

func getTokenOperation(
	index int,
	token domain.Token,
	transaction *transaction,
	transactionResult string,
	transactionType string,
) (*types.Operation, *rTypes.Error) {
	payerId, err := constructAccount(transaction.PayerAccountId)
	if err != nil {
		return nil, err
	}

	operation := &types.Operation{
		Index:   int64(index),
		Type:    transactionType,
		Status:  transactionResult,
		Account: payerId,
	}

	metadata := make(map[string]interface{})
	metadata["currency"] = types.Token{Token: token}.ToRosettaCurrency()
	metadata["freeze_default"] = token.FreezeDefault
	metadata["initial_supply"] = token.InitialSupply
	operation.Metadata = metadata

	return operation, nil
}
