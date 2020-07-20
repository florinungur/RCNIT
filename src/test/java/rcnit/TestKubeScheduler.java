package rcnit;

import io.cucumber.java.Status;
import io.cucumber.java.en.And;
import io.cucumber.java.en.But;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rcnit.util.ExecKubernetesCmd;
import rcnit.util.ExecShellCmd;

public class TestKubeScheduler {

  private static final String NAMESPACE_NAME = "rcnit-kube-scheduler-testing";
  private static final Logger log = LoggerFactory.getLogger(TestKubeScheduler.class);
  private static final String NODE_LABEL = "isThisTheCoolestNodeLabel";
  private static final Map<String, String> NODE_TRUE_LABEL =
      Collections.singletonMap(NODE_LABEL, "true");
  private static Deployment helloWorldDeployment;
  private static Pod cpuRequestPod;
  private static Pod memoryRequestPod;
  private static Pod cpuTestPod1;
  private static Pod memoryTestPod1;
  private static Pod memoryTestPod2;
  private static ServiceAccount serviceAccount;

  @When("I create a pod with a CPU request that is too big for my node")
  public void i_create_a_pod_with_a_CPU_request_that_is_too_big_for_my_node() {
    ExecKubernetesCmd.createNamespace(NAMESPACE_NAME);

    serviceAccount =
        new ServiceAccountBuilder()
            .withNewMetadata()
            .withNewName("rcnit-kube-scheduler-testing-svc-account")
            .endMetadata()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient
          .serviceAccounts()
          .inNamespace(NAMESPACE_NAME)
          .createOrReplace(serviceAccount);
      log.info("Created '" + serviceAccount.getMetadata().getName() + "' service account");
    }

