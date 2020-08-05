package main

import (
	"fmt"
	"log"
	"net/http"

	"github.com/coinbase/rosetta-sdk-go/asserter"
	"github.com/coinbase/rosetta-sdk-go/server"
	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/services"
	"github.com/jinzhu/gorm"
	"github.com/joho/godotenv"

	"os"
)

// NewBlockchainRouter creates a Mux http.Handler from a collection
// of server controllers.
func NewBlockchainRouter(
	network *types.NetworkIdentifier,
	asserter *asserter.Asserter,
	dbClient *gorm.DB
) http.Handler {

	blockAPIService := services.NewBlockAPIService(network)
	blockAPIController := server.NewBlockAPIController(blockAPIService, asserter)

	return server.NewRouter(blockAPIController)
}

func main() {
	args := os.Args[1:]
	if len(args) > 0 {
		godotenv.Load(args[0])
	} else {
		godotenv.Load()
	}

	dbClient := connectToDb()
	defer dbClient.Close()

	network := &types.NetworkIdentifier{
		Blockchain: os.Getenv("BLOCKCHAIN"),
		Network:    os.Getenv("NETWORK"),
	}

	asserter, err := asserter.NewServer(
		[]string{"Transfer"},
		false,
		[]*types.NetworkIdentifier{network},
	)
	if err != nil {
		log.Fatal(err)
	}

	router := NewBlockchainRouter(network, asserter, dbClient)
	loggedRouter := server.LoggerMiddleware(router)
	corsRouter := server.CorsMiddleware(loggedRouter)
	log.Printf("Listening on port %s\n", os.Getenv("PORT"))
	log.Fatal(http.ListenAndServe(fmt.Sprintf(":%s", os.Getenv("PORT")), corsRouter))
}
