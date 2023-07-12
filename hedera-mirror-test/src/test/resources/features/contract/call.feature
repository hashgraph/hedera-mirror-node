@contractbase @fullsuite @acceptance @web3 @call
Feature: eth_call Contract Base Coverage Feature

  Scenario Outline: Validate eth_call
    Given I successfully create a new fungible token
    Given I successfully create a new non-fungible token
    Given I successfully create ERC contract
    Given I successfully create Precompile contract
    Given I successfully create EstimateGas contract
    Then the mirror node REST API should return status 200 for the estimate contract transaction
    Then I call function with IERC721Metadata token name
    Then I call function with IERC721Metadata token symbol
    Then I call function with IERC721Metadata token totalSupply
    Then I call function with IERC721 token balanceOf owner
    Then I call function with HederaTokenService isToken token
    Then I call function with HederaTokenService isFrozen token, account
    Then I call function with HederaTokenService isKyc token, account
    Then I call function with HederaTokenService getTokenDefaultFreezeStatus token
    Then I call function with HederaTokenService getTokenDefaultKycStatus token
    Then I call function with update and I expect return of the updated value
    Then I call function that makes N times state update
    Then I call function with nested deploy using create function
    Then I call function with nested deploy using create2 function
    Then I call function with transfer that returns the balance