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
package org.openremote.modeler.client.widget.buildingmodeler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import net.customware.gwt.dispatch.client.DispatchAsync;

import org.openremote.modeler.client.ModelerGinjector;
import org.openremote.modeler.client.event.DeviceUpdatedEvent;
import org.openremote.modeler.client.lutron.importmodel.AreaOverlay;
import org.openremote.modeler.client.lutron.importmodel.LutronImportResultOverlay;
import org.openremote.modeler.client.lutron.importmodel.OutputOverlay;
import org.openremote.modeler.client.lutron.importmodel.ProjectOverlay;
import org.openremote.modeler.client.lutron.importmodel.RoomOverlay;
import org.openremote.modeler.client.utils.ArrayOverlay;
import org.openremote.modeler.client.utils.CheckboxCellHeader;
import org.openremote.modeler.client.utils.CheckboxCellHeader.ChangeValue;
import org.openremote.modeler.shared.dto.DeviceDTO;
import org.openremote.modeler.shared.lutron.ImportConfig;
import org.openremote.modeler.shared.lutron.ImportLutronConfigAction;
import org.openremote.modeler.shared.lutron.ImportLutronConfigResult;
import org.openremote.modeler.shared.lutron.OutputImportConfig;
import org.openremote.modeler.shared.lutron.OutputType;

import com.google.gwt.cell.client.CheckboxCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiFactory;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.TextColumn;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.FileUpload;
import com.google.gwt.user.client.ui.FormPanel;
import com.google.gwt.user.client.ui.FormPanel.SubmitCompleteEvent;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.DefaultSelectionEventManager;
import com.google.gwt.view.client.MultiSelectionModel;
import com.google.gwt.view.client.SelectionChangeEvent;

public class LutronImportWizard extends DialogBox {

  private static LutronImportWizardUiBinder uiBinder = GWT.create(LutronImportWizardUiBinder.class);

  interface LutronImportWizardUiBinder extends UiBinder<Widget, LutronImportWizard> {
  }
  
  private EventBus eventBus;
  
  private DeviceDTO deviceDTO;
  
  private final MultiSelectionModel<OutputImportConfig> selectionModel = new MultiSelectionModel<OutputImportConfig>();

  final String NoScene = null;
  final String NoLevel = null;
  final String NoKey = null;
  
  @UiFactory
  DialogBox itself() {
    return this;
  }

