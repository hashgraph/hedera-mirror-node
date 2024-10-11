@fullsuite @acceptance @token
Feature: HTS Base Coverage Feature

  @fungible @critical @release
  Scenario Outline: Validate Token Flow - Create, Associate, Freeze, GrantKyc, Fund, Update, Burn, Mint, Wipe, Pause, Unpause, Dissociate, Delete
    Given I successfully create a new unfrozen and granted kyc token
    Then the mirror node REST API should return the transaction
    Then I ensure token has the correct properties
    And I ensure token has the expected metadata and key
    Given I update the token metadata key
    Then the mirror node REST API should return the transaction
    And I ensure token has the expected metadata and key
    Given I update the token metadata
    Then the mirror node REST API should return the transaction
    And I ensure token has the expected metadata and key
    When I associate ALICE with token
    Then the mirror node REST API should return the transaction
    Then the mirror node REST API should return the token relationship for token
    And I set account freeze status to <freezeStatus> for ALICE
    Then the mirror node REST API should return the transaction
    And I set account kyc status to <kycStatus> for ALICE
    Then the mirror node REST API should return the transaction
    Then I transfer <amount> tokens to ALICE
    Then the mirror node REST API should return the transaction for token fund flow
    Then I update the token
    And the mirror node REST API should confirm token update
    Then I airdrop <amount> tokens to CAROL
    Then the mirror node REST API should return the transaction
    And I verify "pending" airdrop of <amount> tokens to CAROL
    Then I cancel the airdrop to CAROL
    Then the mirror node REST API should return the transaction
    And I verify "cancelled" airdrop of <amount> tokens to CAROL
    Then I airdrop <amount> tokens to CAROL
    Then the mirror node REST API should return the transaction
    And I associate CAROL with token
    And I set account kyc status to <kycStatus> for CAROL
    Then the mirror node REST API should return the transaction
    Then CAROL claims the airdrop
    Then the mirror node REST API should return the transaction
    And I verify "successful" airdrop of <amount> tokens to CAROL
    Then CAROL rejects the fungible token
    Then the mirror node REST API should return the transaction CAROL returns <amount> fungible token to OPERATOR
    Then I airdrop <amount> tokens to CAROL
    Then the mirror node REST API should return the transaction
    And I verify "successful" airdrop of <amount> tokens to CAROL
    Then I burn <modifySupplyAmount> from the token
    And the mirror node REST API should return the transaction
    Then I mint <modifySupplyAmount> from the token
    And the mirror node REST API should return the transaction
    Then I wipe <amount> from the token for ALICE
    And the mirror node REST API should return the transaction
    Then I pause the token
    And the mirror node REST API should return the transaction
    And the mirror node Token Info REST API should return pause status PAUSED
    Then I unpause the token
    And the mirror node REST API should return the transaction
    And the mirror node Token Info REST API should return pause status UNPAUSED
    Then I dissociate ALICE from the token
    And the mirror node REST API should return the transaction
    Then I delete the token
    And the mirror node REST API should return the transaction
    Examples:
      | amount | freezeStatus | kycStatus | modifySupplyAmount |
      | 2350   | 2            | 1         | 100                |

  @nft @critical @release
  Scenario: Validate Full NFT Flow - Create, Associate, Mint, Transfer, Burn, Wipe, Update Treasury, Delete
    Given I successfully create a new nft with infinite supplyType
    Then the mirror node REST API should return the transaction
    And I ensure token has the correct properties
    And I ensure token has the expected metadata and key
    Given I update the token metadata key
    Then the mirror node REST API should return the transaction
    And I ensure token has the expected metadata and key
    Given I update the token metadata
    Then the mirror node REST API should return the transaction
    And I ensure token has the expected metadata and key
    When I associate ALICE with token
    And the mirror node REST API should return the transaction
    Then the mirror node REST API should return the token relationship for nft
    Then I mint a serial number from the token
    And the mirror node REST API should return the transaction for token serial number index 0 transaction flow
    Then I transfer serial number index 0 to ALICE
    And the mirror node REST API should return the transaction for token serial number index 0 full flow
    Given I update the metadata for serial number index 0
    Then the mirror node REST API should return the transaction for token serial number index 0 transaction flow
    Given I mint a serial number from the token
    Then the mirror node REST API should return the transaction for token serial number index 1 transaction flow
    Given I update the metadata for serial number indices 0 and 1
    Then the mirror node REST API should return the transaction for token serial number index 0 transaction flow
    And the mirror node REST API should return the transaction for token serial number index 1 transaction flow
    Given I mint a serial number from the token
    Then the mirror node REST API should return the transaction for token serial number index 2 transaction flow
    Then I airdrop serial number 3 to CAROL
    Then the mirror node REST API should return the transaction
    And I verify "pending" airdrop of serial number 3 to CAROL
    Then I cancel the NFT with serial number 3 airdrop to CAROL
    Then the mirror node REST API should return the transaction
    And I verify "cancelled" airdrop of serial number 3 to CAROL
    Then I airdrop serial number 3 to CAROL
    Then the mirror node REST API should return the transaction
    And I associate CAROL with token
    And the mirror node REST API should return the transaction
    Then the mirror node REST API should return the token relationship for nft
    Then CAROL claims airdrop for NFT with serial number 3
    Then the mirror node REST API should return the transaction
    And I verify "successful" airdrop of serial number 3 to CAROL
#    And I transfer serial number index 1 to CAROL
    And CAROL rejects serial number index 2
    Then the mirror node REST API should return the transaction CAROL returns serial number index 2 to OPERATOR
    Then I airdrop serial number 3 to CAROL
    Then the mirror node REST API should return the transaction
    And I verify "successful" airdrop of serial number 3 to CAROL
    Then I wipe serial number index 0 from token for ALICE
    And the mirror node REST API should return the transaction for token serial number index 0 transaction flow
    Then I burn serial number index 1 from token
    And the mirror node REST API should return the transaction for token serial number index 1 transaction flow
        #TODO This test should be updated when services enables the ability to change NFT treasury accounts.
    Then I update the treasury of token to ALICE
    And the mirror node REST API should return the transaction
    Then I update the treasury of token to operator
    And the mirror node REST API should return the transaction
    Then I delete the token
    And the mirror node REST API should return the transaction for token serial number index 1 transaction flow

  @customfees
  Scenario Outline: Validate Base Token Flow with Custom Fees Schedule - Create, Associate, Fund, Transfer
    Given I successfully create a new token with custom fees schedule
      | amount | numerator | denominator | collector | maximum | minimum | token |
      | 50     |           |             | ALICE     |         |         |       |
      | 20     |           |             | CAROL     |         |         |       |
    Then the mirror node REST API should confirm token with custom fees schedule
    # Token metadata and key were not set for this token
    And I ensure token has the expected metadata and key
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
      | amount | collector | effectivePayer | token |
      | 100    | ALICE     | DAVE           |       |
      | 20     | CAROL     | ALICE          | 0     |

    Examples:
      | fundAmount |
      | 3000       |
