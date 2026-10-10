package com.server.sentinel.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class TerminalService {

    private final DockerClient dockerClient;

    public TerminalService(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    public Map<String, Object> executeCommand(String command, String target, String containerId) {
        Map<String, Object> result = new HashMap<>();
        if (command == null || command.trim().isEmpty()) {
            result.put("status", "error");
            result.put("message", "Command cannot be empty");
            result.put("output", "");
            result.put("exitCode", -1);
            return result;
        }

        command = command.trim();

        if ("container".equalsIgnoreCase(target) && containerId != null && !containerId.trim().isEmpty()) {
            return executeInContainer(containerId, command);
        } else {
            return executeOnHost(command);
        }
    }

    private Map<String, Object> executeOnHost(String command) {
        Map<String, Object> result = new HashMap<>();
        StringBuilder output = new StringBuilder();
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                pb = new ProcessBuilder("/bin/sh", "-c", command);
            }
            pb.redirectErrorStream(true);

            Process process = pb.start();
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                result.put("status", "timeout");
                result.put("message", "Command timed out after 15 seconds");
                result.put("output", "Command execution timed out after 15 seconds.");
                result.put("exitCode", 124);
                return result;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    lineCount++;
                    if (lineCount > 1000) {
                        output.append("\n... [Output truncated after 1000 lines] ...\n");
                        break;
                    }
                }
            }

            int exitCode = process.exitValue();
            result.put("status", exitCode == 0 ? "success" : "failed");
            result.put("output", output.toString());
            result.put("exitCode", exitCode);
            return result;
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
            result.put("output", "Execution error: " + e.getMessage());
            result.put("exitCode", -1);
            return result;
        }
    }

    private Map<String, Object> executeInContainer(String containerId, String command) {
        Map<String, Object> result = new HashMap<>();
        final StringBuilder output = new StringBuilder();

        try {
            ExecCreateCmdResponse execCreate = dockerClient.execCreateCmd(containerId)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd("/bin/sh", "-c", command)
                    .exec();

            boolean finished = dockerClient.execStartCmd(execCreate.getId())
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame item) {
                            output.append(new String(item.getPayload()));
                        }
                    })
                    .awaitCompletion(15, TimeUnit.SECONDS);

            if (!finished) {
                result.put("status", "timeout");
                result.put("message", "Container command timed out after 15 seconds");
                result.put("output", output.toString() + "\n... [Execution timed out] ...");
                result.put("exitCode", 124);
                return result;
            }

            result.put("status", "success");
            result.put("output", output.toString());
            result.put("exitCode", 0);
            return result;
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
            result.put("output", "Container exec error: " + e.getMessage());
            result.put("exitCode", -1);
            return result;
        }
    }
}
