/*
 * OpenRemote, the Home of the Digital Home.
 * Copyright 2008-2012, OpenRemote Inc.
 *
 * See the contributors.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openremote.modeler.cache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.app.event.EventCartridge;
import org.apache.velocity.app.event.implement.EscapeXmlReference;
import org.hibernate.ObjectNotFoundException;
import org.openremote.modeler.beehive.Beehive30API;
import org.openremote.modeler.beehive.BeehiveService;
import org.openremote.modeler.beehive.BeehiveServiceException;
import org.openremote.modeler.client.Configuration;
import org.openremote.modeler.client.model.Command;
import org.openremote.modeler.client.utils.PanelsAndMaxOid;
import org.openremote.modeler.configuration.PathConfig;
import org.openremote.modeler.domain.Absolute;
import org.openremote.modeler.domain.Cell;
import org.openremote.modeler.domain.CommandDelay;
import org.openremote.modeler.domain.CommandRefItem;
import org.openremote.modeler.domain.ConfigurationFilesGenerationContext;
import org.openremote.modeler.domain.ControllerConfig;
import org.openremote.modeler.domain.Device;
import org.openremote.modeler.domain.DeviceCommand;
import org.openremote.modeler.domain.DeviceCommandRef;
import org.openremote.modeler.domain.DeviceMacro;
import org.openremote.modeler.domain.DeviceMacroItem;
import org.openremote.modeler.domain.DeviceMacroRef;
import org.openremote.modeler.domain.Group;
import org.openremote.modeler.domain.GroupRef;
import org.openremote.modeler.domain.Panel;
import org.openremote.modeler.domain.ProtocolAttr;
import org.openremote.modeler.domain.Screen;
import org.openremote.modeler.domain.ScreenPairRef;
import org.openremote.modeler.domain.Sensor;
import org.openremote.modeler.domain.Slider;
import org.openremote.modeler.domain.Switch;
import org.openremote.modeler.domain.UICommand;
import org.openremote.modeler.domain.component.ColorPicker;
import org.openremote.modeler.domain.component.Gesture;
import org.openremote.modeler.domain.component.SensorOwner;
import org.openremote.modeler.domain.component.UIButton;
import org.openremote.modeler.domain.component.UIComponent;
import org.openremote.modeler.domain.component.UIControl;
import org.openremote.modeler.domain.component.UIGrid;
import org.openremote.modeler.domain.component.UIImage;
import org.openremote.modeler.domain.component.UILabel;
import org.openremote.modeler.domain.component.UISlider;
import org.openremote.modeler.domain.component.UISwitch;
import org.openremote.modeler.exception.ConfigurationException;
import org.openremote.modeler.exception.FileOperationException;
import org.openremote.modeler.exception.NetworkException;
import org.openremote.modeler.exception.XmlExportException;
import org.openremote.modeler.logging.AdministratorAlert;
import org.openremote.modeler.logging.LogFacade;
import org.openremote.modeler.server.protocol.ProtocolContainer;
import org.openremote.modeler.service.ControllerConfigService;
import org.openremote.modeler.service.DeviceCommandService;
import org.openremote.modeler.service.DeviceMacroService;
import org.openremote.modeler.service.SensorService;
import org.openremote.modeler.service.SliderService;
import org.openremote.modeler.service.SwitchService;
import org.openremote.modeler.service.UserService.UserAccount;
import org.openremote.modeler.shared.dto.DeviceCommandDTO;
import org.openremote.modeler.shared.dto.MacroDTO;
import org.openremote.modeler.shared.dto.UICommandDTO;
import org.openremote.modeler.service.DeviceService;
import org.openremote.modeler.utils.FileUtilsExt;
import org.openremote.modeler.utils.ProtocolCommandContainer;
import org.openremote.modeler.utils.UIComponentBox;
import org.openremote.modeler.utils.XmlParser;
import org.openremote.modeler.utils.dtoconverter.SwitchDTOConverter;
import org.springframework.transaction.annotation.Transactional;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.StaxDriver;

/**
 * Resource cache based on local file system access. This class provides an API for handling
 * and caching account's file resources.
 *
 * @see ResourceCache
 *
 * @author <a href="mailto:juha@openremote.org">Juha Lindfors</a>
 */
public class LocalFileCache implements ResourceCache<File>
{

  // TODO Tasks:
  //
  //    - http://jira.openremote.org/browse/MODELER-284 -- log performance stats
  //    - http://jira.openremote.org/browse/MODELER-285 -- push resource data integrity and
  //                                                       durability aspects to Beehive
  //    - http://jira.openremote.org/browse/MODELER-286 -- Beehive API returns 404 on new users
  //    

  // Constants ------------------------------------------------------------------------------------

  /**
   * The archive file name used in the cache directory to store the downloaded state from Beehive.
   */
  private final static String BEEHIVE_ARCHIVE_NAME = "openremote.zip";

  /**
   * File name prefix used for daily backups of cached Beehive archive.
   */
  private final static String DAILY_BACKUP_PREFIX = BEEHIVE_ARCHIVE_NAME + ".daily";

  /**
   * Convenience constant to indicate a hour granularity on system time (which is in milliseconds)
   */
  private final static int HOUR = 1000 * 60 * 60;

  /**
   * Convenience constant to indicate day granularity on system time (which is in milliseconds)
   */
  private final static int DAY  = 24 * HOUR;

  private static final String PANEL_XML_TEMPLATE = "panelXML.vm";
  private static final String CONTROLLER_XML_TEMPLATE = "controllerXML.vm";

  // Class Members --------------------------------------------------------------------------------

  /**
   * Log category for this cache implementation.
   */
  private final static LogFacade cacheLog =
      LogFacade.getInstance(LogFacade.Category.CACHE);

  /**
   * Admin alert notifications for critical errors in this implementation.
   */
  private final static AdministratorAlert admin =
      AdministratorAlert.getInstance(AdministratorAlert.Type.RESOURCE_CACHE);

  /**
   * Class-wide safety valve on backup file generation. If any errors are detected, halt backups
   * based on the concern that a potential corrupt data might propagate itself into backup cycles. <p>
   *
   * This set contains account IDs that have been flagged and should be prevented from creating
   * more backup copies.  <p>
   *
   * Multiple thread-access is synchronized via copy-on-write implementation. Assumption is that
   * writes are rare (only occur in case of systematic errors) and mostly access is read-only to
   * check existence of account IDs.
   */
  private final static Set<Long> haltAccountBackups = new CopyOnWriteArraySet<Long>();



  // Instance Fields ------------------------------------------------------------------------------

  /**
   * Designer configuration.
   */
  private Configuration configuration;

  /**
   * The current user associated with the calling thread that is accessing the account which
   * this cache belongs to.
   */
  private UserAccount currentUserAccount;

  /**
   * The path to an account's cache folder in the local filesystem.
   */
  private File cacheFolder;

  // Dependency introduced as part of MODELER-390
  private SwitchService switchService;
  private SensorService sensorService;
  private SliderService sliderService;
  private DeviceService deviceService;
  
  // Dependencies introduced as part of MODELER-287
  private DeviceMacroService deviceMacroService;
  private DeviceCommandService deviceCommandService;
  private ControllerConfigService controllerConfigService;
  private VelocityEngine velocity;

  private ProtocolContainer protocolContainer;
  
  // Constructors ---------------------------------------------------------------------------------


  /**
   * Constructs a new instance to manage operations on the given user account's local file cache.
   *
   * @param config    Designer configuration
   * @param user      The current user whose associated account and it's cache in local file
   *                  system will be manipulated.
   */
  public LocalFileCache(Configuration config, UserAccount userAccount)
  {
    this.configuration = config;

    this.currentUserAccount = userAccount;

    this.cacheFolder = new File(PathConfig.getInstance(config).userFolder(currentUserAccount.getAccount()));
  }



  // Implements ResourceCache ---------------------------------------------------------------------

  /**
   * Opens a stream for writing a zip compressed archive with user resources to this cache.
   * Note the API requirements on {@link CacheWriteStream} use : the stream must be marked
   * as complete by the calling client before this implementation accepts the resources.
   *
   * @see CacheWriteStream
   *
   * @return  An open stream that can be used to store a zip compressed resource archive
   *          to this cache. The returned stream object includes an API that differs from
   *          standard Java I/O streaming interfaces with a
   *          {@link org.openremote.modeler.cache.CacheWriteStream#markCompleted()} which
   *          the caller of this method must use in order for this cache implementation to
   *          consider the stream as completed and its contents usable for storing in cache.
   *
   * @throws CacheOperationException
   *            If an error occurs in creating or opening the required files in the local file
   *            system to store the incoming resource archive stream contents.
   *
   * @throws ConfigurationException
   *            If security constraints prevent access to required files in the local filesystem
   */
  @Override public CacheWriteStream openWriteStream()
      throws CacheOperationException, ConfigurationException
  {
    File tempDownloadTarget = new File(
        getCachedArchive().getPath() + "." + UUID.randomUUID().toString() + ".download"
    );

    cacheLog.debug("Downloading to ''{0}''", tempDownloadTarget.getAbsolutePath());

    try
    {
      return new FileCacheWriteStream(tempDownloadTarget);
    }

    catch (FileNotFoundException e)
    {
      throw new CacheOperationException(
          "Cannot open or create file ''{0}'' : {1}", e, tempDownloadTarget, e.getMessage()
      );
    }

    catch (SecurityException e)
    {
      throw new ConfigurationException(
          "Write access to ''{0}'' has been denied : {1}", e, tempDownloadTarget, e.getMessage()
      );
    }
  }


  /**
   * Creates a zip compressed file on the local file system (in this account's cache directory)
   * containing all user resources and returns a readable input stream from it. <p>
   *
   * This input stream can be used where the designer resources are expected as a zipped
   * archive bundle (Beehive API calls, configuration export functions, etc.)
   *
   * @return  an input stream from a zip archive in the local filesystem cache containing
   *          all account artifacts
   *
   * @throws CacheOperationException
   *            if any of the local file system operations fail
   *
   * @throws ConfigurationException
   *            if there are security restrictions on any of the file access
   */
  @Override public InputStream openReadStream() throws CacheOperationException, ConfigurationException
  {
    File exportArchiveFile = createExportArchive();

    try
    {
      return new BufferedInputStream(new FileInputStream(exportArchiveFile));
    }

    catch (Throwable t)
    {
      throw new CacheOperationException(
          "Failed to create input stream to export archive ''{0}'' : {1}",
          t, exportArchiveFile, t.getMessage()
      );
    }

    // TODO : MODELER-284 -- log execution performance
  }


