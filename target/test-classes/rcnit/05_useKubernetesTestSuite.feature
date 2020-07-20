Feature: Use k8s-testsuite

  Scenario: Run k8s-testsuite
    When I deploy the k8s-testsuite
    Then The k8s-testsuite should be successful