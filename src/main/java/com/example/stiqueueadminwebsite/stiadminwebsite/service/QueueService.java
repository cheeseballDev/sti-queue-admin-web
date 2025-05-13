package com.example.stiqueueadminwebsite.stiadminwebsite.service;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

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
@RequestMapping("/api")
public class QueueService {

    private Firestore firestore;

    @Autowired
    public QueueService(Firestore firestore) {
        this.firestore = firestore;
    }

    @PostMapping("/admissions/next")
    public ResponseEntity<?> incrementAdmissionsQueue() {
        return incrementQueue("admission");
    }

    @PostMapping("/cashier/next")
    public ResponseEntity<?> incrementCashierQueue() {
        return incrementQueue("cashier");
    }

    @PostMapping("/registrar/next")
    public ResponseEntity<?> incrementRegistrarQueue() {
        return incrementQueue("registrar");
    }


    public ResponseEntity<?> incrementQueue(String queueType) {
        DocumentReference queueRef = firestore.collection("QUEUES").document(queueType);
        try {
            Map<String, Object> update = firestore.runTransaction(transaction -> {
                DocumentSnapshot snapshot = transaction.get(queueRef).get();
                Long currentNumber = snapshot.getLong("currentNumber");
                if (currentNumber == null) {
                    currentNumber = 0L;
                }
                long nextNumber = currentNumber + 1;
                transaction.update(queueRef, "currentService", nextNumber);
                Map<String, Object> result = new HashMap<>();
                result.put("nextQueueNumber", nextNumber);
                return result;
            }).get();

            return ResponseEntity.ok(update);

        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to increment queue.");
        }
    }
}