  /**
   * Synchronizes the local cached Beehive archive with the Beehive server. If there are
   * existing previous cached copies of the Beehive archive on the local system, those are backed
   * up first. After the Beehive archive has been downloaded, it is extracted in the given
   * account's cache folder.
   *
   * @throws NetworkException
   *            If any errors occur with the network connection to Beehive server -- the basic
   *            assumption here is that network exceptions are recoverable (within a certain
   *            time period) and the method call can optionally be re-attempted at later time.
   *            Do note that the exception class provides a severity level which can be used
   *            to indicate the likelyhood that the network error can be recovered from.
   *
   * @throws ConfigurationException
   *            If any of the cache operations cannot be performed due to security restrictions
   *            on the local file system.
   *
   * @throws CacheOperationException
   *            If any runtime I/O errors occur during the sync.
   */
  @Override public void update() throws NetworkException, ConfigurationException, CacheOperationException
  {

    // Backup existing cached archives.
    //
    // TODO: MODELER-285
    //
    //   - We are over-cautious with cached copies here (which should be throw-away copies under
    //     normal circumstances) because of the issues with state synchronization between
    //     Designer and Beehive that currently exists -- these issues are commented on the
    //     DesignerState class in more detail. Once the implementations have been reviewed on
    //     both sides, the backup functionality can be made less aggressive (sparser) or disabled
    //     altogeher.

    try
    {
      backup();
    }

    // Handle errors from local cache operations (File I/O) explicitly here, and do not propagate
    // them higher up in the call stack. Errors in backups do not prevent normal operation but
    // does mean we've lost the usual recovery mechanisms so admins should be notified and act
    // to correct the problem as soon as possible.

    catch (CacheOperationException e)
    {
      haltAccountBackups.add(currentUserAccount.getAccount().getOid());

      admin.alert("Local cache operation error : {0}", e, e.getMessage());
    }


    cacheLog.info("Updating account cache for {0}.", printUserAccountLog(currentUserAccount));


    // Make sure cache folder is present, if not then create it...

    if (!hasCacheFolder())
    {
      createCacheFolder();      
    }


    // Invoke Beehive REST API to download the currently saved account resources and cache
    // them in Designer's local cache.
    //
    // At this point if there were previously cached archives, they've been backed up
    // (assuming backup operations worked correctly) so we can overwrite it. However,
    // the download is still done to a temp file first to ensure there were no I/O errors
    // and we don't overwrite with a partially downloaded or corrupted archive.

    BeehiveService beehive = new Beehive30API(configuration);


    try
    {
      beehive.downloadResources(currentUserAccount, this);
    }

    catch (BeehiveServiceException e)
    {
      // Generic, unanticipated exception type...

      throw new CacheOperationException("Download of resources failed : {0}", e, e.getMessage());
    }


    // If we got through download without exceptions and still have nothing in cache...

    if (!hasState())
    {
      cacheLog.info(
          "No user resources were downloaded from Beehive. Assuming new user account ''{0}''",
          currentUserAccount.getUsernamePassword().getUsername());

      return;
    }


    // If we made through all the error checking, we're ready to go. Unzip the archive and finish.

    extract(getCachedArchive(), cacheFolder);

    cacheLog.info(
        "Extracted ''{0}'' to ''{1}''.",
        getCachedArchive().getAbsolutePath(), cacheFolder.getAbsolutePath()
    );
  }

  /**
   * Replaces content of the cache with provided information.
   * Note that this only replaces a small part of the configuration: the UI definition.
   * All images are left untouched.
   * Controller definition will be re-generated from the database.
   * 
   * @param panels
   * @param maxOid
   */
  public void replace(Set<Panel> panels, long maxOid) {
    initResources(panels, maxOid);
  }
  
  /**
   * Replaces the local cached Beehive archive with the provided file. If there are
   * existing previous cached copies of the Beehive archive on the local system, those are backed
   * up first. After the Beehive archive has been downloaded, it is extracted in the given
   * account's cache folder.
   * 
   * @param configurationArchive File the configuration file to use as the local cached copy of Beehive archive
   *
   * @throws NetworkException
   *            If any errors occur with the network connection to Beehive server -- the basic
   *            assumption here is that network exceptions are recoverable (within a certain
   *            time period) and the method call can optionally be re-attempted at later time.
   *            Do note that the exception class provides a severity level which can be used
   *            to indicate the likelihood that the network error can be recovered from.
   *
   * @throws ConfigurationException
   *            If any of the cache operations cannot be performed due to security restrictions
   *            on the local file system.
   *
   * @throws CacheOperationException
   *            If any runtime I/O errors occur during the sync.
   */
  public void replace(File configurationArchive) throws NetworkException, ConfigurationException, CacheOperationException
  {
	  // TODO - EBR: should do some basic validation on provided file: zip file, contains OR file, ...
	  
	  
    // Backup existing cached archives.
    //
    // TODO: MODELER-285
    //
    //   - We are over-cautious with cached copies here (which should be throw-away copies under
    //     normal circumstances) because of the issues with state synchronization between
    //     Designer and Beehive that currently exists -- these issues are commented on the
    //     DesignerState class in more detail. Once the implementations have been reviewed on
    //     both sides, the backup functionality can be made less aggressive (sparser) or disabled
    //     altogeher.

    try
    {
      backup();
    }

    // Handle errors from local cache operations (File I/O) explicitly here, and do not propagate
    // them higher up in the call stack. Errors in backups do not prevent normal operation but
    // does mean we've lost the usual recovery mechanisms so admins should be notified and act
    // to correct the problem as soon as possible.

    catch (CacheOperationException e)
    {
      haltAccountBackups.add(currentUserAccount.getAccount().getOid());

      admin.alert("Local cache operation error : {0}", e, e.getMessage());
    }


    cacheLog.info("Replacing account cache for {0}.", printUserAccountLog(currentUserAccount));


    // We want to be sure we don't have any leftovers in the cache
    // Delete cache folder if it exists
    if (hasCacheFolder()) {
      removeCacheFolder();      
    }
    
    createCacheFolder();      

    configurationArchive.renameTo(getCachedArchive());

    // If we made through all the error checking, we're ready to go. Unzip the archive and finish.

    extract(getCachedArchive(), cacheFolder);

    cacheLog.info(
        "Extracted ''{0}'' to ''{1}''.",
        getCachedArchive().getAbsolutePath(), cacheFolder.getAbsolutePath()
    );
  }

  /**
   * Indicates if we've found any resource artifacts in the cache that would imply an existing,
   * previous cache state. This includes the presence of any backup copies. <p>
   *
   * @return    true if account's cache folder holds any previously cache artifacts, false
   *            otherwise
   *
   * @throws    ConfigurationException
   *                If any of the cache operations cannot be performed due to security restrictions
   *                on the local file system.
   */
  @Override public boolean hasState() throws ConfigurationException
  {
    if (hasCachedArchive())
    {
      return true;
    }

    else if (hasNewestDailyBackup())
    {
      return true;
    }

    return false;
  }
  

  /**
   * TODO : See Javadoc on interface definition. This exists to support earlier API patterns.
   */
  @Override public void markInUseImages(Set<File> markedImageFiles)
  {
    for (File file : markedImageFiles)
    {
      try
      {
        // TODO :
        //
        //   There are still bugs in the implementation and/or previous designer serialized state
        //   that cause the domain model to reference images that are not present in the cache.
        //
        //   This is a workaround to prevent later errors occuring due to missing cache
        //   resources -- if the image being marked as 'in-use' does not exist, do not include it,
        //   instead log an error.
        //                                                                            [JPL]

        File cacheResource = new File(cacheFolder, file.getName());

        if (!cacheResource.exists())
        {
          cacheLog.error(
              "BUG: domain model references image {0} ({1}) which was not found in cache folder.",
              file, cacheResource
          );
        }

        else
        {
          imageFiles.add(file);
        }
      }

      catch (SecurityException e)
      {
        cacheLog.error(
            "Security manager denied read access to ''{0}'' : {1}",
            new File(cacheFolder, file.getName()).getAbsolutePath(), e.getMessage()
        );
      }
    }
  }

  private Set<File> imageFiles = new HashSet<File>();



  // Public Instance Methods ----------------------------------------------------------------------


  /**
   * TODO : MODELER-289
   *
   *   - This method should not be public. Once the faulty export implementation in
   *     Designer is corrected (see Modeler-288), should become private
   *
   *
   *
   * Creates an exportable zip file archive on the local file system in the configured
   * account's cache directory. <p>
   * 
   * The archive is created based on the files as currently existing in the cache.
   * It is up to the caller of the method to ensure the cache is in the desired state
   * before creating the export file.
   *
   * This archive is used to send user design and configuration changes, added resource
   * files, etc. as a single HTTP POST payload to Beehive server. <p>
   *
   * This implementation is based on the current object model used in Designer which is
   * not (yet) versioned. The list of artifacts included in the export archive therefore
   * include : <p>
   *
   * <ul>
   *   <li>panel.xml</li>
   *   <li>controller.xml</li>
   *   <li>panels.obj</li>
   *   <li>ui_state.xml</li>
   *   <li>building_modeler.xml</li> // EBR this is not yet part of this branch
   *   <li>lircd.conf</li>
   *   <li>rules</li>
   *   <li>image resources</li>
   * </ul>
   *
   *
   * @return  reference to the export archive file in the account's cache directory
   *
   * @throws  CacheOperationException
   *              if any of the file operations fail
   *
   * @throws  ConfigurationException
   *              if there are any security restrictions on file access
   *
   */
  public File createExportArchive() throws CacheOperationException, ConfigurationException
  {

    File panelXMLFile = new File("panel.xml");
    File controllerXMLFile = new File("controller.xml");
    File panelsObjFile = new File("panels.obj");
    File lircdFile = new File("lircd.conf");
    File rulesFile = new File("rules", "modeler_rules.drl");

    File uiXMLFile = new File("ui_state.xml");
    File buildingXMLFile = new File("building_modeler.xml");

    // Collect all the files going into the archive...

    Set<File> exportFiles = new HashSet<File>();
    exportFiles.addAll(this.imageFiles);
    
    exportFiles.add(uiXMLFile);
    exportFiles.add(buildingXMLFile);
    
    
    exportFiles.add(panelXMLFile);
    exportFiles.add(controllerXMLFile);

    try
    {
      if (new File(cacheFolder, panelsObjFile.getPath()).exists())
      {
        exportFiles.add(panelsObjFile);
      }
    }

    catch (SecurityException e)
    {
      throw new ConfigurationException(
          "Security manager denied read access to file ''{0}'' (Account : {1}) : {2}",
          e, panelsObjFile.getAbsolutePath(), currentUserAccount.getAccount().getOid(), e.getMessage()
      );
    }

    try
    {
      if (new File(cacheFolder, rulesFile.getPath()).exists())
      {
        exportFiles.add(rulesFile);
      }
    }

    catch (SecurityException e)
    {
      throw new ConfigurationException(
          "Security manager denied read access to file ''{0}'' (Account : {1}) : {2}",
          e, rulesFile.getAbsolutePath(), currentUserAccount.getAccount().getOid(), e.getMessage()
      );
    }

    try
    {
      if (new File(cacheFolder, lircdFile.getPath()).exists())
      {
        exportFiles.add(lircdFile);
      }
    }

    catch (SecurityException e)
    {
      throw new ConfigurationException(
          "Security manager denied read access to file ''{0}'' (Account : {1}) : {2}",
          e, lircdFile, currentUserAccount.getAccount().getOid(), e.getMessage()
      );
    }


    // Create export archive file (do not overwrite the existing beehive archive)...

    File exportDir = new File(cacheFolder, "export");
    File targetFile = new File(exportDir, BEEHIVE_ARCHIVE_NAME);

    try
    {
      if (!exportDir.exists())
      {
        boolean success = exportDir.mkdirs();

        if (!success)
        {
          throw new CacheOperationException(
              "Cannot complete export archive operation. Unable to create required " +
              "file directory ''{0}'' for cache (Account ID = {1}).",
              exportDir.getAbsolutePath(), currentUserAccount.getAccount().getOid()
          );
        }
      }

      if (targetFile.exists())
      {
        boolean success = targetFile.delete();

        if (!success)
        {
          throw new CacheOperationException(
              "Cannot complete export archive operation. Unable to delete pre-existing " +
              "file ''{0}'' (Account ID = {1})", targetFile.getAbsolutePath(), currentUserAccount.getAccount().getOid()
          );
        }
      }
    }

    catch (SecurityException e)
    {
      throw new ConfigurationException(
          "Security manager denied access to temporary export archive file ''{0}'' for " +
          "account ID = {1} : {2}", e, targetFile.getAbsolutePath(), currentUserAccount.getAccount().getOid(), e.getMessage()
      );
    }


    // Zip it up...

    compress(targetFile, exportFiles);


    // Done.

    return targetFile;
  }


