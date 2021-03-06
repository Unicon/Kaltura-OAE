package com.kaltura.client.enums;

/**
 * This class was generated using generate.php
 * against an XML schema provided by Kaltura.
 * @date Sun, 19 Jun 11 02:46:50 -0400
 * 
 * MANUAL CHANGES TO THIS CLASS WILL BE OVERWRITTEN.
 */
public enum KalturaAdminUserOrderBy {
    ID_ASC ("+id"),
    ID_DESC ("-id"),
    CREATED_AT_ASC ("+createdAt"),
    CREATED_AT_DESC ("-createdAt");

    String hashCode;

    KalturaAdminUserOrderBy(String hashCode) {
        this.hashCode = hashCode;
    }

    public String getHashCode() {
        return this.hashCode;
    }

    public static KalturaAdminUserOrderBy get(String hashCode) {
        if (hashCode.equals("+id"))
        {
           return ID_ASC;
        }
        else 
        if (hashCode.equals("-id"))
        {
           return ID_DESC;
        }
        else 
        if (hashCode.equals("+createdAt"))
        {
           return CREATED_AT_ASC;
        }
        else 
        if (hashCode.equals("-createdAt"))
        {
           return CREATED_AT_DESC;
        }
        else 
        {
           return ID_ASC;
        }
    }
}
