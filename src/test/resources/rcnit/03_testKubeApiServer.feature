Feature: Test the Kubernetes API server

  Background:
    Given The default admission plugins are enabled
    And A default storage class
    And A 20 GB PersistentVolume

  Scenario: Test the LimitRange admission controller
    Given A max 5 GB LimitRange
    Then A tolerated 3 GB PersistentVolumeClaim will get provisioned
    But A prohibited 6 GB PersistentVolumeClaim will get rejected

  Scenario: Test the ResourceQuota admission controller
    Given A max 5 GB storage ResourceQuota
    Then A tolerated 3 GB PersistentVolumeClaim will get accepted
    But A prohibited 6 GB PersistentVolumeClaim will be denied

  Scenario: Test the DefaultStorageClass admission controller
    Then A PersistentVolumeClaim with no storage specification will get the default one
    When I do not have a default storage class
    Then The DefaultStorageClass admission controller does not do anything
    When I have multiple default storage classes
    Then The DefaultStorageClass admission controller will reject the PersistentVolumeClaim