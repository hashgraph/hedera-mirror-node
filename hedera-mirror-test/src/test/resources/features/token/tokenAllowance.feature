@tokenallowance @allowance @fullsuite
Feature: Account Crypto Allowance Coverage Feature

  @critical @release @acceptance
  Scenario Outline: Validate approval TokenTransfer affect on TokenAllowance amount
    Given I use named token <tokenName>
    Given I associate a new sender account with token 0
    And the mirror node REST API should return the transaction
    Given I associate a new recipient account with token 0
    And the mirror node REST API should return the transaction
    Given I approve sender 0 to transfer up to <approvedAmount> tokens 0
    Then the mirror node REST API should confirm the approved token 0 allowance of <approvedAmount> for sender 0
    Given I transfer <transferAmount> tokens 0 to recipient 0
    And the mirror node REST API should return the transaction for token 0 fund flow
    # This transfer by owner does not debit allowance amount
    And the mirror node REST API should confirm the approved token 0 allowance of <approvedAmount> for sender 0
    Given Sender 0 transfers <transferAmount> tokens 0 from the approved allowance to recipient 0
    Then the mirror node REST API should confirm the debit of <transferAmount> from approved token 0 allowance of <approvedAmount> for sender 0
    Then the mirror node REST API should confirm the approved transfer of <transferAmount> tokens
    When I approve sender 0 to transfer up to <approvedAmount> tokens 0
    Then the mirror node REST API should confirm the approved token 0 allowance of <approvedAmount> for sender 0
    When I delete the allowance on token 0 for sender 0
    Then the mirror node REST API should confirm the token allowance deletion
    Examples:
      | tokenName  | approvedAmount | transferAmount |
      | "FUNGIBLE" | 10000          | 100            |
