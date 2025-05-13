package com.example.stiqueueadminwebsite.stiadminwebsite.service;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.stiqueueadminwebsite.stiadminwebsite.firebase.FirebaseInitializer;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.database.annotations.Nullable;

@RestController
@RequestMapping("/api/queue")
public class QueueService {

    @Autowired
    private Firestore firestore;

    @GetMapping("/greeting")
    public String greeting(){
        return "greetings!";
    }

    /*
    @PostMapping("/next")
    public ResponseEntity<Map<String, Object>> callNextAdmissions(@PathVariable String queueServiceType) {
        // Extract data from the request body if needed
        DocumentReference admissionRef = firestore.collection("QUEUES").document(queueServiceType);

        admissionRef.addSnapshotListener(
            new EventListener<DocumentSnapshot>() {
                @Override
                public void onEvent(@Nullable DocumentSnapshot snapshot) {
                    if (snapshot.exists()) {

                    }
                }
            }
        );
        return ;
        
    }
    */
}