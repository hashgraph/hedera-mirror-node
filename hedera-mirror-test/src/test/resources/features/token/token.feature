@fullsuite @acceptance @critical @release @token
Feature: HTS Base Coverage Feature

  @fungible
  Scenario Outline: Validate Token Flow - Create, Associate, Freeze, GrantKyc, Fund, Update, Burn, Mint, Wipe, Pause, Unpause, Dissociate, Delete
    Given I successfully create a new token with freeze status 2 and kyc status 1
    Then the mirror node REST API should return the transaction
    When I associate ALICE with token
    Then the mirror node REST API should return the transaction
    Then the mirror node REST API should return the token relationship for token
    And I set account freeze status to <freezeStatus>
    Then the mirror node REST API should return the transaction
    And I set account kyc status to <kycStatus>
    Then the mirror node REST API should return the transaction
    Then I transfer <amount> tokens to ALICE
    Then the mirror node REST API should return the transaction for token fund flow
    Then I update the token
    And the mirror node REST API should confirm token update
    Then I burn <modifySupplyAmount> from the token
    And the mirror node REST API should return the transaction
    Then I mint <modifySupplyAmount> from the token
    And the mirror node REST API should return the transaction
    Then I wipe <amount> from the token
    And the mirror node REST API should return the transaction
    Then I pause the token
    And the mirror node REST API should return the transaction
    And the mirror node Token Info REST API should return pause status "PAUSED"
    Then I unpause the token
    And the mirror node REST API should return the transaction
    And the mirror node Token Info REST API should return pause status "UNPAUSED"
    Then I dissociate the account from the token
    And the mirror node REST API should return the transaction
    Then I delete the token
    And the mirror node REST API should return the transaction
    Examples:
      | amount | freezeStatus | kycStatus | modifySupplyAmount |
      | 2350   | 2            | 1         | 100                |

  @nft
  Scenario Outline: Validate Full NFT Flow - Create, Associate, Mint, Transfer, Burn, Wipe, Update Treasury, Delete
    Given I successfully create a new nft with supplyType <supplyType>
    Then the mirror node REST API should return the transaction
    When I associate ALICE with token
    And the mirror node REST API should return the transaction
    Then the mirror node REST API should return the token relationship for nft
    Then I mint a serial number from the token
    And the mirror node REST API should return the transaction for token serial number 0 transaction flow
    Then I transfer serial number 0 to ALICE
    And the mirror node REST API should return the transaction for token serial number 0 full flow
    Then I wipe serial number 0 from token
    And the mirror node REST API should return the transaction for token serial number 0 transaction flow
    Then I mint a serial number from the token
    And the mirror node REST API should return the transaction for token serial number 1 transaction flow
    Then I burn serial number 1 from token
    And the mirror node REST API should return the transaction for token serial number 1 transaction flow
        #TODO This test should be updated when services enables the ability to change NFT treasury accounts.
    Then I update the treasury of token to ALICE
    And the mirror node REST API should return the transaction
    Then I delete the token
    And the mirror node REST API should return the transaction for token serial number 1 transaction flow
    Examples:
      | supplyType |
      | "INFINITE" |

  @acceptance @customfees
  Scenario Outline: Validate Base Token Flow with Custom Fees Schedule - Create, Associate, Fund, Transfer
    Given I successfully create a new token with custom fees schedule
      | amount | numerator | denominator | collector | maximum | minimum | token |
      | 50     |           |             | ALICE     |         |         |       |
      | 20     |           |             | CAROL     |         |         |       |
    Then the mirror node REST API should confirm token with custom fees schedule
    # Create ALICE as hbar fee collector, CAROL as fractional fee collector, and DAVE as token transfer recipient
    Given I associate ALICE with token
    And the mirror node REST API should return the transaction
    Given I associate CAROL with token
    And the mirror node REST API should return the transaction
    Given I associate DAVE with token
    Given I transfer <fundAmount> tokens to DAVE
    And the mirror node REST API should return the transaction for token fund flow
    Given I update token with new custom fees schedule
      | amount | numerator | denominator | collector | maximum | minimum | token |
      | 100    |           |             | ALICE     |         |         |       |
      |        | 1         | 10          | CAROL     |         |         |       |
    And the mirror node REST API should return the transaction
    When DAVE transfers 200 tokens to ALICE with fractional fee 20
    Then the mirror node REST API should return the transaction for token fund flow with assessed custom fees
      | amount | collector | token |
      | 100    | ALICE     |       |
      | 20     | CAROL     | 0     |

    Examples:
      | fundAmount |
      | 3000       |
