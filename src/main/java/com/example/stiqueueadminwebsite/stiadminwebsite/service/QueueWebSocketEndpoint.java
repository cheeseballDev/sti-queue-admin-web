package com.example.stiqueueadminwebsite.stiadminwebsite.service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.EventListener;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreException;

@Component
@ServerEndpoint("/ws/queue/{queueType}")
public class QueueWebSocketEndpoint {

    private static Map<String, Map<Session, Boolean>> sessions = new ConcurrentHashMap<>();
    private final Logger logger = LoggerFactory.getLogger(QueueWebSocketEndpoint.class);
    private final Firestore firestore;
    private final ObjectMapper objectMapper;

    public QueueWebSocketEndpoint(Firestore firestore, ObjectMapper objectMapper) {
        this.firestore = firestore;
        this.objectMapper = objectMapper;
    }

    @OnOpen
    public void onOpen(Session session, @PathParam("queueType") String queueType) {
        logger.info("WebSocket connection opened for queueType: {}", queueType);
        sessions.computeIfAbsent(queueType, k -> new ConcurrentHashMap<>()).put(session, true);
        setupFirebaseListener(session, queueType);
    }

    @OnClose
    public void onClose(Session session, @PathParam("queueType") String queueType) {
        logger.info("WebSocket connection closed for queueType: {}", queueType);
        sessions.get(queueType).remove(session);
        if (sessions.get(queueType).isEmpty()) {
            sessions.remove(queueType);
        }
    }

    @OnError
    public void onError(Session session, @PathParam("queueType") String queueType, Throwable error) {
        logger.error("WebSocket error for queueType {}: {}", queueType, error.getMessage());
        sessions.get(queueType).remove(session);
        if (sessions.get(queueType).isEmpty()) {
            sessions.remove(queueType);
        }
    }

    @OnMessage
    public void onMessage(Session session, String message, @PathParam("queueType") String queueType) {
        // You can handle messages from the client here if needed
        logger.info("Received message from client for queueType {}: {}", queueType, message);
    }

    private void setupFirebaseListener(Session session, String queueType) {
        DocumentReference queueRef = firestore.collection("QUEUES").document(queueType.toUpperCase());

        queueRef.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(DocumentSnapshot snapshot, FirestoreException error) {
                if (snapshot != null && snapshot.exists()) {
                    Long currentServing = snapshot.getLong("currentServing");
                    if (currentServing != null) {
                        Map<String, Object> data = Map.of("currentServing", currentServing);
                        try {
                            String json = objectMapper.writeValueAsString(data);
                            if (session.isOpen() && sessions.get(queueType).containsKey(session)) {
                                session.getBasicRemote().sendText(json);
                            }
                        } catch (IOException e) {
                            logger.error("Error sending WebSocket message for queueType {}: {}", queueType, e.getMessage());
                        }
                    }
                }
            }
        });
    }
}
