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
	log "github.com/sirupsen/logrus"
	prefixed "github.com/x-cray/logrus-prefixed-formatter"
	"gorm.io/gorm"
)

func configLogger(level string) {
	var err error
	var logLevel log.Level

	if logLevel, err = log.ParseLevel(strings.ToLower(level)); err != nil {
		// if invalid, default to info
		logLevel = log.InfoLevel
	}

	log.SetLevel(logLevel)
	log.SetOutput(os.Stdout)
	log.SetFormatter(&prefixed.TextFormatter{
		DisableColors:   true,
		ForceFormatting: true,
		FullTimestamp:   true,
		TimestampFormat: "2006-01-02T15:04:05.000-0700",
	})
}

// newBlockchainOnlineRouter creates a Mux http.Handler from a collection
// of server controllers, serving "online" mode.
// ref: https://www.rosetta-api.org/docs/node_deployment.html#online-mode-endpoints
func newBlockchainOnlineRouter(
	network *rTypes.NetworkIdentifier,
	nodes types.NodeMap,
	asserter *asserter.Asserter,
	version *rTypes.Version,
	dbClient *gorm.DB,
) (http.Handler, error) {
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

	return server.NewRouter(
		networkAPIController,
		blockAPIController,
		mempoolAPIController,
		constructionAPIController,
		accountAPIController,
	), nil
}

// newBlockchainOfflineRouter creates a Mux http.Handler from a collection
// of server controllers, serving "offline" mode.
// ref: https://www.rosetta-api.org/docs/node_deployment.html#offline-mode-endpoints
func newBlockchainOfflineRouter(
	network string,
	nodes types.NodeMap,
	asserter *asserter.Asserter,
) (http.Handler, error) {
	constructionAPIService, err := constructionService.NewConstructionAPIService(network, nodes)
	if err != nil {
		return nil, err
	}
	constructionAPIController := server.NewConstructionAPIController(constructionAPIService, asserter)

	return server.NewRouter(constructionAPIController), nil
}

func main() {
	configLogger("info")

	configuration, err := LoadConfig()
	if err != nil {
		log.Fatalf("Failed to load config: %s", err)
	}

	rosettaConfig := &configuration.Hedera.Mirror.Rosetta
	configLogger(rosettaConfig.Log.Level)

	network := &rTypes.NetworkIdentifier{
		Blockchain: config.Blockchain,
		Network:    strings.ToLower(rosettaConfig.Network),
		SubNetworkIdentifier: &rTypes.SubNetworkIdentifier{
			Network: fmt.Sprintf("shard %s realm %s", rosettaConfig.Shard, rosettaConfig.Realm),
		},
	}

	version := &rTypes.Version{
		RosettaVersion:    rosettaConfig.ApiVersion,
		NodeVersion:       rosettaConfig.NodeVersion,
		MiddlewareVersion: &rosettaConfig.Version,
	}

	asserter, err := asserter.NewServer(
		[]string{config.OperationTypeCryptoTransfer},
		true,
		[]*rTypes.NetworkIdentifier{network},
		nil,
		false,
	)
	if err != nil {
		log.Fatalf("%s", err)
	}

	var router http.Handler

	if rosettaConfig.Online {
		dbClient := connectToDb(rosettaConfig.Db)

		router, err = newBlockchainOnlineRouter(network, rosettaConfig.Nodes, asserter, version, dbClient)
		if err != nil {
			log.Fatalf("%s", err)
		}

		log.Info("Serving Rosetta API in ONLINE mode")
	} else {
		router, err = newBlockchainOfflineRouter(network.Network, rosettaConfig.Nodes, asserter)
		if err != nil {
			log.Fatalf("%s", err)
		}

		log.Info("Serving Rosetta API in OFFLINE mode")
	}

	loggedRouter := server.LoggerMiddleware(router)
	corsRouter := server.CorsMiddleware(loggedRouter)
	log.Infof("Listening on port %d", rosettaConfig.Port)
	log.Fatal(http.ListenAndServe(fmt.Sprintf(":%d", rosettaConfig.Port), corsRouter))
}
