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

package main

import (
	"fmt"
	"log"
	"net/http"
	"os"
	"strings"

	"github.com/coinbase/rosetta-sdk-go/asserter"
	"github.com/coinbase/rosetta-sdk-go/server"
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/account"
	addressBookEntry "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/addressbook/entry"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/block"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/persistence/transaction"
	accountService "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/services/account"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/services/base"
	blockService "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/services/block"
	constructionService "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/services/construction"
	mempoolService "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/services/mempool"
	networkService "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/services/network"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/config"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/types"
	"github.com/jinzhu/gorm"
)

// NewBlockchainOnlineRouter creates a Mux http.Handler from a collection
// of server controllers, serving "online" mode.
// ref: https://www.rosetta-api.org/docs/node_deployment.html#online-mode-endpoints
func NewBlockchainOnlineRouter(network *rTypes.NetworkIdentifier, nodes types.NodeMap, asserter *asserter.Asserter, version *rTypes.Version, dbClient *gorm.DB) (http.Handler, error) {
	blockRepo := block.NewBlockRepository(dbClient)
	transactionRepo := transaction.NewTransactionRepository(dbClient)
	accountRepo := account.NewAccountRepository(dbClient)
	addressBookEntryRepo := addressBookEntry.NewAddressBookEntryRepository(dbClient)

	baseService := base.NewBaseService(blockRepo, transactionRepo)

	networkAPIService := networkService.NewNetworkAPIService(baseService, addressBookEntryRepo, network, version)
	networkAPIController := server.NewNetworkAPIController(networkAPIService, asserter)

	blockAPIService := blockService.NewBlockAPIService(baseService)
	blockAPIController := server.NewBlockAPIController(blockAPIService, asserter)

	mempoolAPIService := mempoolService.NewMempoolAPIService()
	mempoolAPIController := server.NewMempoolAPIController(mempoolAPIService, asserter)

	constructionAPIService, err := constructionService.NewConstructionAPIService(network.Network, nodes)
	if err != nil {
		return nil, err
	}
	constructionAPIController := server.NewConstructionAPIController(constructionAPIService, asserter)

	accountAPIService := accountService.NewAccountAPIService(baseService, accountRepo)
	accountAPIController := server.NewAccountAPIController(accountAPIService, asserter)

	return server.NewRouter(networkAPIController, blockAPIController, mempoolAPIController, constructionAPIController, accountAPIController), nil
}

// NewBlockchainOfflineRouter creates a Mux http.Handler from a collection
// of server controllers, serving "offline" mode.
// ref: https://www.rosetta-api.org/docs/node_deployment.html#offline-mode-endpoints
func NewBlockchainOfflineRouter(network string, nodes types.NodeMap, asserter *asserter.Asserter) (http.Handler, error) {
	constructionAPIService, err := constructionService.NewConstructionAPIService(network, nodes)
	if err != nil {
		return nil, err
	}
	constructionAPIController := server.NewConstructionAPIController(constructionAPIService, asserter)

	return server.NewRouter(constructionAPIController), nil
}

func main() {
	configuration, err := LoadConfig()
	if err != nil {
		log.Printf("Failed to load config: %s", err)
		os.Exit(1)
	}

	network := &rTypes.NetworkIdentifier{
		Blockchain: config.Blockchain,
		Network:    strings.ToLower(configuration.Hedera.Mirror.Rosetta.Network),
		SubNetworkIdentifier: &rTypes.SubNetworkIdentifier{
			Network: fmt.Sprintf("shard %s realm %s", configuration.Hedera.Mirror.Rosetta.Shard, configuration.Hedera.Mirror.Rosetta.Realm),
		},
	}

	version := &rTypes.Version{
		RosettaVersion:    configuration.Hedera.Mirror.Rosetta.ApiVersion,
		NodeVersion:       configuration.Hedera.Mirror.Rosetta.NodeVersion,
		MiddlewareVersion: &configuration.Hedera.Mirror.Rosetta.Version,
	}

	asserter, err := asserter.NewServer(
		[]string{config.OperationTypeCryptoTransfer},
		true,
		[]*rTypes.NetworkIdentifier{network},
	)
	if err != nil {
		log.Printf("%s", err)
		os.Exit(1)
	}

	var router http.Handler
	nodes := configuration.Hedera.Mirror.Rosetta.Nodes

	if configuration.Hedera.Mirror.Rosetta.Online {
		dbClient := connectToDb(configuration.Hedera.Mirror.Rosetta.Db)
		defer dbClient.Close()

		router, err = NewBlockchainOnlineRouter(network, nodes, asserter, version, dbClient)
		if err != nil {
			log.Printf("%s", err)
			os.Exit(1)
		}

		log.Printf("Serving Rosetta API in ONLINE mode")
	} else {
		router, err = NewBlockchainOfflineRouter(network.Network, nodes, asserter)
		if err != nil {
			log.Printf("%s", err)
			os.Exit(1)
		}

		log.Printf("Serving Rosetta API in OFFLINE mode")
	}

	loggedRouter := server.LoggerMiddleware(router)
	corsRouter := server.CorsMiddleware(loggedRouter)
	log.Printf("Listening on port %d\n", configuration.Hedera.Mirror.Rosetta.Port)
	log.Fatal(http.ListenAndServe(fmt.Sprintf(":%d", configuration.Hedera.Mirror.Rosetta.Port), corsRouter))
}
