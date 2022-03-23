/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.xml.ws.cts.dl_swa;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for InputRequestThrowAFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>{@code
 * <complexType name="InputRequestThrowAFault">
 *   <complexContent>
 *     <restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       <sequence>
 *         <element name="mimeType1" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *         <element name="mimeType2" type="{http://www.w3.org/2001/XMLSchema}string"/>
 *       </sequence>
 *     </restriction>
 *   </complexContent>
 * </complexType>
 * }</pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "InputRequestThrowAFault", propOrder = {
    "mimeType1",
    "mimeType2"
})
public class InputRequestThrowAFault {

    @XmlElement(required = true)
    protected String mimeType1;
    @XmlElement(required = true)
    protected String mimeType2;

    /**
     * Gets the value of the mimeType1 property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMimeType1() {
        return mimeType1;
    }

    /**
     * Sets the value of the mimeType1 property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMimeType1(String value) {
        this.mimeType1 = value;
    }

    /**
     * Gets the value of the mimeType2 property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getMimeType2() {
        return mimeType2;
    }

    /**
     * Sets the value of the mimeType2 property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setMimeType2(String value) {
        this.mimeType2 = value;
    }

}
