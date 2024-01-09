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
    Then I verify historical data for "FUNGIBLEHISTORICAL" is returned for getCustomFees
    Then I verify historical data for "NFTHISTORICAL" is returned for getCustomFees
    Then I verify historical data for "FUNGIBLEHISTORICAL" is returned for getTokenDefaultFreezeStatus
    Then I verify historical data for "NFTHISTORICAL" is returned for getTokenDefaultFreezeStatus
    Then I verify historical data for "FUNGIBLEHISTORICAL" is returned for getTokenDefaultKYCStatus
    Then I verify historical data for "NFTHISTORICAL" is returned for getTokenDefaultKYCStatus
    Then I verify historical data for "FUNGIBLEHISTORICAL" is returned for getTokenType
    Then I verify historical data for "NFTHISTORICAL" is returned for getTokenType
    Then I verify historical data for "FUNGIBLEHISTORICAL" is returned for getTokenExpiryInfo
    Then I verify historical data for "NFTHISTORICAL" is returned for getTokenExpiryInfo
    Then I verify historical data for "FUNGIBLEHISTORICAL" in invalid block returns bad request
    Then I verify historical data for "NFTHISTORICAL" in invalid block returns bad request
    Then I verify that historical data for "FUNGIBLEHISTORICAL" is returned via getTokenInfo
    Then I verify that historical data for "NFTHISTORICAL" is returned via getTokenInfo
#    Then I verify that historical data for "FUNGIBLEHISTORICAL" is returned via getTokenInfo when doing mint - disabled due to bug #7497
#    Then I verify that historical data for "FUNGIBLEHISTORICAL" is returned via getTokenInfo when doing burn - disabled due to bug #7497

    Then I mint new nft for "NFTHISTORICAL"
    Then I associate "NFTHISTORICAL"
    Then I associate "FUNGIBLEHISTORICAL"
    Then I grant KYC to "FUNGIBLEHISTORICAL" to receiver account
    Then I grant KYC to "NFTHISTORICAL" to receiver account

    Then I verify that historical data for "FUNGIBLEHISTORICAL" is returned via balanceOf
    Then I verify that historical data for "NFTHISTORICAL" is returned via balanceOf
    Then I verify that historical data for "FUNGIBLEHISTORICAL" is returned via balanceOf by direct call
    Then I verify that historical data for "NFTHISTORICAL" is returned via balanceOf by direct call
    Then I mint new nft for "NFTHISTORICAL"
    Then I verify that historical data for "FUNGIBLEHISTORICAL" is returned via balanceOf when doing burn
    Then I verify that historical data for "NFTHISTORICAL" is returned via balanceOf when doing burn
    Then I verify that historical data for "FUNGIBLEHISTORICAL" is returned via balanceOf when doing wipe
    Then I verify that historical data for "NFTHISTORICAL" is returned via balanceOf when doing wipe
    Then I verify historical data for "FUNGIBLEHISTORICAL" is returned for allowance
    Then I verify historical data for "NFTHISTORICAL" is returned for getApproved
    Then I verify historical data for "FUNGIBLEHISTORICAL" is returned for ERC allowance
    Then I verify historical data for "NFTHISTORICAL" is returned for ERC getApproved
    Then I verify historical data for "FUNGIBLEHISTORICAL" is returned for allowance by direct call
    Then I verify historical data for "NFTHISTORICAL" is returned for getApproved direct call
    Then I verify historical data for "NFTHISTORICAL" is returned for isApprovedForAll
    Then I verify historical data for "NFTHISTORICAL" is returned for ownerOf
    Then I verify historical data for "FUNGIBLEHISTORICAL" is returned for isFrozen
    Then I verify historical data for "NFTHISTORICAL" is returned for isFrozen
    Then I verify historical data for "FUNGIBLEHISTORICAL" is returned for getFungibleTokenInfo
#    Then I verify historical data for "FUNGIBLEHISTORICAL" is returned for getFungibleTokenInfo when doing burn - disabled due to bug #7497

    Then I mint new nft for "NFTHISTORICAL"

#    Then I verify historical data for "NFTHISTORICAL" is returned for getNonFungibleInfo - disabled due to bug #7497
    Then I verify historical data for "FUNGIBLEHISTORICAL" is returned for isKyc
    Then I verify historical data for "NFTHISTORICAL" is returned for isKyc
    Then I verify historical data for "FUNGIBLEHISTORICAL" is returned for isToken
    Then I verify historical data for "NFTHISTORICAL" is returned for isToken
    Then I update the token and account keys for "FUNGIBLEHISTORICAL"
    Then I update the token and account keys for "NFTHISTORICAL"
    Then I verify historical data for "FUNGIBLEHISTORICAL" is returned for getTokenKey
    Then I verify historical data for "NFTHISTORICAL" is returned for getTokenKey
