@contractbase @fullsuite @acceptance @web3 @call @setupEth_callFeature
Feature: eth_call Contract Base Coverage Feature

  Scenario Outline: Validate eth_call
    Given I ensure token "NFT" has been created
    And I ensure token "FUNGIBLE" has been created
    Given I successfully create ERC contract
    Given I successfully create Precompile contract
    Given I successfully create EstimateGas contract
    Given I ensure token "NFT" has been created
    And I ensure token "FUNGIBLE" has been created
    Given I mint a NFT
    Then the mirror node should return status 200 for the HAPI transaction
    Then I call function with IERC721Metadata token "NFT" name
    Then I call function with IERC721Metadata token "NFT" symbol
    Then I call function with IERC721Metadata token NFT totalSupply
    Then I call function with IERC721 token NFT balanceOf owner
    Then I call function with HederaTokenService isToken token FUNGIBLE
    Then I call function with HederaTokenService isFrozen token FUNGIBLE, account
    Then I call function with HederaTokenService isKyc token FUNGIBLE, account
    Then I call function with HederaTokenService getTokenDefaultFreezeStatus token FUNGIBLE
    Then I call function with HederaTokenService getTokenDefaultKycStatus token FUNGIBLE
    Then I call function with update and I expect return of the updated value
    Then I call function that makes N times state update
#    Then I call function with nested deploy using create function
#    Then I call function with nested deploy using create2 function
    Then I call function with transfer that returns the balance
    Then I call function that burns FUNGIBLE token and returns the total supply and balance of treasury
    Then I call function that burns NFT token and returns the total supply and balance of treasury
    Then I call function that pauses "FUNGIBLE" token gets status unpauses and returns the status of the token
    Then I call function that pauses "NFT" token gets status unpauses and returns the status of the token
    And I associate FUNGIBLE token to receiver account
    Then the mirror node should return status 200 for the HAPI transaction
    And I associate NFT token to receiver account
    Then the mirror node should return status 200 for the HAPI transaction
    And I approve and transfer FUNGIBLE token to receiver account
    Then the mirror node should return status 200 for the HAPI transaction
    And I approve and transfer NFT token to receiver account
    Then the mirror node should return status 200 for the HAPI transaction
    Then I call function that mints FUNGIBLE token and returns the total supply and balance of treasury
    Then I call function that mints NFT token and returns the total supply and balance of treasury
    Then I wipe FUNGIBLE token and return the total supply and balance of treasury
    Then I wipe NFT token and return the total supply and balance of treasury
    Then I call function that pauses "FUNGIBLE" token gets status unpauses and returns the status of the token
    Then I call function that pauses "NFT" token gets status unpauses and returns the status of the token
    Then I call function that freezes "FUNGIBLE" token gets freeze status unfreezes and gets freeze status
    Then I call function that freezes "NFT" token gets freeze status unfreezes and gets freeze status
    And I associate precompile contract with the tokens
    Then the mirror node should return status 200 for the HAPI transaction
    And I approve and transfer FUNGIBLE token to the precompile contract
    Then the mirror node should return status 200 for the HAPI transaction
    Given I mint a NFT
    Then the mirror node should return status 200 for the HAPI transaction
    And I approve and transfer NFT token to the precompile contract
    Then the mirror node should return status 200 for the HAPI transaction
    Then I call function that approves FUNGIBLE token and gets allowance
    Then I call function that approves NFT token and gets allowance
    Then I call function that associates FUNGIBLE token dissociates and fails token transfer
    Then I call function that associates NFT token dissociates and fails token transfer
    Then I call function that approves FUNGIBLE token gets balance gets allowance transfers from gets balance gets allowance
    Then I call function that approves NFT token gets balance gets allowance transfers from gets balance gets allowance
