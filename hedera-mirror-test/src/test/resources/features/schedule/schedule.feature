@schedulebase @fullsuite
Feature: Schedule Base Coverage Feature

  @critical @release @acceptance
  Scenario Outline: Validate Base Schedule Flow - ScheduleCreate of CryptoTransfer and ScheduleSign
#    Given I successfully deploy precompile contract
#    Given I successfully schedule a smart contract call - HBAR transfer from treasury to BOB "without" expiration time and wait for expiry "false" - plus 60 seconds
#    Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
#    And the mirror node REST API should verify the "NON_EXECUTED" schedule entity "without" expiration time and wait for expiry "false"
#      Schedule to be manually deleted
    Given I successfully schedule a HBAR transfer from treasury to BOB "without" expiration time and wait for expiry "false" - plus 0 seconds
    Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    And the mirror node REST API should verify the "NON_EXECUTED" schedule entity "without" expiration time and wait for expiry "false"
    Given I successfully delete the schedule
    Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    And the mirror node REST API should verify the "DELETED" schedule entity "without" expiration time and wait for expiry "false"
    #Schedule to be automatically deleted
    Given I successfully schedule a HBAR transfer from treasury to BOB "with" expiration time and wait for expiry "true" - plus 10 seconds
    Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    And the mirror node REST API should verify the "NON_EXECUTED" schedule entity "with" expiration time and wait for expiry "true"
    Then I wait for the schedule to expire
    And the mirror node REST API should verify the "EXPIRED" schedule entity "with" expiration time and wait for expiry "true"

    Given I successfully schedule a HBAR transfer from treasury to BOB "with" expiration time and wait for expiry "false" - plus 60 seconds
    Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    And the mirror node REST API should verify the "NON_EXECUTED" schedule entity "with" expiration time and wait for expiry "false"
    When the scheduled transaction is signed by BOB
    Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    And the mirror node REST API should verify the "NON_EXECUTED" schedule entity "with" expiration time and wait for expiry "false"
    When the scheduled transaction is signed by treasuryAccount
    And the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    And the mirror node REST API should verify the "EXECUTED" schedule entity "with" expiration time and wait for expiry "false"
    And I verify the account balances after the schedule execution
#    Schedule with expiration time and wait for expirty true
    Given I successfully schedule a HBAR transfer from treasury to BOB "with" expiration time and wait for expiry "true" - plus 4 seconds
    Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    And the mirror node REST API should verify the "NON_EXECUTED" schedule entity "with" expiration time and wait for expiry "true"
    When the scheduled transaction is signed by BOB
    Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    And the mirror node REST API should verify the "NON_EXECUTED" schedule entity "with" expiration time and wait for expiry "true"
    When the scheduled transaction is signed by treasuryAccount
    And the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    And the mirror node REST API should verify the "NON_EXECUTED" schedule entity "with" expiration time and wait for expiry "true"
    Then I wait for the schedule to expire
    And the mirror node REST API should verify the "EXECUTED" schedule entity "with" expiration time and wait for expiry "true"
    And I verify the account balances after the schedule execution
#    Smart Contract call schedule

    Examples:
      | httpStatusCode |
      | 200            |
