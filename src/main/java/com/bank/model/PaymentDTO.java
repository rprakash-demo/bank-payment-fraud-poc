package com.bank.model;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAccessType;

@XmlRootElement(name = "payment")
@XmlAccessorType(XmlAccessType.FIELD)
public class PaymentDTO {
    private String transactionId;
    private String payerName;
    private String payeeName;
    private String payerCountryCode;
    private String payeeCountryCode;
    private String payerBank;
    private String payeeBank;
    private String paymentInstruction;
    private String currency;

    public PaymentDTO() {}

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String id) { this.transactionId = id; }
    public String getPayerName() { return payerName; }
    public void setPayerName(String name) { this.payerName = name; }
    public String getPayeeName() { return payeeName; }
    public void setPayeeName(String name) { this.payeeName = name; }
    public String getPayerCountryCode() { return payerCountryCode; }
    public void setPayerCountryCode(String c) { this.payerCountryCode = c; }
    public String getPayeeCountryCode() { return payeeCountryCode; }
    public void setPayeeCountryCode(String c) { this.payeeCountryCode = c; }
    public String getPayerBank() { return payerBank; }
    public void setPayerBank(String b) { this.payerBank = b; }
    public String getPayeeBank() { return payeeBank; }
    public void setPayeeBank(String b) { this.payeeBank = b; }
    public String getPaymentInstruction() { return paymentInstruction; }
    public void setPaymentInstruction(String i) { this.paymentInstruction = i; }
    public String getCurrency() { return currency; }
    public void setCurrency(String c) { this.currency = c; }
}