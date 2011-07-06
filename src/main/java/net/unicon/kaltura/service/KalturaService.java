/**
 * Copyright 2011 Unicon (R) Licensed under the
 * Educational Community License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.osedu.org/licenses/ECL-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package net.unicon.kaltura.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.unicon.kaltura.MediaItem;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.sakaiproject.nakamura.api.connections.ConnectionManager;
import org.sakaiproject.nakamura.api.doc.ServiceDocumentation;
import org.sakaiproject.nakamura.api.files.FileUploadHandler;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.messagebucket.MessageBucketService;
import org.sakaiproject.nakamura.api.profile.ProfileService;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.api.user.BasicUserInfoService;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.lite.content.InternalContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kaltura.client.KalturaApiException;
import com.kaltura.client.KalturaClient;
import com.kaltura.client.KalturaConfiguration;
import com.kaltura.client.enums.KalturaEditorType;
import com.kaltura.client.enums.KalturaMediaType;
import com.kaltura.client.enums.KalturaSessionType;
import com.kaltura.client.services.KalturaBaseEntryService;
import com.kaltura.client.services.KalturaSessionService;
import com.kaltura.client.types.KalturaBaseEntry;
import com.kaltura.client.types.KalturaBaseEntryFilter;
import com.kaltura.client.types.KalturaBaseEntryListResponse;
import com.kaltura.client.types.KalturaFilterPager;
import com.kaltura.client.types.KalturaMediaEntry;
import com.kaltura.client.types.KalturaMixEntry;


/**
 * The Kaltura service which handles all the actual processing related to Kaltura
 * 
 * @author Aaron Zeckoski (azeckoski @ unicon.net) (azeckoski @ vt.edu)
 */
@ServiceDocumentation(
        name = "Kaltura Service",
        description = "Handles all the processing related to the kaltura media integration"
)
@Component(immediate = true, metatype=true)
@Service({KalturaService.class, FileUploadHandler.class})
public class KalturaService implements FileUploadHandler {

    private static final Logger LOG = LoggerFactory.getLogger(KalturaService.class);

    private static final int MAX_ITEMS_FROM_KALTURA = 1000;
    private static final int defaultWidgetWidth = 480;
    private static final int defaultWidgetHeight = 360;

    public static final String KALTURA_MIMETYPE_VIDEO = "kaltura/video";
    public static final String KALTURA_MIMETYPE_AUDIO = "kaltura/audio";
    public static final String KALTURA_MIMETYPE_IMAGE = "kaltura/image";

    @Property(value="Unicon, Inc.")
    static final String SERVICE_VENDOR = "service.vendor";
    @Property(value="Handles all the processing related to the kaltura media integration")
    static final String SERVICE_DESCRIPTION = "service.description";

    @Property(intValue=111, label="Partner Id")
    private static final String KALTURA_PARTNER_ID = "kaltura.partnerid";
    @Property(value="setThisToYourKalturaSecret", label="Secret")
    private static final String KALTURA_SECRET = "kaltura.secret";
    @Property(value="setThisToYourKalturaAdminSecret", label="Admin Secret")
    private static final String KALTURA_ADMIN_SECRET = "kaltura.adminsecret";
    @Property(value="http://www.kaltura.com", label="Endpoint")
    private static final String KALTURA_ENDPOINT = "kaltura.endpoint";
    @Property(value="http://cdn.kaltura.com", label="CDN")
    private static final String KALTURA_CDN = "kaltura.cdn";

    /* DEFAULT set as confirmed by Kaltura (Nir) on 21 Sept 2010 @ 2300
    kaltura.player.image  - 2162571
    kaltura.player.audio  - 2158531
    kaltura.player.view  - 1522202
    kaltura.player.edit  - 1522362
    kaltura.uploader - 2011401
    kaltura.editor - 2733871
     */
    @Property(value="2162571", label="Player - Image")
    private static final String KALTURA_PLAYER_IMAGE = "kaltura.player.image";
    @Property(value="2158531", label="Player - Audio")
    private static final String KALTURA_PLAYER_AUDIO = "kaltura.player.audio";
    @Property(value="1522202", label="Player - Video View")
    private static final String KALTURA_PLAYER_VIEW = "kaltura.player.view";
    //@Property(value="1522362", label="Player - Video Edit")
    //private static final String KALTURA_PLAYER_EDIT = "kaltura.player.edit";
    //@Property(value="2733871", label="Player - Editor")
    //private static final String KALTURA_PLAYER_EDITOR = "kaltura.player.editor";