    cpuRequestPod =
        new PodBuilder()
            .withNewMetadata()
            .withName("cpu-request-test")
            .endMetadata()
            .withNewSpec()
            .withServiceAccount(serviceAccount.getMetadata().getName())
            .addNewContainer()
            .withName("cpu-request-test-container")
            .withImage("k8s.gcr.io/echoserver:1.4")
            .withImagePullPolicy("IfNotPresent")
            .withNewResources()
            .addToRequests(Collections.singletonMap("cpu", new Quantity("9001")))
            .addToLimits(Collections.singletonMap("cpu", new Quantity("9001")))
            .endResources()
            .endContainer()
            .endSpec()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient.pods().inNamespace(NAMESPACE_NAME).createOrReplace(cpuRequestPod);
      log.info("Created '" + cpuRequestPod.getMetadata().getName() + "' pod");
    }

    /*
    Sleeping 1 second to give the pod time to create, otherwise:
    java.lang.IllegalArgumentException: Pod with name:[] in namespace:[] not found!
     */
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  @Then("The scheduler should not schedule the pod due to insufficient CPU")
  public void the_scheduler_should_not_schedule_the_pod_due_to_insufficient_cpu() {
    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      String cpuRequestPodName = cpuRequestPod.getMetadata().getName();
      String podPhase =
          kubernetesClient
              .pods()
              .inNamespace(NAMESPACE_NAME)
              .withName(cpuRequestPodName)
              .get()
              .getStatus()
              .getPhase();
      boolean thePodIsInPendingStatus = podPhase.contentEquals("Pending");
      boolean thereAreNoFailedSchedulingEventsAndTheSchedulerIsHealthy =
          ExecKubernetesCmd.checkForFailedSchedulingEventsAndSchedulerHealth(NAMESPACE_NAME);

      if (!thePodIsInPendingStatus && thereAreNoFailedSchedulingEventsAndTheSchedulerIsHealthy) {
        log.error(
            Status.FAILED
                + "! The '"
                + cpuRequestPodName
                + "' pod is in '"
                + podPhase
                + "' phase!");
        throw new io.cucumber.java.PendingException();
      } else {
        log.info(
            Status.PASSED
                + "! The '"
                + cpuRequestPodName
                + "' pod is in '"
                + podPhase
                + "' phase!");
      }
    }
    ExecKubernetesCmd.deleteNamespace(NAMESPACE_NAME);
  }

  @When("I create a pod with a memory request that is too big for my node")
  public void i_create_a_pod_with_a_memory_request_that_is_too_big_for_my_node() {
    ExecKubernetesCmd.createNamespace(NAMESPACE_NAME);

    serviceAccount =
        new ServiceAccountBuilder()
            .withNewMetadata()
            .withNewName("rcnit-kube-scheduler-testing-svc-account")
            .endMetadata()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient
          .serviceAccounts()
          .inNamespace(NAMESPACE_NAME)
          .createOrReplace(serviceAccount);
      log.info("Created '" + serviceAccount.getMetadata().getName() + "' service account");
    }

    memoryRequestPod =
        new PodBuilder()
            .withNewMetadata()
            .withName("memory-request-test")
            .endMetadata()
            .withNewSpec()
            .withServiceAccount(serviceAccount.getMetadata().getName())
            .addNewContainer()
            .withName("memory-request-test-container")
            .withImage("k8s.gcr.io/echoserver:1.4")
            .withImagePullPolicy("IfNotPresent")
            .withNewResources()
            .addToRequests(Collections.singletonMap("memory", new Quantity("9001Gi")))
            .addToLimits(Collections.singletonMap("memory", new Quantity("9001Gi")))
            .endResources()
            .endContainer()
            .endSpec()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient.pods().inNamespace(NAMESPACE_NAME).createOrReplace(memoryRequestPod);
      log.info("Created '" + memoryRequestPod.getMetadata().getName() + "' pod");
    }
    /*
    Sleeping 1 second to give the pod time to create, otherwise:
    java.lang.IllegalArgumentException: Pod with name:[] in namespace:[] not found!
     */
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  @Then("The scheduler should not schedule the pod due to insufficient memory")
  public void the_scheduler_should_not_schedule_the_pod_due_to_insufficient_memory() {
    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      String memoryRequestPodName = memoryRequestPod.getMetadata().getName();
      String podPhase =
          kubernetesClient
              .pods()
              .inNamespace(NAMESPACE_NAME)
              .withName(memoryRequestPodName)
              .get()
              .getStatus()
              .getPhase();
      boolean thePodIsInPendingStatus = podPhase.contentEquals("Pending");
      boolean thereAreNoFailedSchedulingEventsAndTheSchedulerIsHealthy =
          ExecKubernetesCmd.checkForFailedSchedulingEventsAndSchedulerHealth(NAMESPACE_NAME);

      if (!thePodIsInPendingStatus && thereAreNoFailedSchedulingEventsAndTheSchedulerIsHealthy) {
        log.error(
            Status.FAILED
                + "! The '"
                + memoryRequestPodName
                + "' pod is in '"
                + podPhase
                + "' phase!");
        throw new io.cucumber.java.PendingException();
      } else {
        log.info(
            Status.PASSED
                + "! The '"
                + memoryRequestPodName
                + "' pod is in '"
                + podPhase
                + "' phase!");
      }
    }
    ExecKubernetesCmd.deleteNamespace(NAMESPACE_NAME);
  }

  @When("I use {int} milliCPU from a pod with {int} milliCPU request and limit")
  public void i_use_milliCPU_from_a_pod_with_milliCPU_request_and_limit(
      Integer amountOfCoresToUse, Integer amountOfUsableCores) {
    int amountOfCoresToUseInMacro = (int) (amountOfCoresToUse * 0.001);

    ExecKubernetesCmd.createNamespace(NAMESPACE_NAME);

    serviceAccount =
        new ServiceAccountBuilder()
            .withNewMetadata()
            .withNewName("rcnit-kube-scheduler-testing-svc-account")
            .endMetadata()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient
          .serviceAccounts()
          .inNamespace(NAMESPACE_NAME)
          .createOrReplace(serviceAccount);
      log.info("Created '" + serviceAccount.getMetadata().getName() + "' service account");
    }

    cpuTestPod1 =
        new PodBuilder()
            .withNewMetadata()
            .withName("cpu-test-1")
            .endMetadata()
            .withNewSpec()
            .withServiceAccount(serviceAccount.getMetadata().getName())
            .addNewContainer()
            .withName("cpu-test-1-container")
            .withImage("polinux/stress")
            .withImagePullPolicy("IfNotPresent")
            .withNewResources()
            .addToRequests(Collections.singletonMap("cpu", new Quantity(amountOfUsableCores + "m")))
            .addToLimits(Collections.singletonMap("cpu", new Quantity(amountOfUsableCores + "m")))
            .endResources()
            .addNewCommand("stress")
            .addNewArg("--verbose")
            .addNewArg("--cpu")
            .addNewArg(String.valueOf(amountOfCoresToUseInMacro))
            .addNewArg("--timeout")
            .addNewArg("20s")
            .endContainer()
            .withRestartPolicy("Never")
            .endSpec()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient.pods().inNamespace(NAMESPACE_NAME).createOrReplace(cpuTestPod1);
    }

    /*
    Sleeping 3 seconds to give the pod time to create, otherwise:
    java.lang.IllegalArgumentException: Pod with name:[] in namespace:[] not found!

    It needs 3 seconds because, I guess, on a fresh minikube installation, it takes some time to
    pull the container image. (This is the first pod in this class that uses polinux/stress.)
     */
    try {
      Thread.sleep(3 * 1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    ExecKubernetesCmd.waitForPodToBeReady(cpuTestPod1.getMetadata().getName(), NAMESPACE_NAME);
    ExecKubernetesCmd.getPodWatchLogOutput(
        cpuTestPod1.getMetadata().getName(), NAMESPACE_NAME, "successful run completed");

    /*
    Sleeping 3 seconds to give the pod time to terminate gracefully,
    otherwise kubernetesClient sometimes can't get podExitCode.
     */
    try {
      Thread.sleep(3 * 1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  @Then("The CPU-test pod should be fine")
  public void the_CPU_test_pod_should_be_fine() {
    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      String podPhase =
          kubernetesClient
              .pods()
              .inNamespace(NAMESPACE_NAME)
              .withName(cpuTestPod1.getMetadata().getName())
              .get()
              .getStatus()
              .getPhase();
      boolean podSucceeded = podPhase.contentEquals("Succeeded");

      /*
      Exit code 0 means that the pod terminated normally.
      See https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-phase
       */
      Integer podExitCode =
          kubernetesClient
              .pods()
              .inNamespace(NAMESPACE_NAME)
              .withName(cpuTestPod1.getMetadata().getName())
              .get()
              .getStatus()
              .getContainerStatuses()
              .get(0)
              .getState()
              .getTerminated()
              .getExitCode();
      boolean podExitCodeIs0 = podExitCode.equals(0);
      boolean thereAreNoFailedSchedulingEventsAndTheSchedulerIsHealthy =
          ExecKubernetesCmd.checkForFailedSchedulingEventsAndSchedulerHealth(NAMESPACE_NAME);

      if (podSucceeded
          && podExitCodeIs0
          && thereAreNoFailedSchedulingEventsAndTheSchedulerIsHealthy) {
        log.info(Status.PASSED + "! The pod did not get terminated!");
        log.info("Pod is in '" + podPhase + "' phase with exit code [" + podExitCode + "].");
      } else {
        log.error(Status.FAILED + "! The pod got terminated!");
        log.error("Pod is in '" + podPhase + "' phase with exit code [" + podExitCode + "].");
        throw new io.cucumber.java.PendingException();
      }
    }
    ExecKubernetesCmd.deleteNamespace(NAMESPACE_NAME);
  }

  @When(
      "I use {int} milliCPU from a pod with {int} milliCPU compute allocation "
          + "the CPU-test pod should be throttled")
  public void i_use_CPU_from_a_pod_with_CPU_compute_allocation_the_CPU_test_should_be_throttled(
      Integer amountOfCoresToUse, Integer amountOfUsableCores) {
    int amountOfCoresUsedByTestContainer;

    ExecKubernetesCmd.createNamespace(NAMESPACE_NAME);

    serviceAccount =
        new ServiceAccountBuilder()
            .withNewMetadata()
            .withNewName("rcnit-kube-scheduler-testing-svc-account")
            .endMetadata()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient
          .serviceAccounts()
          .inNamespace(NAMESPACE_NAME)
          .createOrReplace(serviceAccount);
      log.info("Created '" + serviceAccount.getMetadata().getName() + "' service account");
    }

    /*
    You need the metrics-server enabled for this, which collects data every minute.
    See https://github.com/kubernetes-sigs/metrics-server/blob/master/FAQ.md#how-often-metrics-are-scraped
    */
    ExecShellCmd minikubeEnableMetricsAddon = new ExecShellCmd();
    minikubeEnableMetricsAddon.execute("minikube addons enable metrics-server");
    String minikubeEnableMetricsAddonOutput = minikubeEnableMetricsAddon.returnAsString();

    if (minikubeEnableMetricsAddonOutput.contains("The 'metrics-server' addon is enabled")) {
      log.info(minikubeEnableMetricsAddonOutput);
    } else {
      log.error(minikubeEnableMetricsAddonOutput);
      throw new io.cucumber.java.PendingException();
    }

    // Don't put the timeout under 2m; the metrics-server will not return data
    int amountOfCoresToUseInMacro = (int) (amountOfCoresToUse * 0.001);
    Pod cpuTestPod2 =
        new PodBuilder()
            .withNewMetadata()
            .withName("cpu-test-2")
            .endMetadata()
            .withNewSpec()
            .withServiceAccount(serviceAccount.getMetadata().getName())
            .addNewContainer()
            .withName("cpu-test-2-container")
            .withImage("polinux/stress")
            .withImagePullPolicy("IfNotPresent")
            .withNewResources()
            .addToRequests(Collections.singletonMap("cpu", new Quantity(amountOfUsableCores + "m")))
            .addToLimits(Collections.singletonMap("cpu", new Quantity(amountOfUsableCores + "m")))
            .endResources()
            .addNewCommand("stress")
            .addNewArg("--verbose")
            .addNewArg("--cpu")
            .addNewArg(String.valueOf(amountOfCoresToUseInMacro))
            .addNewArg("--timeout")
            .addNewArg("2m")
            .endContainer()
            .withRestartPolicy("Never")
            .endSpec()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient.pods().inNamespace(NAMESPACE_NAME).createOrReplace(cpuTestPod2);

      /*
      Sleeping 1 second to give the pod time to create, otherwise:
      java.lang.IllegalArgumentException: Pod with name:[] in namespace:[] not found!
       */
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      ExecKubernetesCmd.waitForPodToBeReady(cpuTestPod2.getMetadata().getName(), NAMESPACE_NAME);

      ExecKubernetesCmd.getPodWatchLogOutput(
          cpuTestPod2.getMetadata().getName(), NAMESPACE_NAME, "successful run completed");

      /*
      amountOfCoresUsedByTestContainer has three assumptions:
      1. The metrics-server will continue to display the amounts in milliCPU
      2. There is no other pod in namespaceName
      3. The pod created in this method has only one container

      You might get an `IndexOutOfBoundsException` because the pod gets terminated too fast, I think.
       */
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

      amountOfCoresUsedByTestContainer =
          Integer.parseInt(
              kubernetesClient
                  .top()
                  .pods()
                  .metrics(NAMESPACE_NAME)
                  .getItems()
                  .get(0)
                  .getContainers()
                  .get(0)
                  .getUsage()
                  .get("cpu")
                  .getAmount());

      log.info("==== Pod metrics in '" + NAMESPACE_NAME + "' namespace ====");
      kubernetesClient
          .top()
          .pods()
          .metrics(NAMESPACE_NAME)
          .getItems()
          .forEach(
              podMetrics ->
                  podMetrics
                      .getContainers()
                      .forEach(
                          containerMetrics ->
                              log.info(
                                  "{}\t{}\tCPU: {}{}\tMemory: {}{}",
                                  podMetrics.getMetadata().getName(),
                                  containerMetrics.getName(),
                                  containerMetrics.getUsage().get("cpu").getAmount(),
                                  containerMetrics.getUsage().get("cpu").getFormat(),
                                  containerMetrics.getUsage().get("memory").getAmount(),
                                  containerMetrics.getUsage().get("memory").getFormat())));
      log.info("==== Node metrics  ====");
      kubernetesClient
          .top()
          .nodes()
          .metrics()
          .getItems()
          .forEach(
              nodeMetrics ->
                  log.info(
                      "{}\tCPU: {}{}\tMemory: {}{}",
                      nodeMetrics.getMetadata().getName(),
                      nodeMetrics.getUsage().get("cpu").getAmount(),
                      nodeMetrics.getUsage().get("cpu").getFormat(),
                      nodeMetrics.getUsage().get("memory").getAmount(),
                      nodeMetrics.getUsage().get("memory").getFormat()));
    }
    // It can happen that the pod can take a bit more milliCPUs than allocated
    if (amountOfCoresUsedByTestContainer < (amountOfUsableCores + 100)) {
      log.info(Status.PASSED + "! The pod got throttled!");
      log.info(
          "The '"
              + cpuTestPod2.getMetadata().getName()
              + "' pod was forced to use less than ["
              + amountOfCoresToUse
              + "] milliCPUs (+/- 100 milliCPUs)");
    } else {
      log.error(Status.FAILED + "! The pod did not get throttled!");
      log.error(
          "The '"
              + cpuTestPod2.getMetadata().getName()
              + "' pod uses more than ["
              + amountOfCoresToUse
              + "] milliCPUs (+/- 100 milliCPUs)");
      throw new io.cucumber.java.PendingException();
    }
    ExecKubernetesCmd.deleteNamespace(NAMESPACE_NAME);
  }

  @When("I use {int} MB of memory from a pod with {int} MB memory request and limit")
  public void i_use_MB_of_memory_from_a_pod_with_MB_memory_request_and_limit(
      int amountOfMemoryToUse, int amountOfUsableMemory) {
    ExecKubernetesCmd.createNamespace(NAMESPACE_NAME);

    serviceAccount =
        new ServiceAccountBuilder()
            .withNewMetadata()
            .withNewName("rcnit-kube-scheduler-testing-svc-account")
            .endMetadata()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient
          .serviceAccounts()
          .inNamespace(NAMESPACE_NAME)
          .createOrReplace(serviceAccount);
      log.info("Created '" + serviceAccount.getMetadata().getName() + "' service account");
    }

    memoryTestPod1 =
        new PodBuilder()
            .withNewMetadata()
            .withName("memory-test-1")
            .endMetadata()
            .withNewSpec()
            .withServiceAccount(serviceAccount.getMetadata().getName())
            .addNewContainer()
            .withName("memory-test-1")
            .withImage("polinux/stress")
            .withImagePullPolicy("IfNotPresent")
            .withNewResources()
            .addToRequests(
                Collections.singletonMap("memory", new Quantity(amountOfUsableMemory + "Mi")))
            .addToLimits(
                Collections.singletonMap("memory", new Quantity(amountOfUsableMemory + "Mi")))
            .endResources()
            .addNewCommand("stress")
            .addNewArg("--verbose")
            .addNewArg("--vm")
            .addNewArg("1")
            .addNewArg("--vm-bytes")
            .addNewArg(amountOfMemoryToUse + "M")
            .addNewArg("--vm-hang")
            .addNewArg("1")
            .addNewArg("--timeout")
            .addNewArg("20s")
            .endContainer()
            .withRestartPolicy("Never")
            .endSpec()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient.pods().inNamespace(NAMESPACE_NAME).createOrReplace(memoryTestPod1);
    }

    /*
    Sleeping 1 second to give the pod time to create, otherwise:
    java.lang.IllegalArgumentException: Pod with name:[] in namespace:[] not found!
     */
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    ExecKubernetesCmd.waitForPodToBeReady(memoryTestPod1.getMetadata().getName(), NAMESPACE_NAME);

    /*
    The getPodWatchLogOutput() method assumes the memory test is successful.
    It provides another error-point if anything goes wrong. Alternatively, you can do it like this:
    System.out.println("\nLog of 'memory-test-1' pod in namespace '" + namespaceName + "'");
    System.out.println("---------------------------------------------------------------");
    InputStream is =
        kubernetesClient
            .pods()
            .inNamespace(namespaceName)
            .withName(memoryPod1.getMetadata().getName())
            .watchLog()
            .getOutput();
    InputStreamReader isr = new InputStreamReader(is);
    Scanner scanner = new Scanner(isr);

    while (scanner.hasNextLine()) {
      System.out.println(scanner.nextLine());
    }
     */
    ExecKubernetesCmd.getPodWatchLogOutput(
        memoryTestPod1.getMetadata().getName(), NAMESPACE_NAME, "successful run completed");

    /*
    Sleeping 3 seconds to give the pod time to terminate gracefully,
    otherwise kubernetesClient sometimes can't get podExitCode
     */
    try {
      Thread.sleep(3 * 1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  @Then("The memory-test pod should be fine")
  public void the_memory_test_pod_should_be_fine() {
    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      String podPhase =
          kubernetesClient
              .pods()
              .inNamespace(NAMESPACE_NAME)
              .withName(memoryTestPod1.getMetadata().getName())
              .get()
              .getStatus()
              .getPhase();
      boolean podSucceeded = podPhase.contentEquals("Succeeded");

      /*
      Exit code 0 means that the pod terminated normally.
      https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-phase
       */
      Integer podExitCode =
          kubernetesClient
              .pods()
              .inNamespace(NAMESPACE_NAME)
              .withName(memoryTestPod1.getMetadata().getName())
              .get()
              .getStatus()
              .getContainerStatuses()
              .get(0)
              .getState()
              .getTerminated()
              .getExitCode();
      boolean podExitCodeIs0 = podExitCode.equals(0);
      boolean thereAreNoFailedSchedulingEventsAndTheSchedulerIsHealthy =
          ExecKubernetesCmd.checkForFailedSchedulingEventsAndSchedulerHealth(NAMESPACE_NAME);

      if (podSucceeded
          && podExitCodeIs0
          && thereAreNoFailedSchedulingEventsAndTheSchedulerIsHealthy) {
        log.info(
            Status.PASSED
                + "! The '"
                + memoryTestPod1.getMetadata().getName()
                + "' pod did not get terminated!");
        log.info(
            "The '"
                + memoryTestPod1.getMetadata().getName()
                + "' pod is in '"
                + podPhase
                + "' phase with exit code ["
                + podExitCode
                + "].");
      } else {
        log.error(
            Status.FAILED
                + "! The '"
                + memoryTestPod1.getMetadata().getName()
                + "' pod got terminated!");
        log.error(
            "The '"
                + memoryTestPod1.getMetadata().getName()
                + "' pod is in '"
                + podPhase
                + "' phase with exit code ["
                + podExitCode
                + "].");

        /*
        Only use this if you change the pod's restart policy to Always or OnFailure.
        The pod will have BackOff events even if healthy, but with exitCode=0.

        ExecShellCmd getBackOffEventsIfAny = new ExecShellCmd();

        getBackOffEventsIfAny.execute(
            "kubectl get events "
                + "--field-selector reason=BackOff "
                + "--sort-by=.metadata.creationTimestamp "
                + "--namespace "
                + namespaceName);

        String getBackOffEventsIfAnyOutput = getBackOffEventsIfAny.returnAsString();

        if (getBackOffEventsIfAnyOutput.contains("No resources found")) {
          log.info("The are no BackOff events");
        } else {
          log.error("There are BackOff events:");
          log.error(getBackOffEventsIfAnyOutput);
        }
         */
        throw new io.cucumber.java.PendingException();
      }
    }
    ExecKubernetesCmd.deletePod(memoryTestPod1.getMetadata().getName(), NAMESPACE_NAME);
  }

  @When("I use {int} MB of memory from a pod with {int} MB memory allocation")
  public void i_use_MB_of_memory_from_a_pod_with_MB_memory_allocation(
      int amountOfMemoryToUse, int amountOfUsableMemory) {
    memoryTestPod2 =
        new PodBuilder()
            .withNewMetadata()
            .withName("memory-test-2")
            .endMetadata()
            .withNewSpec()
            .withServiceAccount(serviceAccount.getMetadata().getName())
            .addNewContainer()
            .withName("memory-test-2-container")
            .withImage("polinux/stress")
            .withImagePullPolicy("IfNotPresent")
            .withNewResources()
            .addToRequests(
                Collections.singletonMap("memory", new Quantity(amountOfUsableMemory + "Mi")))
            .addToLimits(
                Collections.singletonMap("memory", new Quantity(amountOfUsableMemory + "Mi")))
            .endResources()
            .addNewCommand("stress")
            .addNewArg("--verbose")
            .addNewArg("--vm")
            .addNewArg("1")
            .addNewArg("--vm-bytes")
            .addNewArg(amountOfMemoryToUse + "M")
            .addNewArg("--vm-hang")
            .addNewArg("1")
            .addNewArg("--timeout")
            .addNewArg("20s")
            .endContainer()
            .withRestartPolicy("Never")
            .endSpec()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient.pods().inNamespace(NAMESPACE_NAME).createOrReplace(memoryTestPod2);
    }
    /*
    Sleeping 10 seconds to give the pod time to create.
    Can't use waitForPodToBeReady - see method.
     */
    try {
      Thread.sleep(10 * 1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    // I assume the run failed
    ExecKubernetesCmd.getPodWatchLogOutput(
        memoryTestPod2.getMetadata().getName(), NAMESPACE_NAME, "failed run completed");

    /*
    Sleeping 3 seconds to give the pod time to terminate gracefully,
    otherwise kubernetesClient sometimes can't get podExitCode
     */
    try {
      Thread.sleep(3 * 1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  @Then("The memory-test pod should be terminated")
  public void the_memory_test_pod_should_be_terminated() {
    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      String podPhase =
          kubernetesClient
              .pods()
              .inNamespace(NAMESPACE_NAME)
              .withName(memoryTestPod2.getMetadata().getName())
              .get()
              .getStatus()
              .getPhase();
      boolean podSucceeded = podPhase.contentEquals("Succeeded");

      /*
      Exit code 0 means the pod terminated normally.
      https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-phase
       */
      Integer podExitCode =
          kubernetesClient
              .pods()
              .inNamespace(NAMESPACE_NAME)
              .withName(memoryTestPod2.getMetadata().getName())
              .get()
              .getStatus()
              .getContainerStatuses()
              .get(0)
              .getState()
              .getTerminated()
              .getExitCode();
      boolean podExitCodeIs0 = podExitCode.equals(0);
      boolean thereAreNoFailedSchedulingEventsAndTheSchedulerIsHealthy =
          ExecKubernetesCmd.checkForFailedSchedulingEventsAndSchedulerHealth(NAMESPACE_NAME);

      if (podSucceeded
          && podExitCodeIs0
          && thereAreNoFailedSchedulingEventsAndTheSchedulerIsHealthy) {
        log.error(
            Status.FAILED
                + "! The '"
                + memoryTestPod2.getMetadata().getName()
                + "' pod did not get terminated!");
        log.error(
            "The '"
                + memoryTestPod2.getMetadata().getName()
                + "' pod is in '"
                + podPhase
                + "' phase with exit code ["
                + podExitCode
                + "].");
        throw new io.cucumber.java.PendingException();
      } else {
        log.info(
            Status.PASSED
                + "! The '"
                + memoryTestPod2.getMetadata().getName()
                + "' pod got terminated!");
        log.info(
            "The '"
                + memoryTestPod2.getMetadata().getName()
                + "' pod is in '"
                + podPhase
                + "' phase with exit code ["
                + podExitCode
                + "].");
        /*
        Only use this if you change the pod's restart policy to Always or OnFailure.
        The pod will have BackOff events even if healthy, but with exitCode=0.

        ExecShellCmd getBackOffEventsIfAny = new ExecShellCmd();

        getBackOffEventsIfAny.execute(
            "kubectl get events "
                + "--field-selector reason=BackOff "
                + "--sort-by=.metadata.creationTimestamp "
                + "--namespace "
                + namespaceName);

        String getBackOffEventsIfAnyOutput = getBackOffEventsIfAny.returnAsString();

        if (getBackOffEventsIfAnyOutput.contains("No resources found")) {
          log.info("The are no BackOff events");
        } else {
          log.error("There are BackOff events:");
          log.error(getBackOffEventsIfAnyOutput);
        }
         */
      }
    }
    ExecKubernetesCmd.deleteNamespace(NAMESPACE_NAME);
  }

  @Given("A deployment with {int} replicas")
  public void a_deployment_with_replicas(Integer replicaCount) {
    ExecKubernetesCmd.createNamespace(NAMESPACE_NAME);

    helloWorldDeployment =
        new DeploymentBuilder()
            .withNewMetadata()
            .withNewName("hello-world")
            .addToLabels("app", "echoserver")
            .endMetadata()
            .withNewSpec()
            .withReplicas(replicaCount)
            .withNewSelector()
            .addToMatchLabels("app", "echoserver")
            .endSelector()
            .withNewTemplate()
            .withNewMetadata()
            .addToLabels("app", "echoserver")
            .endMetadata()
            .withNewSpec()
            .addNewContainer()
            .withNewImage("k8s.gcr.io/echoserver:1.4")
            .withNewImagePullPolicy("IfNotPresent")
            .withNewName("echoserver-container")
            .addNewPort()
            .withContainerPort(80)
            .endPort()
            .endContainer()
            .withNewDnsPolicy("ClusterFirst")
            .withSchedulerName("default-scheduler")
            .endSpec()
            .endTemplate()
            .endSpec()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      try {
        kubernetesClient
            .apps()
            .deployments()
            .inNamespace(NAMESPACE_NAME)
            .createOrReplace(helloWorldDeployment);
        log.info("Created '" + helloWorldDeployment.getMetadata().getName() + "' deployment");
      } catch (KubernetesClientException e) {
        log.error(
            "Failed creating '" + helloWorldDeployment.getMetadata().getName() + "' deployment");
        e.printStackTrace();
        throw new io.cucumber.java.PendingException();
      }

      log.info(
          "Waiting for all '"
              + helloWorldDeployment.getMetadata().getName()
              + "' deployment replicas to become available... ");
      try {
        // See https://github.com/fabric8io/kubernetes-client/issues/2129
        kubernetesClient
            .apps()
            .deployments()
            .inNamespace(NAMESPACE_NAME)
            .withName(helloWorldDeployment.getMetadata().getName())
            .waitUntilCondition(
                deployment ->
                    Optional.ofNullable(deployment.getStatus())
                        .map(DeploymentStatus::getAvailableReplicas)
                        .orElse(-1)
                        .equals(replicaCount),
                replicaCount,
                TimeUnit.MINUTES);
      } catch (InterruptedException e) {
        e.printStackTrace();
        throw new io.cucumber.java.PendingException();
      }
    }
  }

  @When("I kill {int} replicas")
  public void i_kill_replicas(Integer numberOfReplicasToKill) {
    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      List<Pod> podList =
          kubernetesClient
              .pods()
              .inNamespace(NAMESPACE_NAME)
              .withLabel("app", "echoserver")
              .list()
              .getItems();
      int count = 0;

      for (Pod replica : podList) {
        if (numberOfReplicasToKill == count) {
          log.info("Killed [" + numberOfReplicasToKill + "] replicas");
          break;
        } else {
          String replicaName = replica.getMetadata().getName();
          if (kubernetesClient.pods().inNamespace(NAMESPACE_NAME).withName(replicaName).delete()) {
            log.info("Killed '" + replicaName + "' replica #" + count);
          } else {
            log.error("Failed killing '" + replicaName + "' replica #" + count);
            throw new io.cucumber.java.PendingException();
          }
        }
        count++;
      }
    }
  }

  @Then("The deployment should create {int} more replicas to equal {int} again")
  public void the_deployment_should_create_more_replicas(
      Integer numberOfReplicasToRecreate, Integer desiredReplicaCount) {
    log.info(
        "Waiting for all '"
            + helloWorldDeployment.getMetadata().getName()
            + "' deployment replicas to become available... ");

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      try {
        kubernetesClient
            .apps()
            .deployments()
            .inNamespace(NAMESPACE_NAME)
            .withName(helloWorldDeployment.getMetadata().getName())
            .waitUntilCondition(
                deployment ->
                    Optional.ofNullable(deployment.getStatus())
                        .map(DeploymentStatus::getAvailableReplicas)
                        .orElse(-1)
                        .equals(desiredReplicaCount),
                desiredReplicaCount,
                TimeUnit.MINUTES);
      } catch (InterruptedException e) {
        e.printStackTrace();
        throw new io.cucumber.java.PendingException();
      }

      int killedPods =
          kubernetesClient
              .events()
              .inNamespace(NAMESPACE_NAME)
              .withField("reason", "Killing")
              .list()
              .getItems()
              .size();
      int actualReplicaCount =
          kubernetesClient
              .apps()
              .deployments()
              .inNamespace(NAMESPACE_NAME)
              .withName(helloWorldDeployment.getMetadata().getName())
              .get()
              .getSpec()
              .getReplicas();

      if (actualReplicaCount == desiredReplicaCount && killedPods == numberOfReplicasToRecreate) {
        log.info(
            "The deployment '"
                + helloWorldDeployment.getMetadata().getName()
                + "' created ["
                + numberOfReplicasToRecreate
                + "] more replicas");
      } else {
        log.error(
            "The deployment '"
                + helloWorldDeployment.getMetadata().getName()
                + "' created ["
                // desiredReplicaCount - killedPods = the state after i_kill_replicas()
                + (actualReplicaCount - (desiredReplicaCount - killedPods))
                + "] more replicas");
        throw new io.cucumber.java.PendingException();
      }
    }
  }

  @And("The scheduler should be healthy")
  public void the_scheduler_should_be_healthy() {
    boolean thereAreNoFailedSchedulingEventsAndTheSchedulerIsHealthy =
        ExecKubernetesCmd.checkForFailedSchedulingEventsAndSchedulerHealth(NAMESPACE_NAME);

    if (thereAreNoFailedSchedulingEventsAndTheSchedulerIsHealthy) {
      log.info(Status.PASSED + "!");
    } else {
      log.info(Status.FAILED + "!");
      throw new io.cucumber.java.PendingException();
    }
    ExecKubernetesCmd.deleteNamespace(NAMESPACE_NAME);
  }

  @Given("A node label")
  public void a_node_label() {
    ExecKubernetesCmd.createNamespace(NAMESPACE_NAME);

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      // This command is minikube-specific
      try {
        kubernetesClient
            .nodes()
            .withName("minikube")
            .edit()
            .editMetadata()
            .addToLabels(NODE_TRUE_LABEL)
            .endMetadata()
            .done();
      } catch (KubernetesClientException e) {
        e.printStackTrace();
      }

      if (kubernetesClient
          .nodes()
          .withName("minikube")
          .get()
          .getMetadata()
          .getLabels()
          .toString()
          .contains(NODE_LABEL + "=true")) {
        log.info("Applied '" + NODE_TRUE_LABEL + "' node label");
      } else {
        log.error("Failed applying '" + NODE_TRUE_LABEL + "' node label!");
        throw new io.cucumber.java.PendingException();
      }
    }
  }

  @Then(
      "A pod with a correct requiredDuringSchedulingIgnoredDuringExecution node affinity "
          + "will get scheduled")
  public void
      a_pod_with_a_correct_requiredDuringSchedulingIgnoredDuringExecution_node_affinity_will_get_scheduled() {
    serviceAccount =
        new ServiceAccountBuilder()
            .withNewMetadata()
            .withNewName("rcnit-kube-scheduler-testing-svc-account")
            .endMetadata()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient
          .serviceAccounts()
          .inNamespace(NAMESPACE_NAME)
          .createOrReplace(serviceAccount);
      log.info("Created '" + serviceAccount.getMetadata().getName() + "' service account");
    }

    Pod nodeAffinityPod1 =
        new PodBuilder()
            .withNewMetadata()
            .withName("node-affinity-test-1")
            .endMetadata()
            .withNewSpec()
            .withServiceAccount(serviceAccount.getMetadata().getName())
            .withNewAffinity()
            .withNewNodeAffinity()
            .withNewRequiredDuringSchedulingIgnoredDuringExecution()
            .addNewNodeSelectorTerm()
            .addNewMatchExpression()
            .withNewKey(NODE_LABEL)
            .withNewOperator("In")
            .addNewValue("true")
            .endMatchExpression()
            .endNodeSelectorTerm()
            .endRequiredDuringSchedulingIgnoredDuringExecution()
            .endNodeAffinity()
            .endAffinity()
            .addNewContainer()
            .withName("node-affinity-test-1-container")
            .withImage("k8s.gcr.io/echoserver:1.4")
            .withImagePullPolicy("IfNotPresent")
            .endContainer()
            .endSpec()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient.pods().inNamespace(NAMESPACE_NAME).createOrReplace(nodeAffinityPod1);
    }

    /*
    Sleeping 1 second to give the pod time to create, otherwise:
    java.lang.IllegalArgumentException: Pod with name:[] in namespace:[] not found!
     */
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    AtomicBoolean podIsScheduled =
        ExecKubernetesCmd.checkIfPodIsScheduled(
            nodeAffinityPod1.getMetadata().getName(), NAMESPACE_NAME);

    ExecKubernetesCmd.getObjectEvents(nodeAffinityPod1.getMetadata().getName(), NAMESPACE_NAME);

    if (podIsScheduled.get()) {
      log.info(
          Status.PASSED
              + "! The '"
              + nodeAffinityPod1.getMetadata().getName()
              + "' pod was scheduled!");
    } else {
      log.error(
          Status.FAILED
              + "! The '"
              + nodeAffinityPod1.getMetadata().getName()
              + "' pod was not scheduled!");
      throw new io.cucumber.java.PendingException();
    }
    ExecKubernetesCmd.deletePod(nodeAffinityPod1.getMetadata().getName(), NAMESPACE_NAME);
  }

  @But(
      "A pod with a wrong requiredDuringSchedulingIgnoredDuringExecution node affinity "
          + "will get rejected")
  public void
      a_pod_with_a_wrong_requiredDuringSchedulingIgnoredDuringExecution_node_affinity_will_get_rejected() {
    Pod nodeAffinityPod2 =
        new PodBuilder()
            .withNewMetadata()
            .withName("node-affinity-test-2")
            .endMetadata()
            .withNewSpec()
            .withServiceAccount(serviceAccount.getMetadata().getName())
            .withNewAffinity()
            .withNewNodeAffinity()
            .withNewRequiredDuringSchedulingIgnoredDuringExecution()
            .addNewNodeSelectorTerm()
            .addNewMatchExpression()
            .withNewKey(NODE_LABEL)
            .withNewOperator("In")
            .addNewValue("falseOrWhateverReally")
            .endMatchExpression()
            .endNodeSelectorTerm()
            .endRequiredDuringSchedulingIgnoredDuringExecution()
            .endNodeAffinity()
            .endAffinity()
            .addNewContainer()
            .withName("node-affinity-test-2-container")
            .withImage("k8s.gcr.io/echoserver:1.4")
            .withImagePullPolicy("IfNotPresent")
            .endContainer()
            .endSpec()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient.pods().inNamespace(NAMESPACE_NAME).createOrReplace(nodeAffinityPod2);
    }

    /*
    Sleeping 1 second to give the pod time to create, otherwise:
    java.lang.IllegalArgumentException: Pod with name:[] in namespace:[] not found!
     */
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    AtomicBoolean podIsScheduled =
        ExecKubernetesCmd.checkIfPodIsScheduled(
            nodeAffinityPod2.getMetadata().getName(), NAMESPACE_NAME);

    ExecKubernetesCmd.getObjectEvents(nodeAffinityPod2.getMetadata().getName(), NAMESPACE_NAME);

    if (podIsScheduled.get()) {
      log.error(
          Status.FAILED
              + "! The '"
              + nodeAffinityPod2.getMetadata().getName()
              + "' pod was scheduled!");
      throw new io.cucumber.java.PendingException();
    } else {
      log.info(
          Status.PASSED
              + "! The '"
              + nodeAffinityPod2.getMetadata().getName()
              + "' pod was not scheduled!");
    }
    ExecKubernetesCmd.deletePod(nodeAffinityPod2.getMetadata().getName(), NAMESPACE_NAME);
  }

  @And(
      "A pod with a correct preferredDuringSchedulingIgnoredDuringExecution node affinity "
          + "will get scheduled")
  public void
      a_pod_with_a_correct_preferredDuringSchedulingIgnoredDuringExecution_node_affinity_will_get_scheduled() {
    Pod nodeAffinityPod3 =
        new PodBuilder()
            .withNewMetadata()
            .withName("node-affinity-test-3")
            .endMetadata()
            .withNewSpec()
            .withServiceAccount(serviceAccount.getMetadata().getName())
            .withNewAffinity()
            .withNewNodeAffinity()
            .addNewPreferredDuringSchedulingIgnoredDuringExecution()
            .withWeight(1)
            .withNewPreference()
            .addNewMatchExpression()
            .withNewKey(NODE_LABEL)
            .withNewOperator("In")
            .addNewValue("true")
            .endMatchExpression()
            .endPreference()
            .endPreferredDuringSchedulingIgnoredDuringExecution()
            .endNodeAffinity()
            .endAffinity()
            .addNewContainer()
            .withName("node-affinity-test-3-container")
            .withImage("k8s.gcr.io/echoserver:1.4")
            .withImagePullPolicy("IfNotPresent")
            .endContainer()
            .endSpec()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient.pods().inNamespace(NAMESPACE_NAME).createOrReplace(nodeAffinityPod3);
    }

    /*
    Sleeping 1 second to give the pod time to create, otherwise:
    java.lang.IllegalArgumentException: Pod with name:[] in namespace:[] not found!
     */
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    AtomicBoolean podIsScheduled =
        ExecKubernetesCmd.checkIfPodIsScheduled(
            nodeAffinityPod3.getMetadata().getName(), NAMESPACE_NAME);

    ExecKubernetesCmd.getObjectEvents(nodeAffinityPod3.getMetadata().getName(), NAMESPACE_NAME);

    if (podIsScheduled.get()) {
      log.info(
          Status.PASSED
              + "! The '"
              + nodeAffinityPod3.getMetadata().getName()
              + "' pod was scheduled!");
    } else {
      log.error(
          Status.FAILED
              + "! The '"
              + nodeAffinityPod3.getMetadata().getName()
              + "' pod was not scheduled!");
      throw new io.cucumber.java.PendingException();
    }
    ExecKubernetesCmd.deletePod(nodeAffinityPod3.getMetadata().getName(), NAMESPACE_NAME);
  }

  @And(
      "A pod with a wrong preferredDuringSchedulingIgnoredDuringExecution node affinity "
          + "will get scheduled")
  public void
      a_pod_with_a_wrong_preferredDuringSchedulingIgnoredDuringExecution_node_affinity_will_get_scheduled() {
    Pod nodeAffinityPod4 =
        new PodBuilder()
            .withNewMetadata()
            .withName("node-affinity-test-4")
            .endMetadata()
            .withNewSpec()
            .withServiceAccount(serviceAccount.getMetadata().getName())
            .withNewAffinity()
            .withNewNodeAffinity()
            .addNewPreferredDuringSchedulingIgnoredDuringExecution()
            // The weight doesn't matter; its range is 1-100 - try it out
            .withWeight(1)
            .withNewPreference()
            .addNewMatchExpression()
            .withNewKey(NODE_LABEL)
            .withNewOperator("In")
            .addNewValue("trueOrYouKnowWhateverMan")
            .endMatchExpression()
            .endPreference()
            .endPreferredDuringSchedulingIgnoredDuringExecution()
            .endNodeAffinity()
            .endAffinity()
            .addNewContainer()
            .withName("node-affinity-test-4-container")
            .withImage("k8s.gcr.io/echoserver:1.4")
            .withImagePullPolicy("IfNotPresent")
            .endContainer()
            .endSpec()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient.pods().inNamespace(NAMESPACE_NAME).createOrReplace(nodeAffinityPod4);
    }

    /*
    Sleeping 1 second to give the pod time to create, otherwise:
    java.lang.IllegalArgumentException: Pod with name:[] in namespace:[] not found!
     */
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    AtomicBoolean podIsScheduled =
        ExecKubernetesCmd.checkIfPodIsScheduled(
            nodeAffinityPod4.getMetadata().getName(), NAMESPACE_NAME);

    ExecKubernetesCmd.getObjectEvents(nodeAffinityPod4.getMetadata().getName(), NAMESPACE_NAME);

    if (podIsScheduled.get()) {
      log.info(
          Status.PASSED
              + "! The '"
              + nodeAffinityPod4.getMetadata().getName()
              + "' pod was scheduled!");
    } else {
      log.error(
          Status.FAILED
              + "! The '"
              + nodeAffinityPod4.getMetadata().getName()
              + "' pod was not scheduled!");
      throw new io.cucumber.java.PendingException();
    }
    ExecKubernetesCmd.deletePod(nodeAffinityPod4.getMetadata().getName(), NAMESPACE_NAME);

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      try {
        kubernetesClient
            .nodes()
            .withName("minikube")
            .edit()
            .editMetadata()
            .removeFromLabels(NODE_TRUE_LABEL)
            .endMetadata()
            .done();
      } catch (KubernetesClientException e) {
        e.printStackTrace();
      }

      if (kubernetesClient
          .nodes()
          .withName("minikube")
          .get()
          .getMetadata()
          .getLabels()
          .toString()
          .contains(NODE_LABEL + "=true")) {
        log.error("Failed removing '" + NODE_TRUE_LABEL + "' NodeRestriction label!");
        throw new io.cucumber.java.PendingException();
      } else {
        log.info("Removed '" + NODE_TRUE_LABEL + "' NodeRestriction label");
      }
    }
    ExecKubernetesCmd.deleteNamespace(NAMESPACE_NAME);
  }
}
