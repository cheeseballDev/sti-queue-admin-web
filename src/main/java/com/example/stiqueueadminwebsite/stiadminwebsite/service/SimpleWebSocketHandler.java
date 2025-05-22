package com.example.stiqueueadminwebsite.stiadminwebsite.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component 
public class SimpleWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(SimpleWebSocketHandler.class);
    private final Set<WebSocketSession> sessions = Collections.synchronizedSet(new HashSet<>());
    private final ObjectMapper objectMapper;
    private final Firestore firestore;


    @Autowired
    public SimpleWebSocketHandler(ObjectMapper objectMapper, Firestore firestore) {
        this.objectMapper = objectMapper;
        this.firestore = firestore;
    }



    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        logger.info("WebSocket connection established: Session ID - {}", session.getId());

        sendInitialCounterStates(session);
        sendInitialAllTicketsState(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        logger.info("WebSocket connection closed: Session ID - {}, Status - {}", session.getId(), status.getCode());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage(), exception);
        sessions.remove(session); // Remove session on error
    }

    public void sendMessageToAllSessions(String message) {
        // Iterate over a copy to avoid ConcurrentModificationException if a session closes during iteration
        for (WebSocketSession session : new HashSet<>(sessions)) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                    logger.debug("Message sent to session {}: {}", session.getId(), message);
                } catch (IOException e) {
                    logger.error("Error sending message to session {}: {}", session.getId(), e.getMessage());
                    // Consider removing the session if sending fails repeatedly
                }
            } else {
                logger.warn("Attempted to send message to closed session: {}", session.getId());
                sessions.remove(session); // Clean up closed sessions
            }
        }
    }

    private void sendInitialCounterStates(WebSocketSession session) {
        List<String> COUNTER_SERVICE_TYPES = List.of("ADMISSION", "CASHIER", "REGISTRAR");

        for (String serviceType : COUNTER_SERVICE_TYPES) {
            try {
                DocumentReference serviceRef = firestore.collection("QUEUES").document(serviceType);
                DocumentSnapshot snapshot = serviceRef.get().get();

                if (snapshot.exists()) {
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

                    Map<String, Object> payload = Map.of(
                        "type", "COUNTER_UPDATE",
                        "serviceType", serviceType,
                        "counter1Serving", currentServingCounter1,
                        "counter2Serving", currentServingCounter2,
                        "counter3Serving", currentServingCounter3
                    );
                        try {
                            String jsonPayload = objectMapper.writeValueAsString(payload);
                            session.sendMessage(new TextMessage(jsonPayload));
                            logger.debug("Sent initial COUNTER_UPDATE for {} to new session {}", serviceType, session.getId());
                        } catch (IOException e) {
                            logger.error("Error sending initial COUNTER_UPDATE to session {}: {}", session.getId(), e.getMessage(), e);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error fetching initial state for service {}: {}", serviceType, e.getMessage(), e);
            }
        }
    }

    private void sendInitialAllTicketsState(WebSocketSession session) {
        try {
            Query allTicketsQuery = firestore.collection("TICKETS")
                    .whereIn("status", List.of("WAITING", "SERVING"))
                    .orderBy("number"); // Order by ticket number
            QuerySnapshot snapshot = allTicketsQuery.get().get();
            List<Map<String, Object>> ticketsData = new ArrayList<>();
            if (!snapshot.isEmpty()) { // Check if the snapshot contains documents
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    Map<String, Object> ticket = doc.getData();
                    if (ticket != null) {
                        ticket.put("id", doc.getId());
                        // add other ticket properties here
                        ticketsData.add(ticket);
                    }
                    Map<String, Object> payload = Map.of(
                        "type", "TICKET_UPDATE",
                        "tickets", ticketsData
                    );
                try {
                    String jsonPayload = objectMapper.writeValueAsString(payload);
                    session.sendMessage(new TextMessage(jsonPayload));
                    logger.debug("Sent initial TICKET_UPDATE (all tickets) to new session {}", session.getId());
                } catch (IOException e) {
                    logger.error("Error sending initial TICKET_UPDATE to session {}: {}", session.getId(), e.getMessage(), e);
                }
            }
        }
        } catch (Exception e) {
            logger.error("Error fetching initial state for all tickets: {}", e.getMessage(), e);
        }
    }
}
