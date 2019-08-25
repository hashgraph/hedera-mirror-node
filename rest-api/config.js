const config = {
    limits: {
        RESPONSE_ROWS: 1000,
        MAX_BIGINT: 9223372036854775807
    },

    // Time to Live for cache entries for each type of API
    ttls: {
        transactions: 10,
        balances: 60,
        accounts: 60,
        events: 10
    }
}

module.exports = config;
