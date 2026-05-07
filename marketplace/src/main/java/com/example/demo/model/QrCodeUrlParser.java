package com.example.demo.model;


public class QrCodeUrlParser extends AbstractQrCodeParser {

    private final QrCodeUrl qrCodeUrl;

    public QrCodeUrlParser(QrCodeUrl qrCodeUrl) {
        this.qrCodeUrl = qrCodeUrl;
    }

    @Override
    public String parse() {
        return this.qrCodeUrl.getUrl();
    }
    
    
}
