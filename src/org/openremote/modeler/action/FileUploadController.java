/* OpenRemote, the Home of the Digital Home.
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

package org.openremote.modeler.action;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.log4j.Logger;
import org.openremote.modeler.client.Configuration;
import org.openremote.modeler.domain.KnxGroupAddress;
import org.openremote.modeler.domain.User;
import org.openremote.modeler.lutron.ImportException;
import org.openremote.modeler.lutron.LutronHomeworksImporter;
import org.openremote.modeler.server.lutron.importmodel.LutronImportResult;
import org.openremote.modeler.server.lutron.importmodel.Project;
import org.openremote.modeler.service.ResourceService;
import org.openremote.modeler.service.UserService;
import org.openremote.modeler.shared.dto.DTO;
import org.openremote.modeler.utils.ImageRotateUtil;
import org.openremote.modeler.utils.KnxImporter;
import org.openremote.modeler.utils.MultipartFileUtil;
import org.openremote.rest.GenericResourceResultWithErrorMessage;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.multiaction.MultiActionController;

import flexjson.JSONSerializer;

/**
 * The Class is used for uploading files.
 * 
 * @author handy.wang
 */
public class FileUploadController extends MultiActionController implements BeanFactoryAware{

    private static final Logger LOGGER = Logger.getLogger(FileUploadController.class);
   
    /** The resource service. */
    private ResourceService resourceService;
    
    private UserService userService;

    private Configuration configuration;

    private BeanFactory beanFactory;

