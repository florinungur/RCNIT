# Resilient Cloud Native Infrastructure Testing (RCNIT)

This project is a set of [Cucumber](https://cucumber.io/docs/guides/overview/) tests for [Kubernetes](https://kubernetes.io/docs/concepts/overview/what-is-kubernetes/).

RCNIT aims to make a robust cloud-native infrastructure through testing Kubernetes.

_Java code written by an amateur programmer._

## Demo

![Demo of the Cucumber tests in action](demo.gif)

Please see [output.txt](output.txt) for a trimmed console output example of running the [TestRunner](src/test/java/rcnit/TestRunner.java).

## Installation

1. Clone the repository: `git clone https://github.com/florinungur/rcnit.git <YOUR-DESIRED-FOLDER>`.
2. Import the repository intro your IDE as a Maven project.
3. [Install JDK 14](https://docs.oracle.com/en/java/javase/14/install/overview-jdk-installation.html).
4. [Install minikube](https://kubernetes.io/docs/tasks/tools/install-minikube/).

Step 4 is optional. If you have a Kubernetes cluster already set up, make sure kubectl can access it and there should be no problems. See my other repository that sets up a beefier k8s cluster using kubespray.

## Configuration

This is the configuration I used for running the tests:

- Default minikube v1.9.0 running on VirtualBox version 6.1.6 r137129 Qt5.6.2
- Kubernetes v1.18.0 on Docker v19.03.8
- Windows 10 Pro (v10.0.18363 Build 18363) laptop with 12 gigs of memory

[00_validateEnvironment.feature](src/test/resources/rcnit/00_validateEnvironment.feature) makes sure that the barebones requirements for running the tests are met.

Also, see the [pom.xml](pom.xml) file for the dependencies required to run this project.

## Assumptions

- You run the tests on minikube with at least the same resources as those defined in [00_validateEnvironment.feature](src/test/resources/rcnit/00_validateEnvironment.feature).
- You use the same [pom.xml](pom.xml).
- You don't change the file structure.

## File structure

- Each used testing tool has its own feature file.
    - Prefaced by the verb _use_
    - Used testing tools:
        - [Chaos Mesh](https://github.com/pingcap/chaos-mesh)
        - [k8s-testsuite](https://github.com/mrahbar/k8s-testsuite)
        - [Pumba](https://github.com/alexei-led/pumba)
- The other feature files are personal tests made for individual Kubernetes components.
    - Prefaced by the verb _test_
- The feature files are in a certain order because I assume minikube is not configured at the start.
    - Cucumber doesn't allow for order specification in the `TestRunner` class.
- The `util` folder has three classes with several helper methods.

## Code logic

The code is verbose, heavily commented, and follows the [Google Java Style Guide](https://web.archive.org/web/20200707211921/https://google.github.io/styleguide/jsguide.html).

Each Cucumber feature file has its own namespace declared at the beginning of each Java class under `NAMESPACE_NAME`. (But, remember, [not all objects are in a namespace](https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/#not-all-objects-are-in-a-namespace).)

I delete resources used in multiple scenarios at the last step of the last scenario (e.g. the namespace).

I delete resources used in multiple steps at the last step (e.g. the second storage class in the `Test the DefaultStorageClass admission controller` scenario).

All Kubernetes objects contain the word **test** to be easily tracked by the `get-all` [kubectl plugin](https://github.com/kubernetes-sigs/krew).
Usage:
- `kubectl get-all | findstr test` (Windows)
- `kubectl get-all | grep test` (Linux)

## References & Comments

I create a new `kubernetesClient` every time due to https://github.com/fabric8io/kubernetes-client/issues/1522.

### Feature: Validate environment

See [00_validateEnvironment.feature](src/test/resources/rcnit/00_validateEnvironment.feature).

This feature assumes a powered-off default minikube installation. This file will start minikube and configure it.

The minikube environment uses the maximum amount of computing power I can allocate on my laptop in order for it to remain useful.

`kubectl describe node minikube` output:
```text
Allocatable:
    cpu:                4
    ephemeral-storage:  16390427417
    memory:             8061172Ki
    pods:               110
```

There are 9 default pods which cannot be deleted (in the `kube-system` namespace), so we can only play with 102 pods.

### Feature: Test etcd

See [01_testEtcd.feature](src/test/resources/rcnit/01_testEtcd.feature).

* Scenario: Encrypt data at rest
    - https://kubernetes.io/docs/tasks/administer-cluster/encrypt-data/
    - https://kubernetes.io/docs/concepts/configuration/secret/#decoding-a-secret
    
### Feature: Test the Kubernetes kubelet

See [02_testKubelet.feature](src/test/resources/rcnit/02_testKubelet.feature).

* Scenario: Test the liveness probe
    - https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/

### Feature: Test the Kubernetes API server

See [03_testKubeApiServer.feature](src/test/resources/rcnit/03_testKubeApiServer.feature).

I recommend running only the entire feature file.

If you run, for example, the `Test the DefaultStorageClass admission controller` scenario followed by
the `Test the LimitRange admission controller` scenario, you will get the error:
```text
FAILED! The 'tolerated-claim-test-1' PersistentVolumeClaim was rejected!
Failure executing: POST at: https://192.168.99.254:8443/api/v1/namespaces/rcnit-kube-apiserver-testing/persistentvolumeclaims. Message: Forbidden! User minikube doesn't have permission. persistentvolumeclaims "tolerated-claim-test-1" is forbidden: Internal error occurred: 2 default StorageClasses were found.
```

* Background
    - The `the_default_admission_plugins_are_enabled()` method uses a command that is minikube-specific, version-specific... very specific.
    - https://kubernetes.io/docs/tasks/configure-pod-container/configure-persistent-volume-storage/
    - I assume there are no more `PersistentVolume`s in `namespaceName`.

* Scenario: Test the LimitRange admission controller
    - https://kubernetes.io/docs/tasks/administer-cluster/limit-storage-consumption/#limitrange-to-limit-requests-for-storage
    - https://kubernetes.io/docs/concepts/storage/persistent-volumes/#binding

* Scenario: Test the ResourceQuota admission controller
    - https://kubernetes.io/docs/tasks/administer-cluster/limit-storage-consumption/#storagequota-to-limit-pvc-count-and-cumulative-storage-capacity

* Scenario: Test the DefaultStorageClass admission controller
    - https://kubernetes.io/docs/reference/access-authn-authz/admission-controllers/#defaultstorageclass
    - The fact that `pvc-with-no-class-specification-test-2` stays in `Pending` is expected behavior

### Feature: Test the Kubernetes scheduler

See [04_testKubeScheduler.feature](src/test/resources/rcnit/04_testKubeScheduler.feature).

* Scenario: Test CPU request scheduling
    - https://kubernetes.io/docs/tasks/configure-pod-container/assign-cpu-resource/#specify-a-cpu-request-that-is-too-big-for-your-nodes

* Scenario: Test memory request scheduling
    - https://kubernetes.io/docs/tasks/configure-pod-container/assign-memory-resource/#specify-a-memory-request-that-is-too-big-for-your-nodes

* Scenario: CPU-test a pod
    - https://kubernetes.io/docs/tasks/configure-pod-container/assign-cpu-resource/
    - https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/#meaning-of-cpu
    - Only play with whole integers or edit the code a bit to support `doubles`.
    - This scenario uses an accuracy of +/- 100 milliCPU.

* Scenario: Memory-test a pod
    - https://kubernetes.io/docs/tasks/configure-pod-container/assign-memory-resource/

* Scenario: Test node affinity
    - https://kubernetes.io/docs/tasks/configure-pod-container/assign-pods-nodes-using-node-affinity/

### Feature: Use k8s-testsuite

See [05_useKubernetesTestSuite.feature](src/test/resources/rcnit/05_useKubernetesTestSuite.feature).

* Scenario: Run k8s-testsuite
    - Go to https://github.com/mrahbar/k8s-testsuite to download the repository.
    - The `--set` arguments used in the `helmInstall` command make the load-test smaller and thus faster. Otherwise, my machine gets `FailedScheduling: Insufficient cpu` events.
    - The network test only works on multi-node k8s environments.
    
### Feature: Use Pumba

See [06_usePumba.feature](src/test/resources/rcnit/06_usePumba.feature).

* Scenario: Deploy Pumba tests
    - https://github.com/alexei-led/pumba
    - See `pumba/all-actions.yaml` for the Pumba pods used in this scenario.
    - The `pumba-stress` action is not working: https://github.com/alexei-led/pumba/issues/153
    
### Feature: Use Chaos Mesh

See [07_useChaosMesh.feature](src/test/resources/rcnit/07_useChaosMesh.feature).

`IOChaos` can't be implemented yet: https://github.com/pingcap/chaos-mesh/issues/463

There are several other shortcomings with this tool. See the code for more.

* Scenario Outline: Inject chaos actions
    - Scaling the test pods doesn't have the effect you would expect; see https://github.com/pingcap/chaos-mesh
    - Chaos Mesh actions run on a scheduler (e.g. daily, every 5 minutes, etc.).
    - Leave the `time-chaos` action last. See code.
