package com.example.stiqueueadminwebsite.stiadminwebsite.service;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.springframework.http.HttpStatus;
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

    @GetMapping("/admissions/next")
    public ResponseEntity<?> incrementAdmissionsQueue() {
        return incrementQueue("admission");
    }

    @GetMapping("/cashier/next")
    public ResponseEntity<?> incrementCashierQueue() {
        return incrementQueue("cashier");
    }

    @GetMapping("/registrar/next")
    public ResponseEntity<?> incrementRegistrarQueue() {
        return incrementQueue("registrar");
    }


    public ResponseEntity<?> incrementQueue(String queueType) {
        DocumentReference queueRef = firestore.collection("QUEUES").document(queueType.toUpperCase());
        try {
            Map<String, Object> update = firestore.runTransaction(transaction -> {
                DocumentSnapshot snapshot = transaction.get(queueRef).get();
                Long currentNumber = snapshot.getLong("currentNumber");
                Long currentServing = snapshot.getLong("currentServing");
                if (currentNumber != null && currentServing != null && currentNumber.equals(currentServing)) {
                    return null;
                }
                long nextNumber = (currentServing == null ? 0 : currentServing) + 1;
                transaction.update(queueRef, "currentService", nextNumber);
                Map<String, Object> result = new HashMap<>();
                result.put("nextQueueNumber", nextNumber);
                return result;
            }).get();

            if (update == null) {
            return ResponseEntity.ok().body(Map.of("warning", "Queue is already at the current number."));
            } else {
                return ResponseEntity.ok(update);
            }
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to increment queue.");
        }
    }
}