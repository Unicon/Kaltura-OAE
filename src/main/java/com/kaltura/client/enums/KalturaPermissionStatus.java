package com.kaltura.client.enums;

/**
 * This class was generated using generate.php
 * against an XML schema provided by Kaltura.
 * @date Sun, 19 Jun 11 02:46:50 -0400
 * 
 * MANUAL CHANGES TO THIS CLASS WILL BE OVERWRITTEN.
 */
public enum KalturaPermissionStatus {
    ACTIVE (1),
    BLOCKED (2),
    DELETED (3);

    int hashCode;

    KalturaPermissionStatus(int hashCode) {
        this.hashCode = hashCode;
    }

    public int getHashCode() {
        return this.hashCode;
    }

    public static KalturaPermissionStatus get(int hashCode) {
        switch(hashCode) {
            case 1: return ACTIVE;
            case 2: return BLOCKED;
            case 3: return DELETED;
            default: return ACTIVE;
        }
    }
}
