/*
 * Copyright (c) 2004, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v@@BUILD_VERSION@@ 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2006.04.06 at 03:27:58 PM EDT 
//


package client.dispatch.xmlhttp_jaxb.client;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ResultSet element declaration.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>{@code
 * <element name="ResultSet">
 *   <complexType>
 *     <complexContent>
 *       <restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *         <sequence>
 *           <element name="Result" type="{urn:yahoo:yn}ResultType" maxOccurs="50" minOccurs="0"/>
 *         </sequence>
 *         <attribute name="firstResultPosition" type="{http://www.w3.org/2001/XMLSchema}integer" />
 *         <attribute name="totalResultsAvailable" type="{http://www.w3.org/2001/XMLSchema}integer" />
 *         <attribute name="totalResultsReturned" type="{http://www.w3.org/2001/XMLSchema}integer" />
 *       </restriction>
 *     </complexContent>
 *   </complexType>
 * </element>
 * }</pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "result"
})
@XmlRootElement(name = "ResultSet")
public class ResultSet {

    @XmlElement(name = "Result", namespace = "urn:yahoo:yn", required = true)
    protected List<ResultType> result;
    @XmlAttribute
    protected BigInteger firstResultPosition;
    @XmlAttribute
    protected BigInteger totalResultsAvailable;
    @XmlAttribute
    protected BigInteger totalResultsReturned;

    /**
     * Gets the value of the result property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the result property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getResult().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ResultType }
     * 
     * 
     */
    public List<ResultType> getResult() {
        if (result == null) {
            result = new ArrayList<ResultType>();
        }
        return this.result;
    }

    /**
     * Gets the value of the firstResultPosition property.
     * 
     * @return
     *     possible object is
     *     {@link BigInteger }
     *     
     */
    public BigInteger getFirstResultPosition() {
        return firstResultPosition;
    }

    /**
     * Sets the value of the firstResultPosition property.
     * 
     * @param value
     *     allowed object is
     *     {@link BigInteger }
     *     
     */
    public void setFirstResultPosition(BigInteger value) {
        this.firstResultPosition = value;
    }

    /**
     * Gets the value of the totalResultsAvailable property.
     * 
     * @return
     *     possible object is
     *     {@link BigInteger }
     *     
     */
    public BigInteger getTotalResultsAvailable() {
        return totalResultsAvailable;
    }

    /**
     * Sets the value of the totalResultsAvailable property.
     * 
     * @param value
     *     allowed object is
     *     {@link BigInteger }
     *     
     */
    public void setTotalResultsAvailable(BigInteger value) {
        this.totalResultsAvailable = value;
    }

    /**
     * Gets the value of the totalResultsReturned property.
     * 
     * @return
     *     possible object is
     *     {@link BigInteger }
     *     
     */
    public BigInteger getTotalResultsReturned() {
        return totalResultsReturned;
    }

    /**
     * Sets the value of the totalResultsReturned property.
     * 
     * @param value
     *     allowed object is
     *     {@link BigInteger }
     *     
     */
    public void setTotalResultsReturned(BigInteger value) {
        this.totalResultsReturned = value;
    }

}
