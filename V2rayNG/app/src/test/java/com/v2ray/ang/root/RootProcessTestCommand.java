package com.v2ray.ang.root;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

/** Test-only subprocess fixture with no shell or Kotlin runtime dependency. */
public final class RootProcessTestCommand {
    public static List<String> command(String mode) throws URISyntaxException {
        File bin = new File(System.getProperty("java.home"), "bin");
        File java = new File(bin, "java.exe");
        if (!java.isFile()) java = new File(bin, "java");
        // Gradle's worker classpath need not contain the test classes; use their actual location.
        File classes = new File(RootProcessTestCommand.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        return Arrays.asList(java.getAbsolutePath(), "-cp", classes.getAbsolutePath(),
                RootProcessTestCommand.class.getName(), mode);
    }

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "sleep":
                Thread.sleep(5000);
                break;
            case "inherited-stdout":
                new ProcessBuilder(command("sleep")).inheritIO().start();
                System.out.println("complete");
                break;
            case "large-output":
                byte[] chunk = new byte[4096];
                Arrays.fill(chunk, (byte) 'x');
                for (int i = 0; i < 256; i++) System.out.write(chunk);
                System.out.flush();
                break;
            case "failure":
                System.err.println("lock-unavailable");
                System.exit(4);
                break;
            default:
                throw new IllegalArgumentException("Unknown test command: " + args[0]);
        }
    }
}
