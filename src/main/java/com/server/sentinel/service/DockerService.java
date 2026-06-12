package com.server.sentinel.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DockerService {
    
    private final DockerClient dockerClient;
    
    public DockerService(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }
    
    public List<Container> getAllContainers() {
        // Lấy danh sách tất cả các container, bao gồm cả những cái đang tắt
        return dockerClient.listContainersCmd().withShowAll(true).exec();
    }
    
    public List<Container> getExitedContainers() {
        // Lọc ra những container đang có trạng thái là "exited"
        return getAllContainers().stream()
                .filter(container -> {
                    String state = container.getState();
                    return state != null && state.equalsIgnoreCase("exited");
                })
                .collect(Collectors.toList());
    }
    
    public void startContainer(String containerId) {
        // Gửi lệnh 'docker start <containerId>' tới Docker Daemon
        dockerClient.startContainerCmd(containerId).exec();
    }
    
    public void stopContainer(String containerId) {
        // Gửi lệnh 'docker stop <containerId>' tới Docker Daemon
        dockerClient.stopContainerCmd(containerId).exec();
    }
    
    public void restartContainer(String containerId) {
        // Gửi lệnh 'docker restart <containerId>' tới Docker Daemon
        dockerClient.restartContainerCmd(containerId).exec();
    }
    
    public String getContainerLogs(String containerId, int tailLines) {
        final StringBuilder logs = new StringBuilder();
        try {
            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(tailLines)
                    .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.Frame>() {
                        @Override
                        public void onNext(com.github.dockerjava.api.model.Frame item) {
                            logs.append(new String(item.getPayload()));
                        }
                    }).awaitCompletion(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            return "Loi khi lay logs container: " + e.getMessage();
        }
        return logs.toString();
    }
}