  public LutronImportWizard(final DeviceDTO deviceDTO, final EventBus eventBus) {
    this.eventBus = eventBus;
    this.deviceDTO = deviceDTO;

    uiBinder.createAndBindUi(this);
    importButton.setEnabled(false);
    mainLayout.setSize("50em", "20em");
    center();
        
    final CheckboxCellHeader selectionHeader = new CheckboxCellHeader(new CheckboxCell());
    selectionHeader.setChangeValue(new ChangeValue() {      
      @Override
      public void changedValue(int columnIndex, Boolean value) {
        if (value) {
          for (OutputImportConfig oic : table.getVisibleItems()) {
            selectionModel.setSelected(oic, true);
          }
        } else {
          selectionModel.clear();          
        }
      }
    });
    
    TextColumn<OutputImportConfig> areaNameColumn = new TextColumn<OutputImportConfig>() {
      @Override
      public String getValue(OutputImportConfig outputConfig) {
        return outputConfig.getAreaName();
      }
    };
    TextColumn<OutputImportConfig> roomNameColumn = new TextColumn<OutputImportConfig>() {
      @Override
      public String getValue(OutputImportConfig outputConfig) {
        return outputConfig.getRoomName();
      }
    };
    TextColumn<OutputImportConfig> outputNameColumn = new TextColumn<OutputImportConfig>() {
      @Override
      public String getValue(OutputImportConfig outputConfig) {
        return outputConfig.getOutputName();
      }
    };
    TextColumn<OutputImportConfig> outputTypeColumn = new TextColumn<OutputImportConfig>() {
      @Override
      public String getValue(OutputImportConfig outputConfig) {
        return outputConfig.getType().toString();
      }
    };
    
    selectionModel.addSelectionChangeHandler(new SelectionChangeEvent.Handler() {      
      @Override
      public void onSelectionChange(SelectionChangeEvent event) {
        // Have import button only enabled if user has selected items to import
        importButton.setEnabled(!selectionModel.getSelectedSet().isEmpty());

        // And manage the select all/deselect all header based on individual selection
        if (selectionModel.getSelectedSet().isEmpty()) {
          selectionHeader.setValue(false);
        }
        if (selectionModel.getSelectedSet().size() == table.getVisibleItemCount()) {
          selectionHeader.setValue(true);
        }
      }
    });

    table.setSelectionModel(selectionModel, DefaultSelectionEventManager.<OutputImportConfig> createCheckboxManager());
    
    // Add the columns.
    Column<OutputImportConfig, Boolean> checkColumn = new Column<OutputImportConfig, Boolean>(new CheckboxCell(false, false)) {
      @Override
      public Boolean getValue(OutputImportConfig object) {
        return selectionModel.isSelected(object);
      }
    };
    table.addColumn(checkColumn, selectionHeader);
    table.addColumn(areaNameColumn, "Area");
    table.addColumn(roomNameColumn, "Room");
    table.addColumn(outputNameColumn, "Output");
    table.addColumn(outputTypeColumn, "Type");
    table.setRowCount(0); // No rows for now, otherwise loading indicator is displayed
    
    errorMessageLabel.setText("");

    uploadForm.setEncoding(FormPanel.ENCODING_MULTIPART);
    uploadForm.setMethod(FormPanel.METHOD_POST);
    uploadForm.setAction(GWT.getModuleBaseURL() + "fileUploadController.htm?method=importLutron");

    uploadForm.addSubmitCompleteHandler(new FormPanel.SubmitCompleteHandler() {
    
      @Override
      public void onSubmitComplete(SubmitCompleteEvent event) {
        table.setRowCount(0); // No rows for now, otherwise loading indicator is displayed

        LutronImportResultOverlay importResult = LutronImportResultOverlay.fromJSONString(event.getResults());
        if (importResult.getErrorMessage() != null) {
          reportError(importResult.getErrorMessage());
          return;
        }
        
        ProjectOverlay projectOverlay = importResult.getProject();
        if (projectOverlay.getAreas() == null) {
          reportError("File does not contain any information");
          return;
        }
        
        List<OutputImportConfig> outputs = new ArrayList<OutputImportConfig>();        
        ArrayOverlay<AreaOverlay> areas = projectOverlay.getAreas();
        for (int i = 0; i < areas.length(); i++) {
          AreaOverlay areaOverlay = areas.get(i);
          if (areaOverlay.getRooms() != null) {
            for (int j = 0; j < areaOverlay.getRooms().length(); j++) {
              RoomOverlay roomOverlay = areaOverlay.getRooms().get(j);
              if (roomOverlay.getOutputs() != null) {
                for (int k = 0; k < roomOverlay.getOutputs().length(); k++) {
                  OutputOverlay outputOverlay = roomOverlay.getOutputs().get(k);
                  outputs.add(new OutputImportConfig(outputOverlay.getName(), OutputType.valueOf(outputOverlay.getType()), outputOverlay.getAddress(), roomOverlay.getName(), areaOverlay.getName()));
                }
              }
            }
          }
        }        
        table.setRowData(outputs);
       }
    });
  }
  
  private void reportError(String errorMessage) {
    uploadForm.reset();
    errorMessageLabel.setText(errorMessage);
  }

  @UiField
  CellTable<OutputImportConfig> table;
  
  @UiField
  DockLayoutPanel mainLayout;

  @UiField
  Label errorMessageLabel;
  
  @UiField
  Button loadButton;
  
  @UiField
  Button cancelButton;
  
  @UiField
  Button importButton;
  
  @UiField
  FormPanel uploadForm;
  

  @UiField
  FileUpload uploadField;
  
  @UiHandler("loadButton")
  void handleSubmit(ClickEvent e) {
    // TODO: this is not really working because GUI is not updated while file uploads, only afterwards
    selectionModel.clear(); // Must clear selection, otherwise keeps previous selection
    table.setVisibleRangeAndClearData(table.getVisibleRange(), false);
    errorMessageLabel.setText("");
  }
  
  @UiHandler("cancelButton")
  void handleClick(ClickEvent e) {
    hide();
  }
  
  @UiHandler("importButton")
  void handleImportClick(ClickEvent e) {
    ModelerGinjector injector = GWT.create(ModelerGinjector.class);
    DispatchAsync dispatcher = injector.getDispatchAsync();

    ImportConfig importConfig = new ImportConfig();
    importConfig.setOutputs(new HashSet<OutputImportConfig>(selectionModel.getSelectedSet()));
    
    ImportLutronConfigAction action = new ImportLutronConfigAction(importConfig);
    action.setDevice(this.deviceDTO);
    
    dispatcher.execute(action, new AsyncCallback<ImportLutronConfigResult>() {

      @Override
      public void onFailure(Throwable caught) {
        reportError(caught.getMessage());
      }

      @Override
      public void onSuccess(ImportLutronConfigResult result) {
         eventBus.fireEvent(new DeviceUpdatedEvent(LutronImportWizard.this.deviceDTO));
         hide();
      }
      
    });
  }
}