  // Private Instance Methods ---------------------------------------------------------------------



  private void validateArchive(File tempArchive)
  {
    // TODO
  }


  /**
   * Constructs the local filesystem directory structure to store a cached
   * Beehive archive associated with a given account. <p>
   *
   * Both the local cache directory and common subdirectories are created.
   *
   * @see #hasCacheFolder
   *
   * @throws ConfigurationException
   *            if the creation of the directories fail for any reason
   */
  private void createCacheFolder() throws ConfigurationException
  {
    try
    {
      boolean success = cacheFolder.mkdirs();

      if (!success)
      {
        throw new ConfigurationException(
            "Unable to create required directories for ''{0}''.", cacheFolder.getAbsolutePath()
        );
      }

      else
      {
        cacheLog.info(
            "Created account {0} cache folder (User: {1}).", currentUserAccount.getAccount().getOid(), currentUserAccount.getUsernamePassword().getUsername()
        );
      }
    }

    catch (SecurityException e)
    {
      throw new ConfigurationException(
          "Security manager has denied read/write access to local user cache in ''{0}'' : {1}",
          e, cacheFolder.getAbsolutePath(), e.getMessage()
      );
    }

    if (!hasBackupFolder())      // TODO : See MODELER-285
    {
      createBackupFolder();
    }
  }
  
  /**
   * Removes the local filesystem directory structure to store a cached
   * Beehive archive associated with a given account. <p>
   *
   * @see #createCacheFolder
   * @see #hasCacheFolder
   *
   * @throws ConfigurationException
   *            if the deletion of the directories fail for any reason
   */
  private void removeCacheFolder() throws ConfigurationException
  {
    try
    {
      FileUtils.deleteDirectory(cacheFolder);
      cacheLog.info(
          "Deleted account {0} cache folder (User: {1}).", currentUserAccount.getAccount().getOid(), currentUserAccount.getUsernamePassword().getUsername()
      );
    }

    catch (SecurityException e)
    {
      throw new ConfigurationException(
          "Security manager has denied read/write access to local user cache in ''{0}'' : {1}",
          e, cacheFolder.getAbsolutePath(), e.getMessage()
      );
    }
    
    catch (IOException e)
    {
    	throw new ConfigurationException(
            "Unable to delete cache directory for ''{0}''.", cacheFolder.getAbsolutePath()
        );
    }	
  }

  /**
   * Checks for the existence of local cache folder this cache implementation uses for its
   * operations.
   *
   * @return true if cache directory already exists, false otherwise
   *
   * @throws ConfigurationException
   *            if read access to file system has been denied
   */
  private boolean hasCacheFolder() throws ConfigurationException
  {
    try
    {
      return cacheFolder.exists();
    }

    catch (SecurityException e)
    {
      throw new ConfigurationException(
          "Security manager has denied read access to local user cache in ''{0}'' : {1}",
          e, cacheFolder.getAbsolutePath(), e.getMessage()
      );
    }
  }


  /**
   * Returns the local filesystem path to a Beehive archive in current user's account.
   *
   * @return    file path where the Beehive archive is stored in cache folder for
   *            this account
   */
  private File getCachedArchive()
  {
    return new File(cacheFolder, BEEHIVE_ARCHIVE_NAME);
  }

  /**
   * Tests for existence of a Beehive archive in the user's cache directory (as specified
   * by {@link #BEEHIVE_ARCHIVE_NAME} constant}.
   *
   * @see #getCachedArchive
   *
   * @return    true if Beehive archive is present in user account's cache folder, false
   *            otherwise
   *
   * @throws ConfigurationException
   *            if file read access is denied by security manager
   */
  private boolean hasCachedArchive() throws ConfigurationException
  {
    File f = getCachedArchive();

    try
    {
      return f.exists();
    }

    catch (SecurityException e)
    {
      throw new ConfigurationException(
        "Security manager has denied read access to ''{0}'' : {1}",
        e, f.getAbsolutePath(), e.getMessage()
      );
    }
  }




  /**
   * TODO : MODELER-285 -- this implementation should migrate to Beehive side.
   *
   * Creates a backup of the downloaded Beehive archive in the given account's cache folder.
   * Backups are created at most once per day. Number of daily backups can be limited. Daily
   * backups may optionally be rolled to weekly or monthly backups.
   *
   * @throws ConfigurationException
   *            if read/write access to cache folder or cache backup folder has not been granted,
   *            or creation of the required files fails for any other reason
   *
   * @throws CacheOperationException
   *            if any runtime or I/O errors occur during the normal file operations required
   *            to create backups, or if there's any indication that backup operation is not
   *            working as expected and admin should be notified
   */
  private void backup() throws ConfigurationException, CacheOperationException
  {

    // See if we should run...

    if (haltAccountBackups.contains(currentUserAccount.getAccount().getOid()))
    {
      cacheLog.warn("Backups for account {0} have been stopped!", currentUserAccount.getAccount().getOid());

      return;
    }


    // Sanity check...

    if (!hasCachedArchive() && hasNewestDailyBackup())
    {
      // It looks like the originally cached BEEHIVE_ARCHIVE_NAME has disappeared, but some
      // cache backup is still in place.
      //
      // This is odd, so bail out and notify.

      throw new CacheOperationException(
          "The Beehive archive was not found in cache but some backups were created earlier. " +
          "Should investigate the issue."
      );
    }


    // If no BEEHIVE_ARCHIVE_NAME file is found in cache directory, (and there are no backups)
    // it means this is the first time the Beehive archive will be downloaded (hopefully,
    // otherwise we got some deeper issues). There's nothing to back up yet.

    if (!hasCachedArchive())
    {
      cacheLog.info("No existing Beehive archive found. Backup skipped.");

      return;
    }


    // If it looks like this is the first backup being made (hopefully not due to cache
    // backups getting wiped due to some other errors)...

    if (!hasBackupFolder())
    {
      createBackupFolder();
    }

    try
    {
      // If there are no existing backups, just make a blanket copy...

      if (!hasNewestDailyBackup())
      {
        makeDailyBackup();
      }

      else
      {

        // If previous backups exist, check the timestamps and create a new one if the
        // newest is older than 24 hours...

        long timestamp = getCachedArchive().lastModified();
        long backuptime = getNewestDailyBackupFile().lastModified();

        if (timestamp - backuptime > DAY)
        {
          cacheLog.info(
              "Current daily backup is {0} days old. Creating a new backup...",
              new DecimalFormat("######0.00").format((float)(timestamp - backuptime) / DAY)
          );

          makeDailyBackup();
        }

        else
        {
          cacheLog.info(
              "Current archive was created only {0} hours ago. Skipping daily backup...",
              new DecimalFormat("######0.00").format(((float)(timestamp - backuptime)) / HOUR)
          );
        }
      }
    }

    catch (SecurityException e)
    {
      throw new ConfigurationException(
          "Security manager has denied read access to ''{0}'' : {1}",
          e, getCachedArchive().getAbsolutePath(), e.getMessage()
      );
    }
  }


