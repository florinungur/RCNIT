package rcnit;

import io.cucumber.java.Status;
import io.cucumber.java.en.But;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rcnit.util.ExecKubernetesCmd;
import rcnit.util.ExecShellCmd;

public class TestKubelet {

  private static final String NAMESPACE_NAME = "rcnit-kubelet-testing";
  private static final Logger log = LoggerFactory.getLogger(TestKubelet.class);
  private static Pod livenessProbePod;

  @Given("A pod with a liveness probe of {int} periodSeconds and a {string} container command")
  public void a_pod_with_a_liveness_probe_of_periodSeconds_and_a_container_command(
      Integer periodSeconds, String containerSleepCommand) {
    List<String> containerArgs =
        Arrays.asList(
            "/bin/sh",
            "-c",
            "touch /tmp/healthy; " + containerSleepCommand + "; rm -rf /tmp/healthy; sleep 600");

    ExecKubernetesCmd.createNamespace(NAMESPACE_NAME);

    ServiceAccount serviceAccount =
        new ServiceAccountBuilder()
            .withNewMetadata()
            .withNewName("rcnit-kubelet-testing-svc-account")
            .endMetadata()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient
          .serviceAccounts()
          .inNamespace(NAMESPACE_NAME)
          .createOrReplace(serviceAccount);
      log.info("Created '" + serviceAccount.getMetadata().getName() + "' service account");
    }

    livenessProbePod =
        new PodBuilder()
            .withNewMetadata()
            .withName("liveness-probe-pod")
            .endMetadata()
            .withNewSpec()
            .withServiceAccount(serviceAccount.getMetadata().getName())
            .addNewContainer()
            .withNewName("liveness-probe-pod-container")
            .withNewImage("k8s.gcr.io/busybox")
            .withNewImagePullPolicy("IfNotPresent")
            .withArgs(containerArgs)
            .withNewLivenessProbe()
            .withNewExec()
            .addNewCommand("cat /tmp/healthy")
            .endExec()
            .withInitialDelaySeconds(5)
            .withPeriodSeconds(periodSeconds)
            .endLivenessProbe()
            .endContainer()
            .withRestartPolicy("Never")
            .endSpec()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient.pods().inNamespace(NAMESPACE_NAME).createOrReplace(livenessProbePod);
      log.info("Created '" + livenessProbePod.getMetadata().getName() + "' pod");
    }
    ExecKubernetesCmd.waitForPodToBeReady(livenessProbePod.getMetadata().getName(), NAMESPACE_NAME);
  }

  @Then("There are no anomalous pod events for the first ten seconds")
  public void there_are_no_anomalous_pod_events_for_the_first_ten_seconds() {
    ExecShellCmd getPodEvents = new ExecShellCmd();
    getPodEvents.execute(
        "kubectl get events"
            + " --field-selector=involvedObject.name="
            + livenessProbePod.getMetadata().getName()
            + " --field-selector=type!=Normal"
            + " --namespace="
            + NAMESPACE_NAME
            + " --sort-by=.metadata.creationTimestamp");
    String getPodEventsOutput = getPodEvents.returnAsString();

    if (getPodEventsOutput.contains("No resources found") || getPodEventsOutput.isEmpty()) {
      log.info(
          Status.PASSED
              + "! There are no anomalous events for '"
              + livenessProbePod.getMetadata().getName()
              + "' pod!");
      ExecKubernetesCmd.getObjectEvents(livenessProbePod.getMetadata().getName(), NAMESPACE_NAME);
    } else {
      log.error(
          Status.FAILED
              + "! There are anomalous events for '"
              + livenessProbePod.getMetadata().getName()
              + "' pod:\n"
              + getPodEventsOutput);
      throw new io.cucumber.java.PendingException();
    }
  }

  @But("There are anomalous pod events after {int} seconds")
  public void there_are_anomalous_pod_events_after_seconds(
      Integer amountOfTimeBeforeAnomalousEvents) {
    log.info("Sleeping for [" + amountOfTimeBeforeAnomalousEvents + "] seconds...");
    try {
      Thread.sleep(amountOfTimeBeforeAnomalousEvents * 1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    ExecShellCmd getPodEvents = new ExecShellCmd();
    getPodEvents.execute(
        "kubectl get events"
            + " --field-selector=involvedObject.name="
            + livenessProbePod.getMetadata().getName()
            + " --field-selector=type!=Normal"
            + " --namespace="
            + NAMESPACE_NAME
            + " --sort-by=.metadata.creationTimestamp");
    String getPodEventsOutput = getPodEvents.returnAsString();

    if (getPodEventsOutput.contains("No resources found") || getPodEventsOutput.isEmpty()) {
      log.error(
          Status.FAILED
              + "! There are no anomalous events for '"
              + livenessProbePod.getMetadata().getName()
              + "' pod!");
      ExecKubernetesCmd.getObjectEvents(livenessProbePod.getMetadata().getName(), NAMESPACE_NAME);
      ExecKubernetesCmd.deletePod(livenessProbePod.getMetadata().getName(), NAMESPACE_NAME);
      ExecKubernetesCmd.deleteNamespace(NAMESPACE_NAME);
      throw new io.cucumber.java.PendingException();
    } else {
      log.info(
          Status.PASSED
              + "! There are anomalous events for '"
              + livenessProbePod.getMetadata().getName()
              + "' pod:\n"
              + getPodEventsOutput);
    }
    ExecKubernetesCmd.deletePod(livenessProbePod.getMetadata().getName(), NAMESPACE_NAME);
    ExecKubernetesCmd.deleteNamespace(NAMESPACE_NAME);
  }
}
