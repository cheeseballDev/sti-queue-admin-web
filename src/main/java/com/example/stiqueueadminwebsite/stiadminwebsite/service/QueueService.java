package com.example.stiqueueadminwebsite.stiadminwebsite.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteBatch;
import com.google.cloud.firestore.WriteResult;

@RestController
@RequestMapping("/api")
public class QueueService {

    private Firestore firestore;

    public QueueService(Firestore firestore) {
        this.firestore = firestore;
    }

    
    @PostMapping("/{queueType}/next")
    public ResponseEntity<?> incrementQueue(@RequestParam String queueType, @RequestParam int counterNumber, @RequestParam boolean isChecked) {
        return incrementDatabase(queueType, counterNumber, isChecked);
    }

    @PostMapping("/{queueType}/togglepause")
    public ResponseEntity<?> pauseQueue(@RequestParam String queueType, @RequestParam int counterNumber) {
        return togglePause(queueType, counterNumber);
    } 

    @DeleteMapping("/{queueType}/clear")
    public ResponseEntity<?> clearQueue(@RequestParam String queueType) {
        return clearAllTickets(queueType);
    }

    public ResponseEntity<?> incrementDatabase(String queueType, int counterNumber, boolean prioritizePWD) {
        CollectionReference ticketsCollectionReference = firestore.collection("TICKETS");
        DocumentReference queueRef = firestore.collection("QUEUES").document(queueType.toUpperCase());
        try {
        Map<String, Object> updateQueue = firestore.runTransaction(transaction -> {

            // SERVING TO COMPLETE
            Query oldServingTicketQuery = ticketsCollectionReference
                .whereEqualTo("service", queueType)
                .whereEqualTo("counter", counterNumber)
                .whereEqualTo("status", "serving")
                .limit(1);

            QuerySnapshot oldServingTicketsSnapshot = transaction.get(oldServingTicketQuery).get();

            if (!oldServingTicketsSnapshot.isEmpty()) {
                DocumentReference oldServingTicketRef = oldServingTicketsSnapshot.getDocuments().get(0).getReference();
                transaction.update(oldServingTicketRef, "status", "completed");
            }

            QueryDocumentSnapshot nextTicketDocument = null;

            if (prioritizePWD) {
                Query pwdQuery = ticketsCollectionReference
                    .whereEqualTo("service", queueType)
                    .whereEqualTo("status", "waiting")
                    .whereEqualTo("isPWD", true)
                    .orderBy("number")
                    .limit(1);
                QuerySnapshot nextPwdTicketsSnapshot = transaction.get(pwdQuery).get();

                if (!nextPwdTicketsSnapshot.isEmpty()) {
                    nextTicketDocument = nextPwdTicketsSnapshot.getDocuments().get(0);
                }
            }

            if (nextTicketDocument == null) {
                Query regularQuery = ticketsCollectionReference
                    .whereEqualTo("service", queueType)
                    .whereEqualTo("status", "waiting")
                    .orderBy("number")
                    .limit(1);

                QuerySnapshot nextTicketsSnapshot = transaction.get(regularQuery).get();

                if (!nextTicketsSnapshot.isEmpty()) {
                    nextTicketDocument = nextTicketsSnapshot.getDocuments().get(0);
                }
            }

            Map<String, Object> result = new HashMap<>();

            if (nextTicketDocument != null) {
                DocumentReference nextTicketRef = nextTicketDocument.getReference();
                Long nextTicketNumber = nextTicketDocument.getLong("number");

                Map<String, Object> ticketUpdates = new HashMap<>();
                ticketUpdates.put("status", "serving");
                ticketUpdates.put("counter", counterNumber);
                transaction.update(nextTicketRef, ticketUpdates);

                transaction.update(queueRef, "currentServing", nextTicketNumber, "counter", counterNumber);

                result.put("status", "success");
                result.put("message", "Queue incremented successfully.");
                result.put("nextQueueNumber", nextTicketNumber);
                result.put("serviceType", queueType);
                result.put("counterNumber", counterNumber);
                result.put("isPWDServed", nextTicketDocument.getBoolean("isPWD"));
                return result;
            }
            result.put("serviceType", queueType);
            result.put("counterNumber", counterNumber);

            return result;

        }).get();

        if (updateQueue == null) {
            return ResponseEntity.noContent().build();
        }
        String status = (String) updateQueue.get("status");
        if ("error".equals(status)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(updateQueue);
        }
        return ResponseEntity.ok(updateQueue);
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to increment queue.");
        }
    }

    public ResponseEntity<?> togglePause(String activeQueueType, int counterNumber) {
        int currentCounter = counterNumber;
        DocumentReference queueRef = firestore.collection("QUEUES").document(activeQueueType.toUpperCase());
        try {
            Map<String, Object> update = firestore.runTransaction(transaction -> {
                DocumentSnapshot snapshot = transaction.get(queueRef).get();
                Boolean isOnBreak = snapshot.getBoolean("isOnBreak");
                if (isOnBreak != null && isOnBreak) {
                    transaction.update(queueRef, "isOnBreak", false, "counter", currentCounter);
                    Map<String, Object> result = new HashMap<>();
                    result.put("isOnBreak", isOnBreak);
                    return result;
                }
                transaction.update(queueRef, "isOnBreak", true, "counter", currentCounter);
                Map<String, Object> result = new HashMap<>();
                result.put("isOnBreak", isOnBreak);
                return result;
            }).get();

            return ResponseEntity.ok(update);
        } catch (ExecutionException | InterruptedException e){ 
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to increment queue.");
        }
    }

    public ResponseEntity<?> clearAllTickets(String activeQueueType) {
        CollectionReference ticketsCollectionReference = firestore.collection("TICKETS");
        DocumentReference queueRef = firestore.collection("QUEUES").document(activeQueueType.toUpperCase());

        try {
            firestore.runTransaction(transaction -> {
                DocumentSnapshot snapshot = transaction.get(queueRef).get();
                if (snapshot.exists())
                    for (int i = 1; i < 4; i++) 
                        transaction.update(queueRef, "currentNumber", 0, "counter", i);
                return null;
            });
        
            while (true) {
                ApiFuture<QuerySnapshot> queryFuture = ticketsCollectionReference
                    .whereEqualTo("service", activeQueueType)
                    .limit(50)
                    .get();

                List<QueryDocumentSnapshot> documents = queryFuture.get().getDocuments();

                if (documents.isEmpty())
                    return null;

                WriteBatch batch = firestore.batch();
                for (QueryDocumentSnapshot document : documents)
                    batch.delete(document.getReference());

                ApiFuture<List<WriteResult>> batchFuture = batch.commit();
                batchFuture.get(10, TimeUnit.SECONDS);
                
                if (documents.size() < 500) 
                    break;
            }
            return ResponseEntity.ok("Successfully cleared queue " + activeQueueType + " and its tickets.");
                
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error clearing tickets: " + e.getMessage());
        }
    }
}