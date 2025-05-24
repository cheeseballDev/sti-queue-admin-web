package com.example.stiqueueadminwebsite.stiadminwebsite.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.EventListener;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreException;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;

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

    private final List<String> ALL_SERVICE_TYPES = List.of("ADMISSION", "CASHIER", "REGISTRAR");

    public QueueUpdateService(Firestore firestore, ObjectMapper objectMapper, SimpleWebSocketHandler webSocketHandler) {
        this.firestore = firestore;
        this.objectMapper = objectMapper;
        this.webSocketHandler = webSocketHandler;
    }

    @PostConstruct
    public void initListeners() {
        for (String serviceType : ALL_SERVICE_TYPES)
            setupServiceCountersListener(serviceType);
        for (String serviceType : ALL_SERVICE_TYPES) {
            setupSpecificServiceTicketsListener(serviceType.toLowerCase());
        }
    }

    private void setupServiceCountersListener(String serviceType) {
        String upperServiceType = serviceType.toUpperCase();
        DocumentReference serviceRef = firestore.collection("QUEUES").document(upperServiceType);

        serviceRef.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(DocumentSnapshot snapshot, FirestoreException error) {
                if (snapshot.exists()) {
                    long currentCounter = snapshot.getLong("counter");

                    long currentServingCounter1 = 0L;
                    long currentServingCounter2 = 0L;
                    long currentServingCounter3 = 0L;

                    if (currentCounter == 1)
                        currentServingCounter1 = snapshot.getLong("currentServing");
                    if (currentCounter == 2)
                        currentServingCounter2 = snapshot.getLong("currentServing");
                    if (currentCounter == 3)
                        currentServingCounter3 = snapshot.getLong("currentServing");

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
                }
                logger.warn("Document for service counters {} does not exist or is null.", upperServiceType);
                try {
                    Map<String, Object> resetPayload = Map.of("type", "COUNTER_UPDATE", "serviceType", upperServiceType,
                            "counter1Serving", 0, "counter2Serving", 0, "counter3Serving", 0, "status", "unavailable");
                    webSocketHandler.sendMessageToAllSessions(objectMapper.writeValueAsString(resetPayload));
                } catch (Exception e) {
                    logger.error("Error sending reset COUNTER_UPDATE for {}: {}", upperServiceType, e.getMessage(), e);
                }
            }
        });
    }

    private void setupSpecificServiceTicketsListener(String serviceType) {
        Query ticketsQuery = firestore.collection("TICKETS")
            .orderBy("number")
            .whereEqualTo("service", serviceType);
            
        ticketsQuery.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(QuerySnapshot snapshots, FirestoreException error) {
                if (error != null) {
                    logger.error("Firebase listener error for {} tickets: {}", serviceType, error.getMessage(), error);
                    return;
                }

                List<Map<String, Object>> ticketsData = new ArrayList<>();
                if (snapshots != null && snapshots.isEmpty()) {
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Map<String, Object> ticket = doc.getData();
                        if (ticket != null) {
                            ticket.put("id", doc.getId());
                            ticket.put("service", doc.getString("service"));
                            ticket.put("dateCreated", doc.getDate("createdAt"));
                            ticket.put("isForm", doc.getBoolean("isForm"));
                            ticketsData.add(ticket);
                        }
                    }
                }
                Map<String, Object> payload = new HashMap<>();
                payload.put("type", "TICKET_UPDATE");
                payload.put("serviceType", serviceType);
                payload.put("tickets", ticketsData);
                try {
                    String jsonPayload = objectMapper.writeValueAsString(payload);
                    // Use the targeted send method from SimpleWebSocketHandler!
                    webSocketHandler.sendMessageToServiceSessions(serviceType, jsonPayload);
                    logger.debug("Sent TICKET_UPDATE for {} tickets to relevant sessions. Count: {}", serviceType, ticketsData.size());
                } catch (JsonProcessingException e) {
                    logger.error("Error serializing or sending TICKET_UPDATE for {} tickets: {}", serviceType, e.getMessage(), e);
                }
            }
        });
    }
}