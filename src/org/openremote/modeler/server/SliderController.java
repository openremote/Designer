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
package org.openremote.modeler.server;

import java.util.ArrayList;

import org.openremote.modeler.client.rpc.SliderRPCService;
import org.openremote.modeler.domain.DeviceCommand;
import org.openremote.modeler.domain.Sensor;
import org.openremote.modeler.domain.Slider;
import org.openremote.modeler.service.DeviceCommandService;
import org.openremote.modeler.service.SensorService;
import org.openremote.modeler.service.SliderService;
import org.openremote.modeler.service.UserService;
import org.openremote.modeler.shared.dto.DTOReference;
import org.openremote.modeler.shared.dto.DeviceCommandDTO;
import org.openremote.modeler.shared.dto.SliderDTO;
import org.openremote.modeler.shared.dto.SliderDetailsDTO;
import org.openremote.modeler.shared.dto.SliderWithInfoDTO;

/**
 * The server side implementation of the RPC service <code>SliderRPCService</code>.
 */
@SuppressWarnings("serial")
public class SliderController extends BaseGWTSpringController implements SliderRPCService {

  private SliderService sliderService;

  @Override
  public void delete(long id) {
    sliderService.delete(id);
  }

  public void setSliderService(SliderService switchService) {
    this.sliderService = switchService;
  }

  @Override
  public SliderDetailsDTO loadSliderDetails(long id) {
	  return sliderService.loadSliderDetailsDTO(id);
  }

  @Override
  public ArrayList<SliderWithInfoDTO> loadAllSliderWithInfosDTO() {
    return new ArrayList<SliderWithInfoDTO>(sliderService.loadAllSliderWithInfosDTO());
  }

  public static SliderWithInfoDTO createSliderWithInfoDTO(Slider slider) {
    return new SliderWithInfoDTO(slider.getOid(), slider.getDisplayName(), (slider.getSetValueCmd() != null) ? slider.getSetValueCmd().getDisplayName() : null, (slider.getSliderSensorRef() != null) ? slider.getSliderSensorRef().getDisplayName() : null,
            slider.getDevice().getDisplayName());
  }

  public static SliderDTO createSliderDTO(Slider slider) {
    SliderDTO sliderDTO = new SliderDTO(slider.getOid(), slider.getDisplayName());
    if (slider.getSetValueCmd() != null) {
      DeviceCommand dc = slider.getSetValueCmd().getDeviceCommand();
      sliderDTO.setCommand(new DeviceCommandDTO(dc.getOid(), dc.getDisplayName(), dc.getFullyQualifiedName(), dc.getProtocol().getType()));
    }
    return sliderDTO;
  }

  @Override
  public void updateSliderWithDTO(SliderDetailsDTO sliderDTO) {
    sliderService.updateSliderWithDTO(sliderDTO);
  }

  @Override
  public void saveNewSlider(SliderDetailsDTO sliderDTO, long deviceId) {
    sliderService.saveNewSlider(sliderDTO, deviceId);
  }

}
