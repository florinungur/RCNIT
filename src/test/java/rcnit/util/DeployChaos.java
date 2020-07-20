package rcnit.util;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rcnit.UseChaosMesh;

public class DeployChaos {

  private static final String NAMESPACE_NAME = UseChaosMesh.NAMESPACE_NAME;
  private static final String MY_CHAOS_MESH_TESTS_PATH = UseChaosMesh.MY_CHAOS_MESH_TESTS_PATH;
  private static final Logger log = LoggerFactory.getLogger(DeployChaos.class);
  private static Deployment deployment;

  public static void launchAction(String testName, String actionName, int amountOfTestPods) {
    // The labels should match the labelSelectors from MY_CHAOS_MESH_TESTS_PATH
    deployment =
        new DeploymentBuilder()
            .withNewMetadata()
            .withNewName("hello-world")
            .addToLabels("app", "rcnit-chaos-mesh-testing")
            .endMetadata()
            .withNewSpec()
            .withReplicas(amountOfTestPods)
            .withNewSelector()
            .addToMatchLabels("app", "rcnit-chaos-mesh-testing")
            .endSelector()
            .withNewTemplate()
            .withNewMetadata()
            .addToLabels("app", "rcnit-chaos-mesh-testing")
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
            .createOrReplace(deployment);
        log.info("Created '" + deployment.getMetadata().getName() + "' deployment");
      } catch (KubernetesClientException e) {
        log.error("Failed creating '" + deployment.getMetadata().getName() + "' deployment");
        e.printStackTrace();
        throw new io.cucumber.java.PendingException();
      }

      log.info(
          "Waiting for all '"
              + deployment.getMetadata().getName()
              + "' deployment replicas to become available...");
      try {
        kubernetesClient
            .apps()
            .deployments()
            .inNamespace(NAMESPACE_NAME)
            .withName(deployment.getMetadata().getName())
            .waitUntilCondition(
                deployment ->
                    Optional.ofNullable(deployment.getStatus())
                        .map(DeploymentStatus::getAvailableReplicas)
                        .orElse(-1)
                        .equals(amountOfTestPods),
                amountOfTestPods,
                TimeUnit.MINUTES);
      } catch (InterruptedException e) {
        e.printStackTrace();
        throw new io.cucumber.java.PendingException();
      }
      log.info(
          "All '" + deployment.getMetadata().getName() + "' deployment replicas are available");
    }

    /*
    Give the webhook-cert pods (which have well-documented issues) some time, otherwise:
    Error from server (InternalError):
    error when creating "src/test/resources/my-chaos-mesh-tests/pod-failure.yaml":
    Internal error occurred: failed calling webhook "mpodchaos.kb.io":
    Post https://chaos-mesh-controller-manager.rcnit-chaos-mesh-testing.svc:443/mutate-pingcap-com-v1alpha1-podchaos?timeout=30s:

    dial tcp 10.96.175.44:443: connect: connection refused
    OR
    service "chaos-mesh-controller-manager" not found

    See https://github.com/pingcap/chaos-mesh/issues/435
     */
    try {
      Thread.sleep(15 * 1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    /*
    Launching the Chaos Mesh actions can't be done with fabric8's Kubernetes client.
    See https://github.com/fabric8io/kubernetes-client/issues/2139
    */
    ExecShellCmd applyAction = new ExecShellCmd();
    applyAction.execute(
        "kubectl apply -f "
            + MY_CHAOS_MESH_TESTS_PATH
            + actionName
            + ".yaml --namespace "
            + NAMESPACE_NAME);
    String actionOutput = applyAction.returnAsString();

    if (actionOutput.contains(testName + ".pingcap.com/" + actionName + " created")) {
      log.info(actionOutput);
    } else {
      log.error("Failed creating '" + actionName + "' action!");
      log.error(actionOutput);
      ExecKubernetesCmd.checkForFailedSchedulingEventsAndSchedulerHealth(NAMESPACE_NAME);
      throw new io.cucumber.java.PendingException();
    }

    /*
    No other way to do it as of 19.04.2020.
    The chaos-controller-manager and chaos-daemon logs are the only logs, but they're useless.

    You can't see chaos logs:
    no kind "PodChaos" is registered for version "pingcap.com/v1alpha1"
    in scheme "k8s.io/kubectl/pkg/scheme/scheme.go:28"

    See https://github.com/pingcap/chaos-mesh/issues/430
     */
    log.info("Sleeping for [2] minutes to let the '" + actionName + "' action finish...");
    try {
      Thread.sleep(2 * 60 * 1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  public static void checkIfActionSuccessful(String testName, String actionName) {
    ExecShellCmd kubectlDescribe = new ExecShellCmd();
    kubectlDescribe.execute(
        "kubectl describe " + testName + " " + actionName + " --namespace " + NAMESPACE_NAME);
    String kubectlDescribeOutput = kubectlDescribe.returnAsString();

    // https://github.com/pingcap/chaos-mesh/issues/648
    if ((kubectlDescribeOutput.contains("Phase:     Finished") ||
        kubectlDescribeOutput.contains("Phase:     Waiting"))
        && ExecKubernetesCmd.checkForFailedSchedulingEventsAndSchedulerHealth(NAMESPACE_NAME)) {
      log.info("'" + actionName + "' action is finished!\n" + kubectlDescribeOutput);
    } else {
      log.error("Failed running '" + actionName + "' action!");
      log.error(kubectlDescribeOutput);
      throw new io.cucumber.java.PendingException();
    }

    // Restoring minikube to empty Chaos Mesh configuration
    ExecKubernetesCmd.deleteDeployment(deployment.getMetadata().getName(), NAMESPACE_NAME);

    ExecShellCmd kubectlDeleteAction = new ExecShellCmd();
    kubectlDeleteAction.execute(
        "kubectl delete -f "
            + MY_CHAOS_MESH_TESTS_PATH
            + actionName
            + ".yaml --namespace "
            + NAMESPACE_NAME);
    String kubectlDeleteActionOutput = kubectlDeleteAction.returnAsString();

    if (kubectlDeleteActionOutput.isEmpty()) {
      log.error("Failed deleting '" + actionName + "' action!");
      log.error(kubectlDeleteActionOutput);
      throw new io.cucumber.java.PendingException();
    } else {
      log.info(kubectlDeleteActionOutput);
    }
  }
}
