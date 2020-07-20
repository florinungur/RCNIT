Feature: Use Pumba

  Scenario: Deploy Pumba tests
    When I run all Pumba actions for 3 minutes
    Then The actions should be successful