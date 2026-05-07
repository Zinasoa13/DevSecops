package com.example.demo.service;
//genere le code de double facteur
import org.springframework.stereotype.Service;

@Service
public class OtpService {

public String generateOtp(){
 return String.valueOf((int)(Math.random()*900000)+100000);
}
}