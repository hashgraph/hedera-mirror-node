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
    Then I burn FUNGIBLE token and get the total supply and balance
    Then I burn NFT and get the total supply and balance
    Then I pause "FUNGIBLE" token, unpause and get the status of the token
    Then I pause "NFT" token, unpause and get the status of the token
    And I associate FUNGIBLE token to receiver account
    Then the mirror node should return status 200 for the HAPI transaction
    And I associate NFT token to receiver account
    Then the mirror node should return status 200 for the HAPI transaction
    And I approve and transfer FUNGIBLE token to receiver account
    Then the mirror node should return status 200 for the HAPI transaction
    And I approve and transfer NFT token to receiver account
    Then the mirror node should return status 200 for the HAPI transaction
    Then I mint FUNGIBLE token and get the total supply and balance
    Then I mint NFT token and get the total supply and balance
    Then I wipe FUNGIBLE token and get the total supply and balance
    Then I wipe NFT and get the total supply and balance
    Then I freeze "FUNGIBLE" token, unfreeze and get status
    Then I freeze "NFT" token, unfreeze and get status
    And I associate precompile contract with the tokens
    Then the mirror node should return status 200 for the HAPI transaction
    And I transfer FUNGIBLE token to the precompile contract
    Then the mirror node should return status 200 for the HAPI transaction
    Given I mint a NFT
    Then the mirror node should return status 200 for the HAPI transaction
    And I approve and transfer NFT token to the precompile contract
    Then the mirror node should return status 200 for the HAPI transaction
    Then I approve a FUNGIBLE token and get allowance
    Then I approve a NFT token and get allowance
    Then I dissociate a FUNGIBLE token and fail transfer
    Then I dissociate a NFT and fail transfer
    Then I approve a NFT token and transfer it
    And I associate FUNGIBLE_KYC_UNFROZEN token to receiver account
    Then the mirror node should return status 200 for the HAPI transaction
    Then I grant and revoke KYC
    Then I successfully update the balance of an account and get the updated balance
    Then I approve a FUNGIBLE token and transfer it
    Then I successfully update the balance of an account and get the updated balance after 2 seconds

    Examples:
      | fungible | nft |
      | "FUNGIBLE" | "NFT" |
