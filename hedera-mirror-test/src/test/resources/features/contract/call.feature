@contractbase @fullsuite @acceptance @web3 @call @setupEth_callFeature
Feature: eth_call Contract Base Coverage Feature

  Scenario Outline: Validate eth_call
    Given I ensure token "NFT" has been created
    Given I mint a "NFT"
    And I ensure token "FUNGIBLE" has been created
    Given I successfully create ERC contract
    Given I successfully create Precompile contract
    Given I successfully create EstimateGas contract
    Given I ensure token "NFT" has been created
    And I ensure token "FUNGIBLE" has been created
    Then I call function with IERC721Metadata token "NFT" name
    Then I call function with IERC721Metadata token "NFT" symbol
    Then I call function with IERC721Metadata token "NFT" totalSupply
    Then I call function with IERC721 token NFT balanceOf owner
    Then I call function with HederaTokenService isToken token "FUNGIBLE"
    Then I call function with HederaTokenService isFrozen token "FUNGIBLE", account
    Then I call function with HederaTokenService isKyc token "FUNGIBLE", account
    Then I call function with HederaTokenService getTokenDefaultFreezeStatus token "FUNGIBLE"
    Then I call function with HederaTokenService getTokenDefaultKycStatus token "FUNGIBLE"
    Then I call function with update and I expect return of the updated value
    Then I call function that makes N times state update
#    Then I call function with nested deploy using create function
#    Then I call function with nested deploy using create2 function
    Then I call function with transfer that returns the balance
    Then I call function that burns 1 FUNGIBLE token and returns the total supply and balance of treasury
    Then I call function that burns 1 NFT token and returns the total supply and balance of treasury
    Then I call function that pauses "FUNGIBLE" token gets status unpauses and returns the status of the token
    Then I call function that pauses "NFT" token gets status unpauses and returns the status of the token
    And I associate "FUNGIBLE" token to receiver account
    And I associate "NFT" token to receiver account
    And I approve and transfer 1 FUNGIBLE token to receiver account
    And I approve and transfer 1 NFT token to receiver account
    Then I call function that mints 1 FUNGIBLE token and returns the total supply and balance of treasury
    Then I call function that mints 1 NFT token and returns the total supply and balance of treasury
    Then I wipe 1 FUNGIBLE token and return the total supply and balance of treasury
    Then I wipe 1 NFT token and return the total supply and balance of treasury