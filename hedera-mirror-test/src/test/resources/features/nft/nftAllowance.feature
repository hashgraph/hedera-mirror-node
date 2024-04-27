@nftallowance @allowance @fullsuite @critical @release @acceptance
Feature: Account NFT Allowance Coverage Feature

  Scenario Outline: Validate approval NftTransfer affect on NftAllowance
    Given I ensure NFT <tokenName> has been created
    And I associate account <recipient> with token <tokenName>
    Then I mint a serial number from the token
    And the mirror node REST API should return the transaction for token serial number 0 transaction flow
    Given I approve <spender> to all serials of the NFT
    Then the mirror node REST API should confirm the approved allowance of NFT <tokenName> and <spender> when owner is "true"
    And the mirror node REST API should confirm the approved allowance of NFT <tokenName> and <spender> when owner is "false"
    Given <spender> transfers NFT <tokenName> to <recipient>
    Then the mirror node REST API should confirm the approved transfer of serial number 0 and confirm the new owner is <recipient>
    When I delete the allowance on NFT <tokenName> for spender <spender>
    Then the mirror node REST API should confirm the approved allowance for NFT <tokenName> and <spender> is no longer available
    Examples:
      | tokenName             | spender | recipient |
      | ALLOWANCENONFUNGIBLE  | BOB     | ALICE     |


