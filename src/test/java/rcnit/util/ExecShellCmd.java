package rcnit.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class ExecShellCmd {

  private static String collect = null;

  public void execute(String command) {
    String[] cmd = new String[0];

    /*
    Make the command work on Windows or on Linux.
    ASSUMPTION: programs like kubectl or minikube are in PATH.
     */
    String osName = System.getProperty("os.name").toLowerCase();
    if (osName.contains("win")) {
      cmd = new String[] {"cmd.exe", "/c", command};
    } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
      cmd = new String[] {"/bin/sh", "- c", command};
    }

    try {
      // Create the ProcessBuilder
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(true);

      // Start the process
      Process process = pb.start();

      /*
      Read the process' output.
      This will hang on an infinite stream due to the nature of BufferedReader.
       */
      InputStream is = process.getInputStream();
      InputStreamReader isr = new InputStreamReader(is);
      BufferedReader br = new BufferedReader(isr);
      collect = br.lines().collect(Collectors.joining("\n"));

      // Clean-up
      br.close();
      isr.close();
      is.close();
      process.destroy();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public String returnAsString() {
    return collect;
  }
}
