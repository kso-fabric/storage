package cz.kso.fabric.storage.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Configuration
@Validated
@ConfigurationProperties(prefix = "cz.kso.fabric.storage")
public class StorageProperties {

    private String discRoot;

    @Min(1)
    private int integrityBatchSize = 100;
}
