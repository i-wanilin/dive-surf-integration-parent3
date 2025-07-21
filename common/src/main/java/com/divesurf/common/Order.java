package com.divesurf.common;

import java.io.Serializable;
import java.util.UUID;

public class Order implements Serializable {
    private String CustomerID;
    private String FirstName;
    private String LastName;
    private String OverallItems;
    private String NumberOfDivingSuits;
    private String NumberOfSurfboards;
    private String OrderID;
    private String Valid;
    private String validationResult;

    public Order() {
        this.OrderID = UUID.randomUUID().toString();
    }

    public Order(String CustomerID, String FirstName, String LastName,
                 int NumberOfDivingSuits, int NumberOfSurfboards) {
        this();
        this.CustomerID = CustomerID;
        this.FirstName = FirstName;
        this.LastName = LastName;
        this.NumberOfDivingSuits = String.valueOf(NumberOfDivingSuits);
        this.NumberOfSurfboards = String.valueOf(NumberOfSurfboards);
        this.OverallItems = String.valueOf(NumberOfDivingSuits + NumberOfSurfboards);
    }


    public String getCustomerID() {
        return CustomerID;
    }

    public void setCustomerID(String customerID) {
        CustomerID = customerID;
    }

    public String getFirstName() {
        return FirstName;
    }

    public void setFirstName(String firstName) {
        FirstName = firstName;
    }

    public String getLastName() {
        return LastName;
    }

    public void setLastName(String lastName) {
        LastName = lastName;
    }

    public String getOverallItems() {
        return OverallItems;
    }

    public void setOverallItems(String overallItems) {
        OverallItems = overallItems;
    }

    public String getNumberOfDivingSuits() {
        return NumberOfDivingSuits;
    }

    public void setNumberOfDivingSuits(String numberOfDivingSuits) {
        NumberOfDivingSuits = numberOfDivingSuits;
    }

    public String getNumberOfSurfboards() {
        return NumberOfSurfboards;
    }

    public void setNumberOfSurfboards(String numberOfSurfboards) {
        NumberOfSurfboards = numberOfSurfboards;
    }

    public String getOrderID() {
        return OrderID;
    }

    public void setOrderID(String orderID) {
        OrderID = orderID;
    }

    public String getValid() {
        return Valid;
    }

    public void setValid(String valid) {
        Valid = valid;
    }

    public String getValidationResult() {
        return validationResult;
    }

    public void setValidationResult(String validationResult) {
        this.validationResult = validationResult;
    }

    @Override
    public String toString() {
        return "Order{" +
                "OrderID='" + OrderID + '\'' +
                ", CustomerID='" + CustomerID + '\'' +
                ", FirstName='" + FirstName + '\'' +
                ", LastName='" + LastName + '\'' +
                ", OverallItems=" + OverallItems +
                ", DivingSuits=" + NumberOfDivingSuits +
                ", Surfboards=" + NumberOfSurfboards +
                ", Valid=" + Valid +
                ", validationResult='" + validationResult + '\'' +
                '}';
    }
}