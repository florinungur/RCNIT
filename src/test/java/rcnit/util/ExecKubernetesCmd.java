package rcnit.util;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecKubernetesCmd {

  private static final Logger log = LoggerFactory.getLogger(ExecKubernetesCmd.class);

  public static void createNamespace(String namespaceName) {
    log.info("Creating '" + namespaceName + "' namespace...");

    Namespace ns =
        new NamespaceBuilder().withNewMetadata().withName(namespaceName).endMetadata().build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      try {
        kubernetesClient.namespaces().createOrReplace(ns);
        log.info("Created '" + namespaceName + "' namespace");
      } catch (KubernetesClientException e) {
        log.error("Failed creating '" + namespaceName + "' namespace!");
        e.printStackTrace();
        throw new io.cucumber.java.PendingException();
      }
    }
  }

  public static void createPersistentVolumeClaim(
      PersistentVolumeClaim persistentVolumeClaim, String namespaceName) {
    log.info(
        "Creating '"
            + persistentVolumeClaim.getMetadata().getName()
            + "' PersistentVolumeClaim...");

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      /*
      Don't surround with try {} because some calls to this method are supposed to fail and they
      are accounted for in the caller method.
       */
      kubernetesClient
          .persistentVolumeClaims()
          .inNamespace(namespaceName)
          .createOrReplace(persistentVolumeClaim);
    }
  }

  public static void waitForPodsInNamespaceToBeReady(String namespaceName) {
    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      List<Pod> podList = kubernetesClient.pods().inNamespace(namespaceName).list().getItems();
      int numberOfPods = podList.size();

      log.info("Waiting for all pods in '" + namespaceName + "' namespace to be ready...");

      for (Pod item : podList) {
        try {
          /*
          If all PodConditions are Ready, then the Pod is ready.
          Ready = the Pod is able to serve requests
          and should be added to the load balancing pools of all matching Services
          See https://kubernetes.io/docs/concepts/workloads/pods/pod-lifecycle/#pod-conditions

          The kubernetesClient waits for numberOfPods minutes for a pod to be ready.

          Code from
          https://github.com/zalando/zalenium/blob/master/src/main/java/de/zalando/ep/zalenium/container/kubernetes/KubernetesContainerClient.java#L474
          */
          kubernetesClient
              .pods()
              .inNamespace(namespaceName)
              .withName(item.getMetadata().getName())
              .waitUntilCondition(
                  pod ->
                      pod.getStatus().getConditions().stream()
                          .filter(condition -> condition.getType().equals("Ready"))
                          .map(condition -> condition.getStatus().equals("True"))
                          .findFirst()
                          .orElse(false),
                  numberOfPods,
                  TimeUnit.MINUTES);
          log.info("Pod '" + item.getMetadata().getName() + "' is ready");
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
  }

  public static void waitForPodToBeReady(String podName, String namespaceName) {
    log.info(
        "Waiting for '" + podName + "' pod in '" + namespaceName + "' namespace to be ready...");

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      // This assumes that the pod is not designed to fail from the get-go
      try {
        kubernetesClient
            .pods()
            .inNamespace(namespaceName)
            .withName(podName)
            .waitUntilCondition(
                pod ->
                    pod.getStatus().getConditions().stream()
                        .filter(condition -> condition.getType().equals("Ready"))
                        .map(condition -> condition.getStatus().equals("True"))
                        .findFirst()
                        .orElse(false),
                3,
                TimeUnit.MINUTES);
      } catch (InterruptedException interruptedException) {
        interruptedException.printStackTrace();
      }
      log.info("Pod '" + podName + "' is ready");
    }
  }

  public static AtomicBoolean checkIfPodIsScheduled(String podName, String namespaceName) {
    AtomicBoolean isScheduled = new AtomicBoolean(false);

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      try {
        kubernetesClient
            .pods()
            .inNamespace(namespaceName)
            .withName(podName)
            .get()
            .getStatus()
            .getConditions()
            .forEach(
                condition -> {
                  try {
                    if (condition.getType().equals("PodScheduled")
                        && condition.getStatus().equals("True")) {
                      isScheduled.set(true);
                    }
                  } catch (KubernetesClientException e) {
                    e.printStackTrace();
                  }
                });
      } catch (KubernetesClientException e) {
        e.printStackTrace();
      }
    }
    return isScheduled;
  }

  public static void getPodWatchLogOutput(
      String podName, String namespaceName, String lastExpectedLogLine) {
    System.out.println("\nLog of '" + podName + "' pod in namespace '" + namespaceName + "'");
    System.out.println("-------------------------------------------------------------------");

    // If I don't initialize another client here, I get a log callback failure
    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      InputStream is =
          kubernetesClient
              .pods()
              .inNamespace(namespaceName)
              .withName(podName)
              .watchLog()
              .getOutput();

      InputStreamReader isr = new InputStreamReader(is);
      Scanner scanner = new Scanner(isr);

      /*
      Read pod log until it outputs the lastExpectedLogLine; this lines is assumed to never change
       */
      for (String line = scanner.nextLine(); line != null; line = scanner.nextLine()) {
        System.out.println(line);
        if (line.contains(lastExpectedLogLine)) {
          break;
        }
      }
    }
  }

  public static void getObjectEvents(String objectName, String namespaceName) {
    ExecShellCmd getObjectEvents = new ExecShellCmd();

    getObjectEvents.execute(
        "kubectl get events"
            + " --field-selector=involvedObject.name="
            + objectName
            + " --namespace="
            + namespaceName
            + " --sort-by=.metadata.creationTimestamp");
    String getObjectEventsOutput = getObjectEvents.returnAsString();

    if (getObjectEventsOutput.contains("No resources found") || getObjectEventsOutput.isEmpty()) {
      log.info("There are no events for the '" + objectName + "' object");
    } else {
      log.info("Events for '" + objectName + "' object:\n" + getObjectEventsOutput);
    }

    /*
    This code works, but the alternative ^ is simpler and prettier.

    If you wanna improve the formatting, start here:
    https://stackoverflow.com/questions/2745206/output-in-a-table-format-in-javas-system-out

        log.info("LAST SEEN \t\t\t\t TYPE \t\t REASON \t\t\t OBJECT \t\t\t\t MESSAGE");
    kubernetesClient
        .events()
        .inNamespace(namespaceName)
        .list()
        .getItems()
        .forEach(
            event -> {
              if (event.getInvolvedObject().getName().equals(objectName)) {
                log.info(
                    "{} \t {} \t {} \t {} \t {} \t",
                    event.getLastTimestamp(),
                    event.getType(),
                    event.getReason(),
                    event.getInvolvedObject().getName(),
                    event.getMessage());
              }
            });
     */
  }

  public static boolean checkForFailedSchedulingEventsAndSchedulerHealth(String namespaceName) {
    /*
    Reason AND type can be FailedScheduling.
    See https://www.bluematador.com/blog/kubernetes-events-explained
     */
    ExecShellCmd getSchedulingEventsByType = new ExecShellCmd();
    ExecShellCmd getSchedulingEventsByReason = new ExecShellCmd();
    boolean schedulerIsHealthy;

    getSchedulingEventsByType.execute(
        "kubectl get events "
            + "--field-selector type=FailedScheduling "
            + "--sort-by=.metadata.creationTimestamp "
            + "--namespace "
            + namespaceName);
    String failedSchedulingEventsByType = getSchedulingEventsByType.returnAsString();

    getSchedulingEventsByReason.execute(
        "kubectl get events "
            + "--field-selector reason=FailedScheduling "
            + "--sort-by=.metadata.creationTimestamp "
            + "--namespace "
            + namespaceName);
    String failedSchedulingEventsByReason = getSchedulingEventsByReason.returnAsString();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      schedulerIsHealthy =
          kubernetesClient
              .componentstatuses()
              .withName("scheduler")
              .get()
              .toString()
              .contains("type=Healthy");
    }

    if (failedSchedulingEventsByType.contains("No resources found")
        && failedSchedulingEventsByReason.contains("No resources found")
        && schedulerIsHealthy) {
      log.info("The scheduler is healthy and there are no FailedScheduling events!");
      return true;
    } else {
      log.info("There are FailedScheduling events:");

      // Making the output pretty
      if (failedSchedulingEventsByType.contains("No resources found")) {
        log.info(failedSchedulingEventsByReason);
      }
      if (failedSchedulingEventsByReason.contains("No resources found")) {
        log.info(failedSchedulingEventsByType);
      }
      return false;
    }
  }

  public static void helmUninstall(String releaseName, String namespaceName) {
    ExecShellCmd helmUninstall = new ExecShellCmd();

    log.info("Uninstalling '" + releaseName + "' Helm chat...");
    helmUninstall.execute("helm uninstall " + releaseName + " --namespace=" + namespaceName);
    String helmUninstallOutput = helmUninstall.returnAsString();

    if (helmUninstallOutput.contentEquals("release \"" + releaseName + "\" uninstalled")) {
      log.info("Uninstalled '" + releaseName + "' Helm chart");
    } else {
      log.error("Failed uninstalling '" + releaseName + "' Helm chart:\n" + helmUninstallOutput);
      throw new io.cucumber.java.PendingException();
    }
  }

  public static void deleteNamespace(String namespaceName) {
    log.info("Deleting '" + namespaceName + "' namespace...");

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      if (kubernetesClient.namespaces().withName(namespaceName).delete()) {
        try {
          kubernetesClient
              .namespaces()
              .withName(namespaceName)
              .waitUntilCondition(
                  namespaceObject ->
                      namespaceObject == null
                          || !namespaceObject.getStatus().getPhase().equals("Terminating"),
                  3,
                  TimeUnit.MINUTES);
          log.info("Deleted '" + namespaceName + "' namespace");
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      } else {
        log.error("Failed deleting '" + namespaceName + "' namespace!");
        throw new io.cucumber.java.PendingException();
      }
    }
  }

  public static void deletePod(String podName, String namespaceName) {
    log.info("Deleting '" + podName + "' pod...");

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      if (kubernetesClient.pods().inNamespace(namespaceName).withName(podName).delete()) {
        try {
          kubernetesClient
              .pods()
              .inNamespace(namespaceName)
              .withName(podName)
              .waitUntilCondition(
                  pod -> pod == null || !pod.getStatus().getPhase().equals("Terminating"),
                  3,
                  TimeUnit.MINUTES);
          log.info("Deleted '" + podName + "' pod");
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      } else {
        log.error("Failed deleting '" + podName + "' pod!");
        throw new io.cucumber.java.PendingException();
      }
    }
  }

  public static void deletePersistentVolume(String volumeName) {
    log.info("Deleting '" + volumeName + "' PersistentVolume...");

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      if (kubernetesClient.persistentVolumes().withName(volumeName).delete()) {
        try {
          kubernetesClient
              .persistentVolumes()
              .withName(volumeName)
              .waitUntilCondition(
                  volume -> volume == null || !volume.getStatus().getPhase().equals("Terminating"),
                  3,
                  TimeUnit.MINUTES);
          log.info("Deleted '" + volumeName + "' PersistentVolume");
        } catch (InterruptedException e) {
          log.error("Failed deleting '" + volumeName + "' PersistentVolume!");
          throw new RuntimeException(e);
        }
      } else {
        log.error("Failed deleting '" + volumeName + "' PersistentVolume!");
        throw new io.cucumber.java.PendingException();
      }
    }
  }

  public static void deleteStorageClass(String storageClassName, String namespaceName) {
    log.info("Deleting '" + storageClassName + "' StorageClass...");

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      if (kubernetesClient
          .storage()
          .storageClasses()
          .inNamespace(namespaceName)
          .withName(storageClassName)
          .delete()) {
        try {
          kubernetesClient
              .storage()
              .storageClasses()
              .inNamespace(namespaceName)
              .withName(storageClassName)
              .waitUntilCondition(
                  storageClass ->
                      storageClass == null || storageClass.getMetadata().getName().isBlank(),
                  3,
                  TimeUnit.MINUTES);
          log.info("Deleted '" + storageClassName + "' StorageClass");
        } catch (InterruptedException e) {
          log.error("Failed deleting '" + storageClassName + "' StorageClass!");
          throw new RuntimeException(e);
        }
      } else {
        log.error("Failed deleting '" + storageClassName + "' StorageClass!");
        throw new io.cucumber.java.PendingException();
      }
    }
  }

  public static void deletePersistentVolumeClaim(
      String persistentVolumeClaimName, String namespaceName) {
    log.info("Deleting '" + persistentVolumeClaimName + "' PersistentVolumeClaim...");

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      if (kubernetesClient
          .persistentVolumeClaims()
          .inNamespace(namespaceName)
          .withName(persistentVolumeClaimName)
          .delete()) {
        try {
          kubernetesClient
              .persistentVolumeClaims()
              .inNamespace(namespaceName)
              .withName(persistentVolumeClaimName)
              .waitUntilCondition(
                  pvc -> pvc == null || pvc.getMetadata().getName().isBlank(), 3, TimeUnit.MINUTES);
          log.info("Deleted '" + persistentVolumeClaimName + "' PersistentVolumeClaim");
        } catch (InterruptedException e) {
          log.error("Failed deleting '" + persistentVolumeClaimName + "' PersistentVolumeClaim!");
          throw new RuntimeException(e);
        }
      } else {
        log.error("Failed deleting '" + persistentVolumeClaimName + "' PersistentVolumeClaim!");
        throw new io.cucumber.java.PendingException();
      }
    }
  }

  public static void deleteLimitRange(String limitRangeName, String namespaceName) {
    log.info("Deleting '" + limitRangeName + "' LimitRange...");

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      if (kubernetesClient
          .limitRanges()
          .inNamespace(namespaceName)
          .withName(limitRangeName)
          .delete()) {
        try {
          kubernetesClient
              .limitRanges()
              .inNamespace(namespaceName)
              .withName(limitRangeName)
              .waitUntilCondition(
                  limitRange -> limitRange == null || limitRange.getMetadata().getName().isBlank(),
                  3,
                  TimeUnit.MINUTES);
          log.info("Deleted '" + limitRangeName + "' LimitRange");
        } catch (InterruptedException e) {
          log.error("Failed deleting '" + limitRangeName + "' LimitRange!");
          throw new RuntimeException(e);
        }
      } else {
        log.error("Failed deleting '" + limitRangeName + "' LimitRange!");
        throw new io.cucumber.java.PendingException();
      }
    }
  }

  public static void deleteResourceQuota(String resourceQuotaName, String namespaceName) {
    log.info("Deleting '" + resourceQuotaName + "' ResourceQuota...");

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      if (kubernetesClient
          .resourceQuotas()
          .inNamespace(namespaceName)
          .withName(resourceQuotaName)
          .delete()) {
        try {
          kubernetesClient
              .resourceQuotas()
              .inNamespace(namespaceName)
              .withName(resourceQuotaName)
              .waitUntilCondition(
                  resourceQuota ->
                      resourceQuota == null || resourceQuota.getMetadata().getName().isBlank(),
                  3,
                  TimeUnit.MINUTES);
          log.info("Deleted '" + resourceQuotaName + "' ResourceQuota");
        } catch (InterruptedException e) {
          log.error("Failed deleting '" + resourceQuotaName + "' ResourceQuota!");
          throw new RuntimeException(e);
        }
      } else {
        log.error("Failed deleting '" + resourceQuotaName + "' ResourceQuota!");
        throw new io.cucumber.java.PendingException();
      }
    }
  }

  public static void deleteDeployment(String deploymentName, String namespaceName) {
    log.info("Deleting '" + deploymentName + "' Deployment...");

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      if (kubernetesClient
          .apps()
          .deployments()
          .inNamespace(namespaceName)
          .withName(deploymentName)
          .delete()) {
        try {
          kubernetesClient
              .apps()
              .deployments()
              .inNamespace(namespaceName)
              .withName(deploymentName)
              .waitUntilCondition(
                  deployment -> deployment == null || deployment.getMetadata().getName().isBlank(),
                  3,
                  TimeUnit.MINUTES);
          log.info("Deleted '" + deploymentName + "' Deployment");
        } catch (InterruptedException e) {
          log.error("Failed deleting '" + deploymentName + "' Deployment!");
          throw new RuntimeException(e);
        }
      } else {
        log.error("Failed deleting '" + deploymentName + "' Deployment!");
        throw new io.cucumber.java.PendingException();
      }
    }
  }
}
