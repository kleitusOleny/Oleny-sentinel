package com.server.sentinel.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DockerClientConfig {
    @Bean
    public DockerClient dockerClient() {
        String os = System.getProperty("os.name").toLowerCase();
        
        // Neu la Windows (chay tren IDE), dung Named Pipe cua Docker Desktop.
        // Neu la Linux (chay tren server hoac trong container), dung Unix Socket.
        String dockerHost = os.contains("win")
                ? "npipe:////./pipe/docker_engine"
                : "unix:///var/run/docker.sock";
        
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost)
                .build();
        
        DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();
        
        return DockerClientImpl.getInstance(config, httpClient);
    }
}