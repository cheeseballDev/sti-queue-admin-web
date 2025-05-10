package com.example.stiqueueadminwebsite.stiadminwebsite.config;

import java.io.FileInputStream;
import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

@Configuration
public class FirebaseConfig {
    


    @Bean
    public static Firestore getFirestore() throws IOException {
        FileInputStream serviceAccount =
        new FileInputStream("C:\\Users\\Matthew\\OneDrive - STI College Ortigas-Cainta\\Documents\\VSCode\\WebDev\\stiadminwebsite\\sti-queueing-app-firebase-adminsdk-fbsvc-d3d3f33f48.json");

        FirebaseOptions options = FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
        .build();

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
        }

        return FirestoreClient.getFirestore();
    }
}
