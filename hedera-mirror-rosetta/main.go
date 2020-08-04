package main

import (
    "fmt"
    "log"
    "net/http"

    "github.com/coinbase/rosetta-sdk-go/asserter"
    "github.com/coinbase/rosetta-sdk-go/server"
    "github.com/coinbase/rosetta-sdk-go/types"
    "github.com/joho/godotenv"

    "os"
)

func NewBlockchainRouter(
        network *types.NetworkIdentifier,
        asserter *asserter.Asserter,
) http.Handler {
    return server.NewRouter()
}

func main() {
    err := godotenv.Load()
    if err != nil {
        log.Fatal(err);
    }

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


    router := NewBlockchainRouter(network, asserter)
    loggedRouter := server.LoggerMiddleware(router)
    corsRouter := server.CorsMiddleware(loggedRouter)
    log.Printf("Listening on port %s\n", os.Getenv("PORT"))
    log.Fatal(http.ListenAndServe(fmt.Sprintf(":%s", os.Getenv("PORT")), corsRouter))
}
