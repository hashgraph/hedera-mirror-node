@contractbase @fullsuite @acceptance @web3
Feature: eth_call Contract Base Coverage Feature
  
  Scenario Outline: Validate eth_call
    Given I successfully create ERC contract
    Given I successfully create Precompile contract
    Then I call function with IERC721Metadata token name
    Then I call function with IERC721Metadata token symbol
    Then I call function with IERC721Metadata token totalSupply
    Then I call function with IERC721 token balanceOf owner
    Then I call function with HederaTokenService isToken token
    Then I call function with HederaTokenService isFrozen token, account
    Then I call function with HederaTokenService isKyc token, account
    Then I call function with HederaTokenService getTokenDefaultFreezeStatus token
    Then I call function with HederaTokenService getTokenDefaultKycStatus token