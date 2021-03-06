package com.kaltura.client.enums;

/**
 * This class was generated using generate.php
 * against an XML schema provided by Kaltura.
 * @date Sun, 19 Jun 11 02:46:50 -0400
 * 
 * MANUAL CHANGES TO THIS CLASS WILL BE OVERWRITTEN.
 */
public enum KalturaThumbCropType {
    RESIZE (1),
    RESIZE_WITH_PADDING (2),
    CROP (3),
    CROP_FROM_TOP (4);

    int hashCode;

    KalturaThumbCropType(int hashCode) {
        this.hashCode = hashCode;
    }

    public int getHashCode() {
        return this.hashCode;
    }

    public static KalturaThumbCropType get(int hashCode) {
        switch(hashCode) {
            case 1: return RESIZE;
            case 2: return RESIZE_WITH_PADDING;
            case 3: return CROP;
            case 4: return CROP_FROM_TOP;
            default: return RESIZE;
        }
    }
}