    //@Property(intValue=KalturaService.defaultWidgetWidth, label="Player - Image - Width")
    //private static final String KALTURA_PLAYER_IMAGE_WIDTH = "kaltura.player.image.width";
    //@Property(intValue=KalturaService.defaultWidgetHeight, label="Player - Image - Height")
    //private static final String KALTURA_PLAYER_IMAGE_HEIGHT = "kaltura.player.image.height";
    @Property(intValue=KalturaService.defaultWidgetWidth, label="Player - Audio - Width")
    private static final String KALTURA_PLAYER_AUDIO_WIDTH = "kaltura.player.audio.width";
    @Property(intValue=30, label="Player - Audio - Height")
    private static final String KALTURA_PLAYER_AUDIO_HEIGHT = "kaltura.player.audio.height";
    @Property(intValue=KalturaService.defaultWidgetWidth, label="Player - Video - Width")
    private static final String KALTURA_PLAYER_VIDEO_WIDTH = "kaltura.player.video.width";
    @Property(intValue=KalturaService.defaultWidgetHeight, label="Player - Video - Height")
    private static final String KALTURA_PLAYER_VIDEO_HEIGHT = "kaltura.player.video.height";

    KalturaConfiguration kalturaConfig;
    String kalturaCDN = null;
    /*
     * The kaltura widget ids from config
     */
    String kalturaPlayerIdImage = null;
    String kalturaPlayerIdAudio = null;
    String kalturaPlayerIdView = null;
    String kalturaPlayerIdEdit = null;
    String kalturaEditorId = null;
    /*
     * widgets sizes from config
     */
    int kalturaPlayerImageWidth = KalturaService.defaultWidgetWidth;
    int kalturaPlayerImageHeight = KalturaService.defaultWidgetHeight;
    int kalturaPlayerAudioWidth = KalturaService.defaultWidgetWidth;
    int kalturaPlayerAudioHeight = 30;
    int kalturaPlayerVideoWidth = KalturaService.defaultWidgetWidth;
    int kalturaPlayerVideoHeight = KalturaService.defaultWidgetHeight;

    // SERVICES

    @Reference
    Repository repository;

    @Reference
    ConnectionManager connectionManager;

    @Reference
    ProfileService profileService;

    @Reference
    MessageBucketService messageBucketService;

    @Reference
    SolrSearchServiceFactory searchServiceFactory;

    @Reference
    BasicUserInfoService basicUserInfoService;

    // OSGI INIT CODE

    @Activate
    protected void activate(Map<?, ?> properties) {
        LOG.info("Kaltura: start");
        init(properties);
    }

    @Deactivate
    protected void deactivate(Map<?, ?> properties) {
        LOG.info("Kaltura: stop");
    }

    @Modified
    protected void modified(Map<?, ?> properties) {
        LOG.info("Kaltura: modified config");
        init(properties);
    }

    /**
     * Initialize the configuration based on the OSGi config properties for this service
     * @param properties map of config settings
     */
    protected void init(Map<?, ?> properties) {
        // load up the config
        int kalturaPartnerId = getConfigurationSetting(KALTURA_PARTNER_ID, -1, properties);
        String kalturaSecret = getConfigurationSetting(KALTURA_SECRET, null, properties);
        String kalturaAdminSecret = getConfigurationSetting(KALTURA_ADMIN_SECRET, null, properties);
        String kalturaEndpoint = getConfigurationSetting(KALTURA_ENDPOINT, null, properties);
        this.kalturaCDN = getConfigurationSetting(KALTURA_CDN, null, properties);

        // supports customizing the look and feel AND functionality of the kaltura widgets
        //this.kalturaPlayerIdImage = getConfigurationSetting(KALTURA_PLAYER_IMAGE, "2162571", properties);
        this.kalturaPlayerIdAudio = getConfigurationSetting(KALTURA_PLAYER_AUDIO, "2158531", properties);
        this.kalturaPlayerIdView = getConfigurationSetting(KALTURA_PLAYER_VIEW, "1522202", properties);
        //this.kalturaPlayerIdEdit = getConfigurationSetting(KALTURA_PLAYER_EDIT, "1522362", properties);
        //this.kalturaEditorId = getConfigurationSetting(KALTURA_PLAYER_EDITOR, "2733871", properties);

        // allows for config of the sizes of the players
        //this.kalturaPlayerImageWidth = getConfigurationSetting(KALTURA_PLAYER_IMAGE_WIDTH, this.kalturaPlayerImageWidth, properties);
        //this.kalturaPlayerImageHeight = getConfigurationSetting(KALTURA_PLAYER_IMAGE_HEIGHT, this.kalturaPlayerImageHeight, properties);
        this.kalturaPlayerAudioWidth = getConfigurationSetting(KALTURA_PLAYER_AUDIO_WIDTH, this.kalturaPlayerAudioWidth, properties);
        this.kalturaPlayerAudioHeight = getConfigurationSetting(KALTURA_PLAYER_AUDIO_HEIGHT, this.kalturaPlayerAudioHeight, properties);
        this.kalturaPlayerVideoWidth = getConfigurationSetting(KALTURA_PLAYER_VIDEO_WIDTH, this.kalturaPlayerVideoWidth, properties);
        this.kalturaPlayerVideoHeight = getConfigurationSetting(KALTURA_PLAYER_VIDEO_HEIGHT, this.kalturaPlayerVideoHeight, properties);

        MediaItem.setDefaultSizes(
                this.kalturaPlayerImageWidth, this.kalturaPlayerImageHeight,
                this.kalturaPlayerAudioWidth, this.kalturaPlayerAudioHeight,
                this.kalturaPlayerVideoWidth, this.kalturaPlayerVideoHeight
        );

        // create the shared kaltura config
        KalturaConfiguration kc = new KalturaConfiguration();
        kc.setPartnerId(kalturaPartnerId);
        kc.setSecret(kalturaSecret);
        kc.setAdminSecret(kalturaAdminSecret);
        kc.setEndpoint(kalturaEndpoint);
        this.kalturaConfig = kc;
        // dump the config
        dumpServiceConfigToLog(properties);

        // test out that the kc can initialize a session
        KalturaClient kalturaClient = makeKalturaClient("admin", KalturaSessionType.ADMIN, 10);
        if (kalturaClient == null || kalturaClient.getSessionId() == null) {
            throw new RuntimeException("Failed to connect to kaltura server endpoint ("+kc.getEndpoint()+") as admin");
        }
        kalturaClient = makeKalturaClient("admin", KalturaSessionType.USER, 10);
        if (kalturaClient == null || kalturaClient.getSessionId() == null) {
            throw new RuntimeException("Failed to connect to kaltura server endpoint ("+kc.getEndpoint()+") as user");
        }
        LOG.info("Kaltura: Init complete: API version: "+kalturaClient.getApiVersion()+", Connected to endpoint: "+kc.getEndpoint());
    }

