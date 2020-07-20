package rcnit;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rcnit.util.ExecKubernetesCmd;

public class UsePumba {

  private static final String NAMESPACE_NAME = "rcnit-pumba-testing";
  private static final Logger log = LoggerFactory.getLogger(UsePumba.class);

  @When("I run all Pumba actions for {int} minutes")
  public void i_run_all_Pumba_actions_for_minutes(Integer minutesToRunPumbaActions) {
    ExecKubernetesCmd.createNamespace(NAMESPACE_NAME);

    ServiceAccount serviceAccount =
        new ServiceAccountBuilder()
            .withNewMetadata()
            .withNewName("rcnit-pumba-testing-svc-account")
            .endMetadata()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient
          .serviceAccounts()
          .inNamespace(NAMESPACE_NAME)
          .createOrReplace(serviceAccount);
      log.info("Created '" + serviceAccount.getMetadata().getName() + "' service account");
    }

    PodBuilder testPodTemplate =
        new PodBuilder()
            .withNewSpec()
            .withServiceAccount(serviceAccount.getMetadata().getName())
            .addToContainers(
                new ContainerBuilder()
                    .withImage("k8s.gcr.io/echoserver:1.4")
                    .withImagePullPolicy("IfNotPresent")
                    .build())
            .addToContainers(
                new ContainerBuilder()
                    .withImage("k8s.gcr.io/echoserver:1.4")
                    .withImagePullPolicy("IfNotPresent")
                    .build())
            .addToContainers(
                new ContainerBuilder()
                    .withImage("k8s.gcr.io/echoserver:1.4")
                    .withImagePullPolicy("IfNotPresent")
                    .build())
            .endSpec();

    Pod testKillPod =
        testPodTemplate
            .withNewMetadata()
            .withName("test-kill")
            .addToLabels("app", "pumba-kill")
            .endMetadata()
            .editSpec()
            .editContainer(0)
            .withName("test-kill-1")
            .and()
            .editContainer(1)
            .withName("test-kill-2")
            .and()
            .editContainer(2)
            .withName("test-kill-3")
            .endContainer()
            .endSpec()
            .build();
    Pod testPausePod =
        testPodTemplate
            .withNewMetadata()
            .withName("test-pause")
            .addToLabels("app", "pumba-pause")
            .endMetadata()
            .editSpec()
            .editContainer(0)
            .withName("test-pause-1")
            .and()
            .editContainer(1)
            .withName("test-pause-2")
            .and()
            .editContainer(2)
            .withName("test-pause-3")
            .endContainer()
            .endSpec()
            .build();
    Pod testRemovePod =
        testPodTemplate
            .withNewMetadata()
            .withName("test-remove")
            .addToLabels("app", "pumba-remove")
            .endMetadata()
            .editSpec()
            .editContainer(0)
            .withName("test-remove-1")
            .and()
            .editContainer(1)
            .withName("test-remove-2")
            .and()
            .editContainer(2)
            .withName("test-remove-3")
            .endContainer()
            .endSpec()
            .build();
    Pod testDelayPod =
        testPodTemplate
            .withNewMetadata()
            .withName("test-delay")
            .addToLabels("app", "pumba-delay")
            .endMetadata()
            .editSpec()
            .editContainer(0)
            .withName("test-delay-1")
            .and()
            .editContainer(1)
            .withName("test-delay-2")
            .and()
            .editContainer(2)
            .withName("test-delay-3")
            .endContainer()
            .endSpec()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient.pods().inNamespace(NAMESPACE_NAME).createOrReplace(testKillPod);
      log.info("Deployed 'test-kill' pod");
      kubernetesClient.pods().inNamespace(NAMESPACE_NAME).createOrReplace(testPausePod);
      log.info("Deployed 'test-pause' pod");
      kubernetesClient.pods().inNamespace(NAMESPACE_NAME).createOrReplace(testRemovePod);
      log.info("Deployed 'test-remove' pod");
      kubernetesClient.pods().inNamespace(NAMESPACE_NAME).createOrReplace(testDelayPod);
      log.info("Deployed 'test-delay' pod");
    }
    ExecKubernetesCmd.waitForPodsInNamespaceToBeReady(NAMESPACE_NAME);

    log.info("Deploying pumba actions...");
    List<HasMetadata> result = null;
    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      try {
        result =
            kubernetesClient
                .load(new FileInputStream("src/test/resources/pumba/all-actions.yaml"))
                .get();
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      }
      kubernetesClient
          .resourceList(result)
          .inNamespace(NAMESPACE_NAME)
          .deletingExisting()
          .createOrReplace();
    }
    ExecKubernetesCmd.waitForPodsInNamespaceToBeReady(NAMESPACE_NAME);

    log.info("Letting the pumba actions run for [" + minutesToRunPumbaActions + "] minutes...");
    try {
      Thread.sleep(minutesToRunPumbaActions * 60 * 1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  @Then("The actions should be successful")
  public void the_actions_should_be_successful() {
    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      PodList podList = kubernetesClient.pods().inNamespace(NAMESPACE_NAME).list();
      List<Pod> items = podList.getItems();

      for (Pod item : items) {
        // Skip test pods because they don't contain logs
        if (!item.getMetadata().getName().contains("test")) {
          String pumbaPodLog =
              kubernetesClient
                  .pods()
                  .inNamespace(NAMESPACE_NAME)
                  .withName(item.getMetadata().getName())
                  .getLog();

          // The 'level=fatal' logs are displayed only if the log-level is debug
          if (pumbaPodLog.contains("level=fatal")) {
            log.error(
                "Something went wrong during the '" + item.getMetadata().getName() + "' action:");
            System.out.println(pumbaPodLog);
            throw new io.cucumber.java.PendingException();
          } else {
            System.out.println("'" + item.getMetadata().getName() + "' logs");
            System.out.println("------------------");
            System.out.println(pumbaPodLog);
          }
        }
      }
    }
    ExecKubernetesCmd.checkForFailedSchedulingEventsAndSchedulerHealth(NAMESPACE_NAME);
    ExecKubernetesCmd.deleteNamespace(NAMESPACE_NAME);
  }
}
