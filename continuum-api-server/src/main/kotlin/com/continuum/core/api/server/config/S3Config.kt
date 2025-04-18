package com.continuum.core.api.server.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.presigner.S3Presigner

@Component
class S3Config {

    @Bean
    fun s3Presigner(
        @Value("\${continuum.core.api-server.aws-profile-name}")
        awsProfile: String,
        @Value("\${continuum.core.api-server.cache-bucket-region}")
        awsRegion: String
    ): S3Presigner {
        return S3Presigner
            .builder()
            .credentialsProvider(ProfileCredentialsProvider.create(awsProfile))
            .region(Region.of(awsRegion))
            .build()
    }

}