  /**
   * TODO : MODELER-285 -- migrate this implementation to Beehive side
   *
   * Creates a daily backup copy of the downloaded (and cached) Beehive archive for the associated
   * account. The backup is only created if the existing (if any) backup is older than one day
   * (based on file timestamps). The copy is created via a temp file in case I/O errors occur.
   *
   * @see #hasCachedArchive
   * @see #getCachedArchive
   * @see #hasBackupFolder
   * @see #getBackupFolder
   *
   * @throws CacheOperationException
   *            if any runtime or I/O errors occur while making backups
   *
   * @throws ConfigurationException
   *            if read/write access to cache backup directory has been denied
   */
  private void makeDailyBackup() throws CacheOperationException, ConfigurationException
  {

    /**
     * The Maximum number of daily backups we'll create.
     */
    final int MAX_DAILY_BACKUPS = 5;

    File cacheFolder = getBackupFolder();

    cacheLog.debug("Making a daily backup of current Beehive archive...");

    try
    {
      File oldestDaily = new File(DAILY_BACKUP_PREFIX + "." + MAX_DAILY_BACKUPS);

      // If we've reached the maximum number of copies already, see if any of them
      // are more than a week old (and could be stored to weekly backups).

      if (oldestDaily.exists())
      {
        moveToWeeklyBackup(oldestDaily);
      }


      // Shuffle older backup copies out of the way (down by one index) to make space
      // for the latest to be saved in DAILY_BACKUP_PREFIX.1

      for (int index = MAX_DAILY_BACKUPS - 1; index > 0; index--)
      {
        File daily = new File(cacheFolder, DAILY_BACKUP_PREFIX + "." + index);
        File target = new File(cacheFolder, DAILY_BACKUP_PREFIX + "." + (index + 1));

        if (!daily.exists())
        {
          cacheLog.debug(
              "Daily backup file ''{0}'' was not present. Skipping...", daily.getAbsolutePath()
          );

          continue;
        }

        if (!daily.renameTo(target))
        {
          sortBackups();

          throw new CacheOperationException(
              "There was an error moving ''{0}'' to ''{1}''.",
              daily.getAbsolutePath(), target.getAbsolutePath()
          );
        }

        else
        {
          cacheLog.debug("Moved " + daily.getAbsolutePath() + " to " + target.getAbsolutePath());
        }
      }
    }

    catch (SecurityException e)
    {
      throw new ConfigurationException(
          "Security Manager has denied read/write access to daily backup files in ''{0}'' : {1}" +
          e, cacheFolder.getAbsolutePath(), e.getMessage()
      );
    }


    // Now make the actual copy of existing Beehive archive into cache backups. Copy to a
    // temp file first in case of errors (such as out-of-disk-space) might occur.

    File beehiveArchive = getCachedArchive();
    File tempBackupArchive = new File(cacheFolder, BEEHIVE_ARCHIVE_NAME + ".tmp");

    BufferedInputStream archiveReader = null;
    BufferedOutputStream tempBackupWriter = null;

    try
    {
      archiveReader = new BufferedInputStream(new FileInputStream(beehiveArchive));
      tempBackupWriter = new BufferedOutputStream(new FileOutputStream(tempBackupArchive));

      int len, bytecount = 0;
      final int BUFFER_SIZE = 4096;
      byte[] buffer = new byte[BUFFER_SIZE];

      while ((len = archiveReader.read(buffer, 0, BUFFER_SIZE )) != -1)
      {
        tempBackupWriter.write(buffer, 0, len);

        bytecount += len;
      }

      tempBackupWriter.flush();

      long originalFileSize = beehiveArchive.length();


      // Just a sanity check -- we should get the same amount of bytes on both sides on copy...

      if (originalFileSize != bytecount)
      {
        throw new CacheOperationException(
            "Original archive size was {0} bytes but only {1} were copied.",
            originalFileSize, bytecount
        );
      }

      cacheLog.debug(
          "Finished copying ''{0}'' to ''{1}''.",
          beehiveArchive.getAbsolutePath(), tempBackupArchive.getAbsolutePath()
      );
    }

    catch (FileNotFoundException e)
    {
      throw new CacheOperationException(
          "Files required for copying a backup of Beehive archive could not be found, opened " +
          "or created : {1}", e, e.getMessage()
      );
    }

    catch (IOException e)
    {
      throw new CacheOperationException(
          "Error while making a copy of the Beehive archive : {0}", e, e.getMessage()
      );
    }

    finally
    {
      if (archiveReader != null)
      {
        try
        {
          archiveReader.close();
        }

        catch (Throwable t)
        {
          cacheLog.warn(
              "Failed to close stream to ''{0}'' : {1}",
              t, beehiveArchive.getAbsolutePath(), t.getMessage()
          );
        }
      }

      if (tempBackupWriter != null)
      {
        try
        {
          tempBackupWriter.close();
        }

        catch (Throwable t)
        {
          cacheLog.warn(
              "Failed to close stream to ''{0}'' : {1}",
              t, tempBackupArchive.getAbsolutePath(), t.getMessage()
          );
        }
      }
    }


    validateArchive(tempBackupArchive);


    // Copy was ok, now move from temp to actual location...

    File newestDaily = getNewestDailyBackupFile();

    try
    {
      if (!tempBackupArchive.renameTo(newestDaily))
      {
        throw new CacheOperationException(
            "Error moving ''{0}'' to ''{1}''.",
            tempBackupArchive.getAbsolutePath(), newestDaily.getAbsolutePath()
        );
      }

      else
      {
        cacheLog.info("Backup complete. Saved in ''{0}''", newestDaily.getAbsolutePath());
      }
    }

    catch (SecurityException e)
    {
      throw new ConfigurationException(
          "Security Manager has denied write access to ''{0}'' : {1}",
          e, newestDaily.getAbsolutePath(), e.getMessage()
      );
    }
  }


  private static void moveToWeeklyBackup(File dailyBackupToMove)
  {
    // TODO

    cacheLog.error("Cannot make weekly backup. Weekly backups not implemented yet.");
  }

  private static void sortBackups()
  {
    // TODO
  }


  /**
   * TODO : http://jira.openremote.org/browse/MODELER-285
   *
   * Constructs the local filesystem backup directory for Beehive archives.
   *
   * @throws ConfigurationException
   *            if the creation of the directory fail for any reason
   */
  private void createBackupFolder() throws ConfigurationException
  {
    File backupFolder = getBackupFolder();

    try
    {
      boolean success = backupFolder.mkdirs();

      if (!success)
      {
        throw new ConfigurationException(
            "Unable to create required directories for ''{0}''.", backupFolder.getAbsolutePath()
        );
      }

      else
      {
        cacheLog.debug(
            "Created cache backup folder for account {0} (User: {1}).",
            currentUserAccount.getAccount().getOid(), currentUserAccount.getUsernamePassword().getUsername()
        );
      }
    }

    catch (SecurityException e)
    {
      throw new ConfigurationException(
          "Security manager has denied read/write access to local user cache in ''{0}'' : {1}",
          e, backupFolder.getAbsolutePath(), e.getMessage()
      );
    }
  }


  /**
   * TODO : http://jira.openremote.org/browse/MODELER-285
   *
   * Checks for the existence of cache *backup* folder.
   *
   * @return true if cache backup directory already exists, false otherwise
   *
   * @throws ConfigurationException
   *            if read access to file system has been denied
   */
  private boolean hasBackupFolder() throws ConfigurationException
  {
    File backupFolder = getBackupFolder();

    try
    {
      return backupFolder.exists();
    }

    catch (SecurityException e)
    {
      throw new ConfigurationException(
          "Security manager has denied read access to local user cache in ''{0}'' : {1}",
          e, backupFolder.getAbsolutePath(), e.getMessage()
      );
    }
  }


  /**
   * TODO : http://jira.openremote.org/browse/MODELER-285
   *
   * Returns a file path to archive backup folder of this account's local file cache.
   *
   * @return  path to a directory that can be used to store backups of downloaded
   *          Beehive archives for the associated account
   */
  private File getBackupFolder()
  {
    return new File(cacheFolder, "cache-backup");
  }


  /**
   * TODO : http://jira.openremote.org/browse/MODELER-285
   *
   * Checks for the existence of a latest daily backup copy of this account's Beehive
   * archive.
   *
   * @see #getNewestDailyBackupFile
   *
   * @return    true if what is marked as the most recent copy of the account's Beehive archive
   *            exists in the backup directory, false otherwise
   *
   * @throws ConfigurationException
   *            if read access to the latest daily backup copy has been denied
   */
  private boolean hasNewestDailyBackup() throws ConfigurationException
  {
    File cacheFolder = getBackupFolder();
    File newestDaily = getNewestDailyBackupFile();

    try
    {
      return newestDaily.exists();
    }

    catch (SecurityException e)
    {
      throw new ConfigurationException(
          "Security manager has denied read access to ''{0}'' in ''{1}'' : {2}",
          e, newestDaily, cacheFolder.getAbsolutePath(), e.getMessage()
      );
    }
  }


  /**
   * TODO : http://jira.openremote.org/browse/MODELER-285
   *
   * Returns a file path to the latest daily backup of this account's Beehive archive.
   *
   * @see #hasNewestDailyBackup
   *
   * @return  path to a file where the latest daily backup of this account's Beehive archive
   *          is stored
   */
  private File getNewestDailyBackupFile()
  {
    return new File(getBackupFolder(), DAILY_BACKUP_PREFIX + ".1");
  }


  /**
   * Extracts a source resource archive to target path in local filesystem. Necessary
   * subdirectories will be created according to archive structure if necessary. Existing
   * files and directories matching the archive structure <b>will be deleted!</b>
   *
   * @param sourceArchive     file path to source archive in the local filesystem.
   * @param targetDirectory   file path to target directory where to extract the archive
   *
   * @throws CacheOperationException
   *            if any file I/O errors occured during the extract operation
   *
   * @throws ConfigurationException
   *            if security manager has imposed access restrictions to the required files or
   *            directories
   */
  private void extract(File sourceArchive, File targetDirectory) throws CacheOperationException,
                                                                        ConfigurationException
  {
    ZipInputStream archiveInput = null;
    ZipEntry zipEntry;

    try
    {
      archiveInput = new ZipInputStream(new BufferedInputStream(new FileInputStream(sourceArchive)));

      while ((zipEntry = archiveInput.getNextEntry()) != null)
      {
        if (zipEntry.isDirectory())
        {
          // Do nothing -- relevant subdirectories will be created when handling the node files...

          continue;
        }

        File extractFile = new File(targetDirectory, zipEntry.getName());
        BufferedOutputStream extractOutput = null;

        try
        {
          FileUtilsExt.deleteQuietly(extractFile); // TODO : don't be quiet

          // create parent directories if necessary...

          if (!extractFile.getParentFile().exists())
          {
            boolean success = extractFile.getParentFile().mkdirs();

            if (!success)
            {
              throw new CacheOperationException(
                  "Unable to create cache folder directories ''{0}''. Reason unknown.",
                  extractFile.getParent()
              );
            }
          }

          extractOutput = new BufferedOutputStream(new FileOutputStream(extractFile));

          int len, bytecount = 0;
          byte[] buffer = new byte[4096];

          while ((len = archiveInput.read(buffer)) != -1)
          {
            try
            {
              extractOutput.write(buffer, 0, len);

              bytecount += len;
            }

            catch (IOException e)
            {
              throw new CacheOperationException(
                  "Error writing to ''{0}'' : {1}",
                  e, extractFile.getAbsolutePath(), e.getMessage()
              );
            }
          }

          cacheLog.debug(
              "Wrote {0} bytes to ''{1}''...", bytecount, extractFile.getAbsolutePath()
          );
        }

        catch (SecurityException e)
        {
          throw new ConfigurationException(
              "Security manager has denied access to ''{0}'' : {1}",
              e, extractFile.getAbsolutePath(), e.getMessage()
          );
        }

        catch (FileNotFoundException e)
        {
          throw new CacheOperationException(
              "Could not create file ''{0}'' : {1}",
              e, extractFile.getAbsolutePath(), e.getMessage()
          );
        }

        catch (IOException e)
        {
          throw new CacheOperationException(
              "Error reading zip entry ''{0}'' from ''{1}'' : {2}",
              e, zipEntry.getName(), sourceArchive.getAbsolutePath(), e.getMessage()
          );
        }

        finally
        {
          if (extractOutput != null)
          {
            try
            {
              extractOutput.close();
            }

            catch (Throwable t)
            {
              cacheLog.error(
                  "Could not close extracted file ''{0}'' : {1}",
                  t, extractFile.getAbsolutePath(), t.getMessage()
              );
            }
          }

          if (archiveInput != null)
          {
            try
            {
              archiveInput.closeEntry();
            }

            catch (Throwable t)
            {
              cacheLog.warn(
                  "Could not close zip entry ''{0}'' in archive ''{1}'' : {2}",
                  t, zipEntry.getName(), t.getMessage()
              );
            }
          }
        }
      }
    }

    catch (SecurityException e)
    {
      throw new ConfigurationException(
          "Security Manager has denied access to ''{0}'' : {1}",
          e, sourceArchive.getAbsolutePath(), e.getMessage()
      );
    }

    catch (FileNotFoundException e)
    {
      throw new CacheOperationException(
          "Archive ''{0}'' cannot be opened for reading : {1}",
          e, sourceArchive.getAbsolutePath(), e.getMessage()
      );
    }

    catch (IOException e)
    {
      throw new CacheOperationException(
          "Error reading archive ''{0}'' : {1}",
          e, sourceArchive.getAbsolutePath(), e.getMessage()
      );
    }

    finally
    {
      try
      {
        if (archiveInput != null)
        {
          archiveInput.close();
        }
      }

      catch (Throwable t)
      {
        cacheLog.error(
            "Error closing input stream to archive ''{0}'' : {1}",
            t, sourceArchive.getAbsolutePath(), t.getMessage()
        );
      }
    }
  }


