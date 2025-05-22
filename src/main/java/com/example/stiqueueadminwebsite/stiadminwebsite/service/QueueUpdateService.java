package com.example.stiqueueadminwebsite.stiadminwebsite.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.EventListener;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreException;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class QueueUpdateService {

    private final Logger logger = LoggerFactory.getLogger(QueueUpdateService.class);
    private final Firestore firestore;
    private final ObjectMapper objectMapper;
    private final SimpleWebSocketHandler webSocketHandler;

    private static final List<String> COUNTER_SERVICE_TYPES = List.of("ADMISSION", "CASHIER", "REGISTRAR");


    @Autowired
    public QueueUpdateService(Firestore firestore, ObjectMapper objectMapper, SimpleWebSocketHandler webSocketHandler) {
        this.firestore = firestore;
        this.objectMapper = objectMapper;
        this.webSocketHandler = webSocketHandler;
    }

    @PostConstruct
    public void initListeners() {
        for (String serviceType : COUNTER_SERVICE_TYPES) {
            setupServiceCountersListener(serviceType);
        }

        setupAllTicketsListener();
    }

    private void setupServiceCountersListener(String serviceType) {
        String upperServiceType = serviceType.toUpperCase();
        DocumentReference serviceRef = firestore.collection("QUEUES").document(upperServiceType);

        serviceRef.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(DocumentSnapshot snapshot, FirestoreException error) {
                if (error != null) {
                    logger.error("Firebase listener error for service counters {}: {}", upperServiceType, error.getMessage(), error);
                    return;
                }

                if (snapshot.exists()) {
                    long currentCounter = snapshot.getLong("counter");

                    long currentServingCounter1 = 0L;
                    long currentServingCounter2 = 0L;
                    long currentServingCounter3 = 0L;

                    if (currentCounter == 1) {
                        currentServingCounter1 = snapshot.getLong("currentServing");
                    }
                    
                    if (currentCounter == 2) {
                        currentServingCounter2 = snapshot.getLong("currentServing");
                    }

                    if (currentCounter == 3) {
                        currentServingCounter3 = snapshot.getLong("currentServing");
                    }

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("type", "COUNTER_UPDATE"); 
                    payload.put("serviceType", upperServiceType); 
                    payload.put("counter1Serving", currentServingCounter1);
                    payload.put("counter2Serving", currentServingCounter2);
                    payload.put("counter3Serving", currentServingCounter3);

                    try {
                        String jsonPayload = objectMapper.writeValueAsString(payload);
                        webSocketHandler.sendMessageToAllSessions(jsonPayload);
                        logger.debug("Sent COUNTER_UPDATE for {}: {}", upperServiceType, jsonPayload);
                    } catch (Exception e) {
                        logger.error("Error serializing or sending COUNTER_UPDATE for {}: {}", upperServiceType, e.getMessage(), e);
                    }
                } else {
                    logger.warn("Document for service counters {} does not exist or is null.", upperServiceType);
                    try {
                        Map<String, Object> resetPayload = Map.of("type", "COUNTER_UPDATE", "serviceType", upperServiceType,
                                "counter1Serving", 0, "counter2Serving", 0, "counter3Serving", 0, "status", "unavailable");
                        webSocketHandler.sendMessageToAllSessions(objectMapper.writeValueAsString(resetPayload));
                    } catch (Exception e) {

                        logger.error("Error sending reset COUNTER_UPDATE for {}: {}", upperServiceType, e.getMessage(), e);
                    }
                }
            }
        });
    }

    private void setupAllTicketsListener() {
                Query allTicketsQuery = firestore.collection("TICKETS")
                .whereIn("status", List.of("WAITING", "SERVING")) // Adjust statuses as needed
                .orderBy("number"); // Order by ticket number

        allTicketsQuery.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(QuerySnapshot snapshots, FirestoreException error) {
                if (error != null) {
                    logger.error("Firebase listener error for ALL tickets: {}", error.getMessage(), error);
                    return;
                }

                List<Map<String, Object>> ticketsData = new ArrayList<>();
                if (snapshots != null && !snapshots.isEmpty()) {
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Map<String, Object> ticket = doc.getData();
                        if (ticket != null) {
                            ticket.put("id", doc.getId()); // Include document ID
                            ticketsData.add(ticket);
                        }
                    }
                }
                // Always send the list, even if empty, to clear the frontend display if no tickets
                Map<String, Object> payload = new HashMap<>();
                payload.put("type", "TICKET_UPDATE"); // Type identifier
                // No 'serviceType' field here, as it's a list for ALL services
                payload.put("tickets", ticketsData);

                try {
                    String jsonPayload = objectMapper.writeValueAsString(payload);
                    webSocketHandler.sendMessageToAllSessions(jsonPayload);
                    logger.debug("Sent TICKET_UPDATE for ALL tickets: {}", jsonPayload);
                } catch (Exception e) {
                    logger.error("Error serializing or sending TICKET_UPDATE for ALL tickets: {}", e.getMessage(), e);
                }
            }
        });
    }
}