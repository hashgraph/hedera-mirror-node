@contractbase @fullsuite @historical @call @web3 @acceptance
Feature: Historical Feature

  Scenario Outline: Validate Historical Data
    Given I successfully create estimateGas contract
    Given I successfully create erc contract
    Given I successfully create precompile contract
    Given I successfully create estimate precompile contract
    Then the mirror node REST API should return status 200 for the contracts creation
    Then I verify the estimate precompile contract bytecode is deployed
    Given I create fungible token
    Given I create non-fungible token

    Then I successfully update the contract storage and get the initial value via historical data
    Then I successfully update the balance of an account and get the initial balance via historical data
    Then I verify that historical data for negative block returns bad request
    Then I verify that historical data for unknown block returns bad request
    Then I verify that historical data for "safe" block is treated as latest
    Then I verify that historical data for "pending" block is treated as latest
    Then I verify that historical data for "finalized" block is treated as latest
    Then I verify the response from non existing account
    Then I verify historical data for "FUNGIBLE_HISTORICAL" is returned for getCustomFees
    Then I verify historical data for "NFT_HISTORICAL" is returned for getCustomFees
    Then I verify historical data for "FUNGIBLE_HISTORICAL" is returned for getTokenDefaultFreezeStatus
    Then I verify historical data for "NFT_HISTORICAL" is returned for getTokenDefaultFreezeStatus
    Then I verify historical data for "FUNGIBLE_HISTORICAL" is returned for getTokenDefaultKYCStatus
    Then I verify historical data for "NFT_HISTORICAL" is returned for getTokenDefaultKYCStatus
    Then I verify historical data for "FUNGIBLE_HISTORICAL" is returned for getTokenType
    Then I verify historical data for "NFT_HISTORICAL" is returned for getTokenType
    Then I verify historical data for "FUNGIBLE_HISTORICAL" is returned for getTokenExpiryInfo
    Then I verify historical data for "NFT_HISTORICAL" is returned for getTokenExpiryInfo
    Then I verify historical data for "FUNGIBLE_HISTORICAL" in invalid block returns bad request
    Then I verify historical data for "NFT_HISTORICAL" in invalid block returns bad request
    Then I verify that historical data for "FUNGIBLE_HISTORICAL" is returned via getTokenInfo
    Then I verify that historical data for "NFT_HISTORICAL" is returned via getTokenInfo

    Then I mint new nft for "NFT_HISTORICAL"
    Then I associate "NFT_HISTORICAL"
    Then I associate "FUNGIBLE_HISTORICAL"
    Then I grant KYC to "FUNGIBLE_HISTORICAL" to receiver account
    Then I grant KYC to "NFT_HISTORICAL" to receiver account

    Then I verify that historical data for "FUNGIBLE_HISTORICAL" is returned via balanceOf
    Then I verify that historical data for "NFT_HISTORICAL" is returned via balanceOf
    Then I verify that historical data for "FUNGIBLE_HISTORICAL" is returned via balanceOf by direct call
    Then I verify that historical data for "NFT_HISTORICAL" is returned via balanceOf by direct call
    Then I mint new nft for "NFT_HISTORICAL"
    Then I verify that historical data for "FUNGIBLE_HISTORICAL" is returned via balanceOf when doing burn
    Then I verify that historical data for "NFT_HISTORICAL" is returned via balanceOf when doing burn
    Then I verify historical data for "FUNGIBLE_HISTORICAL" is returned for allowance
    Then I verify historical data for "NFT_HISTORICAL" is returned for getApproved
    Then I verify historical data for "FUNGIBLE_HISTORICAL" is returned for ERC allowance
    Then I verify historical data for "NFT_HISTORICAL" is returned for ERC getApproved
    Then I verify historical data for "FUNGIBLE_HISTORICAL" is returned for allowance by direct call
    Then I verify historical data for "NFT_HISTORICAL" is returned for getApproved direct call
    Then I verify historical data for "NFT_HISTORICAL" is returned for isApprovedForAll
    Then I verify historical data for "NFT_HISTORICAL" is returned for ownerOf
    Then I verify historical data for "FUNGIBLE_HISTORICAL" is returned for isFrozen
    Then I verify historical data for "NFT_HISTORICAL" is returned for isFrozen
    Then I verify historical data for "FUNGIBLE_HISTORICAL" is returned for getFungibleTokenInfo

    Then I mint new nft for "NFT_HISTORICAL"

    Then I verify historical data for "NFT_HISTORICAL" is returned for getNonFungibleInfo
    Then I verify historical data for "FUNGIBLE_HISTORICAL" is returned for isKyc
    Then I verify historical data for "NFT_HISTORICAL" is returned for isKyc
    Then I verify historical data for "FUNGIBLE_HISTORICAL" is returned for isToken
    Then I verify historical data for "NFT_HISTORICAL" is returned for isToken
    Then I update the token and account keys for "FUNGIBLE_HISTORICAL"
    Then I update the token and account keys for "NFT_HISTORICAL"
    Then I verify historical data for "FUNGIBLE_HISTORICAL" is returned for getTokenKey
    Then I verify historical data for "NFT_HISTORICAL" is returned for getTokenKey
