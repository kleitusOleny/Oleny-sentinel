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
}