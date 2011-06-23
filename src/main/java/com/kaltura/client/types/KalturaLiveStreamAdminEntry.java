package com.kaltura.client.types;

import java.util.IllegalFormatException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.kaltura.client.KalturaObjectBase;
import com.kaltura.client.KalturaParams;
import com.kaltura.client.KalturaApiException;
import com.kaltura.client.KalturaObjectFactory;


/**
 * This class was generated using generate.php
 * against an XML schema provided by Kaltura.
 * @date Sun, 19 Jun 11 02:46:50 -0400
 * 
 * MANUAL CHANGES TO THIS CLASS WILL BE OVERWRITTEN.
 */

public class KalturaLiveStreamAdminEntry extends KalturaLiveStreamEntry {
    public String encodingIP1;
    public String encodingIP2;
    public String streamPassword;
    public String streamUsername;

    public KalturaLiveStreamAdminEntry() {
    }

    public KalturaLiveStreamAdminEntry(Element node) throws KalturaApiException {
        super(node);
        NodeList childNodes = node.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node aNode = childNodes.item(i);
            String txt = aNode.getTextContent();
            String nodeName = aNode.getNodeName();
            if (false) {
                // noop
            } else if (nodeName.equals("encodingIP1")) {
                this.encodingIP1 = txt;
                continue;
            } else if (nodeName.equals("encodingIP2")) {
                this.encodingIP2 = txt;
                continue;
            } else if (nodeName.equals("streamPassword")) {
                this.streamPassword = txt;
                continue;
            } else if (nodeName.equals("streamUsername")) {
                this.streamUsername = txt;
                continue;
            } 

        }
    }

    public KalturaParams toParams() {
        KalturaParams kparams = super.toParams();
        kparams.setString("objectType", "KalturaLiveStreamAdminEntry");
        kparams.addStringIfNotNull("encodingIP1", this.encodingIP1);
        kparams.addStringIfNotNull("encodingIP2", this.encodingIP2);
        kparams.addStringIfNotNull("streamPassword", this.streamPassword);
        return kparams;
    }
}

