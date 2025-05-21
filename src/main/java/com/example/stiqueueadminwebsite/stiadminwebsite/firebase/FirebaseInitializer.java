package com.example.stiqueueadminwebsite.stiadminwebsite.firebase;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.example.stiqueueadminwebsite.stiadminwebsite.config.FirebaseConfig;
import com.google.cloud.firestore.Firestore;

@Configuration
public class FirebaseInitializer {
    @Bean
    public Firestore firestore() throws IOException {
        return FirebaseConfig.getFirestore();
    }
}
