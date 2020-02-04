@Accounts @FullSuite
Feature: Account Coverage Feature

    Background: User has sdk client
        Given Config context is loaded
        And User obtained SDK client for account feature

    @BalanceCheck @Sanity
    Scenario Outline: Validate account balance check scenario
        When I request balance info for this account
        Then the result should be greater than or equal to <threshold>
        Examples:
            | threshold |
            | 1000000   |