    /** sets the bean factory
     * @param beanFactory
     */
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }
    /**
     * Import openremote.zip into application, but now is not use.
     * 
     * @param request
     *            the request
     * @param response
     *            the response
     * 
     * @return the model and view
     * @throws IOException 
     */
    public void importFile(HttpServletRequest request, HttpServletResponse response) throws IOException {

      GenericResourceResultWithErrorMessage result = new GenericResourceResultWithErrorMessage();
        try {
          Map<String, Collection<? extends DTO>> importResult = resourceService.getDotImportFileForRender(request.getSession().getId(),
                    MultipartFileUtil.getMultipartFileFromRequest(request, "file").getInputStream());
            
            result.setResult(importResult);
        } catch (Exception e) {
            LOGGER.error("Import file error.", e);
            if (e.getMessage() != null) {
              result.setErrorMessage(e.getMessage());
            } else {
              result.setErrorMessage("Error during import, please report to support");
            }
        }
        JSONSerializer serializer = new JSONSerializer();
        System.out.println("Generated JSON >" + serializer.exclude("*.class").deepSerialize(result) + "<");
        
        response.setHeader("content-type", "text/html");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(serializer.exclude("*.class").deepSerialize(result));
    }

    public void importETS4(HttpServletRequest request, HttpServletResponse response) throws IOException {
        MultipartFile multipartFile = MultipartFileUtil.getMultipartFileFromRequest(request, "file");
        String contentType = multipartFile.getContentType();
        List<KnxGroupAddress> addresses = null;
        HashMap<String, Object> data = new HashMap<String, Object>();
        
        try
        {
          if ("application/octet-stream" .equalsIgnoreCase(contentType) || "application/x-zip-compressed".equalsIgnoreCase(contentType)) {
            addresses = new KnxImporter().importETS4Configuration(multipartFile.getInputStream());
          } else {
            addresses = new KnxImporter().importETS3GroupAddressCsvExport(multipartFile.getInputStream());
          }
          data.put("records", addresses);
        } catch (Exception e)
        {
          LOGGER.error("Could not import ETS data", e);
          data.put("exception", e.toString());
        }
        
        JSONSerializer serializer = new JSONSerializer();
        String jsonResult = serializer.exclude("*.class").deepSerialize(data);
        logger.debug("Responding with string\n" + jsonResult);
        response.setHeader("content-type", "text/html");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().println(jsonResult);
    }

    public void importLutron(HttpServletRequest request, HttpServletResponse response) throws IOException {
      MultipartFile multipartFile = MultipartFileUtil.getMultipartFileFromRequest(request, "lutron");

      LutronImportResult importResult = new LutronImportResult();

      try {
        Project project = LutronHomeworksImporter.importXMLConfiguration(multipartFile.getInputStream());
        importResult.setProject(project);
      } catch (ImportException e) {
        LOGGER.error("Import file error.", e);
        importResult.setErrorMessage(e.getMessage());        
      }
      JSONSerializer serializer = new JSONSerializer();
      System.out.println("Generated JSON >" + serializer.exclude("*.class").deepSerialize(importResult) + "<");
      response.setHeader("content-type", "text/html");
      response.setCharacterEncoding("UTF-8");
      response.getWriter().println(serializer.exclude("*.class").deepSerialize(importResult));
    }
    
    public void importIRPronto(HttpServletRequest request, HttpServletResponse response) throws IOException {     
      // Relay upload to IRService
      HttpClient client = new HttpClient();
      
      client.getParams().setAuthenticationPreemptive(true);
      UserService.UsernamePassword usernamePassword = userService.getCurrentUsernamePassword();
      Credentials defaultCredentials = new UsernamePasswordCredentials(usernamePassword.getUsername(), usernamePassword.getPassword());
      
      URL url = new URL(configuration.getIrServiceRESTRootUrl());
      client.getState().setCredentials(new AuthScope(url.getHost(), url.getPort(), AuthScope.ANY_REALM), defaultCredentials);
        
      PostMethod postMethod = new PostMethod(configuration.getIrServiceRESTRootUrl() + "ProntoFile");
      postMethod.setRequestEntity(new InputStreamRequestEntity(request.getInputStream()));
      postMethod.setRequestHeader("Content-type", request.getHeader("Content-type"));

      int statusCode = client.executeMethod(postMethod);
      
      response.setHeader("Content-type", "text/html");
      response.setCharacterEncoding("UTF-8");      
      response.flushBuffer();
      if (statusCode == 200) {
        response.getWriter().print(postMethod.getResponseBodyAsString());
      } else {
        response.getWriter().print("{\"errorMessage\":\"Communication error with IRService\",\"result\":null}");
      }
      postMethod.releaseConnection();
    }

    /**
     * Sets the resource service.
     * 
     * @param resourceService
     *            the new resource service
     */
    public void setResourceService(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /**
     * upload an image.<br /> your action should be :
     * fileUploadController.htm?method=uploadImage&uploadFieldName=<b>your
     * upload Field Name</b> .
     * 
     * @param request
     * @param response
     * @throws IOException
     */
    public void uploadImage(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String uploadFieldName = request.getParameter("uploadFieldName");

        if (uploadFieldName == null || uploadFieldName.trim().length() == 0) {
            LOGGER.error("The action must have a parameter 'uploadFieldName'");
            return;
        }

        long maxImageSize = 1024 * 1024 * 5;
        MultipartFile multipartFile = MultipartFileUtil.getMultipartFileFromRequest(request, uploadFieldName);
        if (multipartFile.getSize() == 0 || multipartFile.getSize() > maxImageSize) {
            return;
        }
        File file = resourceService.uploadImage(multipartFile.getInputStream(), multipartFile.getOriginalFilename());

        if (("panelImage".equals(uploadFieldName) || "tabbarImage".equals(uploadFieldName)) && file.exists()) {
            rotateBackgroud(file);
            BufferedImage buff = ImageIO.read(file);
            response.getWriter().print(
                    "{\"name\": \"" + resourceService.getRelativeResourcePathByCurrentAccount(file.getName())
                            + "\",\"width\":" + buff.getWidth() + ",\"height\":" + buff.getHeight() + "}");
        } else {
            response.getWriter().print(resourceService.getRelativeResourcePathByCurrentAccount(file.getName()));
        }
    }

    private void rotateBackgroud(File sourceFile) {
        String targetImagePath = sourceFile.getParent() + File.separator + sourceFile.getName().replace(".", "_h.");
        ImageRotateUtil.rotate(sourceFile, targetImagePath, -90);
    }

    public void setUserService(UserService userService) {
      this.userService = userService;
    }

    public void setConfiguration(Configuration configuration) {
      this.configuration = configuration;
    }

}
