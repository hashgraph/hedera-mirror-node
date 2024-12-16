@schedulebase @fullsuite
Feature: Schedule Base Coverage Feature

  @critical @release @acceptance
  Scenario Outline: Validate Base Schedule Flow - ScheduleCreate of CryptoTransfer and ScheduleSign
#    Scheduled transaction without expiration (defaults to 30 minutes) that is being deleted
    Given I successfully schedule a HBAR transfer from treasury to BOB "without" expiration time and wait for expiry "false" - plus 0 seconds
    Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    And the mirror node REST API should verify the "NON_EXECUTED" schedule entity "without" expiration time and wait for expiry "false"
    Given I successfully delete the schedule
    Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    And the mirror node REST API should verify the "DELETED" schedule entity "without" expiration time and wait for expiry "false"
#    Scheduled transaction that doesn't collects the needed signatures
    Given I successfully schedule a HBAR transfer from treasury to BOB "with" expiration time and wait for expiry "true" - plus 2 seconds
    Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    Then I wait for the schedule to expire
    And the mirror node REST API should verify the "EXPIRED" schedule entity "with" expiration time and wait for expiry "true"
#    Scheduled transaction that collects the needed signatures
    Given I successfully schedule a HBAR transfer from treasury to BOB "with" expiration time and wait for expiry "false" - plus 60 seconds
    Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    And the mirror node REST API should verify the "NON_EXECUTED" schedule entity "with" expiration time and wait for expiry "false"
    When the scheduled transaction is signed by BOB
    Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    And the mirror node REST API should verify the "NON_EXECUTED" schedule entity "with" expiration time and wait for expiry "false"
    When the scheduled transaction is signed by treasuryAccount
    And the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    And the mirror node REST API should verify the "EXECUTED" schedule entity "with" expiration time and wait for expiry "false"


    Examples:
      | httpStatusCode |
      | 200            |