    /**
     * Special logging method
     * @param properties
     */
    private void dumpServiceConfigToLog(Map<?, ?> properties) {
        String propsDump="";
        if (properties != null && LOG.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n Properties:\n");
            for (Map.Entry<?, ?> entry : properties.entrySet()) {
                sb.append("  * ");
                sb.append(entry.getKey());
                sb.append(" -> ");
                sb.append(entry.getValue());
                sb.append("\n");
            }
            propsDump = sb.toString();
        }
        LOG.info("\nKalturaService Configuration: START ---------\n"
                +" partnerId="+this.kalturaConfig.getPartnerId()+"\n"
                +" endPoint="+this.kalturaConfig.getEndpoint()+"\n"
                +" timeout="+this.kalturaConfig.getTimeout()+"\n"
                +" kalturaCDN="+this.kalturaCDN+"\n"
                +" kalturaEditorId="+this.kalturaEditorId+"\n"
                +" kalturaPlayerIdView="+this.kalturaPlayerIdView+"\n"
                +" kalturaPlayerIdEdit="+this.kalturaPlayerIdEdit+"\n"
                +" kalturaPlayerIdAudio="+this.kalturaPlayerIdAudio+"\n"
                +" kalturaPlayerIdImage="+this.kalturaPlayerIdImage+"\n"
                +propsDump
                +"KalturaService Configuration: END ---------\n");
    }

