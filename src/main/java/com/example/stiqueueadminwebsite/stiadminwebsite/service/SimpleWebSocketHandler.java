package com.example.stiqueueadminwebsite.stiadminwebsite.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

@Component 
public class SimpleWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(SimpleWebSocketHandler.class);
    private final Map<WebSocketSession, String> activeSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final Firestore firestore;

    public SimpleWebSocketHandler(ObjectMapper objectMapper, Firestore firestore) {
        this.objectMapper = objectMapper;
        this.firestore = firestore;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("WebSocket connection established: Session ID - {}", session.getId());
        sendInitialCounterStates(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        logger.debug("Received message from {}: {}", session.getId(), payload);

        Map<String, Object> msg = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
        String type = (String) msg.get("type");

        // Handle the REGISTER_SERVICE message from the frontend
        if ("CURRENT_SERVICE".equals(type)) {
            String serviceType = (String) msg.get("serviceType");
            if (serviceType != null && !serviceType.isEmpty()) {
                activeSessions.put(session, serviceType.toLowerCase());
                logger.info("Session {} registered for service type: {}", session.getId(), serviceType);
                sendInitialTicketsForSession(session);
            } else {
                logger.warn("Session {} sent REGISTER_SERVICE without a serviceType.", session.getId());
            }
        }
        // Add other message handling here if your frontend sends other commands
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        activeSessions.remove(session);
        logger.info("WebSocket connection closed: Session ID - {}, Status - {}", session.getId(), status.getCode());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage(), exception);
        activeSessions.remove(session);
    }

    public void sendMessageToAllSessions(String message) {
        for (Map.Entry<WebSocketSession, String> entry : activeSessions.entrySet()) {
            WebSocketSession session = entry.getKey();
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    logger.error("Error sending message to all session {}: {}", session.getId(), e.getMessage(), e);
                }
            }
        }
    }

    public void sendMessageToServiceSessions(String targetServiceType, String message) {
        for (Map.Entry<WebSocketSession, String> entry : activeSessions.entrySet()) {
            WebSocketSession session = entry.getKey();
            String sessionServiceType = entry.getValue();

            if (session.isOpen() && targetServiceType.equalsIgnoreCase(sessionServiceType)) {
                try {
                    session.sendMessage(new TextMessage(message));
                    logger.debug("Sent targeted message to session {} for service {}: {}", session.getId(), targetServiceType, message);
                } catch (IOException e) {
                    logger.error("Error sending message to session {}: {}", session.getId(), e.getMessage(), e);
                }
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

    private void sendInitialTicketsForSession(WebSocketSession session) throws IOException, ExecutionException, InterruptedException {
        String serviceType = activeSessions.get(session); // Get the serviceType for this session

        if (serviceType == null || serviceType.isEmpty()) {
            logger.warn("Attempted to send initial tickets to session {} without registered serviceType.", session.getId());
            return;
        }

        Query ticketsQuery = firestore.collection("TICKETS")
                .whereEqualTo("service", serviceType)
                .orderBy("number");

        QuerySnapshot snapshots = ticketsQuery.get().get(); // Blocking call

        List<Map<String, Object>> ticketsData = new ArrayList<>();
        for (DocumentSnapshot doc : snapshots.getDocuments()) {
            Map<String, Object> ticket = doc.getData();
            if (ticket != null) {
                ticket.put("id", doc.getId());
                ticketsData.add(ticket);
            }
        }

        Map<String, Object> payload = Map.of(
            "type", "TICKET_UPDATE",
            "serviceType", serviceType,
            "tickets", ticketsData
        );
        String jsonPayload = objectMapper.writeValueAsString(payload);
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(jsonPayload));
        }
        logger.debug("Sent initial TICKET_UPDATE for service {} to session {}", serviceType, session.getId());
    }
}
