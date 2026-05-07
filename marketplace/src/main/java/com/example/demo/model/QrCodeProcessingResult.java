package com.example.demo.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QrCodeProcessingResult {
    private String image;
    private String encodedText;
    private String successMessage;
    private String errorMessage;

    public boolean isSuccessfull() {
        return successMessage != null;
    }

	public String getImage() {
		return image;
	}

	public void setImage(String image) {
		this.image = image;
	}

	public String getEncodedText() {
		return encodedText;
	}

	public void setEncodedText(String encodedText) {
		this.encodedText = encodedText;
	}

	public String getSuccessMessage() {
		return successMessage;
	}

	public void setSuccessMessage(String successMessage) {
		this.successMessage = successMessage;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}
}
