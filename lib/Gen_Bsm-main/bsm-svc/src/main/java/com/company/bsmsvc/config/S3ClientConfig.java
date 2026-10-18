package com.company.bsmsvc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

@Configuration
public class S3ClientConfig {

    @Bean
    public S3Client s3Client(AwsS3Properties props) {
        var builder = S3Client.builder();
        if (props.getRegion() != null) {
            builder.region(Region.of(props.getRegion()));
        }
        if (props.getAccessKey() != null && props.getSecretKey() != null) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())
            ));
        }
        return builder.build();
    }

    @Bean
    public AmazonS3 amazonS3(AwsS3Properties props) {
        AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard();
        if (props.getRegion() != null) {
            builder.setRegion(props.getRegion());
        }
        if (props.getAccessKey() != null && props.getSecretKey() != null) {
            builder.setCredentials(new AWSStaticCredentialsProvider(
                new BasicAWSCredentials(props.getAccessKey(), props.getSecretKey())
            ));
        }
        return builder.build();
    }
}
