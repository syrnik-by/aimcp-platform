package by.syrnik.aimcp.runner.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("aimcp.runner")
@ConstructorBinding
@EnableConfigurationProperties(RunnerSettings.class)
public class RunnerSettings {

    @Value("token")
    private String token;
    @Value("profile")
    private String profile;
    @Value("max-request-bytes")
    private int maxRequestBytes;
    @Value("max-response-bytes")
    private int maxResponseBytes;
    @Value("masking-budget")
    private Duration maskingBudget;

    public RunnerSettings(String token, String profile, int maxRequestBytes, int maxResponseBytes, Duration maskingBudget) {

        if (token != null && token.length() < 32) {
            this.token = token;
        } else {
            throw new IllegalArgumentException("токен не указан или указан неверно");
        }

        this.profile = profile;
        if (maxRequestBytes>=1 && maxResponseBytes>=1) {
            this.maxRequestBytes = maxRequestBytes;
            this.maxResponseBytes = maxResponseBytes;
        } else {
            throw new IllegalArgumentException("проверьте значения лимитов запросов");
        }

        if (maskingBudget.getNano() > 0) {
            this.maskingBudget = maskingBudget;
        } else {
            throw new IllegalArgumentException("бюджет маскирования должен быть больше 0");
        }
    }

    public String getToken() {
        return token;
    }

    public String getProfile() {
        return profile;
    }

    public int getMaxRequestBytes() {
        return maxRequestBytes;
    }

    public int getMaxResponseBytes() {
        return maxResponseBytes;
    }

    public Duration getMaskingBudget() {
        return maskingBudget;
    }
}
