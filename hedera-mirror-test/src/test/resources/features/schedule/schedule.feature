@schedulebase @fullsuite
Feature: Schedule Base Coverage Feature

  @critical @release @acceptance
  Scenario Outline: Validate Base Schedule Flow - ScheduleCreate of CryptoTransfer and ScheduleSign
    Given I successfully schedule a treasury HBAR disbursement to <accountName>
    Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    And the mirror node REST API should verify the non executed schedule entity
    When the scheduled transaction is signed by <accountName>
    Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    And the mirror node REST API should verify the non executed schedule entity
    When the scheduled transaction is signed by treasuryAccount
    And the mirror node REST API should return status <httpStatusCode> for the schedule transaction
    And the mirror node REST API should verify the executed schedule entity
    Examples:
      | accountName | httpStatusCode |
      | "CAROL"     | 200            |
