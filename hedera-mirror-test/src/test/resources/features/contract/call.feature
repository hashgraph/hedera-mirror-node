@contractbase @fullsuite @acceptance @web3 @call
Feature: eth_call Contract Base Coverage Feature

  Scenario Outline: Validate eth_call
    Given I ensure token <nonFungibleTokenName> has been created
    Given I ensure token <fungibleTokenName> has been created
    Given I successfully create ERC contract
    Given I successfully create Precompile contract
    Given I successfully create EstimateGas contract
    Then the mirror node REST API should return status 200 for the estimate contract transaction
    Then I call function with IERC721Metadata token <nonFungibleTokenName> name
    Then I call function with IERC721Metadata token <nonFungibleTokenName> symbol
    Then I call function with IERC721Metadata token <nonFungibleTokenName> totalSupply
    Then I call function with IERC721 token <nonFungibleTokenName> balanceOf owner
    Then I call function with HederaTokenService isToken token <fungibleTokenName>
    Then I call function with HederaTokenService isFrozen token <fungibleTokenName>, account
    Then I call function with HederaTokenService isKyc token <fungibleTokenName>, account
    Then I call function with HederaTokenService getTokenDefaultFreezeStatus token <fungibleTokenName>
    Then I call function with HederaTokenService getTokenDefaultKycStatus token <fungibleTokenName>
    Then I call function with update and I expect return of the updated value
    Then I call function that makes N times state update
    Then I call function with nested deploy using create function
    Then I call function with nested deploy using create2 function
    Then I call function with transfer that returns the balance
    Examples:
      | fungibleTokenName | nonFungibleTokenName |
      | "FUNGIBLE"        | "NFT"                |