  /**
   * Compresses a set of files into a target zip archive. The file instances should be relative
   * paths used to structure the archive into directories. The relative paths will be resolved
   * to actual file paths in the current account's file cache.
   *
   * @param target    Target file path where the zip archive will be stored.
   * @param files     Set of <b>relative</b> file paths to include in the zip archive. The file
   *                  paths should be set to match the expected directory structure in the final
   *                  archive (therefore should not reflect the absolute file paths expected to
   *                  be included in the archive).
   *
   * @throws CacheOperationException
   *            if any of the zip file operations fail
   *
   * @throws ConfigurationException
   *            if there are any security restrictions about reading the set of included files
   *            or writing the target zip archive file
   */
  private void compress(File target, Set<File> files) throws CacheOperationException,
                                                             ConfigurationException
  {
    ZipOutputStream zipOutput = null;

    try
    {
      zipOutput = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(target)));

      for (File file : files)
      {
        BufferedInputStream fileInput = null;

        // translate the relative zip archive directory path to existing user cache absolute path...

        File cachePathName = new File(cacheFolder, file.getPath());

        try
        {
          if (!cachePathName.exists())
          {
            throw new CacheOperationException(
                "Expected to add file ''{0}'' to export archive ''{1}'' (Account : {2}) but it " +
                "has gone missing (cause unknown). This can indicate implementation or deployment " +
                "error. Aborting export operation as a safety precaution.",
                cachePathName.getPath(), target.getAbsolutePath(), currentUserAccount.getAccount().getOid()
            );
          }

          fileInput = new BufferedInputStream(new FileInputStream(cachePathName));

          ZipEntry entry = new ZipEntry(file.getPath());

          entry.setSize(cachePathName.length());
          entry.setTime(cachePathName.lastModified());

          zipOutput.putNextEntry(entry);

          cacheLog.debug("Added new export zip entry ''{0}''.", file.getPath());

          int count, total = 0;
          final int BUFFER_SIZE = 2048;
          byte[] data = new byte[BUFFER_SIZE];

          while ((count = fileInput.read(data, 0, BUFFER_SIZE)) != -1)
          {
            zipOutput.write(data, 0, count);

            total += count;
          }

          zipOutput.flush();

          // Sanity check...

          if (total != cachePathName.length())
          {
            throw new CacheOperationException(
                "Only wrote {0} out of {1} bytes when archiving file ''{2}'' (Account : {3}). " +
                "This could have occured either due implementation error or file I/O error. " +
                "Aborting archive operation to prevent a potentially corrupt export archive to " +
                "be created.", total, cachePathName.length(), cachePathName.getPath(), currentUserAccount.getAccount().getOid()
            );
          }

          else
          {
            cacheLog.debug(
                "Wrote {0} out of {1} bytes to zip entry ''{2}''",
                total, cachePathName.length(), file.getPath()
            );
          }
        }

        catch (SecurityException e)
        {
          // we've messed up deployment... quite likely unrecoverable...

          throw new ConfigurationException(
              "Security manager has denied r/w access when attempting to read file ''{0}'' and " +
              "write it to archive ''{1}'' (Account : {2}) : {3}",
              e, cachePathName.getPath(), target, currentUserAccount.getAccount().getOid(), e.getMessage()
          );
        }

        catch (IllegalArgumentException e)
        {
          // This may occur if we overrun some fixed size limits in ZIP format...

          throw new CacheOperationException(
              "Error creating ZIP archive for account ID = {0} : {1}",
              e, currentUserAccount.getAccount().getOid(), e.getMessage()
          );
        }

        catch (FileNotFoundException e)
        {
          throw new CacheOperationException(
              "Attempted to include file ''{0}'' in export archive but it has gone missing " +
              "(Account : {1}). Possible implementation error in local file cache. Aborting  " +
              "export operation as a precaution ({2})",
              e, cachePathName.getPath(), currentUserAccount.getAccount().getOid(), e.getMessage()
          );
        }

        catch (ZipException e)
        {
          throw new CacheOperationException(
              "Error writing export archive for account ID = {0} : {1}",
              e, currentUserAccount.getAccount().getOid(), e.getMessage()
          );
        }

        catch (IOException e)
        {
          throw new CacheOperationException(
              "I/O error while creating export archive for account ID = {0}. " +
              "Operation aborted ({1})", e, currentUserAccount.getAccount().getOid(), e.getMessage()
          );
        }

        finally
        {
          if (zipOutput != null)
          {
            try
            {
              zipOutput.closeEntry();
            }

            catch (Throwable t)
            {
              cacheLog.warn(
                  "Unable to close zip entry for file ''{0}'' in export archive ''{1}'' " +
                  "(Account : {2}) : {3}.",
                  t, file.getPath(), target.getAbsolutePath(), currentUserAccount.getAccount().getOid(), t.getMessage()
              );
            }
          }

          if (fileInput != null)
          {
            try
            {
              fileInput.close();
            }

            catch (Throwable t)
            {
              cacheLog.warn(
                  "Failed to close input stream from file ''{0}'' being added " +
                  "to export archive (Account : {1}) : {2}", t, cachePathName.getPath(), currentUserAccount.getAccount().getOid(), t.getMessage()
              );
            }
          }
        }
      }
    }

    catch (FileNotFoundException e)
    {
      throw new CacheOperationException(
          "Unable to create target export archive ''{0}'' for account {1) : {2}",
          e, target, currentUserAccount.getAccount().getOid(), e.getMessage()
      );
    }

    finally
    {
      try
      {
        if (zipOutput != null)
        {
          zipOutput.close();
        }
      }

      catch (Throwable t)
      {
        cacheLog.warn(
            "Failed to close the stream to export archive ''{0}'' : {1}.", 
            t, target, t.getMessage()
        );
      }
    }
  }

  /**
   * @return File for binary panels.obj designer UI state serialization file.
   */
  public File getLegacyPanelObjFile() {
    PathConfig pathConfig = PathConfig.getInstance(configuration);
    return new File(pathConfig.userFolder(currentUserAccount.getAccount()) + "panels.obj"); // TODO : should go through ResourceCache interface : EBR -> JPL : why ?
  }
  
  /**
   * @return File for storing UI elements state in XML format.
   */
  public File getXMLUIFile() {
    PathConfig pathConfig = PathConfig.getInstance(configuration);
    return new File(pathConfig.userFolder(currentUserAccount.getAccount()) + "ui_state.xml");
  }
  
  /**
   * @return File for storing building configuration elements in XML format.
   */
  public File getBuildingModelerXmlFile() {
    PathConfig pathConfig = PathConfig.getInstance(configuration);
    return new File(pathConfig.userFolder(currentUserAccount.getAccount()) + "building_modeler.xml");
  }

  /**
   * @return File for panel XML description (panel.xml)
   */
  public File getPanelXmlFile() {
    PathConfig pathConfig = PathConfig.getInstance(configuration);
    return new File(pathConfig.userFolder(currentUserAccount.getAccount()) + "panel.xml");
  }

  /**
   * @return File for controller XML description (controller.xml)
   */
  public File getControllerXmlFile() {
    PathConfig pathConfig = PathConfig.getInstance(configuration);
    return new File(pathConfig.userFolder(currentUserAccount.getAccount()) + "controller.xml");
  }

  /**
   * @return File for LIRC daemon configuration (lircd.conf)
   */
  public File getLircdFile() {
    PathConfig pathConfig = PathConfig.getInstance(configuration);
    return new File(pathConfig.userFolder(currentUserAccount.getAccount()) + "lircd.conf");
  }
  
  /**
   * Detects the presence of legacy binary panels.obj designer UI state serialization file.
   *
   * @return      true if the panels.obj file is present in local beehive archive cache folder,
   *              false otherwise
   *
   * @throws ConfigurationException
   *              if read access to the file system is denied for any reason
   */
  public boolean hasLegacyDesignerUIState()
      throws ConfigurationException
  {
	  File panelsObjFile = getLegacyPanelObjFile();
    try
    {
      return panelsObjFile.exists();
    }

    catch (SecurityException e)
    {
      PathConfig pathConfig = PathConfig.getInstance(configuration);
      // convert the potential security exception to a checked exception...

      throw new ConfigurationException(
          "Security manager denied access to " + panelsObjFile.getAbsoluteFile() +
          ". File read/write access must be enabled to " + pathConfig.tempFolder() + ".", e
      );
    }
  }
  
  /**
   * Detects the presence of XML designer UI state serialization file.
   *
   * @return      true if the ui_state.xml file is present in local beehive archive cache folder,
   *              false otherwise
   *
   * @throws ConfigurationException
   *              if read access to the file system is denied for any reason
   */
  public boolean hasXMLUIState() throws ConfigurationException
  {
    File xmlUIStateFile = getXMLUIFile();
    try
    {
      return xmlUIStateFile.exists();
    }

    catch (SecurityException e)
    {
      PathConfig pathConfig = PathConfig.getInstance(configuration);
      // convert the potential security exception to a checked exception...

      throw new ConfigurationException(
          "Security manager denied access to " + xmlUIStateFile.getAbsoluteFile() +
          ". File read/write access must be enabled to " + pathConfig.tempFolder() + ".", e
      );
    }
  }
  
  private void persistUIState(Collection<Panel> panels, long maxOid) {
    File xmlUIFile = getXMLUIFile();
    
    XStream xstream = new XStream(new StaxDriver());
    xstream.alias("panel", Panel.class);
    xstream.alias("group", GroupRef.class);
    xstream.alias("screenPair", ScreenPairRef.class);
    xstream.alias("absolute", Absolute.class);
    
    OutputStreamWriter osw = null;
    try {
      // Going through a StreamWriter to enforce UTF-8 encoding
      osw = new OutputStreamWriter(new FileOutputStream(xmlUIFile), "UTF-8");
      xstream.toXML(new PanelsAndMaxOid(panels, maxOid), osw);
    } catch (IOException e) {
      throw new FileOperationException("Failed to write UI state to file " + xmlUIFile.getAbsolutePath() + " : " + e.getMessage(), e);
    } finally {
      try {
        if (osw != null) {
          osw.close();
        }
      } catch (IOException e) {
        cacheLog.warn("Unable to close writer to '" + xmlUIFile + "'.");
      }
    }
  }
  
  @Transactional
  private void initResources(Collection<Panel> panels, long maxOid) {
    // EBR - 20130213 - Left this old comment dating when persistence was done to 
    // Java serialization format.
    // 
    // 1, we must serialize panels at first, otherwise after integrating panel's
    // ui component and commands(such as
    // device command, sensor ...)
    // the oid would be changed, that is not ought to happen. for example :
    // after we restore panels, we create a
    // component with same sensor (like we did last time), the two
    // sensors will have different oid, if so, when we export controller.xml we
    // my find that there are two (or more
    // sensors) with all the same property except oid.
    
    
    persistUIState(panels, maxOid);

    Set<Group> groups = new LinkedHashSet<Group>();
    Set<Screen> screens = new LinkedHashSet<Screen>();
    /*
     * initialize groups and screens.
     */
    Panel.initGroupsAndScreens(panels, groups, screens);

    ConfigurationFilesGenerationContext generationContext = new ConfigurationFilesGenerationContext();
    
    List<Switch> dbSwitches = switchService.loadAll();
    for (Switch sw : dbSwitches) {
      generationContext.putSwitch(sw.getOid(), SwitchDTOConverter.createSwitchDetailsDTO(sw));
    }
    
    List<Slider> dbSliders = sliderService.loadAll();
    for (Slider slider : dbSliders) {
      generationContext.putSlider(slider.getOid(), slider.getSliderDetailsDTO());
    }
    
    List<Sensor> dbSensors = sensorService.loadAll(currentUserAccount.getAccount());
    for (Sensor sensor : dbSensors) {
      generationContext.putSensor(sensor.getOid(), sensor.getSensorDetailsDTO());
    }

    String controllerXmlContent = getControllerXML(screens, maxOid, generationContext);
    String panelXmlContent = getPanelXML(panels, generationContext);
    String sectionIds = getSectionIds(screens);
    String rulesFileContent = getRulesFileContent();

    // replaceUrl(screens, sessionId);
    // String activitiesJson = getActivitiesJson(activities);

    PathConfig pathConfig = PathConfig.getInstance(configuration);
    // File sessionFolder = new File(pathConfig.userFolder(sessionId));
    File userFolder = new File(pathConfig.userFolder(currentUserAccount.getAccount()));
    if (!userFolder.exists()) {
      boolean success = userFolder.mkdirs();

      if (!success) {
        throw new FileOperationException("Failed to create directory path to user folder '" + userFolder + "'.");
      }
    }

    /*
     * copy the default image and default colorpicker image.
     */
    File defaultImage = new File(pathConfig.getWebRootFolder() + UIImage.DEFAULT_IMAGE_URL);
    FileUtilsExt.copyFile(defaultImage, new File(userFolder, defaultImage.getName()));
    File defaultColorPickerImage = new File(pathConfig.getWebRootFolder() + ColorPicker.DEFAULT_COLORPICKER_URL);
    FileUtilsExt.copyFile(defaultColorPickerImage, new File(userFolder, defaultColorPickerImage.getName()));

    File panelXMLFile = getPanelXmlFile();
    File controllerXMLFile = getControllerXmlFile();
    File lircdFile = getLircdFile();

    File rulesDir = new File(pathConfig.userFolder(currentUserAccount.getAccount()), "rules");
    File rulesFile = new File(rulesDir, "modeler_rules.drl");

    /*
     * validate and output panel.xml.
     */
    String newIphoneXML = XmlParser.validateAndOutputXML(new File(getClass().getResource(configuration.getPanelXsdPath()).getPath()), panelXmlContent, userFolder);
    controllerXmlContent = XmlParser.validateAndOutputXML(new File(getClass().getResource(configuration.getControllerXsdPath()).getPath()), controllerXmlContent);
    /*
     * validate and output controller.xml
     */
    try {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("devices", deviceService.loadAllDeviceDetailsWithChildrenDTOs(currentUserAccount.getAccount()));
        map.put("macros", deviceMacroService.loadAllMacroDetailsDTOs(currentUserAccount.getAccount()));
        map.put("configuration", controllerConfigService.listAllConfigDTOs());
        
        XStream xstream = new XStream(new StaxDriver());
        
        OutputStreamWriter osw = null;
        try {
          // Going through a StreamWriter to enforce UTF-8 encoding
          osw = new OutputStreamWriter(new FileOutputStream(getBuildingModelerXmlFile()), "UTF-8");
          xstream.toXML(map, osw);
        } catch (IOException e) {
          throw new FileOperationException("Failed to write building modeler state to file " + getBuildingModelerXmlFile().getAbsolutePath() + " : " + e.getMessage(), e);
        } finally {
          try {
            if (osw != null) {
              osw.close();
            }
          } catch (IOException e) {
            cacheLog.warn("Unable to close writer to '" + getBuildingModelerXmlFile() + "'.");
          }
        }

        FileUtilsExt.deleteQuietly(panelXMLFile);
      FileUtilsExt.deleteQuietly(controllerXMLFile);
      FileUtilsExt.deleteQuietly(lircdFile);
      FileUtilsExt.deleteQuietly(rulesFile);

      FileUtilsExt.writeStringToFile(panelXMLFile, newIphoneXML);
      FileUtilsExt.writeStringToFile(controllerXMLFile, controllerXmlContent);
      FileUtilsExt.writeStringToFile(rulesFile, rulesFileContent);

      if (sectionIds != null && !sectionIds.equals("")) {
        FileUtils.copyURLToFile(buildLircRESTUrl(configuration.getBeehiveLircdConfRESTUrl(), sectionIds), lircdFile);
      }
      if (lircdFile.exists() && lircdFile.length() == 0) {
        boolean success = lircdFile.delete();

        if (!success) {
          cacheLog.error("Failed to delete '" + lircdFile + "'.");
        }

      }

    } catch (IOException e) {
      throw new FileOperationException("Failed to write resource: " + e.getMessage(), e);
    }
  }

  /**
   * TODO
   *
   * Builds the lirc rest url.
   */
  private URL buildLircRESTUrl(String restAPIUrl, String ids)
  {
    URL lircUrl;

    try
    {
      lircUrl = new URL(restAPIUrl + "?ids=" + ids);
    }

    catch (MalformedURLException e)
    {
      // TODO : don't throw runtime exceptions
      throw new IllegalArgumentException("Lirc file url is invalid", e);
    }

    return lircUrl;
  }

  /**
   * Gets the section ids.
   * 
   * @param screenList
   *          the activity list
   * 
   * @return the section ids
   */
  private String getSectionIds(Collection<Screen> screenList) {
    Set<String> sectionIds = new HashSet<String>();
    for (Screen screen : screenList) {
      for (Absolute absolute : screen.getAbsolutes()) {
        if (absolute.getUiComponent() instanceof UIControl) {
          for (UICommand command : ((UIControl) absolute.getUiComponent()).getCommands()) {
            addSectionIds(sectionIds, command);
          }
        }
      }
      for (UIGrid grid : screen.getGrids()) {
        for (Cell cell : grid.getCells()) {
          if (cell.getUiComponent() instanceof UIControl) {
            for (UICommand command : ((UIControl) cell.getUiComponent()).getCommands()) {
              addSectionIds(sectionIds, command);
            }
          }
        }
      }
    }

    StringBuffer sectionIdsSB = new StringBuffer();
    int i = 0;
    for (String sectionId : sectionIds) {
      if (sectionId != null) {
        sectionIdsSB.append(sectionId);
        if (i < sectionIds.size() - 1) {
          sectionIdsSB.append(",");
        }
      }
      i++;
    }
    return sectionIdsSB.toString();
  }

  private void addSectionIds(Set<String> sectionIds, UICommand command) {
    if (command instanceof DeviceMacroItem) {
      sectionIds.addAll(getDeviceMacroItemSectionIds((DeviceMacroItem) command));
    } else if (command instanceof CommandRefItem) {
      sectionIds.add(((CommandRefItem) command).getDeviceCommand().getSectionId());
    }
  }
  
  /**
   * Gets the device macro item section ids.
   * 
   * @param deviceMacroItem
   *          the device macro item
   * 
   * @return the device macro item section ids
   */
  private Set<String> getDeviceMacroItemSectionIds(DeviceMacroItem deviceMacroItem) {
    Set<String> deviceMacroRefSectionIds = new HashSet<String>();
    try {
      if (deviceMacroItem instanceof DeviceCommandRef) {
        deviceMacroRefSectionIds.add(((DeviceCommandRef) deviceMacroItem).getDeviceCommand().getSectionId());
      } else if (deviceMacroItem instanceof DeviceMacroRef) {
        DeviceMacro deviceMacro = ((DeviceMacroRef) deviceMacroItem).getTargetDeviceMacro();
        if (deviceMacro != null) {
          deviceMacro = deviceMacroService.loadById(deviceMacro.getOid());
          for (DeviceMacroItem nextDeviceMacroItem : deviceMacro.getDeviceMacroItems()) {
            deviceMacroRefSectionIds.addAll(getDeviceMacroItemSectionIds(nextDeviceMacroItem));
          }
        }
      }
    } catch (Exception e) {
      cacheLog.warn("Some components referenced a removed DeviceMacro!");
    }
    return deviceMacroRefSectionIds;
  }
  
  private String getPanelXML(Collection<Panel> panels, ConfigurationFilesGenerationContext generationContext) {
    /*
     * init groups and screens.
     */
    Set<Group> groups = new LinkedHashSet<Group>();
    Set<Screen> screens = new LinkedHashSet<Screen>();
    Panel.initGroupsAndScreens(panels, groups, screens);

    Map<String, Object> context = new HashMap<String, Object>();
    context.put("panels", panels);
    context.put("groups", groups);
    context.put("screens", screens);

    context.put("generationContext", generationContext);

    try {
      return mergeXMLTemplateIntoString(PANEL_XML_TEMPLATE, context);
    } catch (Exception e) {
      throw new XmlExportException("Failed to read panel.xml", e);
    }
  }

  @SuppressWarnings("unchecked")
  private String getControllerXML(Collection<Screen> screens, long maxOid, ConfigurationFilesGenerationContext generationContext) {

    // PATCH R3181 BEGIN ---8<-----
    /*
     * Get all sensors and commands from database.
     */
    List<Sensor> dbSensors = currentUserAccount.getAccount().getSensors();
    List<Device> allDevices = currentUserAccount.getAccount().getDevices();
    List<DeviceCommand> allDBDeviceCommands = new ArrayList<DeviceCommand>();

    for (Device device : allDevices) {
      allDBDeviceCommands.addAll(deviceCommandService.loadByDevice(device.getOid()));
    }
    // PATCH R3181 END ---->8-----

    /*
     * store the max oid
     */
    MaxId maxId = new MaxId(maxOid + 1);

    /*
     * initialize UI component box.
     */
    UIComponentBox uiComponentBox = new UIComponentBox();
    initUIComponentBox(screens, uiComponentBox);
    Map<String, Object> context = new HashMap<String, Object>();
    ProtocolCommandContainer eventContainer = new ProtocolCommandContainer(protocolContainer);
    eventContainer.setAllDBDeviceCommands(allDBDeviceCommands);
    addDataBaseCommands(eventContainer, maxId);

    Collection<Sensor> sensors = getAllSensorWithoutDuplicate(screens, maxId, dbSensors);

    Collection<UISwitch> switchs = (Collection<UISwitch>) uiComponentBox.getUIComponentsByType(UISwitch.class);
    Collection<UIComponent> buttons = (Collection<UIComponent>) uiComponentBox.getUIComponentsByType(UIButton.class);
    Collection<UIComponent> gestures = (Collection<UIComponent>) uiComponentBox.getUIComponentsByType(Gesture.class);
    Collection<UIComponent> uiSliders = (Collection<UIComponent>) uiComponentBox.getUIComponentsByType(UISlider.class);
    Collection<UIComponent> uiImages = (Collection<UIComponent>) uiComponentBox.getUIComponentsByType(UIImage.class);
    Collection<UIComponent> uiLabels = (Collection<UIComponent>) uiComponentBox.getUIComponentsByType(UILabel.class);
    Collection<UIComponent> colorPickers = (Collection<UIComponent>) uiComponentBox.getUIComponentsByType(ColorPicker.class);
    Collection<ControllerConfig> configs = controllerConfigService.listAllConfigs();
    configs.removeAll(controllerConfigService.listAllexpiredConfigs());
    configs.addAll(controllerConfigService.listAllMissingConfigs());

    // TODO : BEGIN HACK (TO BE REMOVED)
    //
    // - the following removes the rules.editor configuration section from the
    // controller.xml
    // <config> section. The rules should not be defined in terms of controller
    // configuration
    // in the designer but as artifacts, similar to images (and multiple rule
    // files should
    // be supported).

    for (ControllerConfig controllerConfig : configs) {
      if (controllerConfig.getName().equals("rules.editor")) {
        configs.remove(controllerConfig);

        break; // this fixes a concurrent modification error in this hack..
      }
    }

    // TODO : END HACK -------------------

    context.put("switchs", switchs);
    context.put("buttons", buttons);
    context.put("screens", screens);
    context.put("eventContainer", eventContainer);
    context.put("localFileCache", this);
    context.put("protocolContainer", protocolContainer);
    context.put("sensors", sensors);
    context.put("dbSensors", dbSensors);
    context.put("gestures", gestures);
    context.put("uiSliders", uiSliders);
    context.put("labels", uiLabels);
    context.put("images", uiImages);
    context.put("colorPickers", colorPickers);
    context.put("maxId", maxId);
    context.put("configs", configs);
    context.put("generationContext", generationContext);

    try {
      return mergeXMLTemplateIntoString(CONTROLLER_XML_TEMPLATE, context);
    } catch (Exception e) {
      throw new XmlExportException("Failed to read panel.xml", e);
    }
  }
  
  /**
   * Adds the data base commands into protocolEventContainer.
   */
  private void addDataBaseCommands(ProtocolCommandContainer protocolEventContainer, MaxId maxId) {
    // Part of patch R3181 -- include all components in controller.xml even if
    // not bound to UI components

    List<DeviceCommand> dbDeviceCommands = protocolEventContainer.getAllDBDeviceCommands();

    for (DeviceCommand deviceCommand : dbDeviceCommands) {
      String protocolType = deviceCommand.getProtocol().getType();
      List<ProtocolAttr> protocolAttrs = deviceCommand.getProtocol().getAttributes();

      Command uiButtonEvent = new Command();
      uiButtonEvent.setId(maxId.maxId());
      uiButtonEvent.setProtocolDisplayName(protocolType);
      uiButtonEvent.setDeviceName(deviceCommand.getDevice().getName());
      uiButtonEvent.setDeviceId(Long.toString(deviceCommand.getDevice().getOid()));

      for (ProtocolAttr protocolAttr : protocolAttrs) {
        uiButtonEvent.getProtocolAttrs().put(protocolAttr.getName(), protocolAttr.getValue());
      }

      uiButtonEvent.setLabel(deviceCommand.getName());
      protocolEventContainer.addUIButtonEvent(uiButtonEvent);
    }
  }

  /**
   * EBR - 20130426
   * This version of the getCommandOwner takes an id to lookup the command.
   * In this implementation, it is expected that the id is a DeviceCommand id.
   * This is a limitation compared to the other implementations,
   * as the id might have been a DeviceMacro id.
   * 
   * This is currently used in the controllerXML.vm template.
   * Added as part of fix of controller XML generation, initially caused by the MODELER-390 reworks.
   *
   * @param id
   * @param protocolEventContainer
   * @param maxId
   * @return
   */
  public List<Command> getCommandOwnerById(Long id, ProtocolCommandContainer protocolEventContainer,
      MaxId maxId) {
    List<Command> oneUIButtonEventList = new ArrayList<Command>();
    DeviceCommand deviceCommand = deviceCommandService.loadById(id);
    protocolEventContainer.removeDeviceCommand(deviceCommand);
    addDeviceCommandEvent(protocolEventContainer, oneUIButtonEventList, deviceCommand, maxId);
    return oneUIButtonEventList;
  }

  /**
   * TODO
   * 
   * @param command
   *           the device command item
   * @param protocolEventContainer
   *           the protocol event container
   * 
   * @return the controller xml segment content
   */
  public List<Command> getCommandOwnerByUICommand(UICommand command, ProtocolCommandContainer protocolEventContainer,
        MaxId maxId) {
     List<Command> oneUIButtonEventList = new ArrayList<Command>();
     try {
        if (command instanceof DeviceMacroItem) {
           if (command instanceof DeviceCommandRef) {
              DeviceCommand deviceCommand = deviceCommandService.loadById(((DeviceCommandRef) command)
                    .getDeviceCommand().getOid());
              addDeviceCommandEvent(protocolEventContainer, oneUIButtonEventList, deviceCommand, maxId);
           } else if (command instanceof DeviceMacroRef) {
              DeviceMacro deviceMacro = ((DeviceMacroRef) command).getTargetDeviceMacro();
              deviceMacro = deviceMacroService.loadById(deviceMacro.getOid());
              for (DeviceMacroItem tempDeviceMacroItem : deviceMacro.getDeviceMacroItems()) {
                 oneUIButtonEventList.addAll(getCommandOwnerByUICommand(tempDeviceMacroItem, protocolEventContainer,
                       maxId));
              }
           } else if (command instanceof CommandDelay) {
              CommandDelay delay = (CommandDelay) command;
              Command uiButtonEvent = new Command();
              uiButtonEvent.setId(maxId.maxId());
              uiButtonEvent.setDelay(delay.getDelaySecond());
              oneUIButtonEventList.add(uiButtonEvent);
           }
        } else if (command instanceof CommandRefItem) {
           DeviceCommand deviceCommand = deviceCommandService.loadById(((CommandRefItem) command).getDeviceCommand()
                 .getOid());
           protocolEventContainer.removeDeviceCommand(deviceCommand);
           addDeviceCommandEvent(protocolEventContainer, oneUIButtonEventList, deviceCommand, maxId);
        } else {
           return new ArrayList<Command>();
        }
     } catch (Exception e) {
        cacheLog.warn("Some component (" + command.getOid() + ":" + command.getDisplayName() + ") referenced a removed object:  " + e.getMessage());
        return new ArrayList<Command>();
     }
     return oneUIButtonEventList;
  }

  public List<Command> getCommandOwnerByUICommandDTO(UICommandDTO command, ProtocolCommandContainer protocolEventContainer,
      MaxId maxId) {
   List<Command> oneUIButtonEventList = new ArrayList<Command>();
   
   try {
      if (command instanceof DeviceCommandDTO) {
        DeviceCommand deviceCommand = deviceCommandService.loadById(command.getOid());
        addDeviceCommandEvent(protocolEventContainer, oneUIButtonEventList, deviceCommand, maxId);
      } else if (command instanceof MacroDTO) {
        DeviceMacro deviceMacro = deviceMacroService.loadById(command.getOid());
        for (DeviceMacroItem tempDeviceMacroItem : deviceMacro.getDeviceMacroItems()) {
           oneUIButtonEventList.addAll(getCommandOwnerByUICommand(tempDeviceMacroItem, protocolEventContainer, maxId));
        }
      } else {
         return new ArrayList<Command>();
      }
   } catch (Exception e) {
     cacheLog.warn("Some component (" + command.getOid() + ":" + command.getDisplayName() + ") referenced a removed object:  " + e.getMessage());
      return new ArrayList<Command>();
   }
   return oneUIButtonEventList;
  }
  
  private void addDeviceCommandEvent(ProtocolCommandContainer protocolEventContainer,
        List<Command> oneUIButtonEventList, DeviceCommand deviceCommand, MaxId maxId) {
     String protocolType = deviceCommand.getProtocol().getType();
     List<ProtocolAttr> protocolAttrs = deviceCommand.getProtocol().getAttributes();

     Command uiButtonEvent = new Command();
     uiButtonEvent.setId(maxId.maxId());
     uiButtonEvent.setProtocolDisplayName(protocolType);
     uiButtonEvent.setDeviceName(deviceCommand.getDevice().getName());
     uiButtonEvent.setDeviceId(Long.toString(deviceCommand.getDevice().getOid()));
     for (ProtocolAttr protocolAttr : protocolAttrs) {
        uiButtonEvent.getProtocolAttrs().put(protocolAttr.getName(), protocolAttr.getValue());
     }
     uiButtonEvent.setLabel(deviceCommand.getName());
     
     // EBR - 20130416 : This has the side effect of changing the id of the uiButtonEvent parameter
     // To an already set id, if that uiButtonEvent is already contained by protocolEventContainer
     protocolEventContainer.addUIButtonEvent(uiButtonEvent);

     oneUIButtonEventList.add(uiButtonEvent);
  }

  //
  // TODO: should be removed
  //
  // - rules should not be defined in terms of controller configuration
  // in the designer but as artifacts, similar to images (and multiple rule
  // files should
  // be supported).
  //
  private String getRulesFileContent() {
    Collection<ControllerConfig> configs = controllerConfigService.listAllConfigs();

    configs.removeAll(controllerConfigService.listAllexpiredConfigs());
    configs.addAll(controllerConfigService.listAllMissingConfigs());

    String result = "";

    for (ControllerConfig controllerConfig : configs) {
      if (controllerConfig.getName().equals("rules.editor")) {
        result = controllerConfig.getValue();
      }
    }

    return result;
  }

  private Set<Sensor> getAllSensorWithoutDuplicate(Collection<Screen> screens, MaxId maxId, List<Sensor> dbSensors) {
    Set<Sensor> sensorWithoutDuplicate = new HashSet<Sensor>();
    Collection<Sensor> allSensors = new ArrayList<Sensor>();

    for (Screen screen : screens) {
      for (Absolute absolute : screen.getAbsolutes()) {
        UIComponent component = absolute.getUiComponent();
        initSensors(allSensors, sensorWithoutDuplicate, component);
      }

      for (UIGrid grid : screen.getGrids()) {
        for (Cell cell : grid.getCells()) {
          initSensors(allSensors, sensorWithoutDuplicate, cell.getUiComponent());
        }
      }
    }

    // PATCH R3181 BEGIN ---8<------
    List<Sensor> duplicateDBSensors = new ArrayList<Sensor>();

    try {
      for (Sensor dbSensor : dbSensors) {
        for (Sensor clientSensor : sensorWithoutDuplicate) {
          if (dbSensor.getOid() == clientSensor.getOid()) {
            duplicateDBSensors.add(dbSensor);
          }
        }
      }
    }

    // TODO :
    // strictly speaking this should be unnecessary if database schema has been
    // configured
    // to enforce correct referential integrity constraints -- this hasn't
    // always been the
    // case so catching the error here. Unfortunately there isn't much we can do
    // in terms
    // of recovery other than have the DBA step in.

    catch (ObjectNotFoundException e) {
      AdministratorAlert.getInstance(AdministratorAlert.Type.DATABASE).alert("Database integrity error -- referencing an unknown entity: {0}, id: {1}, message: {2}", e, e.getEntityName(), e.getIdentifier(), e.getMessage());

      // TODO: the wrong exception type, but it will get propagated back to
      // user's browser

      throw new FileOperationException("Save/Export failed due to database integrity error. This requires administrator intervention " + "to solve. Please avoid making any further changes to your account until this issue has been "
          + "resolved (the integrity offender: " + e.getEntityName() + ", id: " + e.getIdentifier() + ").");
    }

    dbSensors.removeAll(duplicateDBSensors);
    // PATCH R3181 END --->8-------

    // MODELER-396

    // Validate there are no duplicate ids
    Set<Long> ids = new HashSet<Long>();

    // First in sensors from UI
    for (Sensor s : sensorWithoutDuplicate) {
      ids.add(s.getOid());
    }
    if (ids.size() != sensorWithoutDuplicate.size()) {
      AdministratorAlert.getInstance(AdministratorAlert.Type.DESIGNER_STATE).alert("Found sensors with same id but different data");
    }
    // Then in sensors from DB
    ids.clear();
    for (Sensor s : dbSensors) {
      ids.add(s.getOid());
    }
    if (ids.size() != dbSensors.size()) {
      AdministratorAlert.getInstance(AdministratorAlert.Type.DESIGNER_STATE).alert("Found sensors with same id but different data");
    }

    // Then combined
    for (Sensor s : sensorWithoutDuplicate) {
      ids.add(s.getOid());
    }
    if (ids.size() != sensorWithoutDuplicate.size() + dbSensors.size()) {
      AdministratorAlert.getInstance(AdministratorAlert.Type.DESIGNER_STATE).alert("Found sensors with same id but different data");
    }

    // MODELER-396 end

    /*
     * reset sensor oid, avoid duplicated id in export xml. make sure same
     * sensors have same oid.
     */
    /*
     * MODELER-396 for (Sensor sensor : sensorWithoutDuplicate) { long
     * currentSensorId = maxId.maxId(); Collection<Sensor> sensorsWithSameOid =
     * new ArrayList<Sensor>(); sensorsWithSameOid.add(sensor); for (Sensor s :
     * allSensors) { if (s.equals(sensor)) { sensorsWithSameOid.add(s); } } for
     * (Sensor s : sensorsWithSameOid) { s.setOid(currentSensorId); } }
     */
    return sensorWithoutDuplicate;
  } 
  
  private void initSensors(Collection<Sensor> allSensors, Set<Sensor> sensorsWithoutDuplicate, UIComponent component) {
    if (component instanceof SensorOwner) {
      SensorOwner sensorOwner = (SensorOwner) component;
      if (sensorOwner.getSensor() != null) {
        allSensors.add(sensorOwner.getSensor());
        sensorsWithoutDuplicate.add(sensorOwner.getSensor());
      }
    }
  }

  private void initUIComponentBox(Collection<Screen> screens, UIComponentBox uiComponentBox) {
    uiComponentBox.clear();
    for (Screen screen : screens) {
      for (Absolute absolute : screen.getAbsolutes()) {
        UIComponent component = absolute.getUiComponent();
        uiComponentBox.add(component);
      }

      for (UIGrid grid : screen.getGrids()) {
        for (Cell cell : grid.getCells()) {
          uiComponentBox.add(cell.getUiComponent());
        }
      }

      for (Gesture gesture : screen.getGestures()) {
        uiComponentBox.add(gesture);
      }
    }
  }

 /**
  * Executes merge on template, performing appropriate XML escaping and returns result as String.
  * 
  * This is basically a simplified copy of the code from VelocityEngineUtils class of Spring framework,
  * but is required to have access to the context before doing the merge.
  * This allows using an EscapeXmlReference subclass to do proper escaping for XML output.
  * The subclass modifies to standard XML escaping to ensure the XML output of our
  * getPanelXml() methods on the UI model don't get escaped. 
  * 
  * @see https://github.com/SpringSource/spring-framework/blob/master/spring-context-support/src/main/java/org/springframework/ui/velocity/VelocityEngineUtils.java
  * 
  * @param templateLocation
  * @param model
  * @return
  * @throws Exception 
  */
 private String mergeXMLTemplateIntoString(String templateLocation, Map model) throws Exception {
   StringWriter result = new StringWriter();
   VelocityContext velocityContext = new VelocityContext(model);
   EventCartridge ec = new EventCartridge();
   ec.addEventHandler(new EscapeXmlReference() {
     @Override
     public Object referenceInsert(String reference, Object value) {
       int lastDot = reference.lastIndexOf(".");
       if (lastDot != -1) {
         if (".getPanelXml($generationContext)}".equals(reference.substring(lastDot))) {
           return value;
         }
       }
       return super.referenceInsert(reference, value);
     }
   });
   ec.attachToContext(velocityContext);
   velocity.mergeTemplate(templateLocation, "UTF8", velocityContext, result);
   return result.toString();
 }
 
  public void setDeviceService(DeviceService deviceService) {
	this.deviceService = deviceService;
  }

  public void setSwitchService(SwitchService switchService) {
    this.switchService = switchService;
  }
  
  public void setSliderService(SliderService sliderService) {
    this.sliderService = sliderService;
  }
  
  public void setSensorService(SensorService sensorService) {
    this.sensorService = sensorService;
  }
  
  public void setDeviceMacroService(DeviceMacroService deviceMacroService) {
    this.deviceMacroService = deviceMacroService;
  }

  public void setDeviceCommandService(DeviceCommandService deviceCommandService) {
    this.deviceCommandService = deviceCommandService;
  }

  public void setControllerConfigService(ControllerConfigService controllerConfigService) {
    this.controllerConfigService = controllerConfigService;
  }

  public void setVelocity(VelocityEngine velocity) {
    this.velocity = velocity;
  }

  public void setProtocolContainer(ProtocolContainer protocolContainer) {
    this.protocolContainer = protocolContainer;
  }

 /**
   * Helper for logging user information.
   *
   * TODO : should be reused via User domain object
   *
   * @param currentUser   current logged in user (as per the http session associated with this
   *                      thread)
   *
   * @return    string with user name, email, role and account id information
   */
  private String printUserAccountLog(UserAccount currentUserAccount)
  {
    return "(User: " + currentUserAccount.getUsernamePassword().getUsername() +
           ", Email: " + currentUserAccount.getEmail() +
           ", Roles: " + currentUserAccount.getRole() +
           ", Account ID: " + currentUserAccount.getAccount().getOid() +
           ")";
  }

  // Inner Classes -------------------------------------------------------------------------------


  /**
   * Implements a file-based write stream into the cache. The after processing is used to move
   * the downloaded archive (once marked complete and validated) from the temporary download file
   * location to the final location in the filesystem. This should ensure we don't deal with
   * partial downloads.
   */
  private class FileCacheWriteStream extends CacheWriteStream
  {

    /**
     * Path to the temporary download location.
     */
    private File temp;

    /**
     * Constructs a new file based cache write stream.
     *
     * @param tempTarget    initial location where the downloaded bytes are stored
     *
     * @throws FileNotFoundException
     *              if the temporary file target cannot be created or opened
     *
     * @throws SecurityException
     *              if security manager denied access to creating or opening the temporary file
     */
    private FileCacheWriteStream(File tempTarget) throws FileNotFoundException, SecurityException
    {
      super(new BufferedOutputStream(new FileOutputStream(tempTarget)));

      this.temp = tempTarget;
    }

    /**
     * Invoked for streams that have been marked completed upon stream close. <p>
     *
     * Validate the download archive and move it to its final path location before continuing.
     *
     * @throws IOException
     */
    @Override protected void afterClose() throws IOException
    {
      // Can check that we have space on the filesystem to extract the archive, that
      // archive is not corrupt, and specific files are included in the archive.

      validateArchive(temp);


      // Got complete download, archive has been validated. Now make it 'final'.
      // Move is often much faster than copy.

      File finalTarget = getCachedArchive();

      try
      {
        boolean success = temp.renameTo(finalTarget);

        if (!success)
        {
          throw new IOException(MessageFormat.format(
              "Failed to replace existing Beehive archive ''{0}'' with ''{1}''",
              finalTarget.getAbsolutePath(), temp.getAbsolutePath())
          );
        }

        cacheLog.info(
            "Moved ''{0}'' to ''{1}''", temp.getAbsolutePath(), finalTarget.getAbsolutePath()
        );
      }

      catch (SecurityException e)
      {
        throw new IOException(MessageFormat.format(
            "Security manager has denied write access to ''{0}'' : {1}",
            e, finalTarget.getAbsolutePath(), e.getMessage())
        );
      }
    }

    @Override public void close()
    {
      try
      {
        super.close();
      }

      catch (Throwable t)
      {
        cacheLog.warn(
            "Unable to close resource archive cache stream : {0}",
            t, t.getMessage()
        );

      }
    }

    @Override public String toString()
    {
      return "Stream Target : " + temp.getAbsolutePath();
    }
  }

  private static class MaxId {
    Long maxId = 0L;

    public MaxId(Long maxId) {
       this.maxId = maxId;
    }

    public Long maxId() {
       return maxId++;
    }
 }

}

