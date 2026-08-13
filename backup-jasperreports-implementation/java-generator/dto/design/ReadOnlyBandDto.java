package com.pdf.generator.dto.design;

public class ReadOnlyBandDto {

	private String name;
	private String reason;

	public ReadOnlyBandDto() {
	}

	public ReadOnlyBandDto(String name, String reason) {
		this.name = name;
		this.reason = reason;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}
}
