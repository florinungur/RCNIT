Feature: Test etcd

  Scenario: Encrypt data at rest
    When I create a secret
    Then The etcd key should be encrypted