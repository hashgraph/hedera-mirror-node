@contractbase @fullsuite @acceptance @web3 @call
Feature: eth_call Contract Base Coverage Feature

  Scenario Outline: Validate eth_call
    Given I successfully create ERC contract
    Given I successfully create Precompile contract
    Given I verify the precompile contract bytecode is deployed
    Given I successfully create EstimateGas contract
    Given I successfully create EstimateGas contract
    Given I ensure token <fungible> has been created
    Given I ensure token <nft> has been created
    Then I call function with IERC721Metadata token <nft> name
    Then I call function with IERC721Metadata token <nft> symbol
    Then I call function with IERC721Metadata token <nft> totalSupply
    Then I call function with IERC721 token <nft> balanceOf owner
    Then I call function with HederaTokenService isToken token <fungible>
    Then I call function with HederaTokenService isFrozen token <fungible>, account
    Then I call function with HederaTokenService isKyc token <fungible>, account
    Then I call function with HederaTokenService getTokenDefaultFreezeStatus token <fungible>
    Then I call function with HederaTokenService getTokenDefaultKycStatus token <fungible>
    Then I call function with update and I expect return of the updated value
    Then I call function that makes N times state update
    Then I call function with nested deploy using create function
    Then I call function with nested deploy using create2 function
    Then I call function with transfer that returns the balance
    Then I successfully update the balance of an account and get the updated balance after 2 seconds

    Examples:
      | fungible | nft |
      | "FUNGIBLE" | "NFT" |
