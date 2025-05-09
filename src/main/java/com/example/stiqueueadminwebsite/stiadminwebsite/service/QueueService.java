package com.example.stiqueueadminwebsite.stiadminwebsite.service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.cloud.firestore.Firestore;

@RestController
@RequestMapping("/api/{queue}")
public class QueueService {

    @Autowired
    private Firestore firestore;

    //@GetMapping("/{number}")
    //public 
}