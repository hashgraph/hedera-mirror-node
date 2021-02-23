@ScheduleBase @FullSuite
Feature: Schedule Base Coverage Feature
  # Enter feature description here

    @Acceptance @Sanity
    Scenario Outline: Validate Base Schedule Flow - Create
        Given I successfully create a new schedule
        Then the mirror node REST API should return status <httpStatusCode> for the schedule transaction
        Examples:
            | httpStatusCode |
            | 200            |