    /**
     * Special logging method
     * @param properties
     * @param name
     */
    protected void dumpMapToLog(Map<?, ?> properties, String name) {
        String propsDump="";
        if (properties != null) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<?, ?> entry : properties.entrySet()) {
                sb.append("  * ");
                sb.append(entry.getKey());
                sb.append(" -> ");
                sb.append(entry.getValue());
                sb.append("\n");
            }
            propsDump = sb.toString();
        }
        LOG.info("\nMap ("+name+"): START ---------\n"
                +propsDump
                +"Map ("+name+"): END ---------\n");
    }

    /**
     * Special method for handling retrieval of OAE config settings in a typesafe way
     * @param <T>
     * @param settingName the key for the setting
     * @param defaultValue the default value if unset
     * @param properties the set of properties to search
     * @return the value of the setting (if set) or default value if not
     */
    @SuppressWarnings("unchecked")
    private <T> T getConfigurationSetting(String settingName, T defaultValue, Map<?,?> properties) {
        T returnValue = defaultValue;
        Object propValue = properties.get(settingName);
        if (defaultValue == null) {
            returnValue = (T) OsgiUtil.toString(propValue, null);
            if ("".equals(returnValue)) {
                returnValue = null;
            }
        } else {
            if (defaultValue instanceof Number) {
                int num = ((Number) defaultValue).intValue();
                int value = OsgiUtil.toInteger(propValue, num);
                returnValue = (T) Integer.valueOf(value);
            } else if (defaultValue instanceof Boolean) {
                boolean bool = ((Boolean) defaultValue).booleanValue();
                boolean value = OsgiUtil.toBoolean(propValue, bool);
                returnValue = (T) Boolean.valueOf(value);
            } else if (defaultValue instanceof String) {
                returnValue = (T) OsgiUtil.toString(propValue, (String) defaultValue);
            }
        }
        return returnValue;
    }


    // OAE FILE UPLOAD HANDLER

    /**
     * NOTE: requires https://github.com/marktriggs/nakamura/tree/fileuploadhandlers for now
     * 
     * This method is called when a file is uploaded via the
     * CreateContentPoolServlet.  It is called after the file has been added to
     * the repository, but has the opportunity to add or replace properties by
     * returning them as a Map.
     *
     * @param poolId
     *          The path of the content object for the file (a unique identifier).
     *
     * @param contentProperties
     *          An immutable map of the content object's properties (see sparsemapcontent's org.sakaiproject.nakamura.lite.content.InternalContent for the names of common properties)
     *
     * @param inputStream
     *          A InputStream on the uploaded content, set to position zero.
     *
     * @param userId
     *          The login name of the client performing the file upload (as per
     *          request.getRemoteUser())
     *
     * @param isNew
     *          True if the uploaded file is new content.  False if it replaces an existing node.
     *
     * @return A map of properties that will be added to the newly-created content object.
     *
     **/
    public Map<String, Object> handleFile(String poolId, Map<String, Object> contentPropertiesMethod,
            InputStream inputStream, String userId, boolean isNew) throws IOException {
        Map<String, Object> contentProperties = getContentProperties(poolId);
        //dumpMapToLog(contentProperties, "handleFile.contentProperties");
        // check if this is a video file and do nothing if it is not
        String mimeType = (String)contentProperties.get(InternalContent.MIMETYPE_FIELD);
        String fileName = (String)contentProperties.get(FilesConstants.POOLED_CONTENT_FILENAME);

        // NOTE: no handling for images yet
        KalturaMediaType mediaType = KalturaMediaType.VIDEO;
        boolean isVideo = isFileVideo(fileName, mimeType);
        boolean isAudio = false;
        if (!isVideo) {
            isAudio = isFileAudio(fileName, mimeType);
            if (isAudio) {
                mediaType = KalturaMediaType.AUDIO;
            }
        }

        if ( userId != null && UserConstants.ANON_USERID.equals(userId)) {
            // only include real users, no anonymous ones
            LOG.warn("Anonymous user uploaded a file - it is not being processed into Kaltura: "+fileName);
        } else if (!isVideo && !isAudio) {
            if (!isAudio) {
                LOG.debug("Uploaded file is not an audio file, no processing for Kaltura: "+fileName);
            } else {
                LOG.debug("Uploaded file is not a video, no processing for Kaltura: "+fileName);
            }
        } else {
            //String fileId = (String)contentProperties.get(InternalContent.UUID_FIELD);
            int version = 1;
            if (isNew) {
                // do something different when this is new

            } else {
                // do things when this is an update to an existing content item
                version = getCurrentVersion(poolId); // exception if lookup fails
            }
            String title = fileName+"_title";
            if (contentProperties.get(FilesConstants.POOLED_CONTENT_FILENAME) != null) {
                title = (String) contentProperties.get(FilesConstants.POOLED_CONTENT_FILENAME);
            }
            title += " - "+version;
            String desc = (String)contentProperties.get(FilesConstants.SAKAI_DESCRIPTION); // may be blank
            String tags = "";
            if (contentProperties.get(FilesConstants.SAKAI_TAGS) != null) {
                // convert tags array into CSV string
                String[] fileTags = (String[]) contentProperties.get(FilesConstants.SAKAI_TAGS);
                if (fileTags.length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < fileTags.length; i++) {
                        String tag = fileTags[i];
                        if (i > 0) {
                            sb.append(",");
                        }
                        sb.append(tag);
                    }
                    tags = sb.toString();
                }
            }
            // do processing of the video file
            long fileSize = (Long) contentProperties.get(InternalContent.LENGTH_FIELD);
            KalturaBaseEntry kbe = uploadItem(userId, fileName, fileSize, inputStream, mediaType, title, desc, tags); // exception if upload fails
            if (kbe != null) {
                // item upload successful
                MediaItem mediaItem = new MediaItem(kbe, userId);

                Map<String, Object> props = new HashMap<String, Object>(10);
                props.put("kaltura-id", mediaItem.getKalturaId());
                props.put("kaltura-thumbnail", mediaItem.getThumbnail());
                props.put("kaltura-download", mediaItem.getDownloadURL());
                props.put("kaltura-duration", mediaItem.getDuration()); // probably will be 0
                props.put("kaltura-height", mediaItem.getHeight());
                props.put("kaltura-width", mediaItem.getWidth());
                props.put("kaltura-type", mediaItem.getType());
                String kalturaMimeType = KALTURA_MIMETYPE_VIDEO;
                if (MediaItem.TYPE_AUDIO.equals(mediaItem.getMediaType())) {
                    kalturaMimeType = KALTURA_MIMETYPE_AUDIO;
                } else if (MediaItem.TYPE_IMAGE.equals(mediaItem.getMediaType())) {
                    kalturaMimeType = KALTURA_MIMETYPE_IMAGE;
                }
                props.put(InternalContent.MIMETYPE_FIELD, kalturaMimeType);

                LOG.info("Completed upload to Kaltura of file ("+fileName+") of type ("+kalturaMimeType+") and created kalturaEntry ("+mediaItem.getKalturaId()+")");

                updateContent(poolId, props); // exception if update fails
            } else {
                // should we fail here if kaltura does not return a valid KBE? -AZ
            }
            LOG.info("Kaltura file upload handler complete: "+fileName);
        }
        return new HashMap<String, Object>(0); // updated the props already, no need to return but have to return a non-null or exception
    }


    // OAE processing methods

    /**
     * Find the current version number (same as the number of versions) for this content item
     * @param poolId the content pool id
     * @return the current version number (defaults to 1)
     */
    private int getCurrentVersion(String poolId) {
        // NOTE: InternalContent.VERSION_NUMBER_FIELD is not useful
        int version = 1;
        try {
            Session adminSession = repository.loginAdministrative();
            ContentManager cm = adminSession.getContentManager();
            // Content content = cm.getVersion(poolId, fileId);
            List<String> versions = cm.getVersionHistory(poolId);
            version = versions.size();
            adminSession.logout();
        } catch (Exception e) {
            LOG.error("Unable to get versions for pool="+poolId+", defaulting to "+version+": "+e, e);
        }
        return version;
    }

    /**
     * Retrieve an OAE content item
     * @param poolId the unique path/poolId of a content object
     * @return the Content object
     * @throws RuntimeException if the content object cannot be retrieved
     */
    private Content getContent(String poolId) {
        Content content = null;
        try {
            Session adminSession = repository.loginAdministrative();
            ContentManager cm = adminSession.getContentManager();
            content = cm.get(poolId);
            adminSession.logout();
        } catch (Exception e) {
            LOG.error("Unable to get content by path="+poolId+": "+e, e);
            throw new RuntimeException("Unable to get content by path="+poolId+": "+e, e);
        }
        if (content == null) {
            throw new RuntimeException("Unable to get content by path="+poolId+": item not found");
        }
        return content;
    }

    /**
     * Retrieve the properties for some OAE content
     * @param poolId the unique path/poolId of a content object
     * @return the Map of content properties
     * @throws RuntimeException if the content object cannot be retrieved
     */
    private Map<String, Object> getContentProperties(String poolId) {
        Content content = getContent(poolId);
        return content.getProperties();
    }

    /**
     * Update an OAE content item
     * @param poolId the unique path/poolId of a content object
     * @param properties the properties to update or delete on this object (props with a NULL value will be removed, all others will be replaced or added)
     * @throws RuntimeException if the content object cannot be updated
     */
    private void updateContent(String poolId, Map<?, ?> properties) {
        Content contentItem = getContent(poolId);
        //dumpMapToLog(properties, "NEW-properties");
        for (Entry<?, ?> entry : properties.entrySet()) {
            String key = (String) entry.getKey();
            Object val = entry.getValue();
            if (val != null) {
                contentItem.setProperty(key, val);
            } else {
                contentItem.removeProperty(key);
            }
        }
        try {
            Session adminSession = repository.loginAdministrative();
            ContentManager contentManager = adminSession.getContentManager();
            contentManager.update(contentItem);
            adminSession.logout();
            LOG.debug("Completed update of content item props ("+poolId+") for Kaltura upload");
        } catch (Exception e) {
            LOG.error("Unable to update content at path="+poolId+": "+e, e);
            throw new RuntimeException("Unable to update content at path="+poolId+": "+e, e);
        }
    }

    /**
     * Determine if a file has video content
     * @param fileName the file name as uploaded
     * @param mimeType the mimetype from the UI
     * @return true if video, false otherwise
     */
    protected boolean isFileVideo(String fileName, String mimeType) {
        boolean video = false;
        if (KALTURA_MIMETYPE_VIDEO.equals(mimeType) || mimeType.startsWith("video/")) {
            video = true;
        } else {
            if (fileName.endsWith(".avi")  // avi
                    || fileName.endsWith(".mpg") // mpeg 2
                    || fileName.endsWith(".mpe") // mpeg 2
                    || fileName.endsWith(".mpeg") // mpeg 2
                    || fileName.endsWith(".mp4") // mpeg 4
                    || fileName.endsWith(".m4v") // mpeg 4
                    || fileName.endsWith(".mov") // quicktime
                    || fileName.endsWith(".qt") // quicktime
                    || fileName.endsWith(".asf") // windows media
                    || fileName.endsWith(".asx") // windows media
                    || fileName.endsWith(".wmv") // windows media
                    || fileName.endsWith(".rm") // real video
                    || fileName.endsWith(".ogm") // OG media
                    || fileName.endsWith(".3gp") // 3gpp
                    || fileName.endsWith(".mkv") // matroska
            ) {
                video = true;
            }
        }
        return video;
    }

    /**
     * Determine if a file has audio content
     * @param fileName the file name as uploaded
     * @param mimeType the mimetype from the UI
     * @return true if audio, false otherwise
     */
    protected boolean isFileAudio(String fileName, String mimeType) {
        boolean audio = false;
        if (KALTURA_MIMETYPE_AUDIO.equals(mimeType) || mimeType.startsWith("audio/")) {
            audio = true;
        } else {
            if (fileName.endsWith(".wav")  // wave audio
                    || fileName.endsWith(".aif") // aiff
                    || fileName.endsWith(".mp3") // mpeg 3
                    || fileName.endsWith(".aac") // aac
                    || fileName.endsWith(".mid") // midi
                    || fileName.endsWith(".mpa") // mpeg 2 audio
                    || fileName.endsWith(".wma") // windows media audio
                    || fileName.endsWith(".ra") // realaudio
            ) {
                audio = true;
            }
        }
        return audio;
    }

    /**
     * Get a user and their data based on the user identifier
     * @param userId user id (username)
     * @return the User object OR null if not found
     */
    protected User getUser(String userId) {
        User u = null;
        Session adminSession = null;
        try {
            adminSession = repository.loginAdministrative();
            AuthorizableManager authorizableManager = adminSession.getAuthorizableManager();
            Authorizable authorizable = authorizableManager.findAuthorizable(userId);
            u = (User) authorizable;
            adminSession.logout();
        } catch (StorageClientException e) {
            // nothing to do here
        } catch (AccessDeniedException e) {
            // nothing to do here
        } finally {
            if ( adminSession != null ) {
                try {
                    adminSession.logout();
                } catch (ClientPoolException e) {
                    LOG.warn(e.getMessage(), e);
                }
            }
        }
        return u;
    }


    // KALTURA CLIENT

    /*
     * NOTE: the KalturaClient is not even close to being threadsafe -AZ
     */
    ThreadLocal<KalturaClient> kctl = new ThreadLocal<KalturaClient>() {
        @Override
        protected KalturaClient initialValue() {
            return makeKalturaClient();
        };
    };
    /**
     * threadsafe method to get a kaltura client
     * @return the current kaltura client for this thread
     */
    public KalturaClient getKalturaClient() {
        return kctl.get();
    }
    /**
     * threadsafe method to get a kaltura client
     * @param userKey the user key (normally should be the username)
     * @return the current kaltura client for this thread
     */
    public KalturaClient getKalturaClient(String userKey) {
        if (userKey != null && !"".equals(userKey)) {
            KalturaClient kc = makeKalturaClient(userKey, KalturaSessionType.ADMIN, 0);
            kctl.set(kc);
        }
        return kctl.get();
    }
    /**
     * destroys the current kaltura client
     */
    public void clearKalturaClient() {
        kctl.remove();
    }

    /**
     * NOTE: this method will generate a new kaltura client using all defaults and sakai user, 
     * make sure you store this into the {@link #kctl} threadlocal if you are generating it using this method
     */
    private KalturaClient makeKalturaClient() {
        // defaults
        String userKey = "anonymous";
        KalturaSessionType sessionType = KalturaSessionType.USER;
        // NOTE: there is no way to get the user outside of a request in OAE
        KalturaClient kc = makeKalturaClient(userKey, sessionType, 0);
        return kc;
    }

    /**
     * NOTE: this method will generate a new kaltura client, 
     * make sure you store this into the {@link #kctl} threadlocal if you are generating it using this method
     */
    private KalturaClient makeKalturaClient(String userKey, KalturaSessionType sessionType, int timeoutSecs) {
        // client is not threadsafe
        if (timeoutSecs <= 0) {
            timeoutSecs = 86400; // NOTE set to 24 hours by request of kaltura   60; // default to 60 seconds
        }
        KalturaClient kalturaClient = new KalturaClient(this.kalturaConfig);
        String secret = this.kalturaConfig.getSecret();
        if (KalturaSessionType.ADMIN.equals(sessionType)) {
            secret = this.kalturaConfig.getAdminSecret();
        }
        KalturaSessionService sessionService = kalturaClient.getSessionService();
        try {
            String sessionId = sessionService.start(secret, userKey, sessionType, 
                    this.kalturaConfig.getPartnerId(), timeoutSecs, "edit:*"); // the edit is needed to fix an issue with kaltura servers
            kalturaClient.setSessionId(sessionId);
            LOG.debug("Created new kaltura client (oid="+kalturaClient.toString()+", tid="+Thread.currentThread().getId()+", ks="+kalturaClient.getSessionId()+")");
        } catch (KalturaApiException e) {
            //kalturaClient.setSessionId(null); // should we clear this?
            LOG.error("Unable to establish a kaltura session ("+kalturaClient.toString()+", "+kalturaClient.getSessionId()+"):: " + e, e);
        }
        return kalturaClient;
    }


    // KALTURA METHODS

    public KalturaBaseEntry uploadItem(String userId, String fileName, long fileSize, InputStream inputStream, 
            KalturaMediaType mediaType, String title, String description, String tags) {
        if (title == null || "".equals(title)) {
            title = fileName;
        }
        if (mediaType == null) {
            mediaType = KalturaMediaType.VIDEO;
        }
        KalturaMediaEntry kme = null;
        KalturaClient kc = getKalturaClient();
        if (kc != null) {
            try {
                String uploadTokenId = kc.getMediaService().upload(inputStream, fileName, fileSize);
                //LOG.info("upload token result: "+uploadTokenId);
                KalturaMediaEntry mediaEntry = new KalturaMediaEntry();
                mediaEntry.mediaType = KalturaMediaType.VIDEO;
                mediaEntry.userId = userId;
                mediaEntry.name = title;
                if (description != null) {
                    mediaEntry.description = description;
                }
                if (tags != null) {
                    mediaEntry.tags = tags;
                }
                //mediaEntry.adminTags = "OAE"; // TODO handle with custom meta fields?
                kme = kc.getMediaService().addFromUploadedFile(mediaEntry, uploadTokenId);
                //kme = kc.getBaseEntryService().update(entryId, mediaEntry); // NOTE: updateKalturaItem()
            } catch (Exception e) {
                LOG.error("Failure uploading item ("+fileName+"): "+e, e);
                throw new RuntimeException(e);
            }
        }
        return kme;
    }

    /**
     * @param textFilter a search filter string, null or "" includes all
     * @param keids [OPTIONAL] listing of keids to limit the results to
     * @param start 0 for all, or >0 start with that item
     * @param max 0 for all, or >0 to only return that many
     * @return the List of kaltura entries
     */
    public List<KalturaBaseEntry> getKalturaItems(String userKey, String textFilter, String[] keids, int start, int max) {
        if (start < 0) {
            start = 0;
        }
        if (max <= 0) {
            max = MAX_ITEMS_FROM_KALTURA;
        }
        List<KalturaBaseEntry> items = new ArrayList<KalturaBaseEntry>();
        if (textFilter == null) {
            textFilter = "";
        }
        KalturaClient kc = getKalturaClient();
        if (kc != null) {
            try {
                // use base entry service instead to get all -AZ
                //KalturaBaseEntry kbe = entryService.get("qqqq");
                KalturaBaseEntryService entryService = kc.getBaseEntryService();
                KalturaBaseEntryFilter filter = new KalturaBaseEntryFilter();
                filter.partnerIdEqual = this.kalturaConfig.getPartnerId();
                filter.userIdEqual = userKey;
                if (StringUtils.isNotBlank(textFilter)) {
                    filter.searchTextMatchOr = textFilter; // I think this is what I need but it does not seem to prioritize results?
                    //filter.nameLike = textFilter;
                }
                filter.statusIn = "0,1,2"; // KalturaEntryStatus.IMPORT+","+KalturaEntryStatus.PRECONVERT+","+KalturaEntryStatus.READY;
                // limit to a set of items as needed
                if (keids != null) {
                    filter.idIn = StringUtils.join(keids, ',');
                }
                //kmef.orderBy = "title";
                KalturaFilterPager pager = new KalturaFilterPager();
                pager.pageSize = max;
                pager.pageIndex = 0; // NOTE - kaltura does not support a start item in the paging API, only a start page
                KalturaBaseEntryListResponse listResponse = entryService.list(filter, pager);
                for (KalturaBaseEntry entry : listResponse.objects) {
                    items.add(entry); // KalturaMediaEntry KalturaMixEntry
                }
            } catch (KalturaApiException e) {
                LOG.error("Unable to get kaltura media items listing using session (oid="+kc.toString()+", tid="+Thread.currentThread().getId()+", ks="+kc.getSessionId()+"):: " + e, e);
            }
        }
        return items;
    }

    /**
     * Retrieve a single KME by the kaltura id
     * @param keid the kaltura entry id
     * @return the entry OR null if none found
     */
    public KalturaBaseEntry getKalturaItem(String userKey, String keid) {
        if (keid == null) {
            throw new IllegalArgumentException("keid must not be null");
        }
        KalturaBaseEntry kme = null;
        KalturaClient kc = getKalturaClient();
        if (kc != null) {
            try {
                //KalturaMediaService mediaService = kc.getMediaService();
                KalturaBaseEntryService entryService = kc.getBaseEntryService();
                kme = getKalturaEntry(userKey, keid, entryService);
            } catch (KalturaApiException e) {
                LOG.error("Unable to get kaltura media item ("+keid+") using session (oid="+kc.toString()+", tid="+Thread.currentThread().getId()+", ks="+kc.getSessionId()+"):: " + e, e);
            }
        }
        return kme;
    }

    public boolean removeKalturaItem(String userKey, String keid) {
        if (keid == null) {
            throw new IllegalArgumentException("keid must not be null");
        }
        boolean removed = false;
        KalturaClient kc = getKalturaClient();
        if (kc != null) {
            try {
                KalturaBaseEntryService entryService = kc.getBaseEntryService();
                KalturaBaseEntry entry = getKalturaEntry(userKey, keid, entryService);
                entryService.delete(entry.id);
                removed = true;
            } catch (KalturaApiException e) {
                LOG.error("Unable to remove kaltura item ("+keid+") using session (oid="+kc.toString()+", tid="+Thread.currentThread().getId()+", ks="+kc.getSessionId()+"):: " + e, e);
                removed = false;
            }
        }
        return removed;
    }

    /**
     * Creates a new kaltura mix for the current user/kaltura session from an existing kaltura entry
     * @param keid the id of the entry to create this mix from
     * @param name OPTIONAL the name for this new mix, null to use the entry name
     * @return the new mix item
     * @throws IllegalStateException if the mix cannot be created
     */
    public KalturaMixEntry createMix(String userKey, String keid, String name) {
        if (keid == null) {
            throw new IllegalArgumentException("keid must not be null");
        }
        KalturaMixEntry kmix = null;
        KalturaClient kc = getKalturaClient();
        if (kc != null) {
            try {
                KalturaBaseEntry kme = getKalturaItem(userKey, keid);
                if (kme == null) {
                    throw new IllegalArgumentException("Invalid keid ("+keid+"), cannot find entry");
                }
                KalturaMixEntry mix = new KalturaMixEntry();
                mix.name = name != null ? name : kme.name;
                mix.editorType = KalturaEditorType.ADVANCED;
                kmix = kc.getMixingService().add(mix);
                // append existing entry to this mix
                kc.getMixingService().appendMediaEntry(kmix.id, kme.id);
                // flattening is async, no way to tell if a mix has been flattened?
                //kc.getMixingService().requestFlattening(entryId, fileFormat);s
            } catch (KalturaApiException e) {
                throw new IllegalStateException("Unable to create new mix ("+name+") using session (oid="+kc.toString()+", tid="+Thread.currentThread().getId()+", ks="+kc.getSessionId()+"):: " + e, e);
            }
        }
        return kmix;
    }

    public KalturaBaseEntry updateKalturaItem(String userKey, KalturaBaseEntry kalturaEntry) {
        if (kalturaEntry == null) {
            throw new IllegalArgumentException("entry must not be null");
        }
        String keid = kalturaEntry.id;
        if (keid == null) {
            throw new IllegalArgumentException("entry keid must not be null");
        }
        KalturaBaseEntry kbe = null;
        KalturaClient kc = getKalturaClient();
        if (kc != null) {
            try {
                KalturaBaseEntryService entryService = kc.getBaseEntryService();
                kbe = getKalturaEntry(userKey, keid, entryService);
                if (kbe == null) {
                    throw new IllegalArgumentException("Cannot find KME to update using id ("+keid+")");
                }
                // integrate the fields we allow to be changed
                KalturaBaseEntry fields = new KalturaBaseEntry();
                //fields.creditUrl = entry.creditUrl;
                //fields.creditUserName = entry.creditUserName;
                fields.description = kalturaEntry.description;
                fields.name = kalturaEntry.name;
                fields.tags = kalturaEntry.tags;
                // now update the KME
                kbe = entryService.update(keid, fields);
            } catch (KalturaApiException e) {
                String msg = "Unable to update kaltura media item ("+keid+") using session (oid="+kc.toString()+", tid="+Thread.currentThread().getId()+", ks="+kc.getSessionId()+"):: " + e;
                LOG.error(msg, e);
                throw new RuntimeException(msg, e);
            }
        }
        return kbe;
    }


    /**
     * Get the KME with a permissions check to make sure the user key matches
     * @param keid the kaltura entry id
     * @param entryService the katura entry service
     * @return the entry
     * @throws KalturaApiException if kaltura cannot be accessed
     * @throws IllegalArgumentException if the keid cannot be found for this user
     */
    private KalturaBaseEntry getKalturaEntry(String userKey, String keid, KalturaBaseEntryService entryService) throws KalturaApiException {
        // DO NOT CACHE THIS ONE
        KalturaBaseEntry entry = null;
        // Cannot use the KMEF because it cannot filter by id correctly -AZ
        /*
        KalturaBaseEntryFilter kmef = new KalturaBaseEntryFilter();
        kmef.partnerIdEqual = this.kalturaConfig.getPartnerId();
        kmef.userIdEqual = currentUserName;
        kmef.idEqual = keid;
        //kmef.orderBy = "title";
        KalturaMediaListResponse listResponse = mediaService.list(kmef);
        if (listResponse != null && ! listResponse.objects.isEmpty()) {
            kme = listResponse.objects.get(0); // just get the first one
        }
         */
        // have to use - mediaService.get(keid); despite it not even checking if we have access to this - AZ
        entry = entryService.get(keid);
        if (entry == null) {
            // did not find the item by keid so we die
            throw new IllegalArgumentException("Cannot find kaltura item ("+keid+") with for user ("+userKey+")");
        }
        // also do a manual check for security, not so sure about this check though -AZ
        if (! entry.userId.equalsIgnoreCase(userKey) && entry.partnerId != this.kalturaConfig.getPartnerId()) {
            throw new SecurityException("currentUserName ("+userKey+") does not match KME user key ("+entry.userId+") and KME partnerId ("+entry.partnerId+") does not match current one ("+this.kalturaConfig.getPartnerId()+"), cannot access this KME ("+keid+")");
        }
        return entry;
    }


}
