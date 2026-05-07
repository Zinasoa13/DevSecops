package com.example.demo.model;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;


@Getter
@Setter
@Validated
public class QrCodeUrl {

    @NotEmpty
    private String url;

    public QrCodeUrl() {
    }

    public QrCodeUrl(String urlToBeEncoded) {
        this.url = urlToBeEncoded;
    }

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}
}
