@contractbase @fullsuite @historical @call @web3 @acceptance
Feature: Historical Feature

  Scenario Outline: Validate Historical Data
    Given I successfully create estimateGas contract
    Given I successfully create precompile contract
    Then the mirror node REST API should return status 200 for the contracts creation
    Given I create <tokenType> token
    Given I create <tokenType> token
#    Then I successfully update the contract storage and get the initial value via historical data
#    Then I successfully update the balance of an account and get the initial balance via historical data
    Then I verify that historical data for "safe" block is treated as latest
    Then I verify that historical data for "pending" block is treated as latest
    Then I verify that historical data for "finalized" block is treated as latest
#    Then I verify the response from non existing account
    Then I verify that historical data for <tokenType> is returned via getTokenInfo
    Then I verify that historical data for <tokenType> is returned via getTokenInfo

    Examples:
      | tokenType |
      | "FUNGIBLE_KYC_UNFROZEN" |
      | "NFT_HISTORICAL